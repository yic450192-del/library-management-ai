# 05 · 领域模型 Domain Model（指导书步骤 2）

> 领域类来自**业务概念**，不是数据库表的机械翻译。
> 本图**不出现** Controller、Repository、DTO 等设计类——那些属于步骤 4 的设计模型。

## 1. 类的分类

| 类别 | 类 |
|---|---|
| 实体 Entity | Reader、StudentReader、TeacherReader、BorrowCard、Librarian、SystemAdmin、BookTitle、LibraryItem、Book、Magazine、Thesis、Loan、Reservation、FineRecord |
| 值对象 Value Object | BorrowPeriod、Money、LoanStatus、CardStatus、ItemStatus、ReaderType、ItemType |
| 策略 / 规则 Policy | BorrowPolicy、FineRule |
| 领域服务 Domain Service | CirculationService（借阅领域服务）、FineCalculator（罚款计算） |

## 2. 账户与读者

### Reader（读者，抽象实体）

- **职责**：表示可被代理借阅的人，是借阅行为的责任主体。
- **关键属性**：`readerId`、`name`、`department`（系别或单位）、`readerType`（ReaderType 枚举）、`status`
- **关键方法**：`isAllowedToBorrow()`、`activeLoanCount()`、`hasOverdueLoan()`
- **约束**：一个 Reader 至多持有一张有效 BorrowCard（BR-003）
- **关系**：1 对 1 组合 BorrowCard；1 对多 Loan；1 对多 Reservation
- **说明**：`readerType` 决定适用哪条 BorrowPolicy，不通过继承分支硬编码数量与期限。

### StudentReader（学生读者）

- **继承** Reader。
- **新增属性**：`studentNo`、`studentCategory`（专科生 / 本科生 / 研究生 / 博士生）
- **约束**：`readerType` ∈ {JUNIOR_COLLEGE, UNDERGRADUATE, POSTGRADUATE, DOCTORAL}

### TeacherReader（教师读者）

- **继承** Reader。
- **新增属性**：`staffNo`、`title`（职称）
- **约束**：`readerType` = TEACHER

### BorrowCard（借阅证，实体）

- **职责**：证明读者具备借阅资格。指导书原文："借阅证可以提供学生的姓名、系别和借阅证号"。
- **关键属性**：`cardId`、`cardNo`（BR-001：`CARD + 年份 + 6 位序号`）、`ownerName`、`department`、`status`（CardStatus：ACTIVE / SUSPENDED / CANCELLED）、`issuedAt`
- **关键方法**：`isValid()`、`suspend()`、`cancel()`
- **约束**：`cardNo` 全局唯一（BR-001）；状态非 ACTIVE 时不得借书（BR-002）
- **关系**：1 对 1 关联 Reader

### Librarian（图书管理员，实体）

- **职责**：作为读者代理发起借书、还书、查询借阅信息（指导书需求 2）。
- **关键属性**：`librarianId`、`name`、`employeeNo`、`status`
- **关键方法**：`borrowOnBehalfOf(...)`、`returnOnBehalfOf(...)`
- **关系**：1 对多 Loan（经手）

### SystemAdmin（系统管理员，实体）

- **职责**：系统维护——办理/删除借阅证、增删管理员、增删图书与标题信息、维护规则（指导书需求 3）。
- **关键属性**：`adminId`、`name`、`employeeNo`
- **约束**：不得办理借还业务（BR-013 / BR-014 权限分离）

## 3. 馆藏与题名

### BookTitle（标题信息，实体）

- **职责**：描述一种文献的**书目信息**，与具体副本分离。对应指导书"添加标题信息 / 删除标题信息"。
- **关键属性**：`titleId`、`isbn`、`title`、`authors`、`publisher`、`publishedYear`、`itemType`（ItemType 枚举）
- **约束**：`isbn` 唯一（FR-015）；题名下仍有副本时不可删除（BR-015）
- **关系**：1 对多 LibraryItem；1 对多 Reservation

### LibraryItem（馆藏副本 / 借出物，抽象实体）

- **职责**：表示**一本可被借出的物理馆藏**，是借还操作的对象。对应指导书"添加图书 / 删除图书"。
- **关键属性**：`itemId`、`barcode`、`status`（ItemStatus：IN_LIBRARY / ON_LOAN / OVERDUE / LOST）、`location`、`acquiredAt`
- **关键方法**：`isAvailable()`、`markOnLoan()`、`markReturned()`、`markOverdue()`
- **约束**：`barcode` 全局唯一；只有 IN_LIBRARY 的副本可借出（BR-007）
- **关系**：多对 1 关联 BookTitle；1 对多 Loan

### Book（图书）/ Magazine（杂志）/ Thesis（论文）

- **继承** LibraryItem，对应指导书需求 5"中外文图书、中外文杂志、论文"。
- **Book**：`language`（中文 / 外文）、`edition`、`pages`
- **Magazine**：`language`、`issue`（期号）、`volume`（卷号）
- **Thesis**：`degree`（学位）、`awardingUnit`（授予单位）
- **说明**：三类借出物的**罚款规则不同**（指导书需求 7），由 `itemType` 决定适用哪条 FineRule。

## 4. 流通

### Loan（借阅记录，实体）

- **职责**：一次借出行为的凭证，承载借出、应还、归还三个时间点。
- **关键属性**：`loanId`、`borrowDate`、`dueDate`、`returnDate`、`status`（LoanStatus：BORROWED / RETURNED / OVERDUE / LOST）、`renewCount`
- **关键方法**：`isOverdue(today)`、`overdueDays(today)`、`markReturned(returnDate)`、`renew(days)`
- **约束**：应还日期 = 借出日期 + 读者类型对应期限（BR-006）；状态需能区分借出 / 已还 / 超期
- **关系**：多对 1 关联 Reader、LibraryItem；多对 1 关联 Librarian（经办人）

### Reservation（预约记录，实体）

- **职责**：读者对题名的排队请求，支持多人排队（指导书需求 6）。
- **关键属性**：`reservationId`、`createdAt`、`queuePosition`、`status`（WAITING / READY / FULFILLED / EXPIRED / CANCELLED）、`expireAt`
- **关键方法**：`isExpired(today)`、`ready()`、`expire()`、`fulfill()`
- **约束**：面向题名而非副本（BR-010）；同一读者同一题名仅一条有效预约（BR-011）；保留 3 天（BR-012）
- **关系**：多对 1 关联 Reader、BookTitle

### FineRecord（罚款记录，实体）

- **职责**：一次超期产生的应付罚金。
- **关键属性**：`fineId`、`amount`（Money）、`overdueDays`、`status`（UNPAID / PAID / WAIVED）、`createdAt`
- **约束**：金额按 FineRule 计算且不超过上限（BR-009）
- **关系**：多对 1 关联 Reader、Loan

## 5. 规则与策略

### BorrowPolicy（借阅规则，策略）

- **职责**：封装"什么读者类型能借多少、借多久"。对应指导书需求 4。
- **关键属性**：`policyId`、`readerType`、`maxBooks`、`loanPeriod`（BorrowPeriod）、`maxRenewTimes`、`renewDays`
- **关键方法**：`checkQuota(activeLoanCount)`、`calculateDueDate(borrowDate)`、`canRenew(renewCount)`
- **说明**：这是 **Strategy 模式**的策略对象。规则集中于此，不散落到界面层（NFR-002）。

### FineRule（罚款规则，策略）

- **职责**：封装"什么借出物类型超期怎么罚"。对应指导书需求 7。
- **关键属性**：`ruleId`、`itemType`、`graceDays`（宽限期）、`dailyRate`（Money）、`maxAmount`（Money，单笔上限）
- **关键方法**：`calculate(overdueDays)`
- **说明**：**Strategy 模式**。按 `itemType` 检索，不同类型的超期时间与罚金都不同。

### CirculationService（借阅领域服务）

- **职责**：借书与还书的用例逻辑编排，横跨 Reader / BorrowCard / LibraryItem / Loan 四个聚合，
  不属于任何一个实体内部，因此提升为领域服务。
- **关键方法**：`borrow(cardNo, barcode, librarianId, today)`、`returnItem(barcode, librarianId, today)`
- **说明**：只依赖领域接口，不依赖 Spring、不依赖持久化框架（NFR-003）。

### FineCalculator（罚款计算，领域服务）

- **职责**：根据 Loan 与 FineRule 生成 FineRecord。
- **关键方法**：`calculateAndCreate(loan, today)`

## 6. 值对象

| 值对象 | 组成 | 说明 |
|---|---|---|
| Money | `amount`（BigDecimal）、`currency` | 金额，避免浮点误差 |
| BorrowPeriod | `days`（int） | 借阅期限 |
| ReaderType | 枚举 | JUNIOR_COLLEGE / UNDERGRADUATE / POSTGRADUATE / DOCTORAL / TEACHER |
| ItemType | 枚举 | CN_BOOK / FOREIGN_BOOK / CN_MAGAZINE / FOREIGN_MAGAZINE / THESIS |
| LoanStatus | 枚举 | BORROWED / RETURNED / OVERDUE / LOST |
| CardStatus | 枚举 | ACTIVE / SUSPENDED / CANCELLED |
| ItemStatus | 枚举 | IN_LIBRARY / ON_LOAN / OVERDUE / LOST |

## 7. 人工审查要点

- [ ] 领域类是否来自业务，而非数据库表的机械翻译？（Loan / Reservation / FineRecord 是业务概念，不是表）
- [ ] 是否体现不同读者类型？（Reader → StudentReader / TeacherReader + ReaderType 枚举）
- [ ] 是否体现不同借出物类型？（LibraryItem → Book / Magazine / Thesis + ItemType 枚举）
- [ ] 借阅规则是否抽象为 BorrowPolicy？（是）
- [ ] 罚款规则是否抽象为 FineRule？（是）
- [ ] Loan 是否能表达借出与归还状态？（LoanStatus 四态 + returnDate）
- [ ] Reservation 是否支持排队？（queuePosition + createdAt 排序）
- [ ] 是否过早引入 Controller / Repository / DTO？（本图不含）
