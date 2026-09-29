# Anime megathread verification matrix — 2026-09-29

This file is a live evidence summary for the Extension APIs project.
"Green" means a current CI run proved the useful path rather than merely loading a homepage.
"Partial" means the site is current/reachable but the full player path was not proven in the latest strict run.
"Blocked" means browser/CDN protection prevented a reliable CI verdict; it does not automatically mean the site is dead.

## Green / proven

| Source | Current result | Evidence |
|---|---|---|
| Anidap | GREEN | Current migration E2E run proved catalog/source path on the current API stack. |
| Miruro | GREEN | Current resolver path proved in the migration E2E run. |
| ani.pm | GREEN | Exact API E2E run completed successfully on 2026-09-29. |
| AnimeX | GREEN | Strict current-provider playback proof passed. |
| AniKuro | GREEN | Strict current-player proof passed on the current domain. |
| AnimeParadise | GREEN | Strict player proof passed through its current same-origin player path. |
| 123anime | GREEN | Current player/provider chain passed strict proof. |
| Yenime | GREEN | Current player/provider chain passed strict proof. |
| AnimePahe download path | GREEN | Current episode-to-download path was proven end-to-end. Streaming is tracked separately. |

## Partial / current but not yet strict-green

| Source | Current result | Evidence |
|---|---|---|
| Animotvslash | PARTIAL | Current episode page works and browser observed live player activity, but the latest strict standalone validation did not produce a final pass. |
| AnimeNana | PARTIAL | Current catalog/detail works; CI reached the title but hit protection on the episode path. |
| AnimeNoSub | PARTIAL / PROTECTED | Current site loads, but the latest strict run could not complete search-to-player. |
| AnimeXin | PARTIAL / PROTECTED | Current site migrated to animexin.dev; latest strict run did not complete player proof. |
| KickAssAnime | PARTIAL | Current site redirects to kaa.lt and loads, but strict current-player verification did not pass. |
| AniZone | PARTIAL / PROTECTED | Home is current; detail/player path was blocked in the CI browser. |
| Myanime | PARTIAL / PROTECTED | Site has current 2026 donghua posts, but CI hit Cloudflare on the automated browser path. |
| AnimeHub | PARTIAL / UNSTABLE | Search-indexed current episode pages exist, while CI hit a 522 origin timeout. |
| AniGo | CURRENT / PENDING | Current title/watch pages are indexed; strict playback sweep is pending. |
| Flixer | CURRENT / PENDING | Current domain observed as flixer.gd; strict playback sweep is pending. |
| WcoFun / WCO | CURRENT / PENDING | Current working alternate observed at wco.tv; strict playback sweep is pending. |
| Cineby | PENDING | Included in the current playback sweep. |
| Allwish | PENDING | Current strict run has not yet produced a successful player proof. |
| Anime Realms | UNSTABLE / PENDING | CI saw TLS/SSL failures on the listed domain. |
| Re:ANIME | NOT CURRENTLY GREEN | Current migration test did not complete playback proof. Older page evidence is not being treated as enough. |

## Clearly changed / retiring

| Source | Result | Note |
|---|---|---|
| Anime Tosho | RETIRING | Updates permanently stopped in May 2026; service shutdown was announced for October 2026. |
| XPrime listed domain | CHANGED / PARKED | xprime.tv resolved to a parked Namecheap page in the current CI run; xprime.stream did not resolve. |

## Active work

- Anime megathread playback sweep 2:
  Cineby, Anidap, AniGo, AniKuro, AnimeHub, AnimeNana, AnimeParadise, XPrime,
  Flixer, WcoFun, 123anime, Allwish, Anime Realms, Yenime, Myanime.
- Anime download/torrent sweep:
  Anime Tosho, Tokyo Insider, AnimeOut, Hi10Anime, NoobSubs, Kayoanime,
  ChauThanh, Nyaa, SubsPlease, Beatrice-Raws, Drevos Index, Erai-Raws,
  Tokyo Toshokan, Project AcgnX.

This matrix should be updated from live run evidence; a green workflow by itself is not enough if its per-source verdict is negative.
