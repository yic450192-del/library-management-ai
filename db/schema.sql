-- =============================================================
-- 图书管理系统 · 实验一 数据库设计
-- 来源：specs/13-database-design.md
-- 目标：MySQL 8.0（已在 8.0.45 验证）
-- 执行：mysql -u root -p < db/schema.sql
-- =============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP DATABASE IF EXISTS library_ai;
CREATE DATABASE library_ai DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE library_ai;

-- ---------------- 1. 无外键依赖的独立表 ----------------

CREATE TABLE readers (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    reader_type      VARCHAR(20)  NOT NULL COMMENT '判别列：JUNIOR_COLLEGE/UNDERGRADUATE/POSTGRADUATE/DOCTORAL/TEACHER',
    name             VARCHAR(64)  NOT NULL,
    department       VARCHAR(128) NOT NULL COMMENT '系别或单位',
    student_no       VARCHAR(32)  NULL COMMENT '学号（教师为NULL）',
    student_category VARCHAR(20)  NULL COMMENT '专科/本科/硕士/博士',
    staff_no         VARCHAR(32)  NULL COMMENT '教工号（学生为NULL）',
    title            VARCHAR(32)  NULL COMMENT '职称（教师）',
    status           VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/SUSPENDED/CANCELLED',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_student_no (student_no),
    UNIQUE KEY uk_staff_no   (staff_no),
    KEY idx_reader_type (reader_type),
    CONSTRAINT ck_readers_type CHECK (reader_type IN ('JUNIOR_COLLEGE','UNDERGRADUATE','POSTGRADUATE','DOCTORAL','TEACHER')),
    CONSTRAINT ck_readers_status CHECK (status IN ('ACTIVE','SUSPENDED','CANCELLED')),
    CONSTRAINT ck_readers_student CHECK (reader_type <> 'TEACHER' OR student_no IS NULL),
    CONSTRAINT ck_readers_staff   CHECK (reader_type =  'TEACHER' OR staff_no   IS NULL)
) ENGINE=InnoDB COMMENT='读者（单表继承，reader_type 为判别列）';

CREATE TABLE librarians (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    name        VARCHAR(64) NOT NULL,
    employee_no VARCHAR(32) NOT NULL,
    status      VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/RESIGNED',
    PRIMARY KEY (id),
    UNIQUE KEY uk_employee_no (employee_no)
) ENGINE=InnoDB COMMENT='图书管理员';

CREATE TABLE system_admins (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    name        VARCHAR(64) NOT NULL,
    employee_no VARCHAR(32) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_employee_no (employee_no)
) ENGINE=InnoDB COMMENT='系统管理员';

CREATE TABLE book_titles (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    isbn           VARCHAR(32)  NOT NULL,
    title          VARCHAR(255) NOT NULL,
    authors        VARCHAR(255) NULL,
    publisher      VARCHAR(128) NULL,
    published_year SMALLINT     NULL,
    item_type      VARCHAR(20)  NOT NULL COMMENT 'CN_BOOK/FOREIGN_BOOK/CN_MAGAZINE/FOREIGN_MAGAZINE/THESIS',
    deleted        TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '软删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_isbn (isbn),
    KEY idx_title (title),
    KEY idx_item_type (item_type),
    CONSTRAINT ck_titles_type CHECK (item_type IN ('CN_BOOK','FOREIGN_BOOK','CN_MAGAZINE','FOREIGN_MAGAZINE','THESIS'))
) ENGINE=InnoDB COMMENT='标题信息（书目）';

-- 借阅规则：按读者类型（指导书需求 4）
CREATE TABLE borrow_policies (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    reader_type      VARCHAR(20) NOT NULL,
    max_books        INT         NOT NULL,
    loan_period_days INT         NOT NULL,
    max_renew_times  INT         NOT NULL DEFAULT 0,
    renew_days       INT         NOT NULL DEFAULT 30,
    updated_at       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_reader_type (reader_type)
) ENGINE=InnoDB COMMENT='借阅规则（按读者类型）';

-- 罚款规则：按借出物类型（指导书需求 7）
CREATE TABLE fine_rules (
    id          BIGINT         NOT NULL AUTO_INCREMENT,
    item_type   VARCHAR(20)    NOT NULL,
    grace_days  INT            NOT NULL DEFAULT 0 COMMENT '宽限期（天）',
    daily_rate  DECIMAL(10,2)  NOT NULL COMMENT '每日罚金',
    max_amount  DECIMAL(10,2)  NOT NULL COMMENT '单笔上限',
    updated_at  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_item_type (item_type)
) ENGINE=InnoDB COMMENT='罚款规则（按借出物类型）';

-- ---------------- 2. 依赖 readers / book_titles 的表 ----------------

CREATE TABLE borrow_cards (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    card_no     VARCHAR(32)  NOT NULL COMMENT 'BR-001：CARD+年份+6位序号',
    reader_id   BIGINT       NOT NULL,
    owner_name  VARCHAR(64)  NOT NULL COMMENT '办证时快照的读者姓名',
    department  VARCHAR(128) NOT NULL,
    status      VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/SUSPENDED/CANCELLED',
    issued_at   DATE         NOT NULL,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_card_no (card_no),
    UNIQUE KEY uk_reader_active_card (reader_id, status) COMMENT 'BR-003：一个读者至多一张有效证',
    CONSTRAINT fk_card_reader FOREIGN KEY (reader_id) REFERENCES readers (id),
    CONSTRAINT ck_card_status CHECK (status IN ('ACTIVE','SUSPENDED','CANCELLED'))
) ENGINE=InnoDB COMMENT='借阅证';

CREATE TABLE library_items (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    barcode       VARCHAR(32) NOT NULL COMMENT '馆藏条码，全局唯一',
    title_id      BIGINT      NOT NULL,
    item_type     VARCHAR(20) NOT NULL COMMENT '判别列：Book/Magazine/Thesis 的具体类型',
    status        VARCHAR(16) NOT NULL DEFAULT 'IN_LIBRARY' COMMENT 'IN_LIBRARY/ON_LOAN/OVERDUE/LOST',
    location      VARCHAR(64) NULL,
    language      VARCHAR(8)  NULL COMMENT '中文/外文（Book、Magazine）',
    edition       VARCHAR(32) NULL COMMENT '版次（Book）',
    pages         INT         NULL COMMENT '页数（Book）',
    issue         VARCHAR(32) NULL COMMENT '期号（Magazine）',
    volume        VARCHAR(32) NULL COMMENT '卷号（Magazine）',
    degree        VARCHAR(32) NULL COMMENT '学位（Thesis）',
    awarding_unit VARCHAR(128) NULL COMMENT '授予单位（Thesis）',
    acquired_at   DATE        NULL,
    version       INT         NOT NULL DEFAULT 0 COMMENT '乐观锁：防并发重复借出',
    PRIMARY KEY (id),
    UNIQUE KEY uk_barcode (barcode),
    KEY idx_title_status (title_id, status),
    CONSTRAINT fk_item_title FOREIGN KEY (title_id) REFERENCES book_titles (id),
    CONSTRAINT ck_item_status CHECK (status IN ('IN_LIBRARY','ON_LOAN','OVERDUE','LOST'))
) ENGINE=InnoDB COMMENT='馆藏副本/借出物（单表继承，item_type 为判别列）';

CREATE TABLE reservations (
    id             BIGINT    NOT NULL AUTO_INCREMENT,
    reader_id      BIGINT    NOT NULL,
    title_id       BIGINT    NOT NULL COMMENT 'BR-010：面向题名而非副本',
    queue_position INT       NOT NULL,
    status         VARCHAR(16) NOT NULL DEFAULT 'WAITING' COMMENT 'WAITING/READY/FULFILLED/EXPIRED/CANCELLED',
    created_at     DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'BR-010：排队依据',
    expire_at      DATETIME  NULL COMMENT 'BR-012：保留到期时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_reader_title_active (reader_id, title_id, status) COMMENT 'BR-011：同一读者同题名仅一条有效预约',
    KEY idx_title_created (title_id, created_at),
    CONSTRAINT fk_res_reader FOREIGN KEY (reader_id) REFERENCES readers (id),
    CONSTRAINT fk_res_title  FOREIGN KEY (title_id)  REFERENCES book_titles (id),
    CONSTRAINT ck_res_status CHECK (status IN ('WAITING','READY','FULFILLED','EXPIRED','CANCELLED'))
) ENGINE=InnoDB COMMENT='预约记录（支持排队）';

-- ---------------- 3. 依赖 library_items / loans 的表 ----------------

CREATE TABLE loans (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    reader_id    BIGINT      NOT NULL,
    item_id      BIGINT      NOT NULL,
    librarian_id BIGINT      NULL COMMENT '经办管理员',
    borrow_date  DATE        NOT NULL,
    due_date     DATE        NOT NULL COMMENT 'BR-006：借出日 + 读者类型期限',
    return_date  DATE        NULL COMMENT 'NULL 表示未还',
    status       VARCHAR(16) NOT NULL DEFAULT 'BORROWED' COMMENT 'BORROWED/RETURNED/OVERDUE/LOST',
    renew_count  INT         NOT NULL DEFAULT 0,
    created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_reader_status (reader_id, status) COMMENT 'BR-004 在借数量 / BR-005 超期检查',
    KEY idx_item_status   (item_id, status)   COMMENT '还书时定位在借记录',
    KEY idx_due_date      (due_date)          COMMENT '超期批量扫描',
    CONSTRAINT fk_loan_reader    FOREIGN KEY (reader_id)    REFERENCES readers (id),
    CONSTRAINT fk_loan_item      FOREIGN KEY (item_id)      REFERENCES library_items (id),
    CONSTRAINT fk_loan_librarian FOREIGN KEY (librarian_id) REFERENCES librarians (id),
    CONSTRAINT ck_loan_status CHECK (status IN ('BORROWED','RETURNED','OVERDUE','LOST')),
    CONSTRAINT ck_loan_return CHECK ((status = 'RETURNED' AND return_date IS NOT NULL)
                                     OR (status <> 'RETURNED'))
) ENGINE=InnoDB COMMENT='借阅记录（可区分借出/已还/超期）';

CREATE TABLE fine_records (
    id           BIGINT         NOT NULL AUTO_INCREMENT,
    loan_id      BIGINT         NOT NULL,
    reader_id    BIGINT         NOT NULL,
    overdue_days INT            NOT NULL,
    amount       DECIMAL(10,2)  NOT NULL COMMENT 'BR-009：超期天数 x 每日罚金，不超上限',
    status       VARCHAR(16)    NOT NULL DEFAULT 'UNPAID' COMMENT 'UNPAID/PAID/WAIVED',
    created_at   DATE           NOT NULL,
    PRIMARY KEY (id),
    KEY idx_reader_status (reader_id, status),
    CONSTRAINT fk_fine_loan   FOREIGN KEY (loan_id)   REFERENCES loans (id),
    CONSTRAINT fk_fine_reader FOREIGN KEY (reader_id) REFERENCES readers (id),
    CONSTRAINT ck_fine_status CHECK (status IN ('UNPAID','PAID','WAIVED'))
) ENGINE=InnoDB COMMENT='罚款记录';

SET FOREIGN_KEY_CHECKS = 1;

-- ---------------- 4. 初始规则数据（对应 specs/01-assumptions.md） ----------------

INSERT INTO borrow_policies (reader_type, max_books, loan_period_days, max_renew_times, renew_days) VALUES
    ('JUNIOR_COLLEGE',  5, 30, 0, 30),
    ('UNDERGRADUATE',   8, 30, 1, 30),
    ('POSTGRADUATE',   12, 60, 1, 30),
    ('DOCTORAL',       15, 90, 2, 30),
    ('TEACHER',        20, 90, 2, 30);

INSERT INTO fine_rules (item_type, grace_days, daily_rate, max_amount) VALUES
    ('CN_BOOK',          0, 0.20,  50.00),
    ('FOREIGN_BOOK',     0, 0.50, 200.00),
    ('CN_MAGAZINE',      0, 0.10,  20.00),
    ('FOREIGN_MAGAZINE', 0, 0.30, 100.00),
    ('THESIS',           0, 1.00, 500.00);
