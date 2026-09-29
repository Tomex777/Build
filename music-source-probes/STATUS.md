# Lyra / SpotUI Music Source Matrix

Last updated: 2026-09-30

This document is generated from live end-to-end source probes on the `music-source-probes-20260930` branch.
A source is not marked verified until the probe reaches actual media bytes.

## Verification rules

- **Verified full media**: catalog/search or resolver succeeds and at least 64 KiB of real media bytes can be fetched.
- **Verified stream**: full-track streaming endpoint is proven, but artist/service download permission is separate.
- **Preview only**: only a commercial preview has been proven.
- **Experimental**: media works, but the integration is web-client-derived, undocumented, volatile, or has material terms/licensing caveats.
- **Manual / self-host**: hosted service explicitly disallows embedding, requires interactive anti-bot flow, or has no API.
- **Unverified**: site/API exists but the full media path has not passed the probe.

## Verified

| Source | Catalog / search | Full media | Direct download | Classification | Notes |
|---|---|---:|---:|---|---|
| YouTube Music | Yes | Yes | Yes through Lyra cache | Existing production baseline | Android instrumentation resolves, downloads every byte, pins cache, then rereads with network disabled. |
| Internet Archive | Yes | Yes | Yes | Public archive | Direct ranged MP3 media. |
| Audius | Yes | Yes | Track-dependent | Public API | Artist can disable downloads; stream path verified. |
| Openverse | Yes | Yes | Yes | Open-license aggregator | Preserve attribution and license metadata. |
| Wikimedia Commons | Yes | Yes | Yes | Open-license repository | Preserve attribution and license metadata. |
| Monochrome direct tracks API | Yes | Yes | Yes from direct media endpoint | Experimental direct backend | Live probe confirmed literal `fLaC` signature and a 26,492,923-byte ranged track; reliability should still be monitored. |
| Bandcamp | Page/search surface | Yes | Artist-controlled | Public artist-enabled stream | Modern `data-audiourl` exposes complete MP3-128 stream when enabled. Do not equate stream availability with purchased/download rights. |
| AnimeThemes | Yes | Yes | Yes | First-party anime-theme API | Structured metadata and direct `a.animethemes.moe` OGG audio. |
| Audiomack web client | Yes | Yes | Not proven | Experimental web-client-derived | Signed media resolver works, but this is not treated as a stable official public API. |
| Lucida | URL resolver | Yes | Yes | Verified remote downloader / volatile | Current Svelte state parsed successfully; SoundCloud resolve -> handoff -> ripping -> completed -> 206 audio/mp4, 7,785,109 bytes total. |

## Partial / constrained

| Source | Result | Current classification |
|---|---|---|
| Deezer | Search and 30s preview media verified; anonymous full media not verified | Metadata / preview only |
| SoundCloud official API | oEmbed works; anonymous track API search returned 401 | Credentials required for official API |
| Jamendo | API shape is suitable, but documentation test client ID is currently suspended | Needs Lyra-owned client ID |
| Udio | Public search endpoint works and still returns `song_path`; current Google Storage media URLs returned 403 | Unverified / volatile |
| Suno | Public playlist endpoint returned 503 from GitHub runner | Unverified / network-sensitive |
| DAB Music Player | Documented anonymous search/stream/download/lyrics API, but current `dabmusic.xyz/api/search` returned 403 from GitHub and old `dab.yeet.su` did not resolve | Documented but not live-verified; AGPLv3 compatibility requirement |
| KHInsider | Search works; GitHub datacenter IP receives 403 on album page | Catalog alive, anti-bot/network-sensitive |
| MikuDB | GitHub runner receives 401 WEDOS protection | Browser/catalog source, not clean API |
| Sitting on Clouds | Homepage/catalog reachable; direct release file path not yet verified | Catalog source |
| SQUID.WTF | Homepage reachable; no clean API/media flow verified | Volatile |
| SpotiDownloader | Frontend references API hosts but also challenge infrastructure | Surface discovery only |
| SpotifyMate / Spotimate | Site reachable with challenge infrastructure | Surface discovery only |
| CnvMP3 | Site reachable; no clean API route found | Surface discovery only |
| YTMP3Hub | Site reachable; challenge infrastructure present | Surface discovery only |
| LocoLoader | Site reachable; challenge/scraper flow | Browser/manual candidate |

## Do not embed hosted service directly

| Source | Reason |
|---|---|
| Cobalt hosted API | Project documents that hosted API is not for third-party embedding without permission; live API requires JWT/Turnstile. If used, prefer self-hosting. |
| DoubleDouble | Own FAQ says there is no public/private API and asks clients not to automate the site; CAPTCHA is part of the service. |

## Tool / self-host candidates

- **Soulseek + slskd**: real searchable/downloadable source through a user-run daemon; Antra has a mature adapter. Requires a running Soulseek/slskd environment.
- **spotDL**: useful reference/fallback orchestrator over YouTube, YouTube Music, SoundCloud, Bandcamp, Piped and yt-dlp; overlaps sources Lyra can resolve directly.
- **Cobalt**: strong open-source self-hostable resolver for supported video/audio sites, but do not depend on its public hosted API.
- **Beatbump continuation fork**: self-host YouTube-oriented search/player/download surface; lower priority because Lyra already has a proven shared YouTube engine.

## SpotUI integration implications

The current Music Source Contract API v2 already exposes `browse`, `search`, `suggestions`, `artist`, `album`, `streams`, and browser-session methods. A separate download RPC is not required for most new sources: a source can return stable stream candidates and Lyra's existing `LyraAudioCache.download()` path performs durable offline download.

The current host discovers compatible extensions but picks the first compatible music source from the first matching service. Before shipping multiple source IDs in one extension, SpotUI should gain an explicit source registry/selector or deterministic source-priority policy rather than relying on PackageManager ordering.

## Current-megathread corrections

- **YAMS**: current Antra integration treats yams.tf as a user-authenticated backend and requires `YAMS_AUTH_TOKEN`; do not classify it as anonymous.
- **Racoon**: its own repository currently marks the project non-functioning because Cobalt public API access was removed; only relevant if rewritten around a self-hosted backend.
- **downloadsound.cloud**: recent public product comments report redirect/ad abuse; do not use as a production dependency without a fresh ownership/safety review.
