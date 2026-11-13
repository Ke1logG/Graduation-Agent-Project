-- ============================================================
-- 导入后校验脚本（只读，不修改任何数据）
-- 用法：Navicat 连接 MySQL -> 打开 anime_db -> 新建查询
--       打开本文件 -> 点击“运行”
-- 期望值来自 data/quality_report.md
-- ============================================================

USE anime_db;

-- ------------------------------------------------------------
-- 0. 表、字符集、关键字段类型
--    如果这里是 Navicat 导入向导自动建的表，类型/外键可能不对
-- ------------------------------------------------------------
SELECT TABLE_NAME, ENGINE, TABLE_COLLATION
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
ORDER BY TABLE_NAME;

SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_KEY
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND (
       (TABLE_NAME = 'anime'        AND COLUMN_NAME IN ('anime_id','title','score','rank','popularity','members','synopsis','start_year','end_year','type','episodes','image_url'))
    OR (TABLE_NAME = 'genre'        AND COLUMN_NAME IN ('genre_id','category','name'))
    OR (TABLE_NAME = 'anime_genre'  AND COLUMN_NAME IN ('anime_id','genre_id'))
    OR (TABLE_NAME = 'studio'       AND COLUMN_NAME IN ('studio_id','name'))
    OR (TABLE_NAME = 'anime_studio' AND COLUMN_NAME IN ('anime_id','studio_id'))
  )
ORDER BY TABLE_NAME, ORDINAL_POSITION;

-- 外键数量，期望 4（anime_genre 两个、anime_studio 两个）
SELECT COUNT(*) AS fk_count
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = DATABASE()
  AND REFERENCED_TABLE_NAME IS NOT NULL;

-- ------------------------------------------------------------
-- 1. 一键 PASS/FAIL 总览
-- ------------------------------------------------------------
SELECT 'anime 行数' AS check_name,
       (SELECT COUNT(*) FROM anime) AS actual,
       9999 AS expected,
       IF((SELECT COUNT(*) FROM anime) = 9999, 'PASS', 'FAIL') AS status
UNION ALL
SELECT 'genre 行数',
       (SELECT COUNT(*) FROM genre), 76,
       IF((SELECT COUNT(*) FROM genre) = 76, 'PASS', 'FAIL')
UNION ALL
SELECT 'anime_genre 行数',
       (SELECT COUNT(*) FROM anime_genre), 31160,
       IF((SELECT COUNT(*) FROM anime_genre) = 31160, 'PASS', 'FAIL')
UNION ALL
SELECT 'studio 行数',
       (SELECT COUNT(*) FROM studio), 718,
       IF((SELECT COUNT(*) FROM studio) = 718, 'PASS', 'FAIL')
UNION ALL
SELECT 'anime_studio 行数',
       (SELECT COUNT(*) FROM anime_studio), 10440,
       IF((SELECT COUNT(*) FROM anime_studio) = 10440, 'PASS', 'FAIL')
UNION ALL
SELECT '有题材的作品数',
       (SELECT COUNT(DISTINCT anime_id) FROM anime_genre), 8655,
       IF((SELECT COUNT(DISTINCT anime_id) FROM anime_genre) = 8655, 'PASS', 'FAIL')
UNION ALL
SELECT '有公司的作品数',
       (SELECT COUNT(DISTINCT anime_id) FROM anime_studio), 9506,
       IF((SELECT COUNT(DISTINCT anime_id) FROM anime_studio) = 9506, 'PASS', 'FAIL')
UNION ALL
SELECT '2025 年作品数',
       (SELECT COUNT(*) FROM anime WHERE start_year = 2025), 329,
       IF((SELECT COUNT(*) FROM anime WHERE start_year = 2025) = 329, 'PASS', 'FAIL')
UNION ALL
SELECT '2026 年作品数（应为 0）',
       (SELECT COUNT(*) FROM anime WHERE start_year = 2026), 0,
       IF((SELECT COUNT(*) FROM anime WHERE start_year = 2026) = 0, 'PASS', 'FAIL')
UNION ALL
SELECT 'episodes 缺失（NULL 或 0）',
       (SELECT COUNT(*) FROM anime WHERE episodes IS NULL OR episodes = 0), 48,
       IF((SELECT COUNT(*) FROM anime WHERE episodes IS NULL OR episodes = 0) = 48, 'PASS', 'FAIL')
UNION ALL
SELECT 'synopsis 缺失（NULL 或空串）',
       (SELECT COUNT(*) FROM anime WHERE synopsis IS NULL OR synopsis = ''), 2,
       IF((SELECT COUNT(*) FROM anime WHERE synopsis IS NULL OR synopsis = '') = 2, 'PASS', 'FAIL')
UNION ALL
SELECT 'start_year 缺失（NULL 或 0）',
       (SELECT COUNT(*) FROM anime WHERE start_year IS NULL OR start_year = 0), 2,
       IF((SELECT COUNT(*) FROM anime WHERE start_year IS NULL OR start_year = 0) = 2, 'PASS', 'FAIL')
UNION ALL
SELECT 'end_year 缺失（NULL 或 0）',
       (SELECT COUNT(*) FROM anime WHERE end_year IS NULL OR end_year = 0), 84,
       IF((SELECT COUNT(*) FROM anime WHERE end_year IS NULL OR end_year = 0) = 84, 'PASS', 'FAIL')
UNION ALL
SELECT '外键孤儿记录（期望 0）',
       (SELECT COUNT(*) FROM anime_genre ag LEFT JOIN anime a ON ag.anime_id = a.anime_id WHERE a.anime_id IS NULL)
     + (SELECT COUNT(*) FROM anime_genre ag LEFT JOIN genre g ON ag.genre_id = g.genre_id WHERE g.genre_id IS NULL)
     + (SELECT COUNT(*) FROM anime_studio ast LEFT JOIN anime a ON ast.anime_id = a.anime_id WHERE a.anime_id IS NULL)
     + (SELECT COUNT(*) FROM anime_studio ast LEFT JOIN studio s ON ast.studio_id = s.studio_id WHERE s.studio_id IS NULL),
       0,
       IF((
            (SELECT COUNT(*) FROM anime_genre ag LEFT JOIN anime a ON ag.anime_id = a.anime_id WHERE a.anime_id IS NULL)
          + (SELECT COUNT(*) FROM anime_genre ag LEFT JOIN genre g ON ag.genre_id = g.genre_id WHERE g.genre_id IS NULL)
          + (SELECT COUNT(*) FROM anime_studio ast LEFT JOIN anime a ON ast.anime_id = a.anime_id WHERE a.anime_id IS NULL)
          + (SELECT COUNT(*) FROM anime_studio ast LEFT JOIN studio s ON ast.studio_id = s.studio_id WHERE s.studio_id IS NULL)
       ) = 0, 'PASS', 'FAIL')
UNION ALL
SELECT '重复标题（期望 0）',
       (SELECT COUNT(*) FROM (SELECT title FROM anime GROUP BY title HAVING COUNT(*) > 1) t), 0,
       IF((SELECT COUNT(*) FROM (SELECT title FROM anime GROUP BY title HAVING COUNT(*) > 1) t) = 0, 'PASS', 'FAIL')
UNION ALL
SELECT '垃圾公司名（期望 0）',
       (SELECT COUNT(*) FROM studio
        WHERE LOWER(TRIM(name)) IN ('none found','add some','none','unknown','null','')), 0,
       IF((SELECT COUNT(*) FROM studio
           WHERE LOWER(TRIM(name)) IN ('none found','add some','none','unknown','null','')) = 0, 'PASS', 'FAIL')
UNION ALL
SELECT '表数量（期望 5）',
       (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()), 5,
       IF((SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()) = 5, 'PASS', 'FAIL');

-- ------------------------------------------------------------
-- 2. NULL / 空串明细
--    如果 Navicat 把空值导成了 0 或空串，这里能看出来
-- ------------------------------------------------------------
SELECT
  SUM(episodes IS NULL)              AS episodes_null,
  SUM(episodes = 0)                  AS episodes_zero,
  SUM(synopsis IS NULL)              AS synopsis_null,
  SUM(synopsis = '')                 AS synopsis_empty,
  SUM(start_year IS NULL)            AS start_year_null,
  SUM(start_year = 0)                AS start_year_zero,
  SUM(end_year IS NULL)              AS end_year_null,
  SUM(end_year = 0)                  AS end_year_zero,
  SUM(score IS NULL OR score = 0)    AS score_missing,
  SUM(type IS NULL OR type = '')     AS type_missing,
  SUM(title IS NULL OR title = '')   AS title_missing,
  SUM(image_url IS NULL OR image_url = '') AS image_url_missing
FROM anime;

-- 期望：episodes 48（NULL 或 0 合计）、synopsis 2、start_year 2、end_year 84
-- score/type/title 期望 0；image_url 非关键字段，只做观察

-- ------------------------------------------------------------
-- 3. 数值范围与分布
-- ------------------------------------------------------------
SELECT
  MIN(score) AS min_score,
  MAX(score) AS max_score,
  ROUND(AVG(score), 2) AS avg_score,
  MIN(start_year) AS min_year,
  MAX(start_year) AS max_year
FROM anime;
-- 期望：6.05 / 9.29 / 6.98 / 1956 / 2025

SELECT type, COUNT(*) AS cnt
FROM anime
GROUP BY type
ORDER BY cnt DESC;
-- 期望：TV 4330 / Movie 1723 / ONA 1338 / OVA 1172 / Special 1003 / TV Special 433

SELECT category, COUNT(*) AS cnt
FROM genre
GROUP BY category
ORDER BY category;
-- 期望：Demographic 5 / Genre 19 / Theme 52

SELECT g.name, COUNT(*) AS cnt
FROM anime_genre ag
JOIN genre g ON ag.genre_id = g.genre_id
GROUP BY g.name
ORDER BY cnt DESC
LIMIT 5;
-- 期望：Comedy 2882 / Action 2812 / Fantasy 2470 / Adventure 2376 / Shounen 1743

-- ------------------------------------------------------------
-- 4. 唯一性与重复检查
-- ------------------------------------------------------------
SELECT 'anime_genre 关联对' AS item,
       COUNT(*) AS total_rows,
       COUNT(DISTINCT anime_id, genre_id) AS distinct_pairs
FROM anime_genre;

SELECT 'anime_studio 关联对' AS item,
       COUNT(*) AS total_rows,
       COUNT(DISTINCT anime_id, studio_id) AS distinct_pairs
FROM anime_studio;

SELECT category, name, COUNT(*) AS cnt
FROM genre
GROUP BY category, name
HAVING COUNT(*) > 1;
-- 期望：无结果

-- ------------------------------------------------------------
-- 5. 工具冒烟测试（对应将来 8 个工具的 SQL）
-- ------------------------------------------------------------
-- search_anime
SELECT anime_id, title, score, `rank`
FROM anime
WHERE title = 'Sousou no Frieren';
-- 期望：9.29，rank 1

SELECT COUNT(*) AS gintama_matches FROM anime WHERE title LIKE '%Gintama%';
SELECT COUNT(*) AS fate_matches     FROM anime WHERE title LIKE '%Fate%';
SELECT COUNT(*) AS fma_matches      FROM anime WHERE title LIKE '%Fullmetal Alchemist%';
-- 期望：24 / 55 / 9（用于“需要澄清”的任务）

-- filter + rank（多步任务示例）
SELECT a.anime_id, a.title, a.score, a.start_year, a.episodes
FROM anime a
JOIN anime_genre ag ON a.anime_id = ag.anime_id
JOIN genre g ON ag.genre_id = g.genre_id
WHERE g.name = 'Sci-Fi'
  AND a.start_year >= 2019
  AND a.score >= 8.5
  AND a.type = 'TV'
  AND a.episodes <= 24
ORDER BY a.score DESC, a.members DESC, a.anime_id ASC
LIMIT 5;
-- 期望 3 条（不是 5 条）：
-- 48569 86 Part 2 8.72
-- 41084 Made in Abyss: Retsujitsu no Ougonkyou 8.60
-- 61322 Dr. Stone: Science Future Part 2 8.52
-- 任务集里应写成“取前 5（不足则全部）”或放宽条件

-- 2026 年不可行任务
SELECT COUNT(*) AS y2026 FROM anime WHERE start_year = 2026;
-- 期望 0

-- rank 保留字查询
SELECT anime_id, title, `rank`
FROM anime
ORDER BY `rank`
LIMIT 3;

-- ------------------------------------------------------------
-- 6. 字符集与列对齐检查
-- ------------------------------------------------------------
SELECT title, HEX(title) AS title_hex
FROM anime
WHERE title LIKE 'Gintama%'
ORDER BY title
LIMIT 5;
-- Gintama° 的 HEX 应以 C2B0 结尾；如果是 3F（?）说明字符已丢失

SELECT title
FROM anime
WHERE title LIKE '%Madoka%'
LIMIT 5;
-- 应能看到 ☆，不应出现 ?? 或 Ã¢Ëœâ€

SELECT anime_id, title, score, `rank`, popularity, members,
       start_year, end_year, type, episodes,
       LEFT(synopsis, 60) AS synopsis_head,
       image_url
FROM anime
ORDER BY anime_id
LIMIT 5;
-- 检查列有没有错位：title 是标题、score 是数字、synopsis 是英文简介、image_url 是 URL

-- ------------------------------------------------------------
-- 7. 表结构（逐个确认主键、索引、外键）
-- ------------------------------------------------------------
SHOW CREATE TABLE anime;
SHOW CREATE TABLE genre;
SHOW CREATE TABLE anime_genre;
SHOW CREATE TABLE studio;
SHOW CREATE TABLE anime_studio;
