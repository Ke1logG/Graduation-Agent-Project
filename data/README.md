# 数据集说明

## 1. 数据源

| 项 | 内容 |
|---|---|
| 公开数据集名称 | Anime dataset (Top 10K Normalized) |
| 来源 URL | https://www.kaggle.com/datasets/udaykumar025/anime-dataset-top-10k-normalized |
| 所有者 / 发布者 | Uday Kumar（Kaggle 用户：`udaykumar025`） |
| 许可证 | CC0 1.0 Universal (Public Domain Dedication) |
| Paper URL / DOI | N/A |
| 访问日期 | 2026-11-13 |
| 版本 / 最后更新 | v1 / 2026-06-29（以 Kaggle 页面为准） |

以上数据源信息与 `Annex_A_Ethics_Checklist` 的 DATA USAGE 部分保持一致。

## 2. 数据交换与分发限制

根据伦理检查表和课程要求：

- **学生与导师不得交换数据集**；双方应各自从上面的原始来源独立下载。
- 本仓库**不重新分发原始数据**；原始下载包不在版本控制中，且应继续从 Kaggle 原地址获取。
- `data/anime_clean/` 下的 CSV 是清洗后的**参考副本**，仅用于代码实现、结果核对和复现实验流程，不作为导师或评审的数据来源。
- 清洗只保留公开的动画元数据；不包含个人数据，不涉及人类被试。

## 3. 原始文件与 SHA-256

清洗脚本只读取以下 4 个原始文件（行数不含表头）：

| 原始文件 | 行数 | SHA-256 |
|---|---:|---|
| `anime.csv` | 10,000 | `bae9f5c09c73887fe82caad6d39d91486e540e2395706bc8a102fea2edcb587c` |
| `anime_genres.csv` | 31,164 | `babb1d6ca0af3a46281dc2d8881d4c28c51542289e2efc75d9991d4bdcdeb790` |
| `entities.csv` | 53,350 | `2d7565e9a3ec3bc7dd4e8134b08a3e94954efb6431a9b58c47ac1a7ded3e4c95` |
| `anime_companies.csv` | 56,627 | `4fb45443885abe5e7a818ca3df61c358ff79bfcc3ee1a5dc18a5119d63c5337a` |

校验和按 2026-11-14 的本地副本计算；如果 Kaggle 页面更新，哈希可能变化，应以实际下载版本为准。

另外 3 个文件 `anime_characters.csv`、`anime_staff.csv`、`anime_voice_actors.csv` 包含真人姓名，清洗脚本**不读取**。`entities.csv` 中 `staff` / `voice_actor` / `character` 类型的 51,276 条记录也在清洗阶段被丢弃，最终只保留 `studio` 记录。这与伦理检查表中“discarded during preprocessing and never entered the database”一致。

## 4. 清洗与复现

```bash
python data/data-clean.py --src "D:/Anime-dataset" --out data/anime_clean
```

- `--src`：原始数据目录，必须包含上表中的 4 个文件。
- `--out`：输出目录，默认是 `data/anime_clean`。
- 脚本不读取 `anime_characters.csv` / `anime_staff.csv` / `anime_voice_actors.csv`。
- 缺失值保持为空字符串，入库时转成 `NULL`；不做 0、均值或中位数填补。
- 输出为 UTF-8 无 BOM；CSV 使用 Python `csv` 模块的默认 CRLF 行尾。

清洗后输出：

| 输出文件 | 行数 |
|---|---:|
| `anime.csv` | 9,999 |
| `genre.csv` | 76 |
| `anime_genre.csv` | 31,160 |
| `studio.csv` | 718 |
| `anime_studio.csv` | 10,440 |
| `quality_report.md` | — |

在当前检出中已验证：重新运行脚本得到的 5 个 CSV 与 `data/anime_clean/` 中的文件内容一致。

## 5. 清洗原则与局限

- 只修复能确定是错误的地方（重复拼接的题材、整行重复、垃圾公司名等）。
- 缺失值一律保留，不猜测、不填补。
- 标题只有英文 / 罗马音，没有中日文别名；日期只有年份精度；数据仅包含 MAL 评分排名前 10,000 的作品。

完整的清洗明细、覆盖度、空值统计和已知局限见 [`data/quality_report.md`](quality_report.md)。
