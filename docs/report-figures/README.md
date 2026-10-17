# 报告专用图

这里的图**只用于实验报告排版**，不是正式规格文件。`specs/` 下的 `.puml` 是权威版本，
本目录是为了让图能在 A4 版心（约 15.5 cm）内看清而重新布局的副本。

## 为什么另建一套

`docs/uml/` 下的原图按屏幕观看设计，尺寸如包图 4863×956、设计类图 4096×1434。
缩进 A4 宽度后字号不足 3 磅，形同废图。本目录把「横向展开」改成「纵向分层」，
横竖比控制在 2.5 以内；界面线框图用 `scale 2.2` 提高像素密度，插入文档后字号清晰。

## 清单

| 文件 | 对应报告图 | 来源 |
|---|---|---|
| `01-arch-layers.png` | 图 3 四层架构与模块依赖 | `specs/07-architecture.md`、`08-package-diagram.puml` |
| `02-borrow-key-classes.png` | 图 4 借书链路关键类图 | `specs/09b-design-class-diagram.puml`（只取 UC-101 主链） |
| `03-domain-model.png` | 图 2 领域模型类图 | `specs/06-domain-class-diagram.puml`（省略属性细节） |
| `04-sequence-borrow.png` | 图 5 借书顺序图 | `specs/10-sequence-borrow-book.puml`（9 条生命线合并为 5 条） |
| `04b~04e-ui-*.png` | 图 6~9 界面线框图 | `specs/20a~20d-*.puml`（加 `scale 2.2`） |

## 重渲

```bash
cd library-management-ai
export GRAPHVIZ_DOT="/c/Program Files/Graphviz/bin/dot.exe"
for f in docs/report-figures/src/*.puml; do
  java -DPLANTUML_LIMIT_SIZE=16384 \
       -DGRAPHVIZ_DOT="$GRAPHVIZ_DOT" \
       -jar tools/plantuml.jar -charset UTF-8 \
       -o "$(pwd)/docs/report-figures" "$f"
done
```

> `-o` 必须是绝对路径。传相对路径时 PlantUML 会相对**源文件所在目录**解析，
> 结果输出到 `docs/report-figures/src/docs/report-figures/`。
