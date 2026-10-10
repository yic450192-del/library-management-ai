# 13 · 数据库设计 Database Design（指导书步骤 5）

> 指导书要求：数据库设计**以领域模型为基础**，经过分析类图和设计类图的细化，
> **找出需要持久化的类，转化为关系模型**。
> 可执行的 DDL 见 `db/schema.sql`，已在本机 MySQL 8.0.45 上验证建表通过。

## 1. 领域类 → 关系表的映射

| 领域类 | 是否持久化 | 表 | 说明 |
|---|---|---|---|
| Reader（含 StudentReader / TeacherReader） | 是 | `readers` | 单表继承，用 `reader_type` 作判别列 |
| BorrowCard | 是 | `borrow_cards` | |
| Librarian | 是 | `librarians` | |
| SystemAdmin | 是 | `system_admins` | |
| BookTitle | 是 | `book_titles` | |
| LibraryItem（含 Book / Magazine / Thesis） | 是 | `library_items` | 单表继承，用 `item_type` 作判别列 |
| Loan | 是 | `loans` | |
| Reservation | 是 | `reservations` | |
| BorrowPolicy | 是 | `borrow_policies` | |
| FineRule | 是 | `fine_rules` | |
| FineRecord | 是 | `fine_records` | |
| Money / BorrowPeriod | 否 | — | 值对象，内联为列（`amount`、`loan_period_days`） |
| CirculationService / FineCalculator | 否 | — | 无状态领域服务 |
| ReaderType / ItemType / 各状态枚举 | 否 | — | 映射为 `VARCHAR` + `CHECK` 或 `ENUM` |

## 2. 继承映射策略

指导书要求"说明领域继承到关系数据库的映射策略"。

**采用单表继承（Single-Table + Discriminator Column）**，理由：

| 维度 | 单表继承 | 每类一表（Joined） |
|---|---|---|
| 子类字段差异 | 本系统子类差异很小（2–3 个字段） | 差异大时才划算 |
| 查询代价 | 无 JOIN，借书主流程只查 1 张表 | 每次借书要 JOIN readers + student_readers |
| 作业量级 | 11 张表，简单可讲清 | 16+ 张表，维护成本高 |
| 代价 | 存在 NULL 列（如教师的 `student_no` 为空） | 无冗余 |

代价可接受：用 `CHECK` 约束保证"读者类型与子类字段的一致性"，
例如 `reader_type = 'TEACHER'` 时 `student_no` 必须为 NULL。

## 3. 表结构

### 3.1 readers（读者）

| 字段 | 类型 | 空 | 键 | 默认 | 说明 |
|---|---|---|---|---|---|
| id | BIGINT | NO | PK | AUTO | 主键 |
| reader_type | VARCHAR(20) | NO | | | 判别列：JUNIOR_COLLEGE / UNDERGRADUATE / POSTGRADUATE / DOCTORAL / TEACHER |
| name | VARCHAR(64) | NO | | | 姓名 |
| department | VARCHAR(128) | NO | | | 系别或单位 |
| student_no | VARCHAR(32) | YES | UQ | NULL | 学号（教师为 NULL） |
| student_category | VARCHAR(20) | YES | | NULL | 专科 / 本科 / 硕士 / 博士 |
| staff_no | VARCHAR(32) | YES | UQ | NULL | 教工号（学生为 NULL） |
| title | VARCHAR(32) | YES | | NULL | 职称 |
| status | VARCHAR(16) | NO | | 'ACTIVE' | ACTIVE / SUSPENDED / CANCELLED |
| created_at | DATETIME | NO | | CURRENT_TIMESTAMP | |

约束：`CHECK (reader_type <> 'TEACHER' OR student_no IS NULL)`；`CHECK (reader_type = 'TEACHER' OR staff_no IS NULL)`

### 3.2 borrow_cards（借阅证）

| 字段 | 类型 | 空 | 键 | 默认 | 说明 |
|---|---|---|---|---|---|
| id | BIGINT | NO | PK | AUTO | |
| card_no | VARCHAR(32) | NO | **UQ** | | BR-001：`CARD + 年份 + 6 位序号` |
| reader_id | BIGINT | NO | FK→readers(id) | | 一个读者至多一张有效证（由 UQ 部分索引保证） |
| owner_name | VARCHAR(64) | NO | | | 冗余读者姓名，办证时快照 |
| department | VARCHAR(128) | NO | | | 冗余系别 |
| status | VARCHAR(16) | NO | | 'ACTIVE' | ACTIVE / SUSPENDED / CANCELLED |
| issued_at | DATE | NO | | | 办证日期 |
| updated_at | DATETIME | NO | | CURRENT_TIMESTAMP | |

约束：`UNIQUE KEY uk_card_reader_active (reader_id, status)` 配合状态位保证 BR-003；
`CHECK (status IN ('ACTIVE','SUSPENDED','CANCELLED'))`

### 3.3 librarians（图书管理员）

| 字段 | 类型 | 空 | 键 | 说明 |
|---|---|---|---|---|
| id | BIGINT | NO | PK | |
| name | VARCHAR(64) | NO | | 姓名 |
| employee_no | VARCHAR(32) | NO | UQ | 工号 |
| status | VARCHAR(16) | NO | 默认 ACTIVE | ACTIVE / RESIGNED |

### 3.4 system_admins（系统管理员）

| 字段 | 类型 | 空 | 键 | 说明 |
|---|---|---|---|---|
| id | BIGINT | NO | PK | |
| name | VARCHAR(64) | NO | | 姓名 |
| employee_no | VARCHAR(32) | NO | UQ | 工号 |

### 3.5 book_titles（标题信息）

| 字段 | 类型 | 空 | 键 | 说明 |
|---|---|---|---|---|
| id | BIGINT | NO | PK | |
| isbn | VARCHAR(32) | NO | **UQ** | ISBN |
| title | VARCHAR(255) | NO | | 书名 |
| authors | VARCHAR(255) | YES | | 作者 |
| publisher | VARCHAR(128) | YES | | 出版社 |
| published_year | SMALLINT | YES | | 出版年 |
| item_type | VARCHAR(20) | NO | | CN_BOOK / FOREIGN_BOOK / CN_MAGAZINE / FOREIGN_MAGAZINE / THESIS |
| deleted | TINYINT(1) | NO | 默认 0 | 软删除（历史借阅记录仍引用） |

### 3.6 library_items（馆藏副本 / 借出物）

| 字段 | 类型 | 空 | 键 | 说明 |
|---|---|---|---|---|
| id | BIGINT | NO | PK | |
| barcode | VARCHAR(32) | NO | **UQ** | 馆藏条码，全局唯一 |
| title_id | BIGINT | NO | FK→book_titles(id) | 所属题名 |
| item_type | VARCHAR(20) | NO | | 判别列，同 3.5 |
| status | VARCHAR(16) | NO | 默认 'IN_LIBRARY' | IN_LIBRARY / ON_LOAN / OVERDUE / LOST |
| location | VARCHAR(64) | YES | | 馆藏位置 |
| language | VARCHAR(8) | YES | | 中文 / 外文（Book、Magazine 用） |
| edition | VARCHAR(32) | YES | | 版次（Book） |
| pages | INT | YES | | 页数（Book） |
| issue | VARCHAR(32) | YES | | 期号（Magazine） |
| volume | VARCHAR(32) | YES | | 卷号（Magazine） |
| degree | VARCHAR(32) | YES | | 学位（Thesis） |
| awarding_unit | VARCHAR(128) | YES | | 授予单位（Thesis） |
| acquired_at | DATE | YES | | 入馆日期 |
| version | INT | NO | 默认 0 | **乐观锁**，防止并发重复借出 |

### 3.7 loans（借阅记录）

| 字段 | 类型 | 空 | 键 | 说明 |
|---|---|---|---|---|
| id | BIGINT | NO | PK | |
| reader_id | BIGINT | NO | FK→readers(id) | 借阅人 |
| item_id | BIGINT | NO | FK→library_items(id) | 馆藏副本 |
| librarian_id | BIGINT | YES | FK→librarians(id) | 经办管理员 |
| borrow_date | DATE | NO | | 借出日期 |
| due_date | DATE | NO | | 应还日期（BR-006） |
| return_date | DATE | YES | NULL | 归还日期，NULL 表示未还 |
| status | VARCHAR(16) | NO | 默认 'BORROWED' | **BORROWED / RETURNED / OVERDUE / LOST**（指导书审查点：能否区分借出、已还、超期） |
| renew_count | INT | NO | 默认 0 | 续借次数 |
| created_at | DATETIME | NO | CURRENT_TIMESTAMP | |
| updated_at | DATETIME | NO | CURRENT_TIMESTAMP | |

约束：`CHECK (status = 'RETURNED' AND return_date IS NOT NULL) OR (status <> 'RETURNED')`

### 3.8 reservations（预约记录）

| 字段 | 类型 | 空 | 键 | 说明 |
|---|---|---|---|---|
| id | BIGINT | NO | PK | |
| reader_id | BIGINT | NO | FK→readers(id) | 预约人 |
| title_id | BIGINT | NO | FK→book_titles(id) | **面向题名而非副本**（BR-010） |
| queue_position | INT | NO | | 排队位次 |
| status | VARCHAR(16) | NO | 默认 'WAITING' | WAITING / READY / FULFILLED / EXPIRED / CANCELLED |
| created_at | DATETIME | NO | | 排队依据（BR-010 按创建时间） |
| expire_at | DATETIME | YES | NULL | 保留到期时间（BR-012，3 天） |

约束：`UNIQUE KEY uk_reader_title_active (reader_id, title_id, status)` 保证 BR-011

### 3.9 borrow_policies（借阅规则）

| 字段 | 类型 | 空 | 键 | 说明 |
|---|---|---|---|---|
| id | BIGINT | NO | PK | |
| reader_type | VARCHAR(20) | NO | **UQ** | 按读者类型区分（BR-006） |
| max_books | INT | NO | | 最大借阅数量 |
| loan_period_days | INT | NO | | 借阅期限（天） |
| max_renew_times | INT | NO | 默认 0 | 可续借次数 |
| renew_days | INT | NO | 默认 30 | 每次续借延长天数 |
| updated_at | DATETIME | NO | CURRENT_TIMESTAMP | |

### 3.10 fine_rules（罚款规则）

| 字段 | 类型 | 空 | 键 | 说明 |
|---|---|---|---|---|
| id | BIGINT | NO | PK | |
| item_type | VARCHAR(20) | NO | **UQ** | 按借出物类型区分（指导书需求 7） |
| grace_days | INT | NO | 默认 0 | 宽限期（天） |
| daily_rate | DECIMAL(10,2) | NO | | 每日罚金 |
| max_amount | DECIMAL(10,2) | NO | | 单笔上限（BR-009） |
| updated_at | DATETIME | NO | CURRENT_TIMESTAMP | |

### 3.11 fine_records（罚款记录）

| 字段 | 类型 | 空 | 键 | 说明 |
|---|---|---|---|---|
| id | BIGINT | NO | PK | |
| loan_id | BIGINT | NO | FK→loans(id) | 来源借阅记录 |
| reader_id | BIGINT | NO | FK→readers(id) | 欠款人 |
| overdue_days | INT | NO | | 超期天数 |
| amount | DECIMAL(10,2) | NO | | 罚款金额 |
| status | VARCHAR(16) | NO | 默认 'UNPAID' | UNPAID / PAID / WAIVED |
| created_at | DATE | NO | | |

## 4. 索引设计

| 表 | 索引 | 类型 | 服务于 |
|---|---|---|---|
| borrow_cards | `uk_card_no (card_no)` | UNIQUE | 借书时按证号查找（主流程，每秒级） |
| borrow_cards | `idx_reader (reader_id)` | INDEX | 查某读者的证 |
| library_items | `uk_barcode (barcode)` | UNIQUE | 借还按条码定位 |
| library_items | `idx_title_status (title_id, status)` | 复合 | 统计可借副本数 |
| loans | `idx_reader_status (reader_id, status)` | 复合 | **BR-004 统计在借数量、BR-005 查超期**（主流程） |
| loans | `idx_item_status (item_id, status)` | 复合 | 还书时定位在借记录 |
| loans | `idx_due_date (due_date)` | INDEX | 超期批量扫描 |
| reservations | `idx_title_created (title_id, created_at)` | 复合 | 排队顺序 |
| reservations | `uk_reader_title_active` | UNIQUE | BR-011 防重复预约 |
| book_titles | `uk_isbn (isbn)` | UNIQUE | 题名唯一 |
| book_titles | `idx_title (title)` | INDEX | 按书名检索 |
| readers | `uk_student_no / uk_staff_no` | UNIQUE | 学号/教工号唯一 |

**为什么这样建**：借书主流程有三查（按证号查卡、按读者查在借、按条码查副本），
这三处都在关键路径上，必须是唯一索引或复合索引；`loans` 的 `(reader_id, status)` 复合索引
直接服务 BR-004/BR-005 两条业务规则的校验。

## 5. 指导书审查要点自检

| 审查点 | 结论 |
|---|---|
| `borrow_card.card_no` 是否唯一？ | 是，`UNIQUE KEY uk_card_no` |
| `library_item.barcode` 是否唯一？ | 是，`UNIQUE KEY uk_barcode` |
| `loans` 是否能区分借出、已还、超期？ | 是，`status` 四态 + `return_date` + `CHECK` 约束 |
| `reservations` 是否能支持排队？ | 是，`queue_position` + `created_at` 复合索引 |
| `fine_rules` 是否能支持不同借出物类型？ | 是，`item_type` 唯一键，五种类型各一条 |
| `borrow_policies` 是否能支持不同读者类型？ | 是，`reader_type` 唯一键，五种类型各一条 |
| 是否存在必要外键？ | 是，7 处外键：`borrow_cards→readers`、`library_items→book_titles`、`loans→readers/items/librarians`、`reservations→readers/titles`、`fine_records→loans/readers` |

## 6. 验证方式

```bash
"C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe" -u root -p < db/schema.sql
```

建库 `library_ai`，字符集 `utf8mb4`。建表后可用下列语句验证约束确实生效：

```sql
USE library_ai;
SHOW TABLES;                                  -- 应返回 11 张表
SHOW INDEX FROM borrow_cards WHERE Non_unique = 0;   -- 应看到 uk_card_no
SHOW INDEX FROM library_items WHERE Non_unique = 0;  -- 应看到 uk_barcode
```
