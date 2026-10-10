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
