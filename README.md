# piTube

piTube is a privacy-respecting, feature-rich YouTube client for Android. It is a fork of [Flow](https://github.com/A-EDev/Flow) with **high changes**: the codebase has been heavily reworked, dead code and legacy surfaces removed, and rebuilt around the [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) pipeline plus features researched and ported from the wider free-software YouTube ecosystem.

> **Status:** active development. Expect rough edges; the app is built continuously via GitHub Actions on every push to `main`.

## Features

- **Ad-free, trackless playback** — no Google Ads, no Analytics SDKs
- **Signed-in YouTube support** — like, subscribe, comments, playlists, and **real watch-history sync**: a Koda/yt-dlp-style `videostats` beacon reports your actual playback position (integer `cmt`/`st`/`et`, SAPISIDHASH-authenticated pings from the signed player response) so partially watched videos appear as in-progress in official YouTube history, not as fully watched
- **Multi-account login** — a **"You" tab** opens the redesigned account sheet (partial-height modal, profiles-first) for quick switching, and login happens either through the embedded WebView *or* by pasting a session token: a full OuterTune/ArchiveTune-style bundle (`***INNERTUBE COOKIE***`, `***VISITOR DATA***`, `***DATASYNC ID***`, `***PO TOKEN***`, `***ACCOUNT NAME/EMAIL/CHANNEL HANDLE***`) or a plain `SAPISID`/`SID` cookie header from a browser DevTools export. Per-profile cookies, expired-session detection that flags dead profiles instead of silently falling back
- **Real Watch Later & playlists** — toggling Watch Later and the add-to-playlist sheet write to your actual account (`WL`/`PL…` via the WEB_REMIX client) when signed in, and "Create playlist" creates it on the account through `playlist/create`; a local offline-safe copy keeps everything working without a connection
- **Account library sync** — "Sync now" fully crawls your liked videos (continuation-token pagination, no more 100-item cap), playlists and subscriptions from the WEB client, plus a silent daily **auto-sync**
- **Notification inbox** — the Notifications screen shows your real YouTube inbox (fetched via `notification/get_notification_menu`), refreshed on open and synced on a 12-hour schedule
- **SponsorBlock integration** — skip/mute/notify for all 10 segment categories (sponsor, intro, outro, selfpromo, interaction, music_offtopic, filler, preview, exclusive_access, poi_highlight), custom colors, and segment submission; DeArrow titles/thumbnails; Return YouTube Dislike
- **Enhanced player** — storyboard hover previews on the seek bar, double-tap to seek, background playback, speed control, audio-only mode, sleep timer, playback queue
- **Home feed from YouTube's own "what to watch" endpoint** with a rotation cursor and background refresh so the feed keeps changing, plus the usual subscription, trending, and category feeds
- **Shorts & thumbnails** — working Shorts feed (continuation-token paging, hardened player client ladder) and avatar/thumbnail URL normalization so channel images actually render
- **Picture-in-picture & popup player** — PiP with playback controls, with automatic AppOps permission checks that guide you to the system settings screen when PiP or overlay access is revoked
- **Content filtering** — block channels (persisted), hide watched videos, watched-threshold, shorts shelf toggles, dead-code-free quick-actions sheet
- **Translation everywhere** — translate video titles, descriptions, comments, and channel/playlist names inline, with the original text shown below in "dual" mode when you want it; captions get native YouTube `tlang` tracks for perfectly synced translated subtitles. Providers include the AI families (OpenRouter, OpenAI, Perplexity, Claude, Gemini, X.AI, Mistral, DeepL, custom OpenAI-compatible endpoints) and the free Translate You engines (Kagi, OneRing, Yandex, Mozhi, LibreTranslate, Lingva, MyMemory, MinT, Pons, Glosbe, Apertium, LaraTranslate, SimplyTranslate, DeepL browser)
- **Per-channel remembered tab**, default navigation tab, and extensive theming / layout options
- **Device-to-device sync** between your own piTube installs (watch progress, likes, subscriptions), app update checker, and diagnostics

## Requirements

- Android 8.0 (API 26) or later
- No Google Play services required

## Building

The project uses Gradle with version catalog (`gradle/libs.versions.toml`) and Kotlin 2.x + Compose.

```bash
./gradlew assembleGithubDebug      # debug APK
./gradlew assembleGithubRelease    # release APK
```

Release builds are signed using environment variables (no secrets in the repo):

| Variable | Description |
|---|---|
| `KEYSTORE_FILE` | Path to the release keystore (`~/.keystore/piTube-release.jks`) |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias (e.g. `pitube`) |
| `KEY_PASSWORD` | Key password |

### Continuous integration

`.github/workflows/build.yml` builds both debug and release APKs on every push, decodes the signing keystore from the `KEYSTORE_BASE64` secret, and uploads the signed artifacts:

- `piTube-debug` artifact → `app-universal-debug.apk`
- `piTube-release` artifact → `app-universal-release.apk`

## Credits & Acknowledgements

piTube is a Flow fork with high changes and builds on the shoulders of the free-software YouTube ecosystem. Research, ports, and design inspiration came from:

- **[Flow](https://github.com/ColOrourke/Flow)** — upstream project piTube is forked from (Compose UI, architecture)
- **[Koda](https://github.com/Ivorisnoob/Koda)** — WEB-client account reads (subscribed channels, playlists, liked videos), avatar resolution upgrade, watchtime ping auth (SAPISIDHASH), the multi-account profile backend (per-profile cookies, account switching, session invalidation), **cookie-paste login**, **expired-session detection**, the **real Watch Later / playlist-edit** path (`edit_playlist`/`WL` and `playlist/create` on the music origin), and the **notification inbox** parser
- **[NewPipe / NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor)** — the extraction core (channels, streams, tabs, signed requests, signature timestamp handling)
- **[yt-dlp](https://github.com/yt-dlp/yt-dlp)** — watch-history beacon logic (`videostatsPlaybackUrl`/`videostatsWatchtimeUrl`, `ver=2`/`cpn`/`cmt`/`el=detailpage` params) ported to report real partial positions
- **[SponsorBlock](https://sponsor.ajay.app)** — crowd-sourced segment skipping (10 categories incl. `poi_highlight`), DeArrow, and RYD data
- **[ViMusic](https://github.com/vipulnsward/ViMusic) / [InnerTune](https://github.com/z-huang/InnerTune) / [OuterTune](https://github.com/outertune/outertune) / [Metrolist](https://github.com/abdlquadri/Metrolist) / [ArchiveTune](https://github.com/Archivist-ai/ArchiveTune) / [audiotube](https://github.com/nichpan/audiotube)** — music-client lineage: playback registration, library browsing, media-session patterns
- **[LibreTube](https://github.com/libre-tube/LibreTube)** — design patterns for settings and per-channel behavior
- **[Piped](https://github.com/TeamPiped/Piped)** — API-first client patterns
- **[Invidious](https://github.com/iv-org/invidious)** — feed/community insights
- **[Grayjay](https://gitlab.futo.org/videostreaming/grayjay)** — external player integration ideas
- **[ReVanced](https://gitlab.com/ReVanced/revanced-patches)** — content-filtering and SponsorBlock/DeArrow/RYD integration patterns
- **[ViewTube](https://github.com/ViewTube/viewtube)** — SponsorBlock server-side category handling incl. `poi_highlight`
- **[FlexTube](https://github.com/FlexTube) / [Beatbump](https://github.com/snuffyDev/Beatbump) / [ViewerTube](https://github.com/wartek-dev/viewertube)** — frontend/UX references
- **[Translate You](https://github.com/you-apps/TranslateYou)** — the translation engine abstraction and its free engines (Mozhi, LibreTranslate, Lingva, MyMemory, MinT, Kagi, OneRing, Yandex, Pons, Glosbe, Apertium, LaraTranslate, SimplyTranslate, DeepL browser) re-implemented on Ktor for the in-app translation feature

## License

piTube is licensed under the [GPL-3.0](LICENSE) — same as Flow and NewPipeExtractor. Use, modify, and share freely.

*piTube is not affiliated with YouTube or Google. All product names, logos, and brands are property of their respective owners.*
