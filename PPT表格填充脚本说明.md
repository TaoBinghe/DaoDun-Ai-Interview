# PPT 表格填充脚本说明

脚本文件：`fill_ppt_table.py`

用途：把你指定的“行、列、内容”写入 `pptx` 模板中的表格；如果输入的行数或列数超出模板现有范围，脚本会自动扩展表格。

当前已针对同目录下的 `表格模版.pptx` 做过实际测试。

## 1. 环境要求

- Windows + PowerShell
- Python 3.11 或其他较新的 Python 3 版本
- 不需要安装任何第三方库

## 2. 文件说明

- `fill_ppt_table.py`
  主脚本
- `sample_fill.json`
  示例输入配置，包含自动扩展到 `12 行 x 5 列` 的例子

## 3. 先查看模板里有哪些表格

运行：

```powershell
python fill_ppt_table.py inspect "表格模版.pptx"
```

作用：

- 查看每一页有没有表格
- 查看每个表格是几行几列
- 查看每一行当前的文本内容

你可以先用这个命令确认：

- 第几页有表格
- 第几个表格需要填
- 现有表格大小是多少

## 4. 使用方式一：通过 JSON 配置填充

运行：

```powershell
python fill_ppt_table.py fill "表格模版.pptx" "结果.pptx" --config sample_fill.json
```

执行后会生成新的 `结果.pptx`，不会覆盖原模板。

### JSON 格式

示例：

```json
{
  "slide": 1,
  "table": 1,
  "rows": 12,
  "cols": 5,
  "cells": [
    { "row": 2, "col": 1, "text": "开发语言" },
    { "row": 2, "col": 2, "text": "Python 3.11" },
    { "row": 10, "col": 4, "text": "新增列示例" },
    { "row": 12, "col": 5, "text": "自动扩展到第 12 行第 5 列" }
  ]
}
```

字段说明：

- `slide`
  第几页幻灯片，从 `1` 开始
- `table`
  该页中的第几个表格，从 `1` 开始
- `rows`
  目标总行数，可选；如果大于模板原始行数，脚本会自动补行
- `cols`
  目标总列数，可选；如果大于模板原始列数，脚本会自动补列
- `cells`
  需要写入的单元格列表
- `row`
  单元格所在行，从 `1` 开始
- `col`
  单元格所在列，从 `1` 开始
- `text`
  要写入的内容

说明：

- 如果 `cells` 里出现了超出当前表格大小的坐标，例如 `row=12`、`col=5`，脚本也会自动扩展
- 如果 `rows`、`cols` 没写，脚本会根据 `cells` 中出现的最大行列自动扩展
- 如果 `rows`、`cols` 小于模板现有大小，脚本不会缩小表格

## 5. 使用方式二：命令行直接传入行列内容

如果你不想写 JSON，也可以直接在命令里传：

```powershell
python fill_ppt_table.py fill "表格模版.pptx" "结果.pptx" --slide 1 --table 1 --rows 12 --cols 5 --cell 2 1 "开发语言" --cell 2 2 "Python 3.11" --cell 10 4 "新增列示例" --cell 12 5 "自动扩展到第 12 行第 5 列"
```

说明：

- `--slide 1`
  第 1 页
- `--table 1`
  第 1 个表格
- `--rows 12`
  最终至少扩展到 12 行
- `--cols 5`
  最终至少扩展到 5 列
- `--cell 行 列 内容`
  写入一个单元格，可重复写多个

## 6. 一次处理多个表格

如果你后面要一次处理多个表格，可以把 JSON 改成下面这种结构：

```json
{
  "operations": [
    {
      "slide": 1,
      "table": 1,
      "rows": 12,
      "cols": 5,
      "cells": [
        { "row": 2, "col": 1, "text": "A" }
      ]
    },
    {
      "slide": 2,
      "table": 1,
      "rows": 8,
      "cols": 4,
      "cells": [
        { "row": 3, "col": 2, "text": "B" }
      ]
    }
  ]
}
```

运行方式不变：

```powershell
python fill_ppt_table.py fill "表格模版.pptx" "结果.pptx" --config your_config.json
```

## 7. 已完成的实际测试

已完成以下测试：

- 使用 `inspect` 成功识别模板中的表格结构
- 使用 `sample_fill.json` 成功把表格从原始的 `9 行 x 3 列` 扩展到 `12 行 x 5 列`
- 成功生成新的输出文件
- 再次执行 `inspect`，已确认新增行列存在，新增单元格内容正确写入
- 脚本通过 `python -m py_compile fill_ppt_table.py` 语法校验

## 8. 注意事项

- 建议先关闭正在打开的 PPT 文件，再运行脚本，避免出现 `~$` 临时锁文件影响处理
- 脚本会保留模板原有样式，并用最后一行、最后一列的样式作为新增行列的样式基础
- 当前更适合普通表格
- 如果模板中存在复杂合并单元格，建议先单独测试再批量使用
- 输出文件建议写成新文件名，不要直接覆盖模板

## 9. 最常用命令汇总

查看模板结构：

```powershell
python fill_ppt_table.py inspect "表格模版.pptx"
```

按 JSON 填充：

```powershell
python fill_ppt_table.py fill "表格模版.pptx" "结果.pptx" --config sample_fill.json
```

按命令行直接填充：

```powershell
python fill_ppt_table.py fill "表格模版.pptx" "结果.pptx" --slide 1 --table 1 --rows 12 --cols 5 --cell 2 1 "开发语言" --cell 2 2 "Python 3.11" --cell 10 4 "新增列示例" --cell 12 5 "自动扩展到第 12 行第 5 列"
```
