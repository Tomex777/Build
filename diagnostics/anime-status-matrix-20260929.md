# Anime megathread verification matrix — 2026-09-29

This is the live evidence matrix for the Extension APIs project.

Status rules:
- GREEN = a current test proved a useful end-to-end path, not merely a homepage.
- STREAM GREEN = a current stream/provider path produced playable/decodable media or browser playback advanced.
- DOWNLOAD GREEN = a current final media payload was reached and validated.
- TORRENT GREEN = a valid magnet or real bencoded torrent payload was reached.
- PARTIAL = the current site/catalog works but the full useful path is not yet strictly proven.
- PROTECTED = browser/CDN protection prevents a reliable CI verdict; protected does not mean dead.
- MOVED = old megathread hostname is stale but a current replacement path was proven.
- RETIRING = first-party/current evidence says the service has stopped updating or is shutting down.

## Streaming sources

| Source | Status | Current evidence |
|---|---|---|
| AnimePahe | STREAM GREEN + DOWNLOAD GREEN | Current provider media-decode test passed; separate One Piece episode download reached a real Gofile MP4 and validated file bytes. |
| Miruro | STREAM GREEN | Current resolver path passed through the migrated resolver stack. |
| Anidap | STREAM GREEN / MOVED | Listed anidap.se no longer resolves in generic CI, but the current migrated API/source path passed strict E2E. |
| AniHQ | STREAM GREEN | Direct current episode probe captured real media; ffprobe and decode both passed. |
| AniKuro | STREAM GREEN | Search -> title -> episode -> player reached a ready video and browser playback advanced. |
| AnimeParadise | STREAM GREEN | Current same-origin HLS/player path passed strict media proof. |
| AnimeNana | STREAM GREEN | Final direct One Piece sweep reached video, ready state, and currentTime advanced to ~3.9s. |
| ani.pm | STREAM GREEN | Exact current catalog -> playback bootstrap/session -> source path passed ffprobe and ffmpeg decode. |
| AnimeX | STREAM GREEN | Current provider API returned a source that passed ffprobe/ffmpeg decode. |
| 123anime | STREAM GREEN | Current MegaPlay provider chain resolved and passed strict media proof. |
| Yenime | STREAM GREEN | Current MegaPlay provider chain resolved and passed strict media proof. |
| Animotvslash | STREAM GREEN | Targeted cleanup captured the live media request and validated a real HTTP 206 payload from the current episode/player path. |
| AnimeNoSub | STREAM GREEN | Browser-session proof reached the current episode, decoded six provider embeds, resolved Vidmoly HLS, then fetched a real child media segment with HTTP 206. |
| AnimeXin | PARTIAL / MOVED | Current domain is animexin.dev; current BTTH page loads many embedded player/video elements but playback did not advance in final sweep. |
| KickAssAnime | PARTIAL / MOVED | Current kaa.lt episode page returns 200 and loads embedded players; strict API/player media proof still not green. |
| AniZone | PARTIAL / PROTECTED | Home is current; CI reaches the site but detail/player path returns protection/403. |
| WcoFun / WCO | PARTIAL / MOVED | Current wco.tv One Piece series page returns 200 and loads an embedded player path; playback proof did not advance in final sweep. |
| Allwish | PARTIAL | Current site returns 200 and loads embedded frames; automated title/search route remains inconsistent and playback proof is not green. |
| Flixer | STREAM GREEN | Current TV search API returned One Piece, direct watch `/watch/tv/37854/1/1` loaded, signed player/media requests appeared, and the API-driven validator produced a strict media proof. |
| Myanime | PROTECTED | Current site has current 2026 content but CI is stopped by Cloudflare 403/Just a moment. |
| AnimeHub | UNSTABLE | Current listed domain returned Cloudflare 522/523 origin errors during repeated CI runs. |
| Re:ANIME | PARTIAL / SOURCE-UNRELIABLE | Site/catalog is current, but tested fresh episode pages can report NO_SOURCES. Final direct sweep again got NO_SOURCES. Other indexed episode pages show HD server labels, so this is not classified dead. |
| Cineby | UNRESOLVED DOMAIN | Listed www.cineby.app did not resolve from CI in repeated current tests. |
| AniGo | UNRESOLVED DOMAIN | Listed anigo.to did not resolve; older alternate anigo.buzz returned 404. |
| Anime Realms | BROKEN / TLS | Current listed domain repeatedly failed with SSL protocol errors in CI. |
| XPrime | PARKED / CHANGED | xprime.tv is a Namecheap parking page; xprime.stream did not resolve. |

## Direct-download / torrent sources

| Source | Status | Current evidence |
|---|---|---|
| Kayoanime | DOWNLOAD GREEN | Existing E2E proof reached 11 Drive files and fetched 524288 bytes of a Re:Zero MKV with Matroska EBML signature; total file size ~302 MB. |
| AnimePahe | DOWNLOAD GREEN | Current One Piece episode reached a real Gofile MP4 and final range/media proof passed. |
| Nyaa | TORRENT GREEN | Search/feed -> current release -> real bencoded .torrent payload, 327108 bytes. |
| SubsPlease | TORRENT GREEN | Current release path produced a valid magnet with BTIH and tracker metadata. |
| Tokyo Toshokan | TORRENT GREEN | Current RSS/release handoff reached a real bencoded torrent payload, 5576 bytes. |
| Hi10Anime | ACCOUNT-GATED / PARTIAL | Current site/search works and exposes release candidates, but the targeted cleanup hits account-gated download access before a final payload. |
| ChauThanh | ACTIVE / PARTIAL | Current search returns One Piece and download candidates; final media payload was not yet proven in the strict pass. |
| Beatrice-Raws | ACCOUNT-GATED / PARTIAL | Current site/release results are live; targeted cleanup found many handoff candidates but current access presents account/login gating before a strict final payload. |
| Drevos Index | TORRENT GREEN | Targeted cleanup reached a real magnet link and validated a BTIH hash from the current public path. |
| Erai-Raws | ACCOUNT-GATED / PARTIAL | Site is live, but content/download access requires login in the tested path. |
| Tokyo Insider | ACTIVE / PARTIAL | Current episode/download pages exist, but automated strict final file validation is not yet green. |
| AnimeOut | PROTECTED | Current automated browser receives 403. |
| Project AcgnX | PROTECTED | Current automated browser receives 403. |
| NoobSubs | UNSTABLE / UNREACHABLE | Current CI returned Cloudflare 522. |
| Anime Tosho | RETIRING | Updates permanently stopped May 9, 2026; storage/feed/API shutdown announced for October 2026. Do not build a new long-term extension around it. |

## Highest-confidence extension candidates from this anime pass

Streaming:
- AnimePahe
- Miruro
- Anidap current migrated path
- AniHQ
- AniKuro
- AnimeParadise
- AnimeNana
- ani.pm
- AnimeX
- 123anime
- Yenime
- Animotvslash

Download/torrent:
- Kayoanime
- AnimePahe downloads
- Nyaa
- SubsPlease
- Tokyo Toshokan
- Drevos Index

## Keep as secondary / browser-assisted candidates

- AnimeNoSub
- AnimeXin
- KickAssAnime
- AniZone
- WCO
- Allwish
- Flixer
- Myanime
- Hi10Anime
- ChauThanh
- Beatrice-Raws
- Erai-Raws
- Tokyo Insider

## Do not treat as current production candidates without a new migration

- Anime Tosho (retiring)
- XPrime listed domains (parked/unresolved)
- Anime Realms listed domain (TLS failure)
- Cineby listed hostname (unresolved)
- AniGo listed hostname (unresolved)
- AnimeHub listed origin (522/523 unstable)

A green GitHub workflow is not itself a source pass. Every GREEN status above is based on the per-source evidence inside the workflow or proof artifact.
