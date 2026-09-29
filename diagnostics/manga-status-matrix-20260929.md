# Manga megathread verification matrix — 2026-09-29

This file is the live end-to-end source matrix for manga readers and related tools.

Status rules:
- READER GREEN = search/series/chapter path reached real chapter-image payload bytes and image magic validated.
- MOVED + GREEN = old listed domain migrated, but the current replacement path passed.
- PARTIAL = current reader/catalog works but image-byte proof is not yet complete.
- PROTECTED = automated CI is stopped by anti-bot/403/503; this is not the same as dead.
- TOOL = software/client project, not a first-party manga content source.

## Reader sources already proven

| Source | Status | Evidence |
|---|---|---|
| MangaDex | READER GREEN | API search 200; populated feed; at-home 200; 34-page chapter; fetched a real 1,806,413-byte PNG with valid PNG magic. |
| MangaRead | READER GREEN | Chapter reached with 50 page-like DOM images / 142 image responses; real 348,007-byte JPEG validated. |
| MangaKatana | READER GREEN | Series/chapter 200; 60 chapter candidates; 11 page-like images / 218 network images; real 102,379-byte PNG validated. |
| MangaPill | READER GREEN | Series/chapter 200; 102 page-like images / 112 network images; real 355,335-byte JPEG validated. |
| MangaBuddy -> COMIZY | MOVED + READER GREEN | mangabuddy.com redirects to comizy.io; search -> series -> 52 chapter links -> chapter -> 15 page-like images; real 174,172-byte WebP validated. |

## Current but still being reconciled

| Source | Current result | Note |
|---|---|---|
| WeebCentral | PARTIAL | Exact One Piece series and Chapter 1194 are reachable; first cleanup reloaded the chapter and triggered protection. No-reload proof is running. |
| Toonily | PARTIAL | Exact TBATE series -> Chapter 250 navigation succeeded; reload caused the false block. No-reload proof is running. |
| MangaFox / FanFox | PARTIAL | One Piece series returns 200; generic harness selected javascript:void(0) instead of a real chapter. Exact chapter no-reload proof is running. |
| MangaFire | PARTIAL | Current reader/title pages return 200; reload caused false block. Exact reader no-reload proof is running. |
| MangaHub | PARTIAL | Current home/One Piece series return 200; chapter selection needs source-specific path. Corrected proof is running. |
| Comix | PROTECTED / PARTIAL | Public index exposes current chapters, but comix.to returned 503 to CI. comix.ws alternate is being tested. |
| Kagane | PROTECTED | Current kagane.to series is indexed but CI receives 403 on direct page. |
| MangaKakalot | PROTECTED | Current One Piece/chapter pages are indexed and show many images publicly, but CI receives 403 on exact chapter. |
| 18Kami | PROTECTED | Listed domain redirects to the current reader network and CI receives 403. |

## Reader / downloader software

| Project | Status | Evidence |
|---|---|---|
| Nyora | CURRENT TOOL | Active Android reader/source aggregator; repo not archived; pushed 2026-09-23; latest release v2.7.3 published 2026-07-31 with APK assets. |
| Comics Downloader | CURRENT TOOL | Repo not archived; pushed 2026-07-04. Latest packaged release is v0.33.9 from 2024. |
| Comic-DL | AGING TOOL | Repo not archived; last code push 2025-07-31; latest packaged release 2024-02-10. Provider adapters should be revalidated individually. |
| HakuNeko | LEGACY RELEASE / LIVE REPO | Repo is not archived and was pushed in March 2026, but latest official release is v6.1.7 from January 2020. |
| Manga Bot | LEGACY | Own project site lists latest version 2.5 from January 2018 and old provider hostnames such as mangafox.me. Not a good current integration baseline. |

## Strict green candidates so far

- MangaDex
- MangaRead
- MangaKatana
- MangaPill
- COMIZY (MangaBuddy migration)

The no-reload direct reader workflow is the final reconciliation pass for WeebCentral, Toonily, MangaFox, MangaFire, MangaHub and Comix alternate. Do not upgrade a source to green until the chapter image payload itself is proven.
