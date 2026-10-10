-- =============================================================
-- 数据库设计验证脚本
-- 用途：在 MySQL 8.0 上验证 specs/13-database-design.md 的设计确实成立
-- 执行顺序：先跑 db/schema.sql 建库建表，再跑本文件
-- 执行方式（任选其一）：
--   1) Navicat Premium 17：打开本文件 -> 全选 -> 运行
--   2) 命令行：mysql -u root -p library_ai < db/verify.sql
-- =============================================================

USE library_ai;

-- ---------- 1. 表清单：应返回 11 张表 ----------
SELECT TABLE_NAME AS '表名', TABLE_COMMENT AS '说明'
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'library_ai'
ORDER BY TABLE_NAME;

-- ---------- 2. 唯一约束：指导书点名的两个唯一键 ----------
SELECT TABLE_NAME AS '表名', INDEX_NAME AS '索引名', COLUMN_NAME AS '列'
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = 'library_ai' AND NON_UNIQUE = 0
ORDER BY TABLE_NAME, INDEX_NAME;
-- 预期：borrow_cards.uk_card_no、library_items.uk_barcode、
--       reservations.uk_reader_title_active、borrow_policies.uk_reader_type、
--       fine_rules.uk_item_type、book_titles.uk_isbn

-- ---------- 3. 外键：设计声明了 7 处 ----------
SELECT TABLE_NAME AS '表', COLUMN_NAME AS '列', REFERENCED_TABLE_NAME AS '引用表'
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = 'library_ai' AND REFERENCED_TABLE_NAME IS NOT NULL
ORDER BY TABLE_NAME;
-- 预期：borrow_cards->readers、library_items->book_titles、
--       loans->readers / library_items / librarians、
--       reservations->readers / book_titles、fine_records->loans / readers

-- ---------- 4. 初始规则数据 ----------
SELECT reader_type AS '读者类型', max_books AS '上限', loan_period_days AS '期限(天)',
       max_renew_times AS '可续借', renew_days AS '续借天数'
FROM borrow_policies ORDER BY max_books;

SELECT item_type AS '借出物类型', grace_days AS '宽限', daily_rate AS '日罚金',
       max_amount AS '上限'
FROM fine_rules ORDER BY daily_rate;

-- ---------- 5. 正向验证：插入一组最小数据，验证主流程走得通 ----------

INSERT INTO readers (reader_type, name, department, student_no, student_category)
VALUES ('UNDERGRADUATE', '张三', '计算机科学与技术学院', '20260001', 'UNDERGRADUATE');

INSERT INTO librarians (name, employee_no) VALUES ('李管理员', 'LIB001');
INSERT INTO system_admins (name, employee_no) VALUES ('王管理员', 'ADM001');

INSERT INTO borrow_cards (card_no, reader_id, owner_name, department, issued_at)
VALUES ('CARD2026000001', LAST_INSERT_ID(), '张三', '计算机科学与技术学院', CURDATE());

INSERT INTO book_titles (isbn, title, authors, publisher, published_year, item_type)
VALUES ('9787536692930', '三体', '刘慈欣', '重庆出版社', 2008, 'CN_BOOK');

INSERT INTO library_items (barcode, title_id, item_type, location, language, acquired_at)
VALUES ('BC00001234', LAST_INSERT_ID(), 'CN_BOOK', 'A区-3排-2架', '中文', CURDATE());

-- 借书：应还日期 = 今日 + 30 天（本科生规则）
INSERT INTO loans (reader_id, item_id, librarian_id, borrow_date, due_date, status)
SELECT r.id, i.id, l.id, CURDATE(), DATE_ADD(CURDATE(), INTERVAL 30 DAY), 'BORROWED'
FROM readers r, library_items i, librarians l
WHERE r.student_no = '20260001' AND i.barcode = 'BC00001234' AND l.employee_no = 'LIB001';

UPDATE library_items SET status = 'ON_LOAN' WHERE barcode = 'BC00001234';

-- 预约：面向题名排队
INSERT INTO reservations (reader_id, title_id, queue_position, status)
SELECT r.id, t.id, 1, 'WAITING'
FROM readers r, book_titles t
WHERE r.student_no = '20260001' AND t.isbn = '9787536692930';

-- ---------- 6. 看结果 ----------
SELECT r.name AS '读者', c.card_no AS '借阅证号', bt.title AS '书名',
       i.barcode AS '条码', i.status AS '副本状态',
       ln.borrow_date AS '借出日', ln.due_date AS '应还日', ln.status AS '借阅状态'
FROM loans ln
JOIN readers r ON r.id = ln.reader_id
JOIN borrow_cards c ON c.reader_id = r.id
JOIN library_items i ON i.id = ln.item_id
JOIN book_titles bt ON bt.id = i.title_id;
-- 预期：1 行，应还日 = 借出日 + 30 天，副本状态 ON_LOAN，借阅状态 BORROWED

SELECT r.name AS '读者', res.queue_position AS '排队位次', res.status AS '预约状态'
FROM reservations res JOIN readers r ON r.id = res.reader_id;
-- 预期：张三，位次 1，WAITING

-- ---------- 7. 负向验证（以下 4 条都应报错，逐条单独运行） ----------

-- 7.1 借阅证号重复 -> 期望失败（uk_card_no）
-- INSERT INTO borrow_cards (card_no, reader_id, owner_name, department, issued_at)
-- VALUES ('CARD2026000001', 1, '李四', '数学学院', CURDATE());

-- 7.2 馆藏条码重复 -> 期望失败（uk_barcode）
-- INSERT INTO library_items (barcode, title_id, item_type)
-- VALUES ('BC00001234', 1, 'CN_BOOK');

-- 7.3 教师填了学号 -> 期望失败（ck_readers_student）
-- INSERT INTO readers (reader_type, name, department, student_no)
-- VALUES ('TEACHER', '赵老师', '计算机学院', '20260099');

-- 7.4 已归还但没有归还日期 -> 期望失败（ck_loan_return）
-- INSERT INTO loans (reader_id, item_id, borrow_date, due_date, status, return_date)
-- VALUES (1, 1, CURDATE(), DATE_ADD(CURDATE(), INTERVAL 30 DAY), 'RETURNED', NULL);

-- ---------- 8. 清场（需要重跑时先执行这段） ----------
-- DELETE FROM fine_records; DELETE FROM reservations; DELETE FROM loans;
-- DELETE FROM library_items; DELETE FROM book_titles; DELETE FROM borrow_cards;
-- DELETE FROM librarians; DELETE FROM system_admins; DELETE FROM readers;
