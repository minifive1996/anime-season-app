// tools/generate_database.mjs
import fs from "node:fs";
import path from "node:path";

const API = "https://graphql.anilist.co";
const SEASONS = ["WINTER", "SPRING", "SUMMER", "FALL"];

const START_YEAR = Number(process.env.START_YEAR || 2020);
const END_YEAR = Number(process.env.END_YEAR || new Date().getFullYear());

// 節流：每次 request 成功後至少等這麼久再打下一次
const REQ_DELAY_MS = Number(process.env.REQ_DELAY_MS || 1200);

// 每個 season/year 完成後再多等一下，避免連續尖峰
const SEASON_DELAY_MS = Number(process.env.SEASON_DELAY_MS || 1500);

// 重試設定
const MAX_RETRIES = Number(process.env.MAX_RETRIES || 8);
const BACKOFF_BASE_MS = Number(process.env.BACKOFF_BASE_MS || 1500);
const BACKOFF_MAX_MS = Number(process.env.BACKOFF_MAX_MS || 60000);

const OUT_PATH = path.resolve("docs/api/v1/database/database.json");

const QUERY = `
query ($page:Int, $perPage:Int, $season:MediaSeason, $seasonYear:Int) {
  Page(page:$page, perPage:$perPage) {
    pageInfo { hasNextPage }
    media(type:ANIME, season:$season, seasonYear:$seasonYear, sort:POPULARITY_DESC) {
      id
      title { native romaji english }
      format
      status
      siteUrl
      genres
      startDate { year month day }
      coverImage { extraLarge large }
      bannerImage
    }
  }
}
`;

function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms));
}

function toDateString(d) {
  if (!d?.year || !d?.month || !d?.day) return null;
  const mm = String(d.month).padStart(2, "0");
  const dd = String(d.day).padStart(2, "0");
  return `${d.year}-${mm}-${dd}`;
}

function mapMedia(m) {
  return {
    id: String(m.id),
    title: {
      native: (m.title?.native || "").trim(),
      romaji: (m.title?.romaji || "").trim(),
      english: (m.title?.english || "").trim(),
    },
    image: {
      coverLarge: (m.coverImage?.extraLarge || m.coverImage?.large || "").trim(),
      banner: (m.bannerImage || "").trim(),
    },
    meta: {
      format: m.format || null,
      status: m.status || null, // 選配：有就放，沒有就 null
      siteUrl: (m.siteUrl || "").trim() || null,
      genres: Array.isArray(m.genres) ? m.genres.filter(Boolean) : [],
    },
    airing: {
      startDate: toDateString(m.startDate),
    },
  };
}

function isRateLimitError(resStatus, json) {
  if (resStatus === 429) return true;
  const msg = json?.errors?.[0]?.message || "";
  return typeof msg === "string" && msg.toLowerCase().includes("too many requests");
}

async function gql(query, variables) {
  let attempt = 0;

  while (true) {
    attempt += 1;

    let res;
    let json;
    try {
      res = await fetch(API, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Accept": "application/json",
          // 建議加 UA，較容易被服務端識別為正常工具流量
          "User-Agent": "anime-season-app-database-generator/1.0",
        },
        body: JSON.stringify({ query, variables }),
      });

      // 有些時候即使 429，也會回 JSON
      const text = await res.text();
      json = text ? JSON.parse(text) : null;

      if (res.ok && !json?.errors?.length) {
        // 成功後節流
        await sleep(REQ_DELAY_MS);
        return json.data;
      }

      // 429 / Too Many Requests
      if (isRateLimitError(res.status, json)) {
        const retryAfter = res.headers.get("retry-after");
        const waitMs = retryAfter
          ? Math.min(BACKOFF_MAX_MS, Number(retryAfter) * 1000)
          : Math.min(BACKOFF_MAX_MS, BACKOFF_BASE_MS * Math.pow(2, attempt - 1));

        if (attempt > MAX_RETRIES) {
          throw new Error(`AniList 429 Too Many Requests（已重試 ${MAX_RETRIES} 次仍失敗）`);
        }

        console.log(`Rate limited (429). wait ${waitMs}ms then retry (attempt ${attempt}/${MAX_RETRIES})...`);
        await sleep(waitMs);
        continue;
      }

      // 其他 API 錯誤（GraphQL errors 或 HTTP 非 2xx）
      const errMsg =
        json?.errors?.[0]?.message ||
        `AniList HTTP ${res.status}`;

      if (attempt > MAX_RETRIES) {
        throw new Error(`${errMsg}（已重試 ${MAX_RETRIES} 次仍失敗）`);
      }

      const waitMs = Math.min(BACKOFF_MAX_MS, BACKOFF_BASE_MS * Math.pow(2, attempt - 1));
      console.log(`AniList error: ${errMsg}. wait ${waitMs}ms then retry (attempt ${attempt}/${MAX_RETRIES})...`);
      await sleep(waitMs);
      continue;
    } catch (e) {
      // JSON parse / network error
      if (attempt > MAX_RETRIES) throw e;
      const waitMs = Math.min(BACKOFF_MAX_MS, BACKOFF_BASE_MS * Math.pow(2, attempt - 1));
      console.log(`Network/parse error: ${e?.message || e}. wait ${waitMs}ms then retry (attempt ${attempt}/${MAX_RETRIES})...`);
      await sleep(waitMs);
      continue;
    }
  }
}

async function fetchSeason(year, season) {
  const perPage = 50;
  let page = 1;
  let out = [];

  while (true) {
    const data = await gql(QUERY, { page, perPage, season, seasonYear: year });
    const pageInfo = data.Page.pageInfo;
    const media = data.Page.media || [];
    out.push(...media.map(mapMedia));
    if (!pageInfo?.hasNextPage) break;
    page += 1;
  }

  return out;
}

(async function main() {
  console.log(`Generate database from ${START_YEAR} to ${END_YEAR}...`);
  console.log(`REQ_DELAY_MS=${REQ_DELAY_MS}, SEASON_DELAY_MS=${SEASON_DELAY_MS}, MAX_RETRIES=${MAX_RETRIES}`);

  const map = new Map(); // id -> item（去重）

  for (let y = START_YEAR; y <= END_YEAR; y++) {
    for (const s of SEASONS) {
      console.log(`Fetching ${y} ${s}...`);
      const items = await fetchSeason(y, s);
      for (const it of items) map.set(it.id, it);

      // season 之間再多停一下
      await sleep(SEASON_DELAY_MS);
    }
  }

  const list = Array.from(map.values());

  const root = {
    source: {
      provider: "AniList",
      generatedAt: new Date().toISOString(),
      range: { startYear: START_YEAR, endYear: END_YEAR },
      note: "Database list (no description) for app browsing",
    },
    items: list,
  };

  fs.mkdirSync(path.dirname(OUT_PATH), { recursive: true });
  fs.writeFileSync(OUT_PATH, JSON.stringify(root, null, 2), "utf-8");

  console.log(`Done. items=${list.length}`);
  console.log(`Wrote: ${OUT_PATH}`);
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
