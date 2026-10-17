# 09 · 设计模型 Design Model（指导书步骤 4）

> 指导书原文：根据 1、2、3 的结果，**以用例或用例的场景为基本单元**，设计各层包含的类、接口及其交互
> （类图、顺序图等）以实现用例或场景，尽可能在设计中体现 OO 的设计原则，设计模式。
> 可以一步完成，也可以分两步。
>
> **本设计采用两步法**：
> - 第一步（`09a-analysis-class-diagram.puml`）：不考虑界面与持久化，只考虑领域类，建立**分析类图**（BCE：边界 / 控制 / 实体）与分析类间的交互顺序图；
> - 第二步（`09b-design-class-diagram.puml`）：加入界面类与持久化类，增强为**设计类图**，得到更精确的交互顺序图（`10~12`）。

## 1. 用例场景选取

选取三个核心场景作为设计单元（也是实验二优先实现的场景）：

| 顺序图 | 场景 | 用例 |
|---|---|---|
| `10-sequence-borrow-book.puml` | 图书管理员办理借书（成功 + 超量拒绝） | UC-101 |
| `11-sequence-return-book.puml` | 图书管理员办理还书（超期 → 生成罚款） | UC-102 |
| `12-sequence-reserve-book.puml` | 读者网络预约并排队 | UC-003 |

## 2. 第一步：分析类（BCE）

| 类型 | 类 | 职责 |
|---|---|---|
| 边界类 Boundary | `BorrowUI` | 接收管理员输入的借阅证号与馆藏条码，显示结果 |
| 边界类 | `ReturnUI` | 接收馆藏条码，显示还书与罚款结果 |
| 边界类 | `ReserveUI` | 接收读者的题名预约请求，显示排队位次 |
| 控制类 Control | `BorrowHandler` | 编排借书流程：验证证 → 检查规则 → 校验副本 → 建记录 |
| 控制类 | `ReturnHandler` | 编排还书流程：校验本馆 → 判定超期 → 结清 → 计罚 |
| 控制类 | `ReserveHandler` | 编排预约流程：查题名 → 校验重复 → 入队 |
| 实体类 Entity | Reader / BorrowCard / LibraryItem / Loan / Reservation / FineRecord / BookTitle | 见 `05-domain-model.md` |
| 规则类 | BorrowPolicy / FineRule | 见 `05-domain-model.md` |

分析类图**不出现** Repository、DAO、DTO——持久化与界面技术留到第二步。

## 3. 第二步：设计类（加入界面与持久化）

分析类到设计类的映射：

| 分析类 | 设计类 | 变化 |
|---|---|---|
| `BorrowUI` / `ReturnUI` / `ReserveUI` | `BorrowController` / `ReturnController` / `ReservationController` + `GlobalExceptionHandler` | 落到 Spring MVC，补 DTO 与统一异常处理 |
| `BorrowHandler` / `ReturnHandler` / `ReserveHandler` | `CirculationApplicationService` / `ReservationApplicationService` | 补事务边界（`@Transactional`）与 DTO 装配 |
| 实体类 | 同名实体，但补上 Repository 接口 | 领域层定义 `LoanRepository` 等接口，基础设施层用 Spring Data JPA 实现 |
| — | `LoanPO` / `LibraryItemPO` 等持久化对象 | 实体与 PO 分离，避免 JPA 注解污染领域层 |
| — | `BorrowRequest` / `BorrowResponse` / `LoanResponse` | DTO 隔离接口层与领域层 |

### 关键接口定义

```java
// 领域层定义（不依赖任何框架）
public interface LoanRepository {
    Optional<Loan> findById(LoanId id);
    List<Loan> findActiveByReaderId(ReaderId readerId);   // status in (BORROWED, OVERDUE)
    Optional<Loan> findActiveByItemId(ItemId itemId);
    Loan save(Loan loan);
}

public interface BorrowPolicyRepository {
    BorrowPolicy findByReaderType(ReaderType type);
}

public interface FineRuleRepository {
    FineRule findByItemType(ItemType type);
}
```

```java
// 应用服务：事务边界
@Service
public class CirculationApplicationService {

    private final CirculationService circulationService;   // 领域服务
    private final LoanRepository loanRepository;
    private final LoanMapper loanMapper;                   // PO <-> Entity <-> DTO

    @Transactional
    public BorrowResponse borrow(BorrowRequest req, Operator operator) {
        requireRole(operator, Role.LIBRARIAN);             // BR-013
        Loan loan = circulationService.borrow(
                req.cardNo(), req.barcode(), operator.id(), LocalDate.now());
        return loanMapper.toResponse(loanRepository.save(loan));
    }
}
```

## 4. 设计中体现的 OO 原则

| 原则 | 体现 |
|---|---|
| **单一职责 SRP** | `BorrowPolicy` 只管借阅额度与期限；`FineRule` 只管罚金计算；Controller 只管 HTTP |
| **开闭 OCP** | 新增读者类型只需加一条 `BorrowPolicy` 记录与一个 `ReaderType` 枚举值，不改借书流程代码 |
| **里氏替换 LSP** | `StudentReader` / `TeacherReader` 可替换 `Reader`；`Book` / `Magazine` / `Thesis` 可替换 `LibraryItem` |
| **接口隔离 ISP** | Repository 按聚合拆分，不做大而全的 `BaseRepository` |
| **依赖倒置 DIP** | 领域层定义 Repository **接口**，基础设施层实现；高层不依赖低层细节 |
| **迪米特 LoD** | Controller 只认识 Application Service，不认识 Repository 与 PO |
| **GRASP 信息专家** | `dueDate` 由掌握 `borrowDate` 与 `loanPeriod` 的 `BorrowPolicy` 计算；`isOverdue` 由掌握 `dueDate` 的 `Loan` 自己判断 |

## 5. 设计模式落地位置

| 模式 | 类 | 说明 |
|---|---|---|
| Strategy | `BorrowPolicy`（按 ReaderType）、`FineRule`（按 ItemType） | 规则外置，消除条件分支 |
| Factory Method | `ReaderFactory`、`LibraryItemFactory` | 子类创建集中化 |
| Repository | `LoanRepository` 等接口 + `JpaLoanRepository` 实现 | 持久化抽象 |
| Service Layer | `CirculationApplicationService` | 用例编排与事务边界 |
| DTO / Mapper | `BorrowRequest`、`LoanMapper` | 层间数据隔离 |
| Facade | `CirculationService` | 对上暴露单一借还入口 |
| Template Method | `AbstractPolicy.validate()` 骨架 | 策略复用校验流程 |
| MVC | Controller / Domain / View(JSON) | 业务规则不进 Controller |

## 6. 与顺序图的对应

| 顺序图 | 覆盖的类 | 覆盖的分支 |
|---|---|---|
| `10` 借书 | BorrowController → CirculationApplicationService → CirculationService → BorrowCard / BorrowPolicy / LibraryItem / Loan → LoanRepository | 成功 + 借阅证无效 + 超量 + 副本不可借 |
| `11` 还书 | ReturnController → CirculationApplicationService → CirculationService → LibraryItem / Loan / FineCalculator / FineRule | 正常还 + 超期计罚 + 非本馆藏书 |
| `12` 预约 | ReservationController → ReservationApplicationService → BookTitle / Reservation / ReservationRepository | 成功入队 + 重复预约 + 有可借副本 |

---

## 7. 数据结构、算法与程序运行流程（指导书实验二步骤 1）

> 指导书实验二步骤 1 原文：**以实验一的结果为基础，设计每个具体类的数据结构，算法和程序运行流程。**
> 本节是实验二的设计细化。所列字段与 `src/main/java/com/example/library/domain/` 的代码逐一对应，
> 且该目录可用纯 `javac` 编译验证（constitution 第 12 条）。

### 7.1 数据结构

#### 7.1.1 实体类

| 类 | 字段（类型） | 可变性 | 不变式 |
|---|---|---|---|
| `Loan`<br/>借阅记录 | `id: Long`<br/>`reader: Reader`<br/>`item: LibraryItem`<br/>`librarianId: Long`<br/>`borrowDate: LocalDate`<br/>`dueDate: LocalDate`<br/>`returnDate: LocalDate`<br/>`status: LoanStatus`<br/>`renewCount: int` | `id/reader/item/librarianId/borrowDate` 为 final；`dueDate` 可延（续借）；`returnDate/status/renewCount` 状态驱动 | ① `borrowDate ≤ dueDate`<br/>② `returnDate != null ⟺ status == RETURNED`（对应 CHECK `ck_loan_return`）<br/>③ `status ∈ {BORROWED, OVERDUE, RETURNED, LOST}`<br/>④ `renewCount ≥ 0` |
| `LibraryItem`<br/>馆藏副本 | `id: Long`<br/>`barcode: String`<br/>`titleId: Long`<br/>`itemType: ItemType`<br/>`location: String`<br/>`status: ItemStatus`<br/>`version: int` | `barcode/titleId/itemType` 不可变；`status` 随借还变化；`version` 为**乐观锁**版本号 | ① `barcode` 全局唯一（UNIQUE）<br/>② `status ∈ {AVAILABLE, ON_LOAN, RESERVED, LOST, REMOVED}`<br/>③ 只有 `AVAILABLE` 可被借出（BR-007） |
| `BookTitle`<br/>题名 | `id: Long`<br/>`isbn: String`<br/>`title: String`<br/>`authors: String`<br/>`publisher: String`<br/>`publishedYear: Integer`<br/>`itemType: ItemType` | `isbn/itemType` 不可变；书目信息可改 | ① `isbn` 唯一<br/>② 一个题名对应多个副本（1:N） |
| `BorrowCard`<br/>借阅证 | `id: Long`<br/>`cardNo: String`<br/>`readerId: Long`<br/>`ownerName: String`<br/>`department: String`<br/>`issuedAt: LocalDate`<br/>`status: CardStatus` | 证号与持证人不可变；`status` 可变 | ① `cardNo` 唯一<br/>② 一个读者至多一张有效证（BR-003）<br/>③ 非 `ACTIVE` 不得借书（BR-002） |
| `Reservation`<br/>预约 | `id: Long`<br/>`readerId: Long`<br/>`titleId: Long`<br/>`createdAt: LocalDateTime`<br/>`queuePosition: int`<br/>`status: ReservationStatus`<br/>`expireAt: LocalDateTime` | `createdAt` 不可变；位次与状态可变 | ① 同读者同题名至多一条有效预约（BR-011）<br/>② `expireAt = createdAt + 3 天`（BR-012）<br/>③ 同题名按 `createdAt` 升序排队（BR-010） |
| `Reader`（抽象） | `id: Long`<br/>`name: String`<br/>`department: String`<br/>`readerType: ReaderType`<br/>`status: AccountStatus` | `id/readerType` 不可变 | ① `readerType` 决定借阅规则<br/>② `status != ACTIVE` 不得借书 |
| `StudentReader` | 继承 Reader + `studentNo: String`<br/>`category: StudentCategory` | `studentNo/category` 不可变 | `category` 细分专科/本科/研究生/博士 |
| `TeacherReader` | 继承 Reader + `staffNo: String`<br/>`title: String` | 不可变 | — |
| `FineRecord`<br/>罚款记录 | `id: Long`<br/>`loanId: Long`<br/>`readerId: Long`<br/>`overdueDays: long`<br/>`amount: Money`<br/>`createdAt: LocalDate`<br/>`status: FineStatus` | 金额与天数不可变；`status` 可变 | ① `overdueDays > 0`<br/>② `amount > 0`（否则不生成记录） |

#### 7.1.2 值对象与枚举

| 类型 | 结构 | 说明 |
|---|---|---|
| `Money` | `record Money(BigDecimal amount, String currency)` | 不可变；`currency` 缺省 `CNY`；提供 `multiply(long)`、`cap(Money)`、`isPositive()`。**用 BigDecimal 而非 double**，避免 0.2 × 3 这类浮点误差 |
| `BorrowPeriod` | `record`，内部持 `days:int` | 提供 `dueDateFrom(LocalDate)`，即 BR-006 的唯一实现点 |
| `ReaderType` | 枚举 | `UNDERGRADUATE / GRADUATE / DOCTOR / COLLEGE / TEACHER` |
| `ItemType` | 枚举 | `CHINESE_BOOK / FOREIGN_BOOK / CHINESE_MAGAZINE / FOREIGN_MAGAZINE / THESIS` |
| `LoanStatus` | 枚举 | `BORROWED / OVERDUE / RETURNED / LOST`，带 `isActive()` 判定在借态 |
| `ItemStatus` | 枚举 | `AVAILABLE / ON_LOAN / RESERVED / LOST / REMOVED` |
| `CardStatus` | 枚举 | `ACTIVE / SUSPENDED / CANCELLED` |
| `AccountStatus` | 枚举 | `ACTIVE / DISABLED` |
| `ReservationStatus` | 枚举 | `WAITING / READY / FULFILLED / EXPIRED / CANCELLED` |
| `FineStatus` | 枚举 | `UNPAID / PAID / WAIVED` |
| `StudentCategory` | 枚举 | `COLLEGE / UNDERGRADUATE / GRADUATE / DOCTOR` |

#### 7.1.3 策略与规则类

| 类 | 字段 | 说明 |
|---|---|---|
| `BorrowPolicy` | `id`、`readerType`、`maxBooks: int`、`loanPeriod: BorrowPeriod`、`maxRenewTimes: int`、`renewDays: int` | 一行对应一种读者类型，取值来自 `borrow_policies` 表 |
| `FineRule` | `id`、`itemType`、`graceDays: int`、`dailyRate: Money`、`maxAmount: Money` | 一行对应一种借出物类型，取值来自 `fine_rules` 表 |

> **`graceDays` 是对 BR-009 的细化**：实际罚款先扣宽限期再计费。
> BR-009 原文只写"超期天数 × 每日罚金"，实现层需要区分"超期天数"（`Loan.overdueDays`）
> 与"计费天数"（`overdueDays - graceDays`）——这个区别写进 `FineRule.calculate` 的注释，避免后续歧义。

### 7.2 关键算法

#### 算法 1：在借数量与额度检查（BR-004）

```
算法 checkQuota
输入：reader, activeLoans
输出：允许 / 抛出 QUOTA_EXCEEDED

1. activeCount ← count(activeLoans)      // 只数 status ∈ {BORROWED, OVERDUE}
2. policy ← policyRepository.findByReaderType(reader.readerType)
3. 若 activeCount >= policy.maxBooks → 抛出 BusinessException(QUOTA_EXCEEDED)
4. 否则继续
```

对应代码：`Reader.activeLoanCount(List<Loan>)` + `BorrowPolicy.checkQuota(int)`。

> **为什么数量不存成字段**：`Reader.activeLoanCount` 每次由在借记录**现算**，不冗余存计数列。
> 冗余计数会在并发借还时与 `loans` 表不一致，属于典型的"用缓存换 bug"。

#### 算法 2：应还日期计算（BR-006）

```
算法 calculateDueDate
输入：borrowDate, policy
输出：dueDate

1. dueDate ← borrowDate + policy.loanPeriod.days
2. 返回 dueDate
```

只有 `BorrowPeriod.dueDateFrom` 一处实现，禁止在 Controller 或 Service 里写 `plusDays(30)`。

#### 算法 3：超期判定与超期天数（信息专家原则）

```
算法 isOverdue / overdueDays
输入：loan, today
输出：布尔 / 天数

1. 若 loan.status 不是在借态 → 未超期，天数 = 0
2. 若 today <= dueDate      → 未超期，天数 = 0
3. 否则天数 ← ChronoUnit.DAYS.between(dueDate, today)
```

**边界约定**：`today == dueDate` 当天归还算**未超期**（`isAfter` 而非 `isEqual`）。
理由：应还日当天归还属于按期归还，符合直觉，也避免"最后一天还书被罚款"的投诉。

#### 算法 4：罚金计算（BR-009，含宽限期与上限）

```
算法 calculateFine
输入：overdueDays, rule
输出：Money

1. chargeableDays ← max(0, overdueDays - rule.graceDays)
2. 若 chargeableDays == 0 → 返回 Money.zero()
3. raw     ← rule.dailyRate × chargeableDays
4. amount  ← min(raw, rule.maxAmount)          // 上限截断
5. 返回 amount
```

对应代码：`FineRule.calculate(long)` + `Money.cap(Money)`。

> 上限截断用 `cap` 而不是 `Math.min(double, double)`：全程 BigDecimal，不引入浮点。

#### 算法 5：预约排队位次（BR-010 / BR-012）

```
算法 enqueueReservation
输入：readerId, titleId
输出：Reservation（含 queuePosition）

1. 校验读者存在            → 不存在抛 READER_NOT_FOUND
2. 校验题名存在            → 不存在抛 TITLE_NOT_FOUND
3. 查该读者对同题名的有效预约 → 已存在抛 RESERVATION_DUPLICATE
4. position ← 1 + count(该题名下 status = WAITING 的预约)
5. expireAt ← createdAt + 3 天
6. status   ← WAITING
7. 保存并返回
```

**幂等边界**：第 3 步是唯一的去重关口，靠数据库唯一键 `uk_reservation_active(reader_id, title_id, status)` 兜底，
防止并发下两条请求同时通过第 3 步。

#### 算法 6：借书五步编排（UC-101 主流程）

```
算法 borrow(cardNo, barcode, librarianId)
1. card   ← cardRepository.findByCardNo(cardNo)    失败 → CARD_NOT_FOUND(404)
2. 若 !card.isValid()                              失败 → CARD_NOT_ACTIVE(409)
3. reader ← readerRepository.findById(card.readerId())  失败 → CARD_NOT_FOUND
4. 若 !reader.isAllowedToBorrow()                  失败 → READER_NOT_ALLOWED(409)
5. policy ← policyRepository.findByReaderType(...)
6. activeLoans ← loanRepository.findActiveByReaderId(...)
7. 算法 1 额度检查                                  失败 → QUOTA_EXCEEDED(409)
8. reader.hasOverdueLoan(activeLoans, today)       失败 → OVERDUE_LOAN_EXISTS(409)
9. item ← itemRepository.findByBarcode(barcode)    失败 → ITEM_NOT_OWNED(404)
10. 若 !item.isAvailable()                         失败 → ITEM_NOT_AVAILABLE(409)
11. dueDate ← 算法 2
12. loan ← new Loan(null, reader, item, librarianId, today, dueDate)
13. item.markOnLoan(); itemRepository.save(item)
14. 返回 loanRepository.save(loan)
```

**校验顺序不可调整**：这个顺序严格照指导书原文（验证证 → 查超量 → 查超期 → 校验副本 → 建记录）。
换顺序会导致错误码优先级的改变——例如先查副本再查证，用户会先看到"图书不可借"而不知道自己的证已失效。

### 7.3 程序运行流程

#### 7.3.1 办理借书（UC-101）

| 步 | 参与者 | 动作 | 失败出口 |
|---|---|---|---|
| 1 | 管理员 | 输入借阅证号 | 格式非法 → 400 |
| 2 | 系统 | 查证 + 判状态 | CARD_NOT_FOUND / CARD_NOT_ACTIVE |
| 3 | 系统 | 读读者 + 判账号状态 | READER_NOT_ALLOWED |
| 4 | 系统 | 读规则 + 数在借 + 比额度 | QUOTA_EXCEEDED |
| 5 | 系统 | 判超期未还 | OVERDUE_LOAN_EXISTS |
| 6 | 管理员 | 输入馆藏条码 | — |
| 7 | 系统 | 查副本 + 判可借 | ITEM_NOT_OWNED / ITEM_NOT_AVAILABLE |
| 8 | 系统 | 建 Loan、置副本 ON_LOAN | 乐观锁冲突 → 409 重试 |
| 9 | 系统 | 返回借阅记录与应还日期 | — |

**事务边界**：第 2–8 步在同一个 `@Transactional` 内，由 `CirculationApplicationService.borrow` 包裹。
任一步抛 `BusinessException` 整体回滚，不会出现"副本已被占用但没建借阅记录"。

#### 7.3.2 办理还书（UC-102）

| 步 | 动作 | 失败出口 |
|---|---|---|
| 1 | 输入馆藏条码 | — |
| 2 | 查副本（判是否本馆藏书） | ITEM_NOT_OWNED |
| 3 | 查该副本的在借记录 | LOAN_NOT_FOUND |
| 4 | 判超期 | 未超期跳过第 5 步 |
| 5 | 取罚款规则 → 算法 4 → 生成 FineRecord | 规则缺失 → FINE_RULE_NOT_FOUND |
| 6 | `loan.markReturned(today)`、`item.markReturned()` | — |
| 7 | 返回借阅记录 + 罚款记录 | — |

> 还书**不需要借阅证**——指导书原文："学生还书时只需要将所借的图书交给图书管理员"。

#### 7.3.3 预约图书（UC-003）

| 步 | 动作 | 失败出口 |
|---|---|---|
| 1 | 读者提交题名 | — |
| 2 | 校验读者 / 题名存在 | READER_NOT_FOUND / TITLE_NOT_FOUND |
| 3 | 查重复有效预约 | RESERVATION_DUPLICATE |
| 4 | 算位次、算 expireAt | — |
| 5 | 返回位次与预计保留到期日 | — |

**何时置 READY**：当该题名下有副本归还（`item.markReturned()` 后）且存在 `WAITING` 预约时，
队首预约转 `READY`，副本状态转 `RESERVED`。这一动作放在还书事务的末尾，与第 6 步同事务。

### 7.4 并发与一致性设计

| 风险 | 对策 |
|---|---|
| 两个管理员同时借同一副本 | `library_items.version` 乐观锁；更新失败抛 409，提示重试 |
| 同一读者并发借书突破额度 | 事务内 `SELECT ... FOR UPDATE` 锁读者行（实验二实现时确定） |
| 重复预约 | 唯一键 `uk_reservation_active` 兜底 |
| 罚款重复生成 | `fine_records` 对 `loan_id` 加唯一约束 |

### 7.5 算法复杂度（数据量级的自我约束）

| 算法 | 复杂度 | 说明 |
|---|---|---|
| 在借数量统计 | O(n)，n = 该读者在借记录数 | n ≤ 20（教师上限），可接受 |
| 预约位次 | O(n)，n = 同题名 WAITING 数 | 教学量级足够；真实系统应改为序号列 |
| 超期判定 | O(1) | 纯日期比较 |
| 罚金计算 | O(1) | BigDecimal 乘法 + 比较 |
