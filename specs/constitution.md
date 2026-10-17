# 开发宪法 Constitution

> **地位**：本项目所有 Agent 操作与代码评审的最高约束。与任何 `specs/` 文档冲突时，以本文件为准。
> **依据**：实验概述第 11 节示例 + 本项目在实验一/二中的实际约束。
> **适用对象**：Agent（WorkBuddy / CodeBuddy）与 Violet 本人。

---

## 1. Specs 优先原则

任何代码实现前，必须先完成需求、用例、领域模型、架构、数据库、接口、测试和任务拆解文档。

判定方式：`src/` 下新增的每个类，都要能在 `specs/09-design-model.md` 或 `specs/16-tasks.md` 中找到出处。
找不到出处的类视为违规，须先补规格再写代码。

## 2. Agent 起草，人类确认原则

Agent 可以生成规格初稿，但所有规格必须经 Violet **人工审查**后才能作为实现依据。
审查痕迹记录在 `specs/19-ai-usage-log.md` 的"人工审查结果"列，不得留空。

## 3. UML-as-Spec 原则

用例图、类图、包图、顺序图必须使用 **PlantUML 文本格式**保存到 `specs/`，纳入 Git 管理。
禁止只交图片不交源码——图片是产物，`.puml` 才是规格。

## 4. Baseline 冻结原则

Specs 审查通过后打 tag 冻结：

```
experiment1-specs-baseline-v1   # 实验一：需求 → 架构 → 领域 → 数据库
experiment2-specs-baseline-v1   # 实验二：详细设计 → API → 测试 → 任务
```

冻结后实现阶段 **Agent 不得擅自修改 specs**。
确需变更时，必须走"变更说明 → 人工确认 → 升版本号（v2）"流程，不得原地改。

## 5. 分层架构原则

系统必须采用分层架构，自外向内：

```
Presentation（Controller / DTO）
   → Application（ApplicationService / 事务边界）
      → Domain（实体 / 值对象 / 策略 / 领域服务 / Repository 接口）
         ← Infrastructure（JPA 实现 / PO / Mapper）
```

**依赖方向只能向内**。`Domain` 不得 import 任何 Spring、JPA、Jackson 类型。

## 6. MVC 与业务规则位置原则

Web 层遵循 MVC 或等价分离思想。**Controller 只负责**：接收请求、参数校验、调用应用服务、返回响应。
以下内容一律不得出现在 Controller：

- 借阅数量 / 期限判断
- 超期判断与罚款计算
- 事务控制
- 直接依赖 Repository

## 7. 权限分离原则

读者（Reader）、图书管理员（Librarian）、系统管理员（SystemAdmin）权限必须严格区分（BR-013 / BR-014）。
权限校验在 **Application Service 入口**完成，不在 Controller 里堆 `if`。

## 8. 业务规则集中原则

借阅数量、借阅期限、超期罚款等规则集中在 Policy / Rule 中实现，且**取值来源于数据库表**
（`borrow_policies` / `fine_rules`），不得硬编码在 Java 常量里。
新增一种读者类型或借出物类型，只应新增数据与枚举值，不改流程代码（OCP）。

## 9. 测试闭环原则

核心用例必须有自动化测试（NFR-005），至少覆盖：

- 办理借书（成功 / 证无效 / 超量 / 有超期未还 / 副本不可借）
- 办理还书（成功 / 非本馆藏书 / 无借阅记录 / 超期计罚）
- 预约图书（成功 / 重复预约）
- 权限失败（普通读者执行管理员操作）

测试未通过不得提交。

## 10. Git 审查原则

Agent 修改代码后，Violet 必须查看 `git diff` 再决定是否提交。
一次 commit 对应一个任务（`16-tasks.md` 的 TASK-xxx），commit message 以任务号开头。

## 11. AI 使用记录原则

每次使用 Agent 生成 specs、UML、代码或测试，都必须记录到 `specs/19-ai-usage-log.md`：
使用阶段、任务、Prompt 摘要、Agent 修改文件、测试结果、人工审查结果、Git 提交。
过程全景另记于 `docs/实验过程日志.md`，每条标注 `[AI]` 或 `[人工]`。

## 12. 领域层零框架依赖原则（本项目特有，NFR-003）

`src/main/java/com/example/library/domain/` 下的代码必须能用纯 `javac` 编译：

```bash
javac -encoding UTF-8 -d target/classes $(find src/main/java -name "*.java")
```

**这是分层是否正确的硬判据**：一旦有人往领域层 import 了 `org.springframework.*` 或
`jakarta.persistence.*`，这条命令立刻报错。Agent 每次改动领域层后必须重跑一次。

## 13. 数据库即设计校验器原则（本项目特有）

`db/schema.sql` 必须与 `13-database-design.md` 的 11 张表逐字段一致，并在真实 MySQL 8.0 上跑通。
设计不是纸面声明：约束（唯一键、外键、CHECK）必须能被实测拒绝非法数据。
实测结果写入 `13-database-design.md` 第 6.2 节。

## 14. 过程可追溯原则（课程硬性要求）

老师明确要求"每个操作之后都要记载"。因此：

- 每次实质操作后，立即向 `docs/实验过程日志.md` 追加一条，不得事后补写
- 日志必须区分 `[AI]` 与 `[人工]`，人工确认与纠错同样要记
- 被推翻的决策也要留下记录与理由，不许抹掉

---

## 违反判定速查

| 违规 | 怎么发现 |
|---|---|
| 领域层混入框架依赖 | `javac` 纯编译失败 |
| Controller 写了业务规则 | 看 Controller 方法体是否出现 `if (count > max)` 一类判断 |
| 规则取值硬编码 | 搜 `src/` 里是否出现裸数字 `30`、`0.2` 等业务常量 |
| 规格与代码不一致 | 按类的名字反查 `specs/` 是否有机出处 |
| 未经审查的 Agent 产出 | `19-ai-usage-log.md` 该条"人工审查结果"为空 |
| 事后补日志 | 日志时间戳与 commit 时间明显不符 |
