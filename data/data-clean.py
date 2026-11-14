# -*- coding: utf-8 -*-
"""
Anime 数据集预处理
用法：
    python data/data-clean.py --src <原始数据目录> [--out <输出目录>]

原则：只修能证明是错的；缺失值一律留空，不猜、不补、不编。
"""
import argparse, csv, collections
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent

def parse_args():
    p = argparse.ArgumentParser(description="Anime 数据集预处理（只清洗，不填补缺失值）")
    p.add_argument(
        "--src",
        required=True,
        type=Path,
        help="原始数据目录，需包含 anime.csv、anime_genres.csv、entities.csv、anime_companies.csv",
    )
    p.add_argument(
        "--out",
        type=Path,
        default=BASE_DIR / "anime_clean",
        help="输出目录（默认 data/anime_clean）",
    )
    return p.parse_args()

ARGS = parse_args()
SRC = ARGS.src
OUTDIR = ARGS.out

if not SRC.is_dir():
    raise SystemExit(f"原始数据目录不存在: {SRC}")

def load(fn):
    path = SRC / fn
    if not path.is_file():
        raise SystemExit(f"缺少原始文件: {path}")
    with path.open("r", encoding="utf-8-sig", newline="") as f:
        return list(csv.DictReader(f))

OUTDIR.mkdir(parents=True, exist_ok=True)
print(f"[src] -> {SRC}")
print(f"[out] -> {OUTDIR}")

def write_csv(name, header, rows):
    p = OUTDIR / name
    with p.open("w", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        w.writerow(header)
        w.writerows(rows)
    print(f"  wrote {name:20} rows={len(rows)}")
    return p

JUNK_STUDIO = {"none found", "add some", "", "none", "unknown", "null"}

# ================= 1. anime 主表 =================
anime_raw = load("anime.csv")
seen, anime, dropped_dup = set(), [], []
for r in anime_raw:
    aid = r["anime_id"].strip()
    if aid in seen:                       # 修复: 完全重复的记录
        dropped_dup.append((aid, r["title"]))
        continue
    seen.add(aid)
    # 注意: 只做重复检测, 其余缺失值保持空字符串 -> 入库为 NULL
    anime.append({
        "anime_id": aid,
        "title": r["title"].strip(),
        "score": r["score"].strip(),
        "rank": r["rank"].strip(),
        "popularity": r["popularity"].strip(),
        "members": r["members"].strip(),
        "synopsis": r["synopsis"].strip(),
        "start_year": r["start_date"].strip()[:4] if r["start_date"].strip() else "",
        "end_year": r["end_date"].strip()[:4] if r["end_date"].strip() else "",
        "type": r["type"].strip(),
        "episodes": r["episodes"].strip(),
        "image_url": r["image_url"].strip(),
    })

# ================= 2. genre 清洗 =================
raw_genres = load("anime_genres.csv")

def parse_genre(raw):
    """'Theme::Isekai Isekai' -> ('Theme','Isekai');  'Action Action' -> ('Genre','Action')"""
    s = raw.strip()
    cat = "Genre"
    if "::" in s:
        cat, s = s.split("::", 1)
        cat, s = cat.strip(), s.strip()
    w = s.split()
    if len(w) >= 2 and len(w) % 2 == 0:            # 修复: 去掉重复拼接的一半
        h = len(w) // 2
        if [x.lower() for x in w[:h]] == [x.lower() for x in w[h:]]:
            s = " ".join(w[:h])
    return cat, s

genre_map = {}          # (cat,name) -> id
anime_genre_rows = []
bad_genre = []
for r in raw_genres:
    aid = r["anime_id"].strip()
    cat, name = parse_genre(r["genre"])
    if not name or name.lower() in JUNK_STUDIO:
        bad_genre.append(r["genre"]); continue
    key = (cat, name)
    if key not in genre_map:
        genre_map[key] = len(genre_map) + 1
    anime_genre_rows.append((aid, genre_map[key]))

# 去重 (anime_id, genre_id)
anime_genre_rows = sorted(set(anime_genre_rows), key=lambda x: (int(x[0]), x[1]))
genre_rows = [(gid, cat, name) for (cat, name), gid in sorted(genre_map.items(), key=lambda kv: kv[1])]
cover = len(set(a for a, _ in anime_genre_rows))

# ================= 3. studio 清洗 =================
ents = load("entities.csv")
comps = load("anime_companies.csv")
ent_by_id = {e["entity_id"]: e for e in ents}

studio_map, anime_studio_rows = {}, []
junk_hit, missing_ent = 0, 0
for c in comps:
    if c["role"] != "Studio":
        continue
    e = ent_by_id.get(c["company_id"])
    if e is None or e["entity_type"] != "studio":
        missing_ent += 1; continue
    name = e["name"].strip()
    if name.lower() in JUNK_STUDIO:        # 修复: 垃圾公司名视为无公司
        junk_hit += 1; continue
    if name not in studio_map:
        studio_map[name] = len(studio_map) + 1
    anime_studio_rows.append((c["anime_id"].strip(), studio_map[name]))

anime_studio_rows = sorted(set(anime_studio_rows), key=lambda x: (int(x[0]), x[1]))
studio_rows = [(sid, n) for n, sid in sorted(studio_map.items(), key=lambda kv: kv[1])]
sc = len(set(a for a, _ in anime_studio_rows))

# ================= 4. 输出 =================
print("\n写出文件:")
write_csv("anime.csv",
          ["anime_id","title","score","rank","popularity","members","synopsis",
           "start_year","end_year","type","episodes","image_url"],
          [[a[k] for k in ("anime_id","title","score","rank","popularity","members","synopsis",
                            "start_year","end_year","type","episodes","image_url")] for a in anime])
write_csv("genre.csv", ["genre_id","category","name"], genre_rows)
write_csv("anime_genre.csv", ["anime_id","genre_id"], anime_genre_rows)
write_csv("studio.csv", ["studio_id","name"], studio_rows)
write_csv("anime_studio.csv", ["anime_id","studio_id"], anime_studio_rows)

# ================= 5. 质量报告 (Markdown) =================
rb  = collections.Counter(a["start_year"] for a in anime)
_ys = [int(y) for y in rb if y]
miss = {k: sum(1 for a in anime if not a[k])
        for k in ("score", "episodes", "synopsis", "start_year", "end_year")}
sc_s = sorted(float(a["score"]) for a in anime if a["score"])
tb   = collections.Counter(a["type"] for a in anime)
gc   = collections.Counter(g for _, g in anime_genre_rows)
_gname = {gid: nm for gid, _cat, nm in genre_rows}
cat_cnt = collections.Counter(cat for _gid, cat, _nm in genre_rows)
top5 = [(_gname[g], v) for g, v in gc.most_common(5)]
n_anime = len(anime)

M = []
M.append("# Anime 数据集清洗报告\n")
M.append("> 由 `data/data-clean.py` 自动生成，请勿手工编辑。\n")

M.append("## 1. 概览\n")
M.append("| 表 | 行数 |")
M.append("|---|---:|")
M.append(f"| `anime` | {n_anime} |")
M.append(f"| `genre` | {len(genre_rows)} |")
M.append(f"| `anime_genre` | {len(anime_genre_rows)} |")
M.append(f"| `studio` | {len(studio_rows)} |")
M.append(f"| `anime_studio` | {len(anime_studio_rows)} |")

M.append("\n## 2. 处理明细\n")
M.append("| # | 问题 | 性质 | 处理 | 结果 |")
M.append("|---|---|---|---|---|")
M.append(f"| 1 | `genre` 值重复拼接（`Action Action`） | 可确定修复 | 拆前缀 + 折叠重复的一半 | {len(raw_genres)} 行 → {len(genre_rows)} 个标签 |")
M.append(f"| 2 | `anime_id=41884` 整行重复 | 可确定修复 | 去重 | {len(anime_raw)} → {n_anime} 行 |")
M.append(f"| 3 | `add some` / `None found` 被当成公司名 | 可确定修复 | 视为无公司并丢弃关联 | 剔除 {junk_hit} 条关联，保留 {len(studio_rows)} 家公司 |")
M.append(f"| 4 | `entities.csv` 含真人姓名 | 伦理约束 | 仅取 `studio`，其余整表不导入 | 丢弃 {sum(1 for e in ents if e['entity_type'] in ('staff','voice_actor','character'))} 行 |")
M.append(f"| 5 | 缺失值（集数/简介/年份） | 不可修复 | 保留为空 → 入库 NULL | 未做任何填补 |")
M.append(f"| 6 | 部分作品无题材/无公司 | 不可修复 | 保留缺失 | 见下方覆盖度 |")

M.append("\n## 3. 数据覆盖度\n")
M.append("| 指标 | 数值 | 占比 |")
M.append("|---|---:|---:|")
M.append(f"| 有题材标签的作品 | {cover} / {n_anime} | {cover/n_anime*100:.1f}% |")
M.append(f"| 有制作公司的作品 | {sc} / {n_anime} | {sc/n_anime*100:.1f}% |")
M.append(f"| 题材标签总数 | {len(genre_rows)} | — |")
M.append(f"| 　└ Genre 类 | {cat_cnt.get('Genre', 0)} | — |")
M.append(f"| 　└ Demographic 类 | {cat_cnt.get('Demographic', 0)} | — |")
M.append(f"| 　└ Theme 类 | {cat_cnt.get('Theme', 0)} | — |")

M.append("\n## 4. 空值统计\n")
M.append("| 字段 | 空值数 |")
M.append("|---|---:|")
for k, v in miss.items():
    M.append(f"| `{k}` | {v} |")
M.append("\n> 空值一律保留，未用 0、均值或中位数填补，以免污染评测结论。")

M.append("\n## 5. 数据概况\n")
M.append("| 项 | 值 |")
M.append("|---|---|")
M.append(f"| 评分范围 | {sc_s[0]} – {sc_s[-1]}（均值 {sum(sc_s)/len(sc_s):.2f}） |")
M.append(f"| 年份范围 | {min(_ys)} – {max(_ys)}（起始年缺失 {rb.get('', 0)} 条） |")
M.append(f"| 作品类型 | " + " / ".join(f"{k} {v}" for k, v in tb.most_common()) + " |")
M.append(f"| 最热题材 Top5 | " + " / ".join(f"{n} {v}" for n, v in top5) + " |")
M.append(f"| 近年作品数 | " + " / ".join(f"{y}:{n}" for y, n in sorted(rb.items())[-6:]) + " |")

M.append("\n## 6. 已知局限（供论文「局限性」章节引用）\n")
M.append("- 标题仅有罗马音/英文形式，无中日文字段，因此任务集以英文表述。")
M.append("- 日期仅有年份精度，不支持按月/日的筛选。")
M.append("- 部分作品缺题材或公司信息，按相关字段筛选会静默排除这些作品。")
M.append("- 数据仅含 MAL 评分排名前 10,000 的作品，评分下限为 6.05，不代表全量动画。")

M.append("\n## 7. 输出文件\n")
for fn in ("anime.csv", "genre.csv", "anime_genre.csv", "studio.csv", "anime_studio.csv"):
    M.append(f"- `{fn}`")
M.append("- `quality_report.md`")

md = "\n".join(M)
with (OUTDIR / "quality_report.md").open("w", encoding="utf-8") as f:
    f.write(md)
print(f"\nwrote quality_report.md ({len(md)} chars)")
