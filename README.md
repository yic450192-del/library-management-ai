# 图书管理系统 · 实验一 架构设计

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

`.puml` 文件用 PlantUML 渲染：

```bash
java -jar plantuml.jar specs/*.puml
```

在 VS Code 安装 PlantUML 插件可直接预览。**类图与包图需要 Graphviz**（`dot -V` 验证），
用例图与顺序图不需要。

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
