-- ============================================================
--  为已创建的 workflow_run 增加 parent_run_id
--  用途：把“澄清”建模为两次独立运行；第二次运行指向第一次。
--  适用：已经执行过 db/orchestration_schema.sql 的数据库。
--  说明：脚本可重复执行；每一步都会先检查是否已存在。
-- ============================================================

USE anime_db;

-- ------------------------------------------------------------
-- 1. 增加 parent_run_id 列
-- ------------------------------------------------------------
SET @has_column := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'workflow_run'
    AND COLUMN_NAME = 'parent_run_id'
);

SET @ddl := IF(@has_column = 0,
  'ALTER TABLE workflow_run ADD COLUMN parent_run_id VARCHAR(36) NULL COMMENT ''澄清来源的父运行ID'' AFTER trace_id',
  'SELECT ''parent_run_id 已存在，跳过'' AS message'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 2. 增加索引
-- ------------------------------------------------------------
SET @has_index := (
  SELECT COUNT(*)
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'workflow_run'
    AND INDEX_NAME = 'idx_run_parent'
);

SET @ddl := IF(@has_index = 0,
  'ALTER TABLE workflow_run ADD KEY idx_run_parent (parent_run_id)',
  'SELECT ''idx_run_parent 已存在，跳过'' AS message'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 3. 增加自引用外键
--    父运行被删除时，子运行的 parent_run_id 置空，不级联删除子运行。
-- ------------------------------------------------------------
SET @has_fk := (
  SELECT COUNT(*)
  FROM information_schema.TABLE_CONSTRAINTS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'workflow_run'
    AND CONSTRAINT_NAME = 'fk_run_parent'
    AND CONSTRAINT_TYPE = 'FOREIGN KEY'
);

SET @ddl := IF(@has_fk = 0,
  'ALTER TABLE workflow_run ADD CONSTRAINT fk_run_parent FOREIGN KEY (parent_run_id) REFERENCES workflow_run(run_id) ON DELETE SET NULL',
  'SELECT ''fk_run_parent 已存在，跳过'' AS message'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 4. 验证
-- ------------------------------------------------------------
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'workflow_run'
  AND COLUMN_NAME = 'parent_run_id';

SELECT CONSTRAINT_NAME, COLUMN_NAME, REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'workflow_run'
  AND COLUMN_NAME = 'parent_run_id';

SHOW CREATE TABLE workflow_run;
