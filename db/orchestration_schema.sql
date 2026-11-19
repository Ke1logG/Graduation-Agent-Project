-- ============================================================
--  工作流编排表（MySQL 8.x）
--  数据库：anime_db（需先执行 db/schema.sql 并完成数据导入）
--  本脚本只新增编排表，不修改、不清空现有 5 张动漫数据表。
--  使用 CREATE TABLE IF NOT EXISTS，可重复执行。
-- ============================================================

USE anime_db;

-- ------------------------------------------------------------
-- 1. 运行主表：一次完整任务运行
--    status: CREATED / PLANNING / VALIDATING / EXECUTING / RECOVERING
--            SUCCEEDED / FAILED / NEEDS_CLARIFICATION / INFEASIBLE / ABORTED_BUDGET
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS workflow_run (
  run_id              VARCHAR(36)     NOT NULL COMMENT '运行ID（UUID/ULID）',
  trace_id            VARCHAR(64)     NOT NULL COMMENT '全链路追踪ID',
  parent_run_id       VARCHAR(36)     NULL     COMMENT '澄清来源的父运行ID',
  task_id             VARCHAR(64)     NULL     COMMENT '评测任务ID，演示运行可为空',
  task_group          VARCHAR(32)     NULL     COMMENT 'single/multi/clarify/infeasible',
  prompt              TEXT            NULL     COMMENT '用户原始任务描述',
  mode                VARCHAR(16)     NOT NULL DEFAULT 'LIVE' COMMENT 'LIVE/REPLAY/EVAL',
  planner_mode        VARCHAR(16)     NOT NULL DEFAULT 'LLM' COMMENT 'LLM/SCRIPTED/RECORDED',
  fault_seed          BIGINT          NULL     COMMENT '故障注入种子',
  fault_profile       VARCHAR(32)     NULL     COMMENT '故障配置名称',
  status              VARCHAR(32)     NOT NULL DEFAULT 'CREATED' COMMENT '运行状态',
  plan_json           JSON            NULL     COMMENT '校验通过的计划',
  final_output        JSON            NULL     COMMENT '最终输出',
  error_class         VARCHAR(64)     NULL     COMMENT '失败类型',
  error_message       TEXT            NULL     COMMENT '失败说明',
  replan_count        SMALLINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '重规划次数',
  prompt_tokens       INT UNSIGNED    NULL     COMMENT '累计输入token',
  completion_tokens   INT UNSIGNED    NULL     COMMENT '累计输出token',
  cost_cny            DECIMAL(12,6)   NULL     COMMENT '估算成本（人民币）',
  owner_instance      VARCHAR(128)    NULL     COMMENT '当前执行实例',
  last_heartbeat_at   DATETIME(3)     NULL     COMMENT '执行实例心跳时间',
  created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  finished_at         DATETIME(3)     NULL,
  PRIMARY KEY (run_id),
  KEY idx_run_trace (trace_id),
  KEY idx_run_parent (parent_run_id),
  KEY idx_run_task (task_id),
  KEY idx_run_status (status),
  KEY idx_run_recovery (status, last_heartbeat_at),
  KEY idx_run_created (created_at),
  CONSTRAINT fk_run_parent FOREIGN KEY (parent_run_id) REFERENCES workflow_run(run_id)
    ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='一次完整任务运行';

-- ------------------------------------------------------------
-- 2. 步骤账本：一次运行中的一个逻辑步骤
--    status: PENDING / READY / RUNNING / SUCCEEDED / FAILED_RETRYABLE
--            FAILED_FINAL / SKIPPED / COMPENSATED
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS step_run (
  run_id              VARCHAR(36)     NOT NULL,
  step_id             VARCHAR(64)     NOT NULL COMMENT '计划中的步骤ID，如 s1',
  seq                 INT UNSIGNED    NOT NULL COMMENT '展示/调度顺序',
  tool_name           VARCHAR(64)     NOT NULL,
  args_json           JSON            NULL     COMMENT '工具参数',
  depends_on_json     JSON            NULL     COMMENT '依赖的 step_id 数组',
  status              VARCHAR(32)     NOT NULL DEFAULT 'PENDING',
  attempt_count       INT UNSIGNED    NOT NULL DEFAULT 0,
  output_json         JSON            NULL     COMMENT '步骤输出',
  error_class         VARCHAR(64)     NULL,
  error_message       TEXT            NULL,
  latency_ms          BIGINT UNSIGNED NULL,
  idempotency_key     VARCHAR(128)    NOT NULL COMMENT 'runId:stepId，重试复用',
  lease_expires_at    DATETIME(3)     NULL     COMMENT '执行租约到期时间',
  started_at          DATETIME(3)     NULL,
  finished_at         DATETIME(3)     NULL,
  created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (run_id, step_id),
  KEY idx_step_run_status (run_id, status),
  KEY idx_step_lease (lease_expires_at),
  KEY idx_step_idem (idempotency_key),
  CONSTRAINT fk_step_run FOREIGN KEY (run_id) REFERENCES workflow_run(run_id)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='运行中的逻辑步骤账本';

-- ------------------------------------------------------------
-- 3. 工具调用墓碑：一次真实的工具调用/副作用
--    idempotency_key 唯一，是重复执行次数为 0 的最后防线。
--    重试更新同一行，不在 step_run 之外产生重复副作用。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tool_invocation (
  invocation_id       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  idempotency_key     VARCHAR(128)    NOT NULL COMMENT 'runId:stepId',
  run_id              VARCHAR(36)     NOT NULL,
  step_id             VARCHAR(64)     NOT NULL,
  tool_name           VARCHAR(64)     NOT NULL,
  effect_key          VARCHAR(128)    NULL     COMMENT '业务级副作用键，如报告路径',
  status              VARCHAR(32)     NOT NULL DEFAULT 'RUNNING'
                      COMMENT 'RUNNING / SUCCEEDED / FAILED / UNKNOWN',
  request_json        JSON            NULL     COMMENT '实际调用参数',
  result_json         JSON            NULL     COMMENT '工具返回结果',
  error_class         VARCHAR(64)     NULL,
  error_message       TEXT            NULL,
  started_at          DATETIME(3)     NULL,
  finished_at         DATETIME(3)     NULL,
  created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (invocation_id),
  UNIQUE KEY uk_tool_idem (idempotency_key),
  KEY idx_tool_run_step (run_id, step_id),
  KEY idx_tool_status (status),
  CONSTRAINT fk_tool_step FOREIGN KEY (run_id, step_id)
    REFERENCES step_run(run_id, step_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='工具调用与副作用去重墓碑';

-- ------------------------------------------------------------
-- 4. LLM 调用记录：录制/回放、成本核算
--    purpose: PLAN / REPLAN / REPAIR
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS llm_call (
  id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  run_id              VARCHAR(36)     NOT NULL,
  call_seq            INT UNSIGNED    NOT NULL COMMENT '同一 run 内递增',
  purpose             VARCHAR(16)     NOT NULL COMMENT 'PLAN/REPLAN/REPAIR',
  request_hash        CHAR(64)        NOT NULL COMMENT 'prompt 的 SHA-256',
  request_text        MEDIUMTEXT      NULL     COMMENT '完整请求',
  raw_response        MEDIUMTEXT      NULL     COMMENT '完整原始响应',
  model               VARCHAR(128)    NULL,
  temperature         DECIMAL(3,2)    NULL,
  seed                BIGINT          NULL,
  prompt_tokens       INT UNSIGNED    NULL,
  completion_tokens   INT UNSIGNED    NULL,
  cost_cny            DECIMAL(12,6)   NULL,
  latency_ms          BIGINT UNSIGNED NULL,
  status              VARCHAR(32)     NOT NULL DEFAULT 'SUCCEEDED',
  error_class         VARCHAR(64)     NULL,
  error_message       TEXT            NULL,
  created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_llm_run_seq (run_id, call_seq),
  KEY idx_llm_run_purpose (run_id, purpose),
  KEY idx_llm_created (created_at),
  CONSTRAINT fk_llm_run FOREIGN KEY (run_id) REFERENCES workflow_run(run_id)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='LLM 请求与响应记录，用于回放和成本核算';

-- ------------------------------------------------------------
-- 5. 运行事件：追加式时间线
--    示例 event_type: RUN_CREATED / PLAN_VALIDATED / STEP_STARTED /
--    STEP_FAILED / RETRY_SCHEDULED / STEP_SUCCEEDED / RUN_RESUMED /
--    COMPENSATED / RUN_FINISHED
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS run_event (
  id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  run_id              VARCHAR(36)     NOT NULL,
  seq                 BIGINT UNSIGNED NOT NULL COMMENT '同一 run 内递增',
  event_type          VARCHAR(64)     NOT NULL,
  payload_json        JSON            NULL,
  created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_event_run_seq (run_id, seq),
  KEY idx_event_run_time (run_id, created_at),
  KEY idx_event_type (event_type),
  CONSTRAINT fk_event_run FOREIGN KEY (run_id) REFERENCES workflow_run(run_id)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='追加式运行事件流';

-- ------------------------------------------------------------
-- 6. writer 副作用记录
--    UNIQUE(run_id, step_id) 保证每个 run 的写文件步骤最多一条记录，
--    也是“重复副作用 = 0”的验收依据。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS generated_report (
  report_id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  run_id              VARCHAR(36)     NOT NULL,
  step_id             VARCHAR(64)     NOT NULL,
  file_path           VARCHAR(512)    NULL     COMMENT '相对输出路径',
  content             LONGTEXT        NOT NULL,
  content_hash        CHAR(64)        NOT NULL COMMENT '内容 SHA-256',
  status              VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE'
                      COMMENT 'ACTIVE / COMPENSATED',
  compensated_at      DATETIME(3)     NULL,
  created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (report_id),
  UNIQUE KEY uk_report_run_step (run_id, step_id),
  KEY idx_report_run (run_id),
  CONSTRAINT fk_report_step FOREIGN KEY (run_id, step_id)
    REFERENCES step_run(run_id, step_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='writer 生成的外部副作用记录';
