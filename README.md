# Graduation-Agent-Project

本科毕业设计项目：**340 — AI Tool-Orchestration Workflow Engine（Agent 工具编排引擎）**。

本项目旨在做一个适配多领域的具备故障处理与步骤追溯功能的工作流引擎（agent）：LLM 思考并选择，组合工具，输出结构性分步计划，引擎负责校验、按 DAG 执行、持久化步骤状态、处理故障，并记录可追踪/可回放的数据。本项目以动漫检索场景为主要示例。

**当前状态（2026-11-22）：** P0 进行中。已完成依赖与配置、MySQL 数据导入与校验、11 张表（5 张动漫 + 6 张编排）、`PlanSpec` / `StepSpec`、G1 LLM 冒烟测试，以及 `PlanParser` / `PlanValidator`（L0–L3）。尚未实现 8 个工具、执行器、持久化仓库和 Web API。

### 当前进度

| 模块 | 状态 |
|---|---|
| 数据清洗、导入与校验 | ✅ |
| 数据库：5 张动漫表 + 6 张工作流表 | ✅ |
| 依赖与数据库 / LLM 配置 | ✅ |
| `PlanSpec` / `StepSpec` 与 JSON 解析 | ✅ |
| G1：真实 LLM 返回可解析计划 | ✅ 2026-11-17 |
| `PlanParser` / `PlanValidator` L0–L3 + 20 个夹具 | ✅ 2026-11-22 |
| `ScriptedPlanner` + 最小执行器 | ⏳ 下一步 |
| 8 个工具、持久化、评测、Web | ⏳ |

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
  schema.sql            5 张动漫表的建库建表脚本（不含数据导入）
  orchestration_schema.sql  6 张工作流编排表（可重复执行）
  verify_import.sql     动漫数据导入后的只读校验脚本
src/main/java/com/kl/agent/
  plan/                 PlanSpec / StepSpec / PlanParser / PlanValidator
  tool/                 ToolCatalog / ToolSchema
src/test/java/com/kl/agent/
  plan/                 解析与校验测试
  llm/                  G1 冒烟测试
src/test/resources/
  plans/                8 个合法 + 12 个非法计划夹具
  llm/                  真实 LLM 响应 fixture
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

### 工作流编排表

建表完成后即可执行 [db/orchestration_schema.sql](db/orchestration_schema.sql)，与 CSV 导入顺序无关：

```sql
SOURCE D:/Graduation-Agent-Project/db/orchestration_schema.sql;
```

这会新增 6 张表：`workflow_run`、`step_run`、`tool_invocation`、`llm_call`、`run_event`、`generated_report`。脚本使用 `CREATE TABLE IF NOT EXISTS`，不会清空已有数据；执行后共 **11 张表**。

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
# 常规测试（不会调用真实 LLM）
mvn test

# 只跑计划数据结构测试
mvn -Dtest=PlanSpecJsonTest test

# 计划解析与校验测试（8 个合法 + 12 个非法夹具）
mvn -Dtest=PlanParserTest,PlanValidatorTest test

# G1 冒烟测试：真实调用千问兼容接口，需要设置 ALI-API-KEY
mvn -Dtest=LlmSmokeTest -Dllm.smoke=true test

mvn spring-boot:run
```

> **Windows PowerShell 注意**：PowerShell 会把未加引号的 `-D` 参数在 `.` 或 `#` 处拆开。`-Dllm.smoke=true` 会被拆成 `-Dllm` 和 `.smoke=true`，Maven 报 `Unknown lifecycle phase ".smoke=true"`。带 `.` 或 `#` 的参数要加引号（或改用 Git Bash / cmd）：
>
> ```powershell
> mvn -Dtest=LlmSmokeTest "-Dllm.smoke=true" test
> mvn "-Dtest=SomeTestClass#someMethod" test
> ```
>
> `LlmSmokeTest` 会加载完整 Spring 上下文，跑之前先启动 MySQL80 服务。

当前应用还没有业务接口，启动只验证 Spring Boot 上下文。

### 本地配置

- 提交到 Git 的 `application.yaml` 只包含占位符。
- 本地真实数据库和 LLM 配置放在 `application-dev.yaml`，该文件已 gitignore。
- `application.yaml` 默认激活 `dev` profile；LLM key 使用环境变量 `ALI-API-KEY`。
- 换机器时，需要自行创建本地的 `application-dev.yaml`，或通过环境变量覆盖数据库和 LLM 配置。

## 6. 数据使用限制

- 原始数据不随仓库分发，继续从 Kaggle 原地址获取。
- 不进行数据集交换；导师/评审应独立下载原始数据。
- 仓库中的清洗数据仅用于代码参考、结果核对和复现实验流程。
- 清洗过程已排除真人姓名相关记录（`entities.csv` 中 51,276 条 staff / voice_actor / character 记录），只保留动画元数据和 studio 信息。
