# 16 · 任务拆解 Tasks（实验二步骤 7）

> 依据：全部 `specs/`（`constitution.md` + `02` + `03` + `09` + `13` + `14` + `15`）
> 用途：给编码 Agent 的执行清单。**一个任务 = 一次可独立验证的修改 = 一个 commit。**
> 铁律：`specs/` 已 baseline，实现阶段**不得修改任何 specs 文件**（constitution 第 4 条）。

---

## 1. 拆解原则

| 原则 | 说明 |
|---|---|
| 粒度 | 一个任务应在一次对话内完成、能独立跑通测试。超过就继续拆 |
| 可验证 | 每个任务都有明确的验收标准，且能映射到 `15-test-plan.md` 的用例 |
| 单向依赖 | 只允许后面的任务依赖前面的，禁止循环 |
| 文件边界 | 每个任务声明"允许修改"的文件范围，防止 Agent 顺手改别的模块 |
| 领域层冻结 | `src/main/java/.../domain/**` 在实验一已存在，实验二**原则上只读**；确需调整时单独开 TASK 并说明理由 |

> **关于领域层为什么只读**：实验一的 36 个领域类是本设计的核心资产，已被 `javac` 验证。
> 实验二如果用 JPA 注解污染它们（NFR-003 立刻破功），前面所有分层论证都不成立。
> 所以持久化一律走 **PO 分离**（见 `09-design-model.md` 第 3 节）。

---

## 2. 依赖关系

```
TASK-001 项目骨架
  ├─ TASK-002 统一响应 / 异常 / 错误码
  ├─ TASK-003 持久化对象与 Repository 适配
  │    ├─ TASK-004 规则装载（BorrowPolicy / FineRule）
  │    ├─ TASK-005 读者注册与查询
  │    │    └─ TASK-006 借阅证管理
  │    ├─ TASK-007 题名与馆藏管理
  │    ├─ TASK-008 办理借书 ★   ← 依赖 004 / 006 / 007
  │    ├─ TASK-009 办理还书 ★   ← 依赖 004 / 008
  │    ├─ TASK-010 预约与取消   ← 依赖 005 / 007
  │    └─ TASK-011 借阅与罚款查询
  └─ TASK-012 管理员管理与权限拦截
TASK-013 测试补全与覆盖率    ← 依赖 008~012
TASK-014 交付物（README / curl 脚本 / AI 记录）
```

关键路径：`001 → 003 → 007 → 008 → 009 → 013`。这条链上任何一环卡住都会拖住验收。

---

## 3. 任务清单

### TASK-001 初始化项目结构

| 项 | 内容 |
|---|---|
| 目标 | 建可构建的 Spring Boot 3.x 工程，包结构与 `07-architecture.md` 的四层一致 |
| 输入规格 | `07-architecture.md`、`constitution.md` 第 5 条 |
| 允许修改 | `pom.xml`、`src/main/resources/application*.yml`、`src/main/java/.../LibraryApplication.java`、各层空包（`presentation` / `application` / `infrastructure` / `dto`） |
| 禁止修改 | `specs/**`、`src/main/java/.../domain/**` |
| 实现要求 | ① Java 17 + Spring Boot 3.x；依赖：Web / Data JPA / Validation / H2（test）+ MySQL（runtime）<br/>② 建四个包，只放 `package-info.java` 或空目录，**不写业务**<br/>③ `application.yml` 配两份 profile：`dev`（MySQL）、`test`（H2 内存）<br/>④ 启动类只做 `SpringApplication.run` |
| 验收标准 | `mvn -q clean compile` 成功；`mvn test` 能跑（0 个测试也算通过）；启动后 `/actuator/health` 或空白首页返回 200 |
| 建议测试 | 无（骨架任务） |

### TASK-002 统一响应、全局异常与错误码补齐

| 项 | 内容 |
|---|---|
| 目标 | 落地 `14-api-spec.md` 第 1 章的响应外壳与第 1.4 节的错误码 |
| 输入规格 | `14-api-spec.md` 1.1 / 1.4；`09-design-model.md` 7.2 |
| 允许修改 | `domain/exception/ErrorCode.java`（**仅补枚举值，不改已有项**）、`presentation/ApiResponse.java`、`presentation/GlobalExceptionHandler.java`、`presentation/ErrorCodeAdvice` |
| 禁止修改 | 其余 domain 文件、`specs/**` |
| 实现要求 | ① `ApiResponse<T>{code,message,data,timestamp}` 静态工厂 `ok()` / `fail()`<br/>② `@RestControllerAdvice` 把 `BusinessException` 按 `ErrorCode.httpStatus()` 映射为 HTTP 状态<br/>③ 参数校验异常 → `VALIDATION_FAILED`(400)<br/>④ **补 ErrorCode 缺失项**：`VALIDATION_FAILED`、`UNAUTHORIZED`、`READER_NOT_FOUND`、`TITLE_NOT_FOUND`、`RESERVATION_NOT_FOUND`、`POLICY_NOT_FOUND`、`FINE_RULE_NOT_FOUND`、`CARD_DUPLICATE`、`READER_DUPLICATE`、`BARCODE_DUPLICATE`、`ISBN_DUPLICATE`、`ITEM_HAS_ACTIVE_LOAN`、`RESERVATION_EXISTS`、`CONCURRENT_MODIFICATION`、`RULE_INVALID`<br/>⑤ 补完后**重跑领域层纯编译**，确认零依赖仍成立 |
| 验收标准 | 抛 `BusinessException(QUOTA_EXCEEDED)` 返回 409 且 `code=QUOTA_EXCEEDED`；`javac` 纯编译 0 错误 |
| 建议测试 | TC-103（借证无效时验证状态码与 code） |

### TASK-003 持久化对象与 Repository 适配

| 项 | 内容 |
|---|---|
| 目标 | 领域实体与数据库之间的桥：PO + Mapper + Spring Data + 领域接口实现 |
| 输入规格 | `13-database-design.md` 全部 11 张表；`09-design-model.md` 7.1 |
| 允许修改 | `infrastructure/persistence/**`（`*PO`、`*JpaRepository`、`*RepositoryAdapter`、`*Mapper`） |
| 禁止修改 | `domain/**`（尤其**不得给领域实体加 JPA 注解**）、`specs/**` |
| 实现要求 | ① 11 张表各一个 PO，字段用 JPA 注解，`LibraryItemPO.version` 加 `@Version`（乐观锁）<br/>② `JpaXxxRepository extends JpaRepository<XxxPO, Long>`，每聚合一个<br/>③ `XxxRepositoryAdapter implements domain.repository.XxxRepository`，内部做 PO↔实体转换<br/>④ 单表继承用 `@DiscriminatorColumn`（`reader_type` / `item_type`）<br/>⑤ `LocalDate` / `BigDecimal` 映射不得用 `double` |
| 验收标准 | 启动时 JPA 建表成功（dev profile 连 MySQL 时能对上 `db/schema.sql`）；`ReaderRepository.findById` 能读到种子数据；领域层 `javac` 仍 0 错误 |
| 建议测试 | 冒烟：保存再查一条 `Reader`，断言字段一致 |

### TASK-004 规则装载（策略对象从库读取）

| 项 | 内容 |
|---|---|
| 目标 | 让 `BorrowPolicy` / `FineRule` 的取值来自 `borrow_policies` / `fine_rules` 表，而非常量 |
| 输入规格 | `09-design-model.md` 7.1.3、`13-database-design.md`、`constitution.md` 第 8 条 |
| 允许修改 | `infrastructure/persistence/**` 的规则相关部分、`application/rule/**` |
| 禁止修改 | `domain/policy/**`、`domain/fine/FineRule.java`（已有类不改）、`specs/**` |
| 实现要求 | ① `BorrowPolicyRepositoryAdapter` 实现 `findByReaderType`<br/>② `FineRuleRepositoryAdapter` 实现 `findByItemType`<br/>③ 规则变更**不缓存**或缓存带失效，改完下一次借还立即生效（对齐 `14-api-spec.md` 3.6 的说明）<br/>④ 查不到规则抛 `POLICY_NOT_FOUND` / `FINE_RULE_NOT_FOUND` |
| 验收标准 | 改 `borrow_policies.max_books` 为 6，同一读者第 6 本可借；改回 5 后第 6 本被拒 |
| 建议测试 | TC-001、TC-004（规则层）、TC-105 |

### TASK-005 读者注册与查询

| 项 | 内容 |
|---|---|
| 目标 | UC-001 注册；读者详情读取 |
| 输入规格 | `14-api-spec.md` 3.1；`03-use-cases.md` UC-001 |
| 允许修改 | `presentation/reader/**`、`application/reader/**`、`dto/reader/**` |
| 禁止修改 | `domain/**`、`specs/**` |
| 实现要求 | ① `POST /api/readers` 按 `readerType` 创建 `StudentReader` / `TeacherReader`<br/>② 学号/工号重复 → `READER_DUPLICATE`<br/>③ 用 `@Valid` 做必填与枚举校验<br/>④ `GET /api/readers/{id}` 返回含当前有效 `cardNo` |
| 验收标准 | 注册返回 201 与 `readerId`；重复学号返回 409 `READER_DUPLICATE` |
| 建议测试 | TC-101 |

### TASK-006 借阅证管理

| 项 | 内容 |
|---|---|
| 目标 | UC-201 办证、UC-202 注销 |
| 输入规格 | `14-api-spec.md` 3.3；BR-001 / BR-003 / BR-015 |
| 允许修改 | `presentation/card/**`、`application/card/**`、`dto/card/**`、`infrastructure/persistence/` 中证号序列相关 |
| 禁止修改 | `domain/**`、`specs/**` |
| 实现要求 | ① 证号**后端生成**：`CARD + yyyy + 6 位序号`，序号取当年最大值 +1（事务内）<br/>② 一读者至多一张 ACTIVE 证 → `CARD_DUPLICATE`<br/>③ 注销前查在借 → `CARD_HAS_ACTIVE_LOANS`<br/>④ 仅 `SYSTEM_ADMIN` 可调用 |
| 验收标准 | 同一读者二次办证被拒；有在借记录时注销被拒且证仍 ACTIVE |
| 建议测试 | TC-102、TC-119 |

### TASK-007 题名与馆藏管理

| 项 | 内容 |
|---|---|
| 目标 | UC-205~208：加/删题名、加/删副本 |
| 输入规格 | `14-api-spec.md` 3.6；BR-015 |
| 允许修改 | `presentation/catalog/**`、`application/catalog/**`、`dto/catalog/**` |
| 禁止修改 | `domain/**`、`specs/**` |
| 实现要求 | ① 加题名：ISBN 唯一 → `ISBN_DUPLICATE`<br/>② 加副本：`itemType` **继承自题名**，不接受前端传入；条码唯一 → `BARCODE_DUPLICATE`<br/>③ 删题名：有副本 → `TITLE_HAS_COPIES`，有有效预约 → `RESERVATION_EXISTS`<br/>④ 删副本：在借 → `ITEM_HAS_ACTIVE_LOAN`<br/>⑤ 仅 `SYSTEM_ADMIN` |
| 验收标准 | 三条删除前置校验都能被触发；副本类型与题名一致 |
| 建议测试 | TC-116、TC-117 |

### TASK-008 办理借书 ★核心

| 项 | 内容 |
|---|---|
| 目标 | UC-101 全链路：HTTP → ApplicationService（事务）→ CirculationService → Repository |
| 输入规格 | `10-sequence-borrow-book.puml`、`09-design-model.md` 7.2 算法 6、`15-test-plan.md` TC-103~107 |
| 允许修改 | `presentation/circulation/**`、`presentation/card/CardEligibilityController.java`、`application/circulation/**`、`dto/circulation/**` |
| 禁止修改 | `domain/circulation/CirculationService.java`（编排已存在，**直接用**）、`specs/**` |
| 实现要求 | ① `CirculationApplicationService.borrow` 加 `@Transactional`，入口校验 `Role.LIBRARIAN`<br/>② 直接委托 `circulationService.borrow(cardNo, barcode, operatorId)`，**不得复制校验逻辑**<br/>③ 校验顺序严格按 `CARD → Reader → 额度 → 超期 → 副本`，不可调整<br/>④ 领域层抛的 `BusinessException` 原样冒泡，由 TASK-002 的 advice 转 HTTP<br/>⑤ 响应 DTO 字段与 `14-api-spec.md` ⑦ 完全一致（`dueDate` 用 `yyyy-MM-dd`）<br/>⑥ 补预检接口 `GET /api/borrow-cards/{cardNo}/eligibility`（见 `14-api-spec.md` 3.7 ㉓）：**只读、无 `@Transactional`、不写任何表** |
| 验收标准 | 成功借书 201 且 `dueDate = borrowDate + 期限`；4 类失败各返回对应 code；**失败时副本状态未变**；连续调用预检两次，`loans` 表不新增 |
| 建议测试 | TC-103、TC-104、TC-105、TC-106、TC-107、TC-120 |

### TASK-009 办理还书 ★核心

| 项 | 内容 |
|---|---|
| 目标 | UC-102：还书 + 超期计罚 + 预约转 READY |
| 输入规格 | `11-sequence-return-book.puml`、`09-design-model.md` 7.3.2、TC-108~110 |
| 允许修改 | 同 TASK-008 的还书部分；`application/circulation/ReturnService` |
| 禁止修改 | `domain/circulation/CirculationService.java`、`specs/**` |
| 实现要求 | ① 只接收 `barcode`，不要求借阅证<br/>② 委托 `circulationService.returnItem`，返回其 `ReturnResult`<br/>③ 若该题名有 WAITING 预约，队首转 `READY`、副本转 `RESERVED`（同事务）<br/>④ 响应含 `overdue` / `overdueDays` / `fine` 三块<br/>⑤ 补预览接口 `GET /api/circulation/return-preview`（见 `14-api-spec.md` 3.7 ㉔）：只读，`estimatedFine` 用注入的 `Clock` 估算，字段名必须带 `estimated` 前缀 |
| 验收标准 | 未超期返回 `fine: null`；超期 15 天返回 `3.00`；副本状态回 `AVAILABLE`（有预约则 `RESERVED`）；预览后借阅记录仍为 `BORROWED` |
| 建议测试 | TC-108、TC-109、TC-110、TC-121 |

### TASK-010 预约与取消

| 项 | 内容 |
|---|---|
| 目标 | UC-003 / UC-004 |
| 输入规格 | `12-sequence-reserve-book.puml`、`09-design-model.md` 7.2 算法 5 |
| 允许修改 | `presentation/reservation/**`、`application/reservation/**`、`dto/reservation/**` |
| 禁止修改 | `domain/reservation/**`、`specs/**` |
| 实现要求 | ① 位次 = 该题名 WAITING 数 + 1，`expireAt = now + 3 天`<br/>② 重复有效预约 → `RESERVATION_DUPLICATE`，并靠 `uk_reservation_active` 兜底并发<br/>③ 取消：只能取消自己的，越权 `ROLE_FORBIDDEN`；状态转 `CANCELLED` |
| 验收标准 | 首次预约为第 1 位，第二位读者为第 2 位；重复预约 409 |
| 建议测试 | TC-007、TC-113、TC-114、TC-118 |

### TASK-011 借阅与罚款查询

| 项 | 内容 |
|---|---|
| 目标 | UC-005 / UC-103：读者查自己、管理员查任意，含超期标记 |
| 输入规格 | `14-api-spec.md` 3.4 ⑨⑩ |
| 允许修改 | `application/query/**`、`presentation/reader/LoanQueryController` |
| 禁止修改 | `domain/**`、`specs/**` |
| 实现要求 | ① `READER` 只能查 `readerId == X-Operator-Id`，否则 `ROLE_FORBIDDEN`<br/>② `overdue` / `overdueDays` 用注入的 `Clock` 现算，**不读库里的派生列**<br/>③ 支持 `status` 过滤与分页<br/>④ 罚款列表按 `createdAt` 倒序 |
| 验收标准 | 读者查他人 403；超期记录 `overdue=true` 且天数正确 |
| 建议测试 | TC-111、TC-112 |

### TASK-012 管理员管理与权限拦截

| 项 | 内容 |
|---|---|
| 目标 | UC-203 / UC-204 + 统一权限校验 |
| 输入规格 | `07-architecture.md` 权限矩阵、BR-013 / BR-014 |
| 允许修改 | `presentation/admin/**`、`presentation/security/**`（拦截器或 `HandlerInterceptor`）、`application/admin/**` |
| 禁止修改 | `domain/**`、`specs/**` |
| 实现要求 | ① 读 `X-Operator-Id` / `X-Operator-Role`，缺失 → `UNAUTHORIZED`(401)<br/>② 管理端路径统一要求 `SYSTEM_ADMIN`，流通端要求 `LIBRARIAN`，读者端要求 `READER`<br/>③ 角色校验放在**应用服务入口**，不在 Controller 里堆 if（constitution 第 7 条）<br/>④ 添加/删除管理员 |
| 验收标准 | 无请求头 401；角色不符 403；管理员增删成功 |
| 建议测试 | TC-115 |

### TASK-013 测试补全与覆盖率

| 项 | 内容 |
|---|---|
| 目标 | 让 `15-test-plan.md` 的 TC-001~007、TC-101~119 全部落地并通过 |
| 输入规格 | `15-test-plan.md` 全部 |
| 允许修改 | `src/test/**` |
| 禁止修改 | `src/main/**`（若测试暴露实现 bug，另开修复任务并记录，不得边测边改） |
| 实现要求 | ① 单测用内存 Repository 或 Mockito，不启动 Spring<br/>② 集成测试用 `@SpringBootTest` + `MockMvc` + H2<br/>③ 时间一律用 `Clock.fixed`，禁 `LocalDate.now()`<br/>④ 每条用例注释标注对应 TC 编号<br/>⑤ 断言 `BigDecimal` 用 `compareTo` |
| 验收标准 | `mvn test` 全绿；领域层行覆盖 ≥ 80%、整体 ≥ 70%；全量 < 60 秒 |
| 建议测试 | 本身即测试任务 |

### TASK-014 交付物与记录

| 项 | 内容 |
|---|---|
| 目标 | README、curl 验证脚本、AI 使用记录、过程日志 |
| 输入规格 | `README.md`（实验一版）、`19-ai-usage-log.md`、`docs/实验过程日志.md` |
| 允许修改 | `README.md`、`scripts/api-smoke.sh`（或 `.http`）、`specs/19-ai-usage-log.md`、`docs/实验过程日志.md` |
| 禁止修改 | 其他 specs（除 19 号日志）、`src/main/**` |
| 实现要求 | ① README 补：技术栈、启动方式（含 MySQL 连接）、测试方式、接口清单、演示步骤<br/>② smoke 脚本覆盖：注册 → 办证 → 加题名 → 加副本 → 借书 → 查借阅 → 还书 → 预约，每步断言 code<br/>③ 19 号记录补实验二条目（阶段、Prompt 摘要、改动文件、审查结果、commit）<br/>④ 过程日志追加，标 `[AI]`/`[人工]` |
| 验收标准 | 脚本从头跑到尾全为 `OK`；README 命令可复制即用、无需猜测 |
| 建议测试 | 手工执行 smoke 脚本 |

---

## 4. 执行约定

### 4.1 每个任务的标准流程

```
1. Agent 输出实现计划（改哪些文件、为什么）        ← 不写代码
2. Violet 确认
3. Agent 实现，只碰"允许修改"范围内的文件
4. Agent 跑测试 + 领域层纯编译
5. Agent 报告：改了哪些文件、测试结果、有无偏离 specs
6. Violet 看 git diff
7. commit（message 以 TASK 号开头）
```

### 4.2 commit message 约定

```
TASK-001 initialize spring boot project structure
TASK-002 unify api response and error code
TASK-008 implement borrow book use case
```

好处：`git log --oneline` 能直接对上任务清单，答辩时按任务讲进展。

### 4.3 实现阶段的硬性禁令

| 禁令 | 检测方式 |
|---|---|
| 不得修改 `specs/**` | `git diff --name-only` 里出现 `specs/` 即为违规（19 号日志除外） |
| 不得给领域实体加框架注解 | 领域层纯 `javac` 编译失败即暴露 |
| 不得在 Controller 写业务判断 | 人工看 diff |
| 不得硬编码规则数值 | 搜 `src/main` 是否出现 `30`、`0.2` 等裸常量 |
| 不得跳过测试 | 每个任务都要有测试或明确写"无" |

---

## 5. 与手册步骤的对应

| 实验二手册步骤 | 对应任务 |
|---|---|
| 步骤 10 创建代码项目 | TASK-001 |
| 步骤 11 Agent 初始化（先输出计划） | TASK-001 流程第 1 步 |
| 步骤 12 TASK-001 初始化 | TASK-001 |
| 步骤 13 实现领域实体 | **已在实验一完成**，TASK-002 仅补错误码 |
| 步骤 14 借阅规则策略 | TASK-004 |
| 步骤 15 罚款规则策略 | TASK-004 |
| 步骤 16 办理借阅证 | TASK-006 |
| 步骤 17 图书标题与馆藏 | TASK-007 |
| 步骤 18 办理借书 | TASK-008 |
| 步骤 19 办理还书 | TASK-009 |
| 步骤 20 预约 | TASK-010 |
| 步骤 21 查询借阅 | TASK-011 |
| 步骤 22 完善测试 | TASK-013 |
| 步骤 23 README | TASK-014 |
| 步骤 24 AI 使用记录 | TASK-014 |
