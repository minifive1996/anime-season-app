// tools/anilist_sync.mjs
// Fetch AniList season data via GraphQL and write to docs/api/v1/season/season.json

import fs from "node:fs";
import path from "node:path";

const ENDPOINT = "https://graphql.anilist.co";

// Default: 2026 WINTER (符合你目前進度：2025-12 已在準備 2026-01 冬季)
const SEASON = (process.env.SEASON || "WINTER").toUpperCase(); // WINTER | SPRING | SUMMER | FALL
const YEAR = Number(process.env.YEAR || "2026");
const PER_PAGE = Number(process.env.PER_PAGE || "50");

const OUT_DIR = path.resolve("docs/api/v1/season");
const OUT_FILE = path.join(OUT_DIR, "season.json");

const QUERY = `
query ($page:Int, $perPage:Int, $season: MediaSeason, $seasonYear: Int) {
  Page(page: $page, perPage: $perPage) {
    pageInfo {
      currentPage
      hasNextPage
      total
      perPage
    }
    media(
      type: ANIME
      season: $season
      seasonYear: $seasonYear
      sort: POPULARITY_DESC
      isAdult: false
    ) {
      id
      title { romaji english native }
      format
      status
      episodes
      duration
      startDate { year month day }
      endDate { year month day }
      coverImage { large medium }
      bannerImage
      genres
      description(asHtml: false)
      siteUrl
      studios(isMain: true) { nodes { name } }
      nextAiringEpisode { airingAt timeUntilAiring episode }
      externalLinks { site url type language }
    }
  }
}
`;

function stripWeirdSpaces(s) {
  return (s || "").replace(/\s+/g, " ").trim();
}

async function postGraphQL(query, variables) {
  const res = await fetch(ENDPOINT, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      // 建議帶 UA，避免被當成不明 client
      "User-Agent": "anime-season-app-static-sync/1.0 (GitHub Actions)"
    },
    body: JSON.stringify({ query, variables }),
  });

  if (!res.ok) {
    const text = await res.text().catch(() => "");
    throw new Error(`AniList HTTP ${res.status}: ${text.slice(0, 200)}`);
  }
  return res.json();
}

async function fetchAllSeasonMedia() {
  let page = 1;
  const all = [];

  while (true) {
    const variables = { page, perPage: PER_PAGE, season: SEASON, seasonYear: YEAR };
    const json = await postGraphQL(QUERY, variables);

    if (json.errors?.length) {
      throw new Error(`AniList GraphQL error: ${JSON.stringify(json.errors).slice(0, 200)}`);
    }

    const pageInfo = json?.data?.Page?.pageInfo;
    const media = json?.data?.Page?.media || [];

    for (const m of media) all.push(m);

    if (!pageInfo?.hasNextPage) break;
    page += 1;
  }

  return all;
}

function toIsoDate(d) {
  if (!d?.year || !d?.month || !d?.day) return null;
  const mm = String(d.month).padStart(2, "0");
  const dd = String(d.day).padStart(2, "0");
  return `${d.year}-${mm}-${dd}`;
}

function mapToApiContract(mediaList) {
  return mediaList.map((m) => ({
    id: String(m.id),
    title: {
      romaji: m.title?.romaji || "",
      english: m.title?.english || "",
      native: m.title?.native || "",
    },
    image: {
      coverLarge: m.coverImage?.large || "",
      coverMedium: m.coverImage?.medium || "",
      banner: m.bannerImage || "",
    },
    meta: {
      format: m.format || null,
      status: m.status || null, // 你的規格：可選配，有就顯示，沒有就不顯示
      genres: m.genres || [],
      episodes: m.episodes ?? null,
      durationMin: m.duration ?? null,
      studios: (m.studios?.nodes || []).map((x) => x.name).filter(Boolean),
      siteUrl: m.siteUrl || "",
    },
    airing: {
      startDate: toIsoDate(m.startDate),
      endDate: toIsoDate(m.endDate),
      nextAiringEpisode: m.nextAiringEpisode
        ? {
            airingAtEpochSec: m.nextAiringEpisode.airingAt,
            episode: m.nextAiringEpisode.episode,
            timeUntilAiringSec: m.nextAiringEpisode.timeUntilAiring,
          }
        : null,
    },
    description: stripWeirdSpaces(m.description || ""),
    links: (m.externalLinks || [])
      .map((l) => ({
        site: l.site || "",
        url: l.url || "",
        type: l.type || null,
        language: l.language || null,
      }))
      .filter((l) => l.site && l.url),
  }));
}

async function main() {
  const list = await fetchAllSeasonMedia();

  const payload = {
    generatedAt: new Date().toISOString(),
    source: {
      name: "AniList GraphQL",
      endpoint: ENDPOINT,
      season: SEASON,
      year: YEAR,
      sort: "POPULARITY_DESC",
    },
    items: mapToApiContract(list),
  };

  fs.mkdirSync(OUT_DIR, { recursive: true });
  fs.writeFileSync(OUT_FILE, JSON.stringify(payload, null, 2), "utf8");

  console.log(`Wrote ${payload.items.length} items -> ${OUT_FILE}`);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
