# 图书管理系统 · 软件设计与体系结构实验

> **当前进度**
> - 实验一《应用系统的架构设计》：✅ 完成，冻结于 `experiment1-specs-baseline-v1`
> - 实验二《详细设计》：设计层 ✅ 完成（冻结于 `experiment2-specs-baseline-v1`），代码实现 ⏳ 待启动
>
> 本仓库同时是实验一、实验二的交付物。实验二的设计规格见第六节。

> 课程：《软件设计与体系结构》　学院：武汉理工大学人工智能学院
> 指导书：《软件设计与体系结构实验指导书 2026》（陈明俊，2026.8）
> 实现语言：Java 17 + Spring Boot 3.x　数据库：MySQL 8.0　Agent：WorkBuddy

## 一、本实验按哪份文件做

**内容与评分范围以 Word 指导书为准**，即实验一的 5 个步骤。
指导书末句"实验一、二的详细实施过程和步骤参考课程提供的实验资源"，因此
**实施方式采用实验资源的 AI 增强流程**：Spec 文档化、UML-as-Spec（PlantUML 入 Git）、
Agent 起草 + 人类审查、`ai-usage-log` 全程留痕。

| 来源 | 承担的角色 |
|---|---|
| Word 指导书 | 决定**做什么**：5 个步骤、每一步的交付要求 |
| 实验资源（AI 手册） | 决定**怎么做**：Spec 目录结构、澄清机制、Git 审查、AI 使用记录 |

## 二、指导书 5 步与本仓库文件的对应

| 指导书步骤 | 产出文件 |
|---|---|
| 1. 用例驱动分析（参与者、用例、用例文本、用例模型） | `specs/03-use-cases.md`、`specs/04-use-case-model.puml` |
| 2. 领域模型（类图） | `specs/05-domain-model.md`、`specs/06-domain-class-diagram.puml` |
| 3. 分层架构 + MVC，用包图表达（结合 Spring） | `specs/07-architecture.md`、`specs/08-package-diagram.puml` |
| 4. 各层类、接口及交互（类图、顺序图），体现 OO 原则与设计模式 | `specs/09-design-model.md`、`specs/09a-analysis-class-diagram.puml`、`specs/09b-design-class-diagram.puml`、`specs/10~12-sequence-*.puml` |
| 5. 数据库设计（领域模型 → 持久化类 → 关系模型） | `specs/13-database-design.md`、`db/schema.sql` |

辅助文件：

- `specs/00-project-brief.md` —— 项目背景、边界与"暂不实现"清单
- `specs/01-assumptions.md` —— **需要人工确认的关键假设**，请先读这一份
- `specs/02-requirements.md` —— 功能需求 FR 与业务规则 BR，用例文本回链到它
- `specs/19-ai-usage-log.md` —— 每次使用 Agent 的记录

## 三、如何查看 UML

渲染好的 PNG 已在 `docs/uml/`，可直接插进实验报告。

要重新生成：

```bash
bash tools/render-uml.sh
```

依赖 JDK 17 + Graphviz + 仓库内自带的 `tools/plantuml.jar`，输出到 `docs/uml/`。
**类图与包图需要 Graphviz**（`dot -V` 验证），用例图与顺序图不需要。
装 Graphviz 见 `docs/Graphviz安装流程.md`。

## 四、src 下的 Java 代码是什么

`src/main/java/com/example/library/domain/` 是**领域层骨架**，共 36 个文件，
用 JDK 17 可编译通过：

```bash
javac -encoding UTF-8 -d target/classes $(find src/main/java -name "*.java")
```

它的定位是**设计契约的可编译表达**，不是实验二的实现：

| 已写 | 未写（留给实验二） |
|---|---|
| 实体、值对象、枚举 | Controller、DTO、Mapper |
| 策略类 `BorrowPolicy` / `FineRule` | Application Service、事务注解 |
| Repository **接口** | Repository 的 JPA 实现、PO |
| `CirculationService`（借还编排） | Spring 配置、Security、测试 |

**为什么领域层能独立编译**：遵守 NFR-003，领域层不依赖 Spring、不依赖持久化框架，
因此不需要 Maven 也能 `javac` 验证。这是分层架构正确性的一条硬证据。

## 五、实验一不做什么

实验一是**架构设计与建模**，不写业务代码。代码在实验二《详细设计》中按此处冻结的 Specs 实现。

---

## 六、实验二：详细设计（设计层已完成）

实验二按 Word 指导书 3 步执行，实施细节参考 AI 版实验二手册（24 步）。
**三个 baseline 之外的补充文件**见下表。

### 6.1 实验二新增文件

| 文件 | 对应 | 说明 |
|---|---|---|
| `specs/constitution.md` | 开发宪法 | 14 条约束，含领域层零框架依赖、数据库即校验器、过程可追溯 |
| `specs/09-design-model.md` 第 7 节 | Word 步骤 1 | 数据结构、7 段算法伪代码、3 条程序运行流程、并发与复杂度 |
| `specs/14-api-spec.md` | 手册步骤 5 | 24 个 REST 接口 + 27 个错误码 + 追溯矩阵 |
| `specs/15-test-plan.md` | 手册步骤 6 | 7 个单元用例 + 21 个集成用例，含覆盖矩阵 |
| `specs/16-tasks.md` | 手册步骤 7 | 14 个 TASK，含依赖图、文件边界、禁令与检测方式 |
| `specs/20-ui-design.md` | **Word 步骤 2** | 界面元素、消息映射、可用性设计（AI 手册无此项） |
| `specs/20a~20d-*.puml` | 同上 | 4 张 salt 线框图 → `docs/uml/20*.png` |

### 6.2 Word 与 AI 手册的差异（重要）

| Word 指导书实验二 | AI 手册是否覆盖 | 处理 |
|---|---|---|
| 步骤 1 类的数据结构、算法、程序运行流程 | 部分（只有类职责） | 补进 `09` 第 7 节 |
| **步骤 2 界面元素设计** | ❌ 完全没有 | 新增 `20-ui-design.md` + 4 张线框图 |
| 步骤 3 用代码实现 | ✅ 步骤 10–22 | 拆成 `16-tasks.md` 的 14 个 TASK |

界面设计还**反推出两个接口**（借书资格预检、还书罚金预览），已补进 `14-api-spec.md` 3.7。

### 6.3 实验二进度

| 阶段 | 状态 |
|---|---|
| 详细设计与规格（`09`/`14`/`15`/`16`/`20`） | ✅ 已完成，tag `experiment2-specs-baseline-v1` |
| 代码实现（TASK-001~014） | ⏳ 待启动，见 `specs/16-tasks.md` |

代码实现前需先解决环境：`JAVA_HOME` 指向 JDK 17、`mvn` 加入 PATH（详见 `16-tasks.md` TASK-001）。

