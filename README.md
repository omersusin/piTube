<p align="center">
  <img src="https://raw.githubusercontent.com/omersusin/piTube/main/docs/branding/logo.png" width="120" alt="piTube logo" onerror="this.style.display='none'"/>
</p>

<h1 align="center">piTube</h1>

<p align="center">
  <strong>Private, fast and ad-free YouTube for Android</strong><br/>
  A <a href="https://github.com/A-EDev/Flow">Flow</a> fork — heavily reworked.<br/>
  Sign in — your feed, subs, history & playlists appear instantly.
</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-GPL--3.0-blue.svg" alt="License: GPL-3.0"/></a>
  <a href="https://github.com/omersusin/piTube/releases"><img src="https://img.shields.io/github/v/release/omersusin/piTube?label=latest" alt="Latest release"/></a>
  <a href="https://github.com/omersusin/piTube/releases"><img src="https://img.shields.io/github/downloads/omersusin/piTube/total?label=downloads" alt="Downloads"/></a>
  <a href="https://github.com/omersusin/piTube/actions"><img src="https://img.shields.io/github/actions/workflow/status/omersusin/piTube/build.yml?branch=main" alt="CI"/></a>
  <img src="https://img.shields.io/badge/minSdk-26%20%7C%20Android%208%2B-green" alt="minSdk 26"/>
  <img src="https://img.shields.io/badge/version-1.0.0%20%281%29-blueviolet" alt="Version 1.0.0"/>
  <img src="https://img.shields.io/github/stars/omersusin/piTube?style=social" alt="Stars"/>
</p>

<p align="center">
  <a href="#installation">Installation</a> •
  <a href="#features">Features</a> •
  <a href="#credits">Credits</a> •
  <a href="#disclaimer">Disclaimer</a> •
  <a href="https://github.com/omersusin/piTube/releases">Releases</a>
</p>

---

## Why piTube?

piTube is a **fork of [Flow](https://github.com/A-EDev/Flow)** — privacy-respecting, feature-rich, and heavily reworked. The codebase has been stripped of legacy Flow surfaces (TV/Leanback UI, Discord presence, RSS feeds, neural-engine sync, music-library import) and rebuilt around **NewPipeExtractor** plus features ported from the wider free-software ecosystem. Unlike wrappers, it speaks YouTube's own **InnerTube** API (WEB / WEB_REMIX / ANDROID_VR) directly, so login is instant, the home feed is truly personal, and playback is reliable.

---

## Features

### 🔐 Instant account — Koda parity
Google sign-in via `SAPISIDHASH` + `SessionCookieJar` + per-profile `EncryptedSharedPreferences`. VisitorData cached for **6 hours**, `ANDROID_VR 1.65.10` origin-bound headers, silent **403 → visitor remint** recovery. History, likes, playlists and subs are scoped **per profile** (switch accounts, nothing leaks).

### 🏠 Home that is actually yours
`FEwhat_to_watch` now uses Koda's dynamic parser — `richGrid` / `richSection` / `lockupViewModel` / `gridVideoRenderer` + continuations. Signed-in users get **100% PERSONAL**, signed-out with subs get **65% subs + rest mixed**, unsigned gets discovery/viral. Bad personal? Trending search fills the gap. Chunked 6-way parallel prefetch, Shorts shelf taken straight from the signed response.

### ▶️ Shorts & Discover
Isolated `ShortsPlayerPool` (no main-player heating), dedicated Shorts feed. Discover/Explore lanes are category-aware (music, docs, gaming) and no longer flood with YouTube Music — the `FEmusic_home` leak was removed.

### 🎤 Synced lyrics — 10 engines
Word- and **letter-level** karaoke. Providers: **LRCLIB, KuGou, NetEase, Megalobiz, Unison, BetterLyrics-Portato, Paxsenix (Spotify/YouTube/Musixmatch)**, plus transcript fallback and disk cache. Engines ported from **vivi-music + ArchiveTune**: None / Fade / Glow / Slide / Karaoke / Apple Music / Apple Music V2 / Lyrics V2 Fluid / Vivimusic Fluid / MetroLyrics (Canvas, RTL-aware, DstIn masks, smoothstep fades). Manual search, live sync-offset, translation chunking, entity-decode & span-integrity guard.

### 🎵 Search & Music
Grouped search results, Explore feed, **YouTube Music categories** (WEB_REMIX flat `itemSectionRenderer` shape), main artist from top-result card + *Fans might also like* shelf, lazy loading, collab-aware junk-artist filter, funnel filter sheet (date/duration/features), topic → real channel bridge.

### 📥 Downloads
Hero card + channel avatar + duration, per-lane infinite scroll, **Seal / YTDLnis / yt-dlp** external picker, known-downloader `NEW_TASK` scan, historyIds coroutine fix.

### 🔍 Recognition
Audile-speed recipe: **4 / 8 / 12 s progressive windows with early-stop**, ACRCloud + AudD + Shazam. Audio-reactive morphing blob (bass/mid/treble), voice orb that swells with RMS.

### ✨ Polish
Storyboard preview from NewPipe (finest `durationPerFrame`, 2× cap), Coil 3.5 with VR guard, heating deep-fix (single avatar, no per-card enrich, global cursor, `LruCache`), Paging 3, Hilt, Room, DataStore, SABR/PoToken.

---

## Installation

**GitHub Releases — fastest updates**
1. Open [**Releases**](https://github.com/omersusin/piTube/releases)
2. Pick your ABI: `arm64-v8a` / `armeabi-v7a` (or `universal` on tagged builds)
3. Install the APK (allow *Install unknown apps* once)

**F-Droid / IzzyOnDroid**
Listed as `com.omersusin.pitube` when stores catch up — GitHub Releases is always ahead.

**Requirements:** Android **8.0+** (API 26), target 36, compile 37. No root needed.

---

## Building

```bash
./gradlew assembleDebug          # ABI splits
./gradlew assembleRelease -PuniversalApk  # universal APK
```

Credentials for recognition (`AUDD_TOKEN`, `ACR_CLOUD_*`) are read from `local.properties` and injected as `BuildConfig` fields — builds without them simply disable those providers.

---

## FAQ

**Is my account safe?** Cookies are stored in `EncryptedSharedPreferences` and sent only to `*.googleapis.com` / `*.youtube.com`. No password is ever saved. Use at your own risk per YouTube's ToS.

**Home feed empty?** Pull to refresh — a stale visitorData is re-minted automatically on 403. Check network or wait for trending fallback.

**Lyrics not found?** Try Manual search inside the lyrics sheet — the title is cleaned (keywords, years, `A, B - C` split) and retried across providers.

---

## Credits

piTube **adapts** — not copy-pastes — patterns from the best open-source players. Huge thanks to:

| Project | What we learned / reused |
|---|---|
| [**Flow**](https://github.com/A-EDev/Flow) ★ | **Upstream fork** — base architecture & design; piTube is a heavily reworked fork (see `package.json` description) |
| [**Koda**](https://github.com/Ivorisnoob/Koda) ★ | Auth/session/visitorData 6h, feed backend, profile isolation — primary reference |
| [**SmartTube**](https://github.com/yuliskov/SmartTube) ★ | Subscriptions & Library structure |
| [**Seal**](https://github.com/JunkFood02/Seal) · [**YTDLnis**](https://github.com/deniscerri/ytdlnis) · [**yt-dlp**](https://github.com/yt-dlp/yt-dlp) ★ | Download pipeline (FAZ A3) |
| [**ArchiveTune**](https://github.com/rukamori/ArchiveTune) ★ | 7-provider lyrics set + AiLyricsTranslator / shazamkit |
| [**vivi-music**](https://github.com/vfsfitvnm/ViMusic) fork chain · [**ViMusic**](https://github.com/vfsfitvnm/ViMusic) · [**InnerTune**](https://github.com/z-huang/InnerTune) · [**OuterTune**](https://github.com/OuterTune/OuterTune) · [**RiMusic**](https://github.com/fast4x/RiMusic) · [**Metrolist**](https://github.com/mostafaalban/Metrolist) | Lyrics engines, player UI, Material theming |
| [**NewPipe**](https://github.com/TeamNewPipe/NewPipe) · [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) · [**Piped**](https://github.com/TeamPiped/Piped) · [**Invidious**](https://github.com/iv-org/invidious) · [**LibreTube**](https://github.com/libre-tube/LibreTube) | Extraction & API design (NewPipeExtractor pipeline) |
| [**Grayjay**](https://github.com/futo-org/Grayjay) · [GrayjayDesktop](https://github.com/futo-org/GrayjayDesktop) | Client architecture |
| [**TranslateYou**](https://github.com/you-apps/TranslateYou) | Translation engine catalog (Mozhi > MyMemory > Apertium) |
| [**Audile**](https://github.com/aleksey-saenko/MusicRecognizer) · [AudileTeam](https://github.com/AudileTeam/Audile) | Recognition timing recipe (AudD/ACRCloud/Shazam progressive 4/8/12s) |
| [Beatbump](https://github.com/snuffydev/Beatbump) · [ytmusicapi](https://github.com/sigma67/ytmusicapi) · [ReVanced](https://github.com/ReVanced) | Feed & API references |

If we missed you, please open an issue — credits matter.

---

## Disclaimer

piTube is **not affiliated with Google or YouTube**. It is an independent client using public InnerTube endpoints. Respect YouTube's Terms of Service and local laws. Authors assume no liability for misuse.

## License

**GPL-3.0** — see [LICENSE](LICENSE).

## Stats

- **581 commits** since initial import — see `git log` for full history (`/tmp/git_full.log` style: `2a81b0d — home feed parser` → `3a8af0a Initial commit`)
- **Downloads:** ![Downloads](https://img.shields.io/github/downloads/omersusin/piTube/total) — track on [Releases](https://github.com/omersusin/piTube/releases)
- **Stars / Forks:** ![Stars](https://img.shields.io/github/stars/omersusin/piTube) ![Forks](https://img.shields.io/github/forks/omersusin/piTube)

## Changelog

Full history: `git log` / [**Releases**](https://github.com/omersusin/piTube/releases). Current: **1.0.0 (1)** — *first stable, Flow fork heavily reworked (581 commits)*.
