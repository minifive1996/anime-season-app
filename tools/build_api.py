#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import csv
import json
import os
import sys
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Dict, List, Optional, Tuple
from urllib.request import Request, urlopen


def _now_iso_date() -> str:
    return datetime.now(timezone.utc).astimezone().strftime("%Y-%m-%d")


def _now_iso_datetime() -> str:
    return datetime.now(timezone.utc).astimezone().isoformat(timespec="seconds")


def _truthy(v: str) -> bool:
    s = (v or "").strip().lower()
    return s in ("true", "1", "yes", "y", "t")


def _split_pipe(v: str) -> List[str]:
    s = (v or "").strip()
    if not s:
        return []
    parts = [p.strip() for p in s.split("|")]
    return [p for p in parts if p]


def _safe_int(v: str) -> Optional[int]:
    s = (v or "").strip()
    if not s:
        return None
    try:
        return int(s)
    except Exception:
        return None


def _read_csv_from_url(csv_url: str) -> List[Dict[str, str]]:
    req = Request(
        csv_url,
        headers={
            "User-Agent": "anime-season-app-bot/1.0",
            "Accept": "text/csv,*/*",
        },
    )
    with urlopen(req, timeout=30) as resp:
        raw = resp.read().decode("utf-8", errors="replace")

    reader = csv.DictReader(raw.splitlines())
    rows = []
    for r in reader:
        # normalize keys: strip whitespace
        rows.append({(k or "").strip(): (v or "").strip() for k, v in r.items()})
    return rows


def _parse_links(v: str) -> List[Dict[str, str]]:
    """
    links 欄位格式： Site=https://... | X=https://...
    """
    out: List[Dict[str, str]] = []
    for part in _split_pipe(v):
        if "=" not in part:
            continue
        site, url = part.split("=", 1)
        site = site.strip()
        url = url.strip()
        if site and url:
            out.append({"site": site, "url": url})
    return out


@dataclass
class AnimeItem:
    id: str
    title_zh: str
    title_native: str
    title_en: str
    desc_zh: str
    cover: str
    banner: str
    site_url: str
    year: Optional[int]
    season: str
    format: str
    status: str
    genres: List[str]
    studios: List[str]
    is_current: bool
    start_date: str
    links: List[Dict[str, str]]


def _to_item(row: Dict[str, str]) -> Optional[AnimeItem]:
    anime_id = (row.get("id") or "").strip()
    if not anime_id:
        return None

    title_zh = (row.get("title_zhHant") or "").strip()
    title_native = (row.get("title_native") or "").strip()
    title_en = (row.get("title_english") or "").strip()

    desc_zh = (row.get("description_zhHant") or "").strip()

    cover = (row.get("coverLarge") or "").strip()
    banner = (row.get("banner") or "").strip()
    site_url = (row.get("siteUrl") or "").strip()

    year = _safe_int(row.get("year") or "")
    season = (row.get("season") or "").strip().upper()
    fmt = (row.get("format") or "").strip().upper()
    status = (row.get("status") or "").strip().upper()

    genres = _split_pipe(row.get("genres") or "")
    studios = _split_pipe(row.get("studios") or "")

    is_current = _truthy(row.get("isCurrentSeason") or "")

    start_date = (row.get("startDate") or "").strip()  # YYYY-MM-DD (optional)
    links = _parse_links(row.get("links") or "")

    return AnimeItem(
        id=anime_id,
        title_zh=title_zh,
        title_native=title_native,
        title_en=title_en,
        desc_zh=desc_zh,
        cover=cover,
        banner=banner,
        site_url=site_url,
        year=year,
        season=season,
        format=fmt,
        status=status,
        genres=genres,
        studios=studios,
        is_current=is_current,
        start_date=start_date,
        links=links,
    )


def _make_api_item(it: AnimeItem) -> Dict:
    """
    對齊你現有 App JSON 結構（Season/Database/Detail 都吃得下）
    """
    title_obj = {
        "zhHant": it.title_zh,
        "native": it.title_native,
        "english": it.title_en,
    }
    # 清理空字串欄位，讓 JSON 乾淨
    title_obj = {k: v for k, v in title_obj.items() if (v or "").strip()}

    image_obj = {}
    if it.cover:
        image_obj["coverLarge"] = it.cover
    if it.banner:
        image_obj["banner"] = it.banner

    meta_obj = {}
    if it.format:
        meta_obj["format"] = it.format
    if it.status:
        meta_obj["status"] = it.status
    if it.site_url:
        meta_obj["siteUrl"] = it.site_url
    if it.genres:
        meta_obj["genres"] = it.genres
    if it.studios:
        meta_obj["studios"] = it.studios

    airing_obj = {}
    if it.start_date:
        airing_obj["startDate"] = it.start_date

    obj = {
        "id": it.id,
        "title": title_obj,
        "image": image_obj,
        "meta": meta_obj,
    }

    # description：你現有 DetailScreen 讀 `description`
    # 我們直接輸出繁中到 description，確保 App 以繁中顯示（策略1）
    if it.desc_zh:
        obj["description"] = it.desc_zh

    if airing_obj:
        obj["airing"] = airing_obj

    if it.links:
        obj["links"] = it.links

    return obj


def _write_json(path: Path, data: Dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main() -> int:
    csv_url = os.getenv("SHEET_CSV_URL", "").strip()
    if not csv_url:
        print("ERROR: SHEET_CSV_URL is empty. Set env var SHEET_CSV_URL to your published CSV URL.", file=sys.stderr)
        return 2

    out_root = Path("docs") / "api" / "v1"
    updated_at = _now_iso_datetime()
    updated_date = _now_iso_date()

    rows = _read_csv_from_url(csv_url)
    items: List[AnimeItem] = []
    for r in rows:
        it = _to_item(r)
        if it:
            items.append(it)

    # 基本去重檢查
    seen = set()
    dup = []
    for it in items:
        if it.id in seen:
            dup.append(it.id)
        seen.add(it.id)
    if dup:
        print(f"ERROR: duplicate ids found: {dup}", file=sys.stderr)
        return 3

    # 排序：讓輸出穩定（diff 乾淨）
    items_sorted = sorted(
        items,
        key=lambda x: (
            0 if x.is_current else 1,
            -(x.year or 0),
            x.season,
            x.title_zh or x.title_native or x.title_en or x.id,
        ),
    )

    api_items_all = [_make_api_item(it) for it in items_sorted]
    api_items_season = [_make_api_item(it) for it in items_sorted if it.is_current]

    # database/database.json
    database_json = {
        "source": {"type": "google_sheets", "updatedAt": updated_at, "count": len(api_items_all)},
        "items": api_items_all,
    }
    _write_json(out_root / "database" / "database.json", database_json)

    # season/season.json
    season_title = f"{updated_date} 本季"
    season_json = {
        "source": {"type": "google_sheets", "updatedAt": updated_at, "title": season_title, "count": len(api_items_season)},
        "items": api_items_season,
    }
    _write_json(out_root / "season" / "season.json", season_json)

    # anime/{id}.json（推薦：Detail 之後改讀這個會更穩）
    for it in items_sorted:
        detail_json = {
            "source": {"type": "google_sheets", "updatedAt": updated_at},
            "item": _make_api_item(it),
        }
        _write_json(out_root / "anime" / f"{it.id}.json", detail_json)

    # ping.json
    ping_json = {"ping": "OK", "updatedAt": updated_at, "count": len(items_sorted)}
    _write_json(out_root / "ping.json", ping_json)

    print(f"OK: wrote database/season/anime/* + ping. updatedAt={updated_at}, count={len(items_sorted)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
