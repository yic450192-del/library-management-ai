# 07 · 架构设计 Architecture（指导书步骤 3）

> 指导书要求：结合某一种具体的框架（Java EE / Spring 等）设计系统的架构，
> 要求采用**分层的架构**和 **MVC 模式**，用**包图**表达架构。
> 本设计采用 Java 17 + Spring Boot 3.x。

## 1. 分层架构

```
┌──────────────────────────────────────────────────────┐
│  Presentation Layer  表现层（MVC 的 C + V）            │
│  @RestController / @ControllerAdvice / DTO            │
│  职责：参数校验、鉴权拦截、HTTP 语义转换。不放业务规则。   │
├──────────────────────────────────────────────────────┤
│  Application Layer  应用服务层（用例编排）              │
│  XxxApplicationService                                │
│  职责：事务边界、用例流程编排、调用领域服务与 Repository。 │
├──────────────────────────────────────────────────────┤
│  Domain Layer  领域层（MVC 的 M）                      │
│  Entity / Value Object / Policy / Domain Service       │
│  职责：业务概念与业务规则。不依赖 Spring，不依赖持久化。   │
├──────────────────────────────────────────────────────┤
│  Infrastructure Layer  基础设施层                      │
│  Repository 实现（Spring Data JPA）/ 外部接口适配       │
│  职责：实现领域层定义的 Repository 接口，技术细节。       │
└──────────────────────────────────────────────────────┘
                        ↑
                  Test Layer 贯穿各层
```

**依赖方向**：单向向下。上层依赖下层，下层不知道上层。
Controller **不得**直接依赖 Repository，必须经过 Application Service（NFR-001）。

## 2. MVC 在本设计中的落点

| MVC | 本系统对应 |
|---|---|
| Model | Domain Layer 的实体、值对象、策略对象；以及面向视图的 DTO（只读投影） |
| View | 实验一不实现前端；以 REST JSON 作为表现，实验二可补 Thymeleaf / Vue |
| Controller | `presentation` 包下的 `@RestController`，只做参数绑定、鉴权、结果封装 |

Controller 内**不允许**出现 `if (loanCount >= maxBooks)` 这类判断——那是 `BorrowPolicy` 的职责。

## 3. 模块划分

| 模块 | 包 | 职责 | 对应需求 |
|---|---|---|---|
| reader | `domain.reader` | 读者、学生/教师读者、注册 | FR-001 |
| card | `domain.card` | 借阅证的办理、挂起、注销 | FR-009 FR-010 |
| catalog | `domain.catalog` | 题名、馆藏副本、检索 | FR-002 FR-013~FR-016 |
| circulation | `domain.circulation` | 借书、还书、借阅记录 | FR-006 FR-007 |
| reservation | `domain.reservation` | 预约排队与失效 | FR-003 FR-004 |
| fine | `domain.fine` | 罚款规则与罚款记录 | FR-007 FR-017 |
| admin | `domain.admin` | 管理员、借阅规则与罚款规则维护 | FR-011 FR-012 FR-017 |

## 4. 权限控制策略

三类角色严格分离（BR-013 / BR-014）：

| 角色 | 可执行 | 禁止 |
|---|---|---|
| Reader | 注册、查书、预约、取消预约、查本人借阅信息 | 借书、还书、任何维护操作 |
| Librarian | 借书、还书、查询任意读者借阅信息 | 增删管理员、增删图书与题名、改规则 |
| SystemAdmin | 办/销证、增删管理员、增删副本与题名、维护规则 | 借书、还书 |

**实现方式**：`presentation` 层用自定义注解 `@RequireRole({LIBRARIAN})` + Spring AOP 切面拦截，
在**进入 Controller 方法前**完成角色校验；领域方法内部再做一次断言式校验（防御性），
避免绕过 Web 层直接调用 Service 时失控。

实验一不做真实登录认证，请求头携带 `X-Operator-Id` 与 `X-Operator-Role` 代表操作人；
实验二接入 Spring Security 后只需替换解析方式，切面逻辑不变。

## 5. 异常处理策略

| 层次 | 处理 |
|---|---|
| Domain | 抛**业务异常** `BusinessException(ErrorCode, detail)`。错误码集中在 `ErrorCode` 枚举：`CARD_NOT_FOUND`、`CARD_NOT_ACTIVE`、`QUOTA_EXCEEDED`、`OVERDUE_LOAN_EXISTS`、`ITEM_NOT_OWNED`、`ITEM_NOT_AVAILABLE`、`LOAN_NOT_FOUND`、`RESERVATION_DUPLICATE`、`ROLE_FORBIDDEN` 等（见 `domain/exception/ErrorCode.java`） |
| Application | 不吞异常，仅补充上下文（如把 readerId 写进异常） |
| Presentation | `@RestControllerAdvice` 统一捕获，映射为 HTTP 语义 + 统一响应体 `{code, message, data}` |

映射规则：业务规则违反 → `409 Conflict`；资源不存在 → `404`；参数错误 → `400`；未鉴权 → `401/403`。

**不返回裸堆栈**，message 用中文业务语言（如"该读者存在超期未还图书，请先归还"）。

## 6. 事务边界

事务开在 **Application Service**，不在 Controller，也不在 Domain。

| 用例 | 事务范围 | 理由 |
|---|---|---|
| 办理借书 | `CirculationApplicationService.borrow()` 整体 | 要同时写 Loan + 改 LibraryItem 状态 + 改读者在借数，必须原子 |
| 办理还书 | `CirculationApplicationService.returnItem()` 整体 | Loan 结清 + 副本回馆 + 可能写 FineRecord，必须原子 |
| 预约 | `ReservationApplicationService.reserve()` | 写 Reservation + 计算 queuePosition |
| 规则维护 | `AdminApplicationService` 各方法 | 单表写，仍加事务保证一致 |

**并发控制**：借书时对目标 `LibraryItem` 加**乐观锁**（`@Version`），防止两个管理员同时借同一副本；
乐观锁冲突重试 1 次后仍失败则向上抛 `ConcurrentOperationException`。

## 7. 业务规则放在哪些类

| 规则 | 归属类 | 不放哪里 |
|---|---|---|
| 借阅数量上限、借阅期限、续借 | `BorrowPolicy` | 不放 Controller、不放 Reader |
| 超期天数与罚金计算 | `FineRule` + `FineCalculator` | 不放 Loan 实体内部 |
| 借阅证有效性 | `BorrowCard.isValid()` | 不放 Service 的 if 分支 |
| 副本是否可借 | `LibraryItem.isAvailable()` | 不放 SQL 里 |
| 预约排队与失效 | `Reservation` + `ReservationService` | 不放 Controller |
| 删除前置校验 | 各聚合根自身 + Application Service | 不放数据库触发器 |

## 8. 使用的设计模式

| 模式 | 应用位置 | 解决什么 |
|---|---|---|
| **Strategy** | `BorrowPolicy` 按 ReaderType 取策略；`FineRule` 按 ItemType 取策略 | 不同读者/借出物规则不同，避免 if-else 分支爆炸 |
| **Factory Method** | `ReaderFactory` 按 ReaderType 创建 StudentReader/TeacherReader；`LibraryItemFactory` 按 ItemType 创建 Book/Magazine/Thesis | 子类创建逻辑集中 |
| **Repository** | 领域层定义接口，基础设施层用 Spring Data JPA 实现 | 领域层不依赖持久化框架（NFR-003） |
| **Service Layer** | `XxxApplicationService` 编排用例 | 事务边界与流程调度集中 |
| **DTO** | `dto` 包下的 Request / Response | 隔离接口层与领域层，避免实体直接暴露 |
| **Facade** | `CirculationService` 对 Controller 暴露单一入口 | 降低表现层与领域层的耦合面 |
| **Template Method** | 抽象 `Policy` 基类的校验骨架 | 不同策略复用校验流程 |
| **MVC** | presentation / domain 分离 | 业务规则不进 Controller |

## 9. 技术选型与理由

| 选型 | 理由 |
|---|---|
| Java 17 | Spring Boot 3.x 基线版本；课程材料指定 |
| Spring Boot 3.x | 指导书点名的框架；自动装配 + 内嵌容器，降低实验二实现成本 |
| Spring Data JPA | 直接实现领域层定义的 Repository 接口，领域层零污染 |
| MySQL 8.0 | 本机已有；DDL 可真机验证约束（见 `db/schema.sql`） |
| JUnit 5 + MockMvc | 核心用例自动化测试（NFR-005） |
| PlantUML | UML 文本化入 Git（NFR-006） |
