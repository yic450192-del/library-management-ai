# 15 · 测试计划 Test Plan（实验二步骤 6）

> 依据：`02-requirements.md`、`03-use-cases.md`、`09-design-model.md`、`14-api-spec.md`
> 目标：用测试把"设计说它该这样"变成"代码确实这样"（constitution 第 9 条）。

---

## 1. 测试策略

### 1.1 分层与分工

| 层 | 类型 | 工具 | 测什么 | 不测什么 |
|---|---|---|---|---|
| **Domain** | 单元测试 | JUnit 5 + 手写内存 Repository | 规则与算法：额度、期限、超期、罚金、排队 | 不启动 Spring，不连数据库 |
| **Application + Presentation** | 集成测试 | `@SpringBootTest` + MockMvc + H2 | 用例全链路：HTTP → 应用服务 → 领域 → 持久化 | 不测真实 MySQL（由 `db/verify.sql` 覆盖） |
| **Infrastructure** | 由 `db/verify.sql` 承担 | MySQL 8.0 实跑 | 建表、唯一键、外键、CHECK、主流程 SQL | 不重复做 JPA 层测试 |

**为什么单元测试不碰 Spring**：领域层零框架依赖（NFR-003），启动 Spring 只会让测试变慢、
让失败原因变模糊。一个额度判断要 30 秒才有结果，人不愿跑，测试就废了。

**为什么集成测试用 H2 而不是 MySQL**：H2 内存库不用清理、可在 CI 跑。
代价是 H2 对 `CHECK` 与 `ON UPDATE CURRENT_TIMESTAMP` 的支持与 MySQL 有差异——
所以约束层面的验证交给 `db/verify.sql` 在真实 MySQL 上跑，两者分工不重叠。

### 1.2 测试数据与时间控制

超期、罚款这类用例本质是**时间相关**的。设计阶段特意让 `CirculationService` 注入 `java.time.Clock`，
测试时固定时钟即可，不需要"等 30 天"或改系统时间：

```java
Clock fixed = Clock.fixed(
        LocalDate.of(2026, 12, 1).atStartOfDay(ZoneId.systemDefault()).toInstant(),
        ZoneId.systemDefault());
```

**基准数据集**（每个集成测试类 `@BeforeEach` 重建）：

| 对象 | 值 |
|---|---|
| 读者 | 张三 / UNDERGRADUATE（5 册 / 30 天）、李老师 / TEACHER（20 册 / 90 天） |
| 借阅证 | `CARD2026000001` → 张三（ACTIVE）；`CARD2026000002` → 李四（SUSPENDED，用于证无效用例） |
| 题名 | 《三体》`titleId=201`，`itemType=CHINESE_BOOK` |
| 副本 | `BC10001`（AVAILABLE）、`BC10002`（AVAILABLE）、`BC10003`（ON_LOAN） |
| 借阅规则 | UNDERGRADUATE：maxBooks=5, days=30；TEACHER：maxBooks=20, days=90 |
| 罚款规则 | CHINESE_BOOK：graceDays=0，日罚金 0.20，上限 20.00 |

### 1.3 通过标准

| 指标 | 要求 |
|---|---|
| 用例通过率 | 100%，不允许"已知失败" |
| 核心用例覆盖 | 借书、还书、预约、超期罚款、权限失败 —— 分支全覆盖 |
| 行覆盖率 | 领域层 ≥ 80%，整体 ≥ 70% |
| 执行时间 | 全量 < 60 秒（超过就要拆，说明有测试在连真实外部依赖） |

---

## 2. 单元测试用例（Domain 层）

### TC-001 借阅额度检查
| 项 | 内容 |
|---|---|
| 目标 | `BorrowPolicy.checkQuota` 在边界上的行为正确（BR-004） |
| 前置 | UNDERGRADUATE 规则 maxBooks=5 |
| 输入 | activeLoanCount = 0 / 4 / 5 / 6 |
| 步骤 | 依次调用 `checkQuota(n)` |
| 预期 | 0→true、4→true、**5→false**、6→false（上限是"小于"而非"小于等于"） |

### TC-002 应还日期计算
| 项 | 内容 |
|---|---|
| 目标 | BR-006 的唯一实现点正确 |
| 前置 | UNDERGRADUATE 期限 30 天；TEACHER 90 天 |
| 输入 | borrowDate = 2026-10-17 |
| 步骤 | 分别取两类规则调用 `calculateDueDate` |
| 预期 | 本科生 2026-11-16；教师 2027-01-15（**跨年也要正确**） |

### TC-003 超期判定边界
| 项 | 内容 |
|---|---|
| 目标 | `Loan.isOverdue` / `overdueDays` 的边界语义（若 today == dueDate 怎么办） |
| 前置 | dueDate = 2026-11-16，status = BORROWED |
| 输入 | today = 11-16 / 11-17 / 12-01；另构造 status = RETURNED 的同一借阅 |
| 步骤 | 调用 `isOverdue(today)`、`overdueDays(today)` |
| 预期 | 11-16 → false / 0（**当天归还未超期**）；11-17 → true / 1；12-01 → true / 15；已还 → false / 0 |

### TC-004 罚金计算（含宽限期与上限）
| 项 | 内容 |
|---|---|
| 目标 | BR-009 的计费天数扣减与上限截断（`FineRule.calculate`） |
| 前置 | 规则 A：grace=0 / 0.20/天 / 上限 20.00；规则 B：grace=3 / 0.20/天 / 上限 20.00 |
| 输入 | overdueDays = 0 / 3 / 15 / 200 |
| 步骤 | 两条规则分别计算四组天数 |
| 预期 | A：0→0.00、3→0.60、15→3.00、200→**20.00（截断）**；B：3→0.00（宽限期内）、15→2.40 |

> 断言必须比较 `BigDecimal` 的 `compareTo`，不能用 `equals`——`0.20` 与 `0.2` 的 scale 不同，
> `equals` 会不等而 `compareTo` 相等。这是 BigDecimal 测试的经典坑。

### TC-005 借书校验顺序
| 项 | 内容 |
|---|---|
| 目标 | 校验顺序与指导书一致，错误码优先级正确（`CirculationService.borrow`） |
| 前置 | 一个"证无效 + 副本也不可借"的读者/副本组合 |
| 输入 | cardNo=已挂起证、barcode=ON_LOAN 副本 |
| 步骤 | 调用 `borrow`，捕获异常 |
| 预期 | 抛 `CARD_NOT_ACTIVE` 而**不是** `ITEM_NOT_AVAILABLE`——证明证检查先于副本检查 |

### TC-006 还书超期生成罚款
| 项 | 内容 |
|---|---|
| 目标 | 超期还书时领域服务正确产出 `FineRecord` |
| 前置 | 一条 dueDate=2026-11-16 的 BORROWED 记录 |
| 输入 | clock 固定为 2026-12-01，barcode=BC10001 |
| 步骤 | 调用 `returnItem` |
| 预期 | 返回 `Loan(status=RETURNED, returnDate=12-01)` + 罚款 15 天 × 0.20 = **3.00** |

### TC-007 预约排队位次
| 项 | 内容 |
|---|---|
| 目标 | BR-010 按 `createdAt` 排队、BR-012 保留 3 天 |
| 前置 | 《三体》已有 2 条 WAITING 预约 |
| 输入 | 第三位读者发起预约 |
| 步骤 | 创建 `Reservation` |
| 预期 | `queuePosition = 3`；`expireAt = createdAt + 3 天` |

---

## 3. 集成测试用例（API 层）

> 每条都走完整链路：MockMvc 请求 → Controller → ApplicationService → Domain → H2。
> 断言看 HTTP 状态 + `code` + 关键 `data` 字段。

### TC-101 注册读者成功
| 项 | 内容 |
|---|---|
| 目标 | FR-001 / UC-001 主流程 |
| 前置 | 库中无该学号 |
| 输入 | `POST /api/readers` body: `{name:"张三", department:"计算机学院", readerType:"UNDERGRADUATE", studentNo:"0122101", category:"UNDERGRADUATE"}` |
| 步骤 | 调用接口 |
| 预期 | 201，`code=OK`，`data.readerId` 非空 |

### TC-102 办理借阅证成功
| 项 | 内容 |
|---|---|
| 目标 | FR-009 / UC-201 |
| 前置 | 读者 1001 存在且无有效证；操作人角色 SYSTEM_ADMIN |
| 输入 | `POST /api/borrow-cards` body: `{readerId:1001}` |
| 步骤 | 调用接口 |
| 预期 | 201，`cardNo` 匹配正则 `^CARD\d{4}\d{6}$`，`status=ACTIVE` |

### TC-103 借阅证无效时借书失败
| 项 | 内容 |
|---|---|
| 目标 | BR-002 / 错误码映射 |
| 前置 | `CARD2026000002` 状态 SUSPENDED；操作人角色 LIBRARIAN |
| 输入 | `POST /api/circulation/borrow` body: `{cardNo:"CARD2026000002", barcode:"BC10001"}` |
| 步骤 | 调用接口 |
| 预期 | **409**，`code=CARD_NOT_ACTIVE`，`data=null` |

### TC-104 借书成功
| 项 | 内容 |
|---|---|
| 目标 | FR-006 / UC-101 主流程 + BR-006 应还日期 |
| 前置 | 张三持有效证、当前 0 册在借；`BC10001` 状态 AVAILABLE；clock 固定 2026-10-17 |
| 输入 | `{cardNo:"CARD2026000001", barcode:"BC10001"}` |
| 步骤 | 调用接口 → 查副本状态 → 查读者在借列表 |
| 预期 | 201，`dueDate=2026-11-16`（+30 天）；副本状态变 `ON_LOAN`；读者在借 1 条且 `status=BORROWED` |

### TC-105 超过借阅数量时借书失败
| 项 | 内容 |
|---|---|
| 目标 | BR-004 |
| 前置 | 张三已有 5 条 BORROWED 记录（本科生上限 5）；`BC10001` AVAILABLE |
| 输入 | 借第 6 本 |
| 步骤 | 调用接口 |
| 预期 | 409，`code=QUOTA_EXCEEDED`；**且副本状态仍为 AVAILABLE**（失败必须回滚，不能占用副本） |

> "失败后状态没变"是本用例真正的考点。只断言错误码，无法发现"先改状态后抛异常"的顺序错误。

### TC-106 存在超期未还时借书失败
| 项 | 内容 |
|---|---|
| 目标 | BR-005 |
| 前置 | 张三有 1 条已超期未还（dueDate=2026-10-01），clock 固定 2026-10-17 |
| 输入 | 借 `BC10001` |
| 步骤 | 调用接口 |
| 预期 | 409，`code=OVERDUE_LOAN_EXISTS` |

### TC-107 图书不可借时借书失败
| 项 | 内容 |
|---|---|
| 目标 | BR-007 |
| 前置 | `BC10003` 状态 ON_LOAN（已被他人借走） |
| 输入 | `{cardNo:"CARD2026000001", barcode:"BC10003"}` |
| 步骤 | 调用接口 |
| 预期 | 409，`code=ITEM_NOT_AVAILABLE` |

### TC-108 还书成功
| 项 | 内容 |
|---|---|
| 目标 | FR-007 / UC-102 主流程（未超期） |
| 前置 | 一条 BORROWED 记录，dueDate=2026-11-16；clock 固定 2026-11-10 |
| 输入 | `POST /api/circulation/return` body: `{barcode:"BC10001"}` |
| 步骤 | 调用接口 → 查副本 → 查借阅记录 |
| 预期 | 200，`overdue=false`、`fine=null`；副本回到 `AVAILABLE`；借阅记录 `status=RETURNED` 且 `returnDate=2026-11-10` |

### TC-109 归还非本馆藏书失败
| 项 | 内容 |
|---|---|
| 目标 | BR-008 |
| 前置 | 条码 `BC99999` 在库中不存在 |
| 输入 | `{barcode:"BC99999"}` |
| 步骤 | 调用接口 |
| 预期 | 404，`code=ITEM_NOT_OWNED` |

### TC-110 超期还书生成罚款
| 项 | 内容 |
|---|---|
| 目标 | BR-009 / UC-903 扩展点 |
| 前置 | BORROWED 记录 dueDate=2026-11-16；`CHINESE_BOOK` 日罚金 0.20、无宽限、上限 20.00；clock 固定 2026-12-01 |
| 输入 | `{barcode:"BC10001"}` |
| 步骤 | 调用接口 → 查 `fine_records` |
| 预期 | 200，`overdue=true`、`overdueDays=15`、`fine.amount="3.00"`、`fine.status=UNPAID`；`fine_records` 新增 1 行且关联同一 `loan_id` |

### TC-111 查询借阅信息成功
| 项 | 内容 |
|---|---|
| 目标 | FR-005 / UC-005，读者查自己 |
| 前置 | 张三有 2 条记录（1 在借、1 已还）；`X-Operator-Id=1001, Role=READER` |
| 输入 | `GET /api/readers/1001/loans?status=ALL` |
| 步骤 | 调用接口 |
| 预期 | 200，`total=2`，含 `BORROWED` 与 `RETURNED` 各一条，`overdue` 字段存在 |

### TC-112 读者不能查他人借阅
| 项 | 内容 |
|---|---|
| 目标 | NFR-004 权限隔离 |
| 前置 | 张三（1001）持有 READER 角色，请求查 1002 的记录 |
| 输入 | `GET /api/readers/1002/loans` |
| 步骤 | 调用接口 |
| 预期 | 403，`code=ROLE_FORBIDDEN` |

### TC-113 预约图书成功
| 项 | 内容 |
|---|---|
| 目标 | FR-003 / UC-003，含排队位次与保留期 |
| 前置 | 《三体》无预约；读者 1001 存在 |
| 输入 | `POST /api/reservations` body: `{readerId:1001, titleId:201}` |
| 步骤 | 调用接口 |
| 预期 | 201，`queuePosition=1`、`status=WAITING`、`expireAt = 提交时间 + 3 天` |

### TC-114 重复预约失败
| 项 | 内容 |
|---|---|
| 目标 | BR-011 |
| 前置 | 读者 1001 对《三体》已有一条 WAITING 预约 |
| 输入 | 再次提交同样的预约 |
| 步骤 | 调用接口 |
| 预期 | 409，`code=RESERVATION_DUPLICATE`；预约表**仍只有一条**记录 |

### TC-115 普通读者不能执行管理员操作
| 项 | 内容 |
|---|---|
| 目标 | BR-014 / NFR-004 |
| 前置 | 操作人 `Role=READER` |
| 输入 | `POST /api/admin/book-titles`（合法请求体） |
| 步骤 | 调用接口 |
| 预期 | 403，`code=ROLE_FORBIDDEN`；题名表无新增 |

### TC-116 题名下有副本不可删除
| 项 | 内容 |
|---|---|
| 目标 | BR-015 |
| 前置 | 《三体》(201) 下有 3 个副本；操作人 SYSTEM_ADMIN |
| 输入 | `DELETE /api/admin/book-titles/201` |
| 步骤 | 调用接口 |
| 预期 | 409，`code=TITLE_HAS_COPIES`；题名仍存在 |

### TC-117 查询图书（含可借副本数）
| 项 | 内容 |
|---|---|
| 目标 | FR-002 / UC-002 |
| 前置 | 《三体》3 个副本，1 个 AVAILABLE |
| 输入 | `GET /api/book-titles?keyword=三体` |
| 步骤 | 调用接口 |
| 预期 | 200，`total=1`，`availableCopies=1`、`totalCopies=3` |

### TC-118 取消预约
| 项 | 内容 |
|---|---|
| 目标 | FR-004 / UC-004 |
| 前置 | 读者 1001 有一条 WAITING 预约 7001 |
| 输入 | `DELETE /api/reservations/7001` |
| 步骤 | 调用接口 → 查预约 |
| 预期 | 200；记录 `status=CANCELLED`，不再出现在"有效预约"里 |

### TC-119 有在借记录时不可注销借阅证
| 项 | 内容 |
|---|---|
| 目标 | BR-015 |
| 前置 | `CARD2026000001` 名下 1 条 BORROWED；操作人 SYSTEM_ADMIN |
| 输入 | `DELETE /api/borrow-cards/CARD2026000001` |
| 步骤 | 调用接口 |
| 预期 | 409，`code=CARD_HAS_ACTIVE_LOANS`；证仍为 ACTIVE |

### TC-120 借书资格预检
| 项 | 内容 |
|---|---|
| 目标 | `GET /api/borrow-cards/{cardNo}/eligibility` 的额度计算与只读性 |
| 前置 | 张三已借 2 册（上限 5）、无超期；操作人 LIBRARIAN |
| 输入 | `GET /api/borrow-cards/CARD2026000001/eligibility` |
| 步骤 | 调用接口两次 |
| 预期 | 均 200；`activeLoanCount=2`、`remainingQuota=3`、`eligible=true`；**两次调用结果一致且 `loans` 表未新增记录**（证明无副作用） |

> 断言"没有副作用"是本用例的关键——预检若误写成借书逻辑，第二次调用就会出现 3 册在借。

### TC-121 还书预览与罚金估算
| 项 | 内容 |
|---|---|
| 目标 | `GET /api/circulation/return-preview` 的估算正确性 |
| 前置 | 一条 BORROWED 记录 dueDate=2026-11-16；clock 固定 2026-12-01 |
| 输入 | `GET /api/circulation/return-preview?barcode=BC10001` |
| 步骤 | 调用接口 |
| 预期 | 200；`overdue=true`、`overdueDays=15`、`estimatedFine="3.00"`；**借阅记录仍为 BORROWED、副本仍为 ON_LOAN** |

---

## 4. 覆盖矩阵

| 需求 | 单元 | 集成 |
|---|---|---|
| FR-001 注册读者 | — | TC-101 |
| FR-002 查询图书 | — | TC-117 |
| FR-003 预约图书 | TC-007 | TC-113 |
| FR-004 取消预约 | — | TC-118 |
| FR-005 查询借阅 | — | TC-111 |
| FR-006 办理借书 | TC-001、TC-002、TC-005 | TC-103~107、TC-120 |
| FR-007 办理还书 | TC-003、TC-004、TC-006 | TC-108~110、TC-121 |
| FR-008 查读者借阅 | — | TC-111 |
| FR-009 办理借阅证 | — | TC-102 |
| FR-010 删除借阅证 | — | TC-119 |
| FR-017 维护规则 | TC-001、TC-004 | — |
| BR-002 证状态 | — | TC-103 |
| BR-004 额度 | TC-001 | TC-105 |
| BR-005 超期禁借 | — | TC-106 |
| BR-006 应还日期 | TC-002 | TC-104 |
| BR-007 副本可借 | — | TC-107 |
| BR-008 本馆藏书 | — | TC-109 |
| BR-009 罚金 | TC-004 | TC-110 |
| BR-010/011/012 预约 | TC-007 | TC-113、TC-114 |
| BR-015 删除前置校验 | — | TC-116、TC-119 |
| NFR-004 权限分离 | — | TC-112、TC-115 |
| NFR-005 测试闭环 | 全部 | 全部 |

---

## 5. 测试环境与执行

### 5.1 依赖

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-test</artifactId>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>com.h2database</groupId>
  <artifactId>h2</artifactId>
  <scope>test</scope>
</dependency>
```

### 5.2 执行

```bash
# 全量
mvn test

# 只跑单元测试（领域层，快）
mvn test -Dtest="*Test"

# 只跑集成测试
mvn test -Dtest="*IT"
```

### 5.3 已知差异与规避

| 差异 | 影响 | 规避 |
|---|---|---|
| H2 对 `CHECK` 约束的宽松度与 MySQL 不同 | 约束类失败在 H2 上可能不报错 | 约束验证交给 `db/verify.sql` 在 MySQL 实跑 |
| H2 的 `ON UPDATE CURRENT_TIMESTAMP` 支持有限 | 时间戳字段行为不一致 | 时间戳由应用层赋值，不依赖数据库默认值 |
| 时区 | `LocalDate.now()` 依赖默认时区，跨时区跑测试会漂 | 全部通过注入的 `Clock` 取时间，不用无参 `now()` |

> 第三条是设计决策的直接回报：`CirculationService` 构造器注入 `Clock`，测试才能稳定复现超期场景。

---

## 6. 不做的测试（明确边界）

| 不测 | 原因 |
|---|---|
| 真实 MySQL 连接 | 由 `db/schema.sql` + `db/verify.sql` 在 MySQL 8.0 实跑覆盖 |
| 并发压力测试 | 教学量级，并发正确性靠乐观锁 + 唯一键设计保证，不做压测 |
| 前端界面测试 | 本实验界面为线框设计（`20-ui-design.md`），未实现真实前端 |
| JWT / 认证安全 | 认证已明确简化为请求头，见 `14-api-spec.md` 1.2 |
