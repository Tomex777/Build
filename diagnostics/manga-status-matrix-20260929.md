# Manga megathread verification matrix — 2026-09-29

This file is the live end-to-end source matrix for manga readers and related tools.

Status rules:
- READER GREEN = search/series/chapter path reached real chapter-image payload bytes and image magic validated.
- MOVED + GREEN = old listed domain migrated, but the current replacement path passed.
- PARTIAL = current reader/catalog works but image-byte proof is not yet complete.
- PROTECTED = automated CI is stopped by anti-bot/403/503; this is not the same as dead.
- TOOL = software/client project, not a first-party manga content source.

## Reader sources proven end-to-end

| Source | Status | Evidence |
|---|---|---|
| MangaDex | READER GREEN | API search 200; populated feed; at-home 200; 34-page chapter; fetched a real 1,806,413-byte PNG with valid PNG magic. |
| MangaRead | READER GREEN | Chapter reached with 50 page-like DOM images / 142 image responses; real 348,007-byte JPEG validated. |
| MangaKatana | READER GREEN | Series/chapter 200; 60 chapter candidates; 11 page-like images / 218 network images; real 102,379-byte PNG validated. |
| MangaPill | READER GREEN | Series/chapter 200; 102 page-like images / 112 network images; real 355,335-byte JPEG validated. |
| MangaBuddy -> COMIZY | MOVED + READER GREEN | mangabuddy.com redirects to comizy.io; search -> series -> 52 chapter links -> chapter -> 15 page-like images; real 174,172-byte WebP validated. |
| MangaFox / FanFox | READER GREEN | Exact One Piece chapter returned 200; 3 page-sized DOM images / 19 image responses; real 256,330-byte JPEG from zjcdn.mangafox.me validated. |
| WeebCentral | READER GREEN | Exact Chapter 1194 returned 200; 13 page-sized images / 234 image responses; real 256,272-byte image payload validated. |
| Toonily | READER GREEN | TBATE Chapter 250 returned 200; 15 page-sized images / 40 image responses; real 837,803-byte JPEG from data.tnlycdn.com validated. |

## Current / protected / partial

| Source | Current result | Evidence |
|---|---|---|
| MangaHub | PROTECTED / PARTIAL | Current One Piece series returns 200 and the runner reached Chapter 1194, but the chapter reader then challenged the CI session before page images loaded. |
| MangaFire | PROTECTED / PARTIAL | Current title/reader routes return 200, but the attempted reader path canonicalized back to the title page and the automated session was challenged before image proof. |
| Comix | PROTECTED / PARTIAL | comix.to returned 503 in CI. Alternate comix.ws returns 200 and search route loads, but browse/chapter path triggers protection before image proof. |
| Kagane | PROTECTED | Current kagane.to series is indexed, but CI receives 403 on the direct series route. |
| MangaKakalot | PROTECTED | Current One Piece chapter pages are publicly indexed, but exact chapter returns 403 to CI. |
| 18Kami | PROTECTED | Listed domain redirects to the current reader network and CI receives 403. |

## Reader / downloader software

| Project | Status | Evidence |
|---|---|---|
| Nyora | CURRENT TOOL | Active Android reader/source aggregator; repo not archived; pushed 2026-09-23; latest release v2.7.3 published 2026-07-31 with APK assets. |
| Comics Downloader | CURRENT TOOL | Repo not archived; pushed 2026-07-04. Latest packaged release is v0.33.9 from 2024. |
| Comic-DL | AGING TOOL | Repo not archived; last code push 2025-07-31; latest packaged release 2024-02-10. Provider adapters should be revalidated individually. |
| HakuNeko | LEGACY RELEASE / LIVE REPO | Repo is not archived and was pushed in March 2026, but latest official release is v6.1.7 from January 2020. |
| Manga Bot | LEGACY | Own project site lists latest version 2.5 from January 2018 and old provider hostnames such as mangafox.me. Not a good current integration baseline. |

## Highest-confidence manga extension candidates

- MangaDex
- MangaRead
- MangaKatana
- MangaPill
- COMIZY (MangaBuddy migration)
- MangaFox / FanFox
- WeebCentral
- Toonily

## Browser-assisted / secondary candidates

- MangaHub
- MangaFire
- Comix
- Kagane
- MangaKakalot

The manga phase is considered reconciled for the live megathread: every listed web reader has either a real chapter-image proof or a documented current protection/failure condition. Protected sources can be revisited with an in-app verification/browser flow instead of being treated as dead.
