# 14 · API 接口规范 API Spec（实验二步骤 5）

> 依据：`02-requirements.md`、`03-use-cases.md`、`09-design-model.md`、`13-database-design.md`
> 定位：REST API 是**展现层与领域层之间的契约**。接口定完，`16-tasks.md` 才能拆任务，
> `15-test-plan.md` 才能写验收——所以这份文档是实验二的中枢。

---

## 1. 通用约定

### 1.1 统一响应结构

所有接口（含错误）返回同一外壳，便于前端统一处理：

```json
{
  "code": "OK",
  "message": "成功",
  "data": { },
  "timestamp": "2026-10-17T10:30:00"
}
```

| 字段 | 说明 |
|---|---|
| `code` | 业务码。成功固定 `OK`；失败为 `ErrorCode` 枚举名，如 `QUOTA_EXCEEDED` |
| `message` | 中文提示，直接可展示给用户 |
| `data` | 业务数据。无返回值的接口为 `null` |
| `timestamp` | 服务端时间，便于排查 |

**业务失败也返回 HTTP 200 + `code != OK`，还是返回对应 HTTP 状态码？**

本设计选择**后者**：HTTP 状态码语义化（404 / 409 / 403 / 400），`code` 提供更细的业务区分。
理由：REST 的首要价值是让中间层（网关、监控、浏览器缓存）能读懂结果；全 200 会让所有错误
在监控里变成"成功"。代价是前端要同时判 HTTP 状态与 `code`——用统一拦截器处理一次即可。

### 1.2 鉴权（教学简化）

| 请求头 | 说明 |
|---|---|
| `X-Operator-Id` | 操作人 ID |
| `X-Operator-Role` | `READER` / `LIBRARIAN` / `SYSTEM_ADMIN` |

**为什么不做 JWT**：本实验的考核点是架构分层与详细设计，认证不是重点。
真实系统应替换为 JWT 或 Session，替换点只在 `presentation` 层的拦截器——
`constitution.md` 第 7 条要求权限校验在应用服务入口，与用哪种认证手段无关。
此简化在报告"局限与改进"中明确写出，不装作已经安全。

### 1.3 命名与格式

| 项 | 约定 | 理由 |
|---|---|---|
| JSON 字段 | `camelCase`（`cardNo`、`dueDate`） | 与 Java 字段一致，Jackson 默认；数据库 `snake_case` 由 Mapper 转换 |
| 日期 | `yyyy-MM-dd` | 借阅以天为单位 |
| 时间戳 | ISO-8601 `yyyy-MM-ddTHH:mm:ss` | 预约排队需要秒级 |
| 金额 | `{"amount": "0.40", "currency": "CNY"}` 或直接字符串 `"0.40"` | 用字符串避免前端浮点误差 |
| 分页 | `?page=0&size=20`，响应含 `page/size/total/totalPages` | `page` 从 0 起 |
| 路径 | 资源复数 + 名词，动作用子资源表达（`/circulation/borrow`） | 借还属于过程，不硬套 CRUD |

### 1.4 错误码全表

`code` 取值 = `ErrorCode` 枚举名。**加粗为本次新增**（实验一仅有 12 个，接口设计暴露了缺口）。

| code | HTTP | 提示 | 触发场景 |
|---|---|---|---|
| `VALIDATION_FAILED` **（新增）** | 400 | 请求参数不合法 | 必填缺失、格式错、分页越界 |
| `UNAUTHORIZED` **（新增）** | 401 | 未提供操作人或角色 | 缺少 `X-Operator-*` |
| `ROLE_FORBIDDEN` | 403 | 当前角色无权执行该操作 | BR-013 / BR-014 |
| `CARD_NOT_FOUND` | 404 | 借阅证不存在 | UC-101 第 1 步 |
| `READER_NOT_FOUND` **（新增）** | 404 | 读者不存在 | 办证、预约、查借阅 |
| `TITLE_NOT_FOUND` **（新增）** | 404 | 图书题名不存在 | 预约、加副本、删题名 |
| `ITEM_NOT_OWNED` | 404 | 非本馆藏书 | UC-102 第 1 步 |
| `LOAN_NOT_FOUND` | 409 | 该副本当前未借出 | UC-102 第 3 步 |
| `RESERVATION_NOT_FOUND` **（新增）** | 404 | 预约记录不存在 | UC-004 取消预约 |
| `POLICY_NOT_FOUND` **（新增）** | 404 | 未找到该读者类型的借阅规则 | 规则表缺行 |
| `FINE_RULE_NOT_FOUND` **（新增）** | 404 | 未找到该借出物类型的罚款规则 | 规则表缺行 |
| `CARD_NOT_ACTIVE` | 409 | 借阅证已挂起或注销 | BR-002 |
| `READER_NOT_ALLOWED` | 409 | 读者账号状态不允许借书 | 账号禁用 |
| `QUOTA_EXCEEDED` | 409 | 已达借阅上限 | BR-004 |
| `OVERDUE_LOAN_EXISTS` | 409 | 存在超期未还图书 | BR-005 |
| `ITEM_NOT_AVAILABLE` | 409 | 该副本当前不可借 | BR-007 |
| `RESERVATION_DUPLICATE` | 409 | 您已预约该题名 | BR-011 |
| `CARD_DUPLICATE` **（新增）** | 409 | 该读者已有有效借阅证 | BR-003 |
| `READER_DUPLICATE` **（新增）** | 409 | 该读者账号已存在 | UC-001 |
| `BARCODE_DUPLICATE` **（新增）** | 409 | 馆藏条码重复 | UC-205，`uk_item_barcode` |
| `ISBN_DUPLICATE` **（新增）** | 409 | ISBN 已存在 | UC-207 |
| `TITLE_HAS_COPIES` | 409 | 题名下仍有馆藏副本，不可删除 | BR-015 |
| `CARD_HAS_ACTIVE_LOANS` | 409 | 借阅证下存在在借记录，不可注销 | BR-015 |
| `ITEM_HAS_ACTIVE_LOAN` **（新增）** | 409 | 副本在借，不可删除 | BR-015 |
| `RESERVATION_EXISTS` **（新增）** | 409 | 题名下存在有效预约，不可删除 | BR-015 |
| `CONCURRENT_MODIFICATION` **（新增）** | 409 | 数据已被他人修改，请重试 | 乐观锁失败 |
| `RULE_INVALID` **（新增）** | 400 | 规则取值不合法 | 额度 ≤ 0、罚金为负等 |

> **这张表要同步进代码**：`ErrorCode.java` 需补齐上述加粗项，否则接口文档与实现脱节。

---

## 2. 接口总览

| # | 方法 | 路径 | 用例 | 权限 | 需求 |
|---|---|---|---|---|---|
| 1 | POST | `/api/readers` | UC-001 | 公开 | FR-001 |
| 2 | GET | `/api/readers/{readerId}` | — | Reader 本人 / Librarian | — |
| 3 | GET | `/api/book-titles` | UC-002 | Reader / Librarian | FR-002 |
| 4 | GET | `/api/book-titles/{titleId}` | UC-002 | Reader / Librarian | FR-002 |
| 5 | POST | `/api/borrow-cards` | UC-201 | SystemAdmin | FR-009 |
| 6 | DELETE | `/api/borrow-cards/{cardNo}` | UC-202 | SystemAdmin | FR-010 |
| 7 | POST | `/api/circulation/borrow` | UC-101 | Librarian | FR-006 |
| 8 | POST | `/api/circulation/return` | UC-102 | Librarian | FR-007 |
| 9 | GET | `/api/readers/{readerId}/loans` | UC-005 / UC-103 | 本人 / Librarian | FR-005、FR-008 |
| 10 | GET | `/api/readers/{readerId}/fines` | — | 本人 / Librarian | FR-005 |
| 11 | POST | `/api/reservations` | UC-003 | Reader | FR-003 |
| 12 | DELETE | `/api/reservations/{reservationId}` | UC-004 | Reader 本人 | FR-004 |
| 13 | POST | `/api/admin/librarians` | UC-203 | SystemAdmin | FR-011 |
| 14 | DELETE | `/api/admin/librarians/{librarianId}` | UC-204 | SystemAdmin | FR-012 |
| 15 | POST | `/api/admin/book-titles` | UC-207 | SystemAdmin | FR-015 |
| 16 | DELETE | `/api/admin/book-titles/{titleId}` | UC-208 | SystemAdmin | FR-016 |
| 17 | POST | `/api/admin/library-items` | UC-205 | SystemAdmin | FR-013 |
| 18 | DELETE | `/api/admin/library-items/{itemId}` | UC-206 | SystemAdmin | FR-014 |
| 19 | GET | `/api/admin/borrow-policies` | UC-209 | SystemAdmin | FR-017 |
| 20 | PUT | `/api/admin/borrow-policies/{readerType}` | UC-209 | SystemAdmin | FR-017 |
| 21 | GET | `/api/admin/fine-rules` | UC-209 | SystemAdmin | FR-017 |
| 22 | PUT | `/api/admin/fine-rules/{itemType}` | UC-209 | SystemAdmin | FR-017 |
| 23 | GET | `/api/borrow-cards/{cardNo}/eligibility` | UC-101 前置 | Librarian | FR-006（界面驱动，见 3.7） |
| 24 | GET | `/api/circulation/return-preview` | UC-102 前置 | Librarian | FR-007（界面驱动，见 3.7） |

---

## 3. 接口详述

### 3.1 读者

#### ① POST `/api/readers` — 注册读者（UC-001）

| 项 | 内容 |
|---|---|
| 权限 | 公开 |
| 请求体 | `{ "name": "张三", "department": "计算机学院", "readerType": "UNDERGRADUATE", "studentNo": "0122101", "category": "UNDERGRADUATE" }` |
| 说明 | `readerType = TEACHER` 时改传 `staffNo`、`title` |

成功 `201`：

```json
{ "code": "OK", "message": "注册成功", "data": { "readerId": 1001, "name": "张三", "readerType": "UNDERGRADUATE" } }
```

| 失败 | code |
|---|---|
| 姓名为空 / readerType 非法 / 学生缺 studentNo | `VALIDATION_FAILED` |
| 学号或工号重复 | `READER_DUPLICATE` |

#### ② GET `/api/readers/{readerId}` — 读者详情

成功 `200`：`{ readerId, name, department, readerType, status, cardNo }`（`cardNo` 为当前有效证，无则 `null`）
失败：`READER_NOT_FOUND`（404）

### 3.2 图书查询

#### ③ GET `/api/book-titles` — 查询图书（UC-002）

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `keyword` | string | 否 | 匹配书名 / 作者 / ISBN，模糊 |
| `itemType` | string | 否 | `CHINESE_BOOK` 等 |
| `availableOnly` | boolean | 否 | 只看有可借副本的题名 |
| `page` / `size` | int | 否 | 默认 0 / 20 |

成功 `200`：

```json
{
  "code": "OK", "message": "成功",
  "data": {
    "items": [
      { "titleId": 201, "title": "三体", "authors": "刘慈欣", "isbn": "9787536692930",
        "itemType": "CHINESE_BOOK", "totalCopies": 3, "availableCopies": 1 }
    ],
    "page": 0, "size": 20, "total": 1, "totalPages": 1
  }
}
```

> `availableCopies` 由后端聚合，不要求前端自己数——避免前端拿到全部副本再过滤，数据量大时不可行。

#### ④ GET `/api/book-titles/{titleId}` — 题名详情

成功 `200`：在 ③ 单条基础上增加 `copies: [{ itemId, barcode, location, status }]`
失败：`TITLE_NOT_FOUND`

### 3.3 借阅证

#### ⑤ POST `/api/borrow-cards` — 办理借阅证（UC-201）

| 项 | 内容 |
|---|---|
| 权限 | SystemAdmin |
| 请求体 | `{ "readerId": 1001 }` |
| 证号生成 | 后端生成，格式 `CARD + 年份 + 6 位序号`（BR-001），**不接受前端传入** |

成功 `201`：

```json
{ "code": "OK", "message": "办理成功", "data": { "cardNo": "CARD2026000001", "readerId": 1001, "ownerName": "张三", "issuedAt": "2026-10-17", "status": "ACTIVE" } }
```

| 失败 | code |
|---|---|
| 读者不存在 | `READER_NOT_FOUND` |
| 该读者已有有效证 | `CARD_DUPLICATE` |
| 非系统管理员 | `ROLE_FORBIDDEN` |

> 证号由后端生成而不是前端传入，是为了让 BR-001 的唯一性与格式有单一实现点，前端无法绕过。

#### ⑥ DELETE `/api/borrow-cards/{cardNo}` — 注销借阅证（UC-202）

成功 `200`：`data: null`
失败：`CARD_NOT_FOUND`、`CARD_HAS_ACTIVE_LOANS`（BR-015）、`ROLE_FORBIDDEN`

### 3.4 流通（核心）

#### ⑦ POST `/api/circulation/borrow` — 办理借书（UC-101）★

| 项 | 内容 |
|---|---|
| 权限 | Librarian（BR-013） |
| 请求体 | `{ "cardNo": "CARD2026000001", "barcode": "BC10001" }` |

成功 `201`：

```json
{
  "code": "OK", "message": "借书成功",
  "data": {
    "loanId": 5001, "cardNo": "CARD2026000001", "readerName": "张三",
    "barcode": "BC10001", "title": "三体",
    "borrowDate": "2026-10-17", "dueDate": "2026-11-16"
  }
}
```

失败场景（**顺序即校验顺序，不可调换**）：

| 序 | 场景 | code | HTTP |
|---|---|---|---|
| 1 | 借阅证不存在 | `CARD_NOT_FOUND` | 404 |
| 2 | 借阅证挂起/注销 | `CARD_NOT_ACTIVE` | 409 |
| 3 | 读者账号禁用 | `READER_NOT_ALLOWED` | 409 |
| 4 | 已达借阅上限 | `QUOTA_EXCEEDED` | 409 |
| 5 | 存在超期未还 | `OVERDUE_LOAN_EXISTS` | 409 |
| 6 | 条码非本馆藏书 | `ITEM_NOT_OWNED` | 404 |
| 7 | 副本不可借 | `ITEM_NOT_AVAILABLE` | 409 |
| 8 | 并发冲突 | `CONCURRENT_MODIFICATION` | 409 |
| 9 | 非图书管理员 | `ROLE_FORBIDDEN` | 403 |

#### ⑧ POST `/api/circulation/return` — 办理还书（UC-102）★

| 项 | 内容 |
|---|---|
| 权限 | Librarian |
| 请求体 | `{ "barcode": "BC10001" }` — **不需要借阅证**（指导书原文） |

成功 `200`（超期时带罚款）：

```json
{
  "code": "OK", "message": "还书成功",
  "data": {
    "loanId": 5001, "barcode": "BC10001", "returnDate": "2026-12-01",
    "overdue": true, "overdueDays": 15,
    "fine": { "fineId": 9001, "overdueDays": 15, "amount": "3.00", "currency": "CNY", "status": "UNPAID" }
  }
}
```

未超期时 `overdue: false`、`overdueDays: 0`、`fine: null`。

| 失败 | code |
|---|---|
| 条码非本馆藏书 | `ITEM_NOT_OWNED` |
| 该副本没有在借记录 | `LOAN_NOT_FOUND` |
| 罚款规则缺失 | `FINE_RULE_NOT_FOUND` |
| 非图书管理员 | `ROLE_FORBIDDEN` |

#### ⑨ GET `/api/readers/{readerId}/loans` — 查询借阅信息（UC-005 / UC-103）

| 参数 | 说明 |
|---|---|
| `status` | 可选，`ACTIVE` / `RETURNED` / `OVERDUE` / `ALL`（默认 `ALL`） |
| `page` / `size` | 分页 |

**权限规则**：`READER` 只能查自己（`X-Operator-Id == readerId`），`LIBRARIAN` 可查任意读者。
越权返回 `ROLE_FORBIDDEN`（403），不是 404——不隐藏资源存在性，便于管理端排障。

成功 `200`：

```json
{
  "code": "OK", "message": "成功",
  "data": {
    "items": [
      { "loanId": 5001, "barcode": "BC10001", "title": "三体",
        "borrowDate": "2026-10-17", "dueDate": "2026-11-16",
        "returnDate": null, "status": "BORROWED", "overdue": false, "overdueDays": 0 }
    ],
    "page": 0, "size": 20, "total": 1, "totalPages": 1
  }
}
```

#### ⑩ GET `/api/readers/{readerId}/fines` — 查询罚款

成功 `200`：`items: [{ fineId, loanId, overdueDays, amount, currency, status, createdAt }]`
权限同 ⑨。

### 3.5 预约

#### ⑪ POST `/api/reservations` — 预约图书（UC-003）

| 项 | 内容 |
|---|---|
| 权限 | Reader |
| 请求体 | `{ "readerId": 1001, "titleId": 201 }` |

成功 `201`：

```json
{ "code": "OK", "message": "预约成功，您排在第 2 位", "data": { "reservationId": 7001, "titleId": 201, "queuePosition": 2, "status": "WAITING", "expireAt": "2026-10-20T10:30:00" } }
```

| 失败 | code |
|---|---|
| 读者不存在 | `READER_NOT_FOUND` |
| 题名不存在 | `TITLE_NOT_FOUND` |
| 已有同题名有效预约 | `RESERVATION_DUPLICATE` |

#### ⑫ DELETE `/api/reservations/{reservationId}` — 取消预约（UC-004）

成功 `200`：`data: null`，状态转 `CANCELLED`
失败：`RESERVATION_NOT_FOUND`、非本人 → `ROLE_FORBIDDEN`

### 3.6 系统管理

#### ⑬ POST `/api/admin/librarians` — 添加图书管理员（UC-203）

请求体：`{ "name": "李老师", "employeeNo": "LIB001" }`
成功 `201`：`{ librarianId, name, employeeNo }`
失败：`ROLE_FORBIDDEN`、`VALIDATION_FAILED`

#### ⑭ DELETE `/api/admin/librarians/{librarianId}` — 删除管理员（UC-204）

成功 `200`；失败：`ROLE_FORBIDDEN`

#### ⑮ POST `/api/admin/book-titles` — 添加题名（UC-207）

请求体：`{ "isbn": "9787536692930", "title": "三体", "authors": "刘慈欣", "publisher": "重庆出版社", "publishedYear": 2008, "itemType": "CHINESE_BOOK" }`
成功 `201`：`{ titleId, isbn, title }`
失败：`ISBN_DUPLICATE`、`VALIDATION_FAILED`、`ROLE_FORBIDDEN`

#### ⑯ DELETE `/api/admin/book-titles/{titleId}` — 删除题名（UC-208）

成功 `200`；失败：`TITLE_NOT_FOUND`、`TITLE_HAS_COPIES`（BR-015）、`RESERVATION_EXISTS`、`ROLE_FORBIDDEN`

#### ⑰ POST `/api/admin/library-items` — 添加馆藏副本（UC-205）

请求体：`{ "titleId": 201, "barcode": "BC10001", "location": "A区3排2层" }`
成功 `201`：`{ itemId, barcode, titleId, itemType, status: "AVAILABLE" }`
失败：`TITLE_NOT_FOUND`、`BARCODE_DUPLICATE`、`ROLE_FORBIDDEN`

> `itemType` 不从请求体取，由所属 `titleId` 的题名继承——防止同一题名下出现类型矛盾的副本。

#### ⑱ DELETE `/api/admin/library-items/{itemId}` — 删除副本（UC-206）

成功 `200`；失败：`ITEM_HAS_ACTIVE_LOAN`、`ROLE_FORBIDDEN`

#### ⑲⑳ 借阅规则（UC-209）

`GET /api/admin/borrow-policies` 成功：

```json
{ "code": "OK", "message": "成功", "data": { "items": [
  { "readerType": "UNDERGRADUATE", "maxBooks": 5, "loanPeriodDays": 30, "maxRenewTimes": 1, "renewDays": 15 }
] } }
```

`PUT /api/admin/borrow-policies/{readerType}` 请求体：`{ "maxBooks": 6, "loanPeriodDays": 30, "maxRenewTimes": 1, "renewDays": 15 }`
失败：`POLICY_NOT_FOUND`（改为 POST 新增）、`RULE_INVALID`（额度 ≤ 0 或期限 ≤ 0）、`ROLE_FORBIDDEN`

#### ㉑㉒ 罚款规则（UC-209）

`GET /api/admin/fine-rules` 成功：

```json
{ "data": { "items": [
  { "itemType": "CHINESE_BOOK", "graceDays": 0, "dailyRate": "0.20", "maxAmount": "20.00", "currency": "CNY" }
] } }
```

`PUT /api/admin/fine-rules/{itemType}` 请求体：`{ "graceDays": 0, "dailyRate": "0.20", "maxAmount": "20.00" }`
失败：`FINE_RULE_NOT_FOUND`、`RULE_INVALID`（日罚金为负）、`ROLE_FORBIDDEN`

> 规则修改后**立即生效于下一次借还**，不回改已有记录——已生成的 `loans.due_date` 与
> `fine_records.amount` 是快照，不随规则回溯。这一点必须写进接口文档，否则会出现
> "我改了罚金规则，为什么上个月的罚款没变"的质疑。

---

### 3.7 界面驱动补充接口（由 `20-ui-design.md` 反推出来）

> 这两个接口**不在实验二手册的原始接口清单里**，是做界面设计时发现的缺口：
> 借还台的界面需要在"提交前"就看到读者额度与应缴罚金，否则馆员无法当场答复读者。
> 记录下来，因为它证明了界面设计不是"画完就算"，而会反作用于接口设计。

#### ㉓ GET `/api/borrow-cards/{cardNo}/eligibility` — 借书资格预检

| 项 | 内容 |
|---|---|
| 权限 | Librarian |
| 用途 | 扫证后立即展示读者状态，决定是否启用条码输入框 |
| 副作用 | **无**（纯查询，幂等） |

成功 `200`：

```json
{
  "code": "OK", "message": "成功",
  "data": {
    "cardNo": "CARD2026000001", "cardStatus": "ACTIVE",
    "readerId": 1001, "readerName": "张三", "readerType": "UNDERGRADUATE",
    "activeLoanCount": 2, "maxBooks": 5, "remainingQuota": 3,
    "hasOverdue": false, "eligible": true
  }
}
```

失败：`CARD_NOT_FOUND`(404)、`CARD_NOT_ACTIVE`(409)——**证无效直接返回错误，不返回 `eligible:false`**，
因为"证不存在"与"证存在但不能借"在界面上要显示不同的提示语。

> **预检不是授权**。`eligible: true` 只是提示，第 ⑦ 号接口仍会完整重跑五步校验。
> 两次扫码之间读者可能在别的窗口借满了，所以最终以 ⑦ 的结果为准。

#### ㉔ GET `/api/circulation/return-preview` — 还书预览

| 项 | 内容 |
|---|---|
| 权限 | Librarian |
| 参数 | `?barcode=BC10001` |
| 用途 | 确认还书前展示题名、超期天数、**预计罚金** |

成功 `200`：

```json
{
  "code": "OK", "message": "成功",
  "data": {
    "barcode": "BC10001", "title": "三体", "readerName": "张三",
    "borrowDate": "2026-10-17", "dueDate": "2026-11-16",
    "overdue": true, "overdueDays": 15, "estimatedFine": "3.00"
  }
}
```

未超期时 `overdue: false`、`overdueDays: 0`、`estimatedFine: "0.00"`。

失败：`ITEM_NOT_OWNED`(404)、`LOAN_NOT_FOUND`(409)

> `estimatedFine` 是**估算**：按当前时间算，与真正还书可能差几秒，不影响天数。
> 字段名用 `estimated` 前缀就是要让调用方知道它不具约束力——实际罚金以 ⑧ 的 `fine.amount` 为准。

---

## 4. 本实验不实现的接口（明确边界）

| 接口 | 原因 |
|---|---|
| 续借 `POST /api/circulation/renew` | 指导书与实验二手册均未要求。领域层 `Loan.renew` 与 `BorrowPolicy.maxRenewTimes` 已预留，接口留待扩展 |
| 登录 / JWT | 见 1.2，用请求头替代 |
| 图书丢失赔偿 | `LoanStatus.LOST` 已预留状态，无对应流程 |
| 罚款缴纳 | `FineStatus.PAID` 已预留状态，无支付流程 |

> 写这一节的目的：让"没做"变成**明确的决定**，而不是被问出来才发现遗漏。

---

## 5. 追溯矩阵

| 需求 | 接口 | 用例 | 测试用例（见 `15-test-plan.md`） |
|---|---|---|---|
| FR-001 | ① | UC-001 | TC-101 |
| FR-002 | ③④ | UC-002 | TC-117 |
| FR-003 | ⑪ | UC-003 | TC-007、TC-113 |
| FR-004 | ⑫ | UC-004 | TC-118 |
| FR-005 | ⑨⑩ | UC-005 | TC-111 |
| FR-006 | ⑦㉓ | UC-101 | TC-001、TC-002、TC-005、TC-103~107、TC-120 |
| FR-007 | ⑧㉔ | UC-102 | TC-003、TC-004、TC-006、TC-108~110、TC-121 |
| FR-008 | ⑨ | UC-103 | TC-111、TC-112 |
| FR-009 | ⑤ | UC-201 | TC-102 |
| FR-010 | ⑥ | UC-202 | TC-119 |
| FR-011~014 | ⑬⑭⑰⑱ | UC-203~206 | — |
| FR-015~016 | ⑮⑯ | UC-207~208 | TC-115、TC-116 |
| FR-017 | ⑲~㉒ | UC-209 | TC-001、TC-004 |
| NFR-004 权限 | 全部 | — | TC-112、TC-115 |
