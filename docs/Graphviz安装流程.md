# Graphviz 安装流程（PlantUML 类图 / 包图依赖）

## 为什么必须装

PlantUML 的图分两类渲染引擎：

| 图类型 | 是否需要 Graphviz | 本实验涉及的文件 |
|---|---|---|
| 用例图、顺序图、活动图 | **不需要**（PlantUML 自绘） | `04`、`10`、`11`、`12` |
| **类图、包图、组件图** | **需要 dot** | `06`、`08`、`09a`、`09b` |

也就是说，**不装 Graphviz，领域类图和包图渲染不出来**，而这两张正是指导书步骤 2、3、4 的核心交付物。

---

## 方案 A：winget 一行装完（推荐）

本机已确认 `winget v1.29.380` 可用，包 ID 为 `Graphviz.Graphviz`（当前版本 16.1.0）。

以**管理员身份**打开 PowerShell，执行：

```powershell
winget install Graphviz.Graphviz
```

装完**必须重开一个终端**（PATH 不会注入到已打开的窗口），然后验证：

```bash
dot -V
```

预期输出类似：

```text
dot - graphviz version 16.1.0
```

---

## 方案 B：官网下载安装包

若 winget 因网络或权限失败：

1. 打开 https://graphviz.org/download/
2. 选 Windows 区段的 **Stable Windows install packages** → `graphviz-16.1.0 (64-bit) EXE installer`
3. 安装时勾选 **"Add Graphviz to the system PATH for all users"**（这一步别漏，否则要手动配 PATH）
4. 装完重开终端，`dot -V` 验证

---

## 方案 C：装了但 `dot -V` 找不到

多半是 PATH 没生效。默认安装路径是：

```text
C:\Program Files\Graphviz\bin
```

手动加入 PATH（PowerShell，管理员）：

```powershell
[Environment]::SetEnvironmentVariable(
    "Path",
    [Environment]::GetEnvironmentVariable("Path", "Machine") + ";C:\Program Files\Graphviz\bin",
    "Machine"
)
```

重开终端后再 `dot -V`。

---

## 装完之后：在本项目渲染 UML

### PlantUML 命令行

下 `plantuml.jar` 放到任意目录，然后在项目根目录执行：

```bash
java -jar plantuml.jar specs/*.puml
```

会在 `specs/` 下生成同名的 `.png`，可直接插进实验报告。

### VS Code 插件

1. 装插件 **PlantUML**（作者 jebbs）
2. 打开任意 `.puml`，按 `Alt + D` 预览
3. 若预览报 "dot not found"，在设置里把 `plantuml.jar` 路径和 Graphviz 的 `dot` 路径填对

> 需要 JDK 才能跑 PlantUML，本机已有 JDK 17（`C:\Users\12497\.jdks\ms-17.0.20.1`）。

---

## 常见坑

| 现象 | 原因 | 处理 |
|---|---|---|
| `dot -V` 报"不是内部或外部命令" | PATH 未生效 | 重开终端；仍不行走方案 C |
| 顺序图能出、类图出不来 | Graphviz 未装 | 就是本文件要解决的问题 |
| 类图出来但中文是方块 | 字体缺失 | PlantUML 加 `skinparam defaultFontName Microsoft YaHei` |
| 报 `Cannot find Graphviz` 但 `dot -V` 正常 | VS Code 未读到 PATH | 重启 VS Code |
