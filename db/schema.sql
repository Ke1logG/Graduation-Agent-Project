-- ============================================================
--  Anime 元数据建表脚本 (MySQL 8.x)
--  数据源: Kaggle "Anime dataset (Top 10K Normalized)"  CC0
--  只包含清洗后的 5 张表；staff / voice_actor / character 全部排除
-- ============================================================

CREATE DATABASE IF NOT EXISTS anime_db
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE anime_db;

-- ------------------------------------------------------------
-- 1. 作品主表
--    score/rank/popularity/members/episodes 允许 NULL：
--    原始数据里缺失就是缺失，不做任何填补
-- ------------------------------------------------------------
DROP TABLE IF EXISTS anime_studio;
DROP TABLE IF EXISTS anime_genre;
DROP TABLE IF EXISTS studio;
DROP TABLE IF EXISTS genre;
DROP TABLE IF EXISTS anime;

CREATE TABLE anime (
  anime_id    INT           NOT NULL COMMENT 'MAL 作品ID',
  title       VARCHAR(255)  NOT NULL COMMENT '罗马音/英文标题（无中文）',
  score       DECIMAL(4,2)  NULL     COMMENT '评分 6.05–9.29',
  `rank`      INT           NULL     COMMENT '评分排名',
  popularity  INT           NULL     COMMENT '人气排名',
  members     INT           NULL     COMMENT '收藏人数',
  synopsis    TEXT          NULL     COMMENT '简介，最长约 3.7k 字符',
  start_year  SMALLINT      NULL     COMMENT '放送起始年（原始仅有年份精度）',
  end_year    SMALLINT      NULL     COMMENT '放送结束年',
  type        VARCHAR(16)   NULL     COMMENT 'TV/Movie/OVA/ONA/Special/TV Special',
  episodes    SMALLINT      NULL     COMMENT '集数，1–1787，缺失为 NULL',
  image_url   VARCHAR(255)  NULL,
  PRIMARY KEY (anime_id),
  KEY idx_title      (title),
  KEY idx_score      (score),
  KEY idx_start_year (start_year),
  KEY idx_type       (type),
  KEY idx_episodes   (episodes)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作品元数据';

-- ------------------------------------------------------------
-- 2. 题材标签（三层：Genre / Demographic / Theme）
-- ------------------------------------------------------------
CREATE TABLE genre (
  genre_id TINYINT UNSIGNED NOT NULL,
  category VARCHAR(16)  NOT NULL COMMENT 'Genre / Demographic / Theme',
  name     VARCHAR(64)  NOT NULL COMMENT '已修复重复拼接，如 Sci-Fi 而非 Sci-Fi Sci-Fi',
  PRIMARY KEY (genre_id),
  UNIQUE KEY uk_cat_name (category, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题材标签';

CREATE TABLE anime_genre (
  anime_id INT             NOT NULL,
  genre_id TINYINT UNSIGNED NOT NULL,
  PRIMARY KEY (anime_id, genre_id),
  KEY idx_genre (genre_id),
  CONSTRAINT fk_ag_anime FOREIGN KEY (anime_id) REFERENCES anime(anime_id) ON DELETE CASCADE,
  CONSTRAINT fk_ag_genre FOREIGN KEY (genre_id) REFERENCES genre(genre_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作品-题材关联（13.5% 作品无记录，属正常缺失）';

-- ------------------------------------------------------------
-- 3. 制作公司（只保留 Studio，Producer/Licensor 未纳入）
-- ------------------------------------------------------------
CREATE TABLE studio (
  studio_id SMALLINT UNSIGNED NOT NULL,
  name      VARCHAR(128) NOT NULL,
  PRIMARY KEY (studio_id),
  UNIQUE KEY uk_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='制作公司';

CREATE TABLE anime_studio (
  anime_id  INT               NOT NULL,
  studio_id SMALLINT UNSIGNED NOT NULL,
  PRIMARY KEY (anime_id, studio_id),
  KEY idx_studio (studio_id),
  CONSTRAINT fk_as_anime  FOREIGN KEY (anime_id)  REFERENCES anime(anime_id)   ON DELETE CASCADE,
  CONSTRAINT fk_as_studio FOREIGN KEY (studio_id) REFERENCES studio(studio_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作品-制作公司关联';

-- ============================================================
--  导入示例（Windows 下 MySQL 需允许 local_infile）
--  文件均为 UTF-8 无 BOM，缺失值留空即导入为 NULL
-- ============================================================
-- SHOW VARIABLES LIKE 'local_infile';
-- SET GLOBAL local_infile = 1;
--
-- LOAD DATA LOCAL INFILE 'D:/deepseek/anime_clean/anime.csv'
--   INTO TABLE anime
--   FIELDS TERMINATED BY ',' OPTIONALLY ENCLOSED BY '"'
--   LINES TERMINATED BY '\r\n'
--   IGNORE 1 LINES
--   (anime_id, title, score, @rank, @pop, @mem, @syn, @sy, @ey, @type, @eps, image_url)
--   SET `rank` = NULLIF(@rank,''), popularity = NULLIF(@pop,''),
--       members = NULLIF(@mem,''), synopsis = NULLIF(@syn,''),
--       start_year = NULLIF(@sy,''), end_year = NULLIF(@ey,''),
--       type = NULLIF(@type,''), episodes = NULLIF(@eps,'');
--
-- LOAD DATA LOCAL INFILE 'D:/deepseek/anime_clean/genre.csv'
--   INTO TABLE genre
--   FIELDS TERMINATED BY ',' OPTIONALLY ENCLOSED BY '"'
--   LINES TERMINATED BY '\r\n' IGNORE 1 LINES;
--
-- LOAD DATA LOCAL INFILE 'D:/deepseek/anime_clean/anime_genre.csv'
--   INTO TABLE anime_genre
--   FIELDS TERMINATED BY ',' OPTIONALLY ENCLOSED BY '"'
--   LINES TERMINATED BY '\r\n' IGNORE 1 LINES;
--
-- LOAD DATA LOCAL INFILE 'D:/deepseek/anime_clean/studio.csv'
--   INTO TABLE studio
--   FIELDS TERMINATED BY ',' OPTIONALLY ENCLOSED BY '"'
--   LINES TERMINATED BY '\r\n' IGNORE 1 LINES;
--
-- LOAD DATA LOCAL INFILE 'D:/deepseek/anime_clean/anime_studio.csv'
--   INTO TABLE anime_studio
--   FIELDS TERMINATED BY ',' OPTIONALLY ENCLOSED BY '"'
--   LINES TERMINATED BY '\r\n' IGNORE 1 LINES;
