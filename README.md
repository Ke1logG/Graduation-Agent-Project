# Graduation-Agent-Project

本科毕业设计项目：**340 — AI Tool-Orchestration Workflow Engine（Agent 工具编排引擎）**。

项目目标是做一个领域无关的工作流引擎：LLM 只输出结构化计划，引擎负责校验、按 DAG 执行、持久化步骤状态、处理故障，并记录可追踪/可回放的数据。动漫检索工具包只是试验场。

**当前状态：** Spring Boot 脚手架 + 清洗后的动漫数据 + 数据库脚本；工作流引擎、规划器、工具层和 Web API 尚未实现。

## 技术栈

- Java 21（本机 JDK 23 也可编译/运行）
- Spring Boot 4.1.2
- Spring AI 2.0.1（OpenAI 兼容接口）
- MySQL 8.0
- Maven 3.9+

## 目录

```text
data/
  anime_clean/          清洗后的 5 张 CSV（参考副本）
  data-clean.py         数据清洗脚本
  README.md             数据源、许可证、SHA-256、清洗说明
  quality_report.md     清洗质量报告
db/
  schema.sql            建库建表脚本（不含数据导入）
  verify_import.sql     导入后只读校验脚本
src/                    Spring Boot 源码
pom.xml
```

## 1. 下载原始数据

从公开数据源自行下载：

- 数据集：**Anime dataset (Top 10K Normalized)**
- 地址：https://www.kaggle.com/datasets/udaykumar025/anime-dataset-top-10k-normalized
- 所有者：Uday Kumar（Kaggle 用户 `udaykumar025`）
- 许可证：CC0 1.0 Universal (Public Domain Dedication)
- 访问日期：2026-11-13

清洗脚本需要以下 4 个原始文件：

```text
anime.csv
anime_genres.csv
entities.csv
anime_companies.csv
```

原始文件的 SHA-256 和行数见 [data/README.md](data/README.md)。另外 3 个含真人姓名的文件（`anime_characters.csv`、`anime_staff.csv`、`anime_voice_actors.csv`）不会被清洗脚本读取。

> 根据课程/伦理要求：**导师和学生不得交换数据集**，双方应各自从上面的原始地址独立下载。仓库中的 `data/anime_clean/` 仅作为参考副本，不作为导师或评审的数据来源。

## 2. 运行清洗脚本

要求 Python 3.10+。

```bash
python data/data-clean.py --src "D:/Anime-dataset" --out data/anime_clean
```

- `--src`：原始数据目录，需包含上面的 4 个文件。
- `--out`：输出目录，默认 `data/anime_clean`。
- 缺失值保持为空，不填补；输出 UTF-8、无 BOM。

输出与行数：

| 文件 | 行数 |
|---|---:|
| `data/anime_clean/anime.csv` | 9,999 |
| `data/anime_clean/genre.csv` | 76 |
| `data/anime_clean/anime_genre.csv` | 31,160 |
| `data/anime_clean/studio.csv` | 718 |
| `data/anime_clean/anime_studio.csv` | 10,440 |

如果不想重新清洗，仓库中已经包含上述清洗后的 CSV，可直接用于后续导入。清洗明细见 [data/quality_report.md](data/quality_report.md)。

## 3. 建库建表

1. 启动 MySQL 8.0 服务（Windows 服务名 `MySQL80`，需要管理员权限）：

   ```powershell
   net start MySQL80
   ```

2. 使用 MySQL Workbench、Navicat，或完整路径的客户端连接：

   ```text
   C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe -u root -p
   ```

3. 执行 [db/schema.sql](db/schema.sql)，创建数据库 `anime_db` 和 5 张表：

   ```sql
   SOURCE D:/Graduation-Agent-Project/db/schema.sql;
   ```

> `db/schema.sql` 包含 `DROP TABLE IF EXISTS`，是**初始化脚本**。已有数据时重跑会清空 5 张表；应用启动时不要自动执行它。

## 4. 手动导入 CSV

当前没有提供自动导入脚本，使用 Navicat 或 MySQL Workbench 的导入向导手动导入。**必须按以下顺序导入**，否则会违反外键约束：

| 顺序 | CSV 文件 | 目标表 |
|---:|---|---|
| 1 | `data/anime_clean/anime.csv` | `anime` |
| 2 | `data/anime_clean/genre.csv` | `genre` |
| 3 | `data/anime_clean/anime_genre.csv` | `anime_genre` |
| 4 | `data/anime_clean/studio.csv` | `studio` |
| 5 | `data/anime_clean/anime_studio.csv` | `anime_studio` |

以 Navicat 为例：

1. 连接 MySQL，双击打开 `anime_db`。
2. 右键目标表 → **导入向导 / Import Wizard**。
3. 选择对应的 CSV 文件。
4. 编码选择 **UTF-8**；分隔符选择逗号 `,`；勾选“第一行为字段名”。
5. 目标选择**已有表**（例如 `anime`），不要让向导自动新建表。
6. 检查字段映射后执行导入。
7. 按上表顺序重复 5 次。

注意：

- `anime.csv` 的简介字段包含逗号和引号，导入向导应使用 CSV 解析模式，不要按普通文本分隔。
- 缺失值应导入为 `NULL`，不要填成 0 或空字符串。
- 导入完成后执行 [db/verify_import.sql](db/verify_import.sql)，所有检查应为 `PASS`。期望行数：`anime` 9999、`genre` 76、`anime_genre` 31160、`studio` 718、`anime_studio` 10440。

```sql
SOURCE D:/Graduation-Agent-Project/db/verify_import.sql;
```

## 5. 构建、测试与运行

项目使用系统 Maven；`.mvn/wrapper` 不完整，`mvnw` 尚不可用。

```bash
mvn test
mvn spring-boot:run
```

当前应用还没有业务接口，启动只验证 Spring Boot 上下文。后续配置数据库和 LLM 后会补充环境变量说明。

## 6. 数据使用限制

- 原始数据不随仓库分发，继续从 Kaggle 原地址获取。
- 不进行数据集交换；导师/评审应独立下载原始数据。
- 仓库中的清洗数据仅用于代码参考、结果核对和复现实验流程。
- 清洗过程已排除真人姓名相关记录（`entities.csv` 中 51,276 条 staff / voice_actor / character 记录），只保留动画元数据和 studio 信息。
