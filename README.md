# piTube

piTube is a privacy-respecting, feature-rich YouTube client for Android. It is a fork of [Flow](https://github.com/ColOrourke/Flow) (the discontinued open-source YouTube client), rebuilt around the [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) pipeline and enhanced with features researched and ported from the wider free-software YouTube ecosystem.

> **Status:** active development. Expect rough edges; the app is built continuously via GitHub Actions on every push to `main`.

## Features

- **Ad-free, trackless playback** — no Google Ads, no Analytics SDKs
- **Signed-in YouTube support** — like, subscribe, comments, playlists, and **real watch-history sync** (a yt-dlp-style `videostats` ping reports your actual partial playback position to YouTube, so partially watched videos show up as in-progress)
- **SponsorBlock integration** — skip/mute/notify for all 10 segment categories (sponsor, intro, outro, selfpromo, interaction, music_offtopic, filler, preview, exclusive_access, poi_highlight), custom colors, and segment submission; DeArrow titles/thumbnails; Return YouTube Dislike
- **Enhanced player** — storyboard hover previews on the seek bar, double-tap to seek, background playback, speed control, audio-only mode, playback queue
- **Home feed from YouTube's own "what to watch" endpoint** with a rotation cursor, plus the usual subscription, trending, and category feeds
- **Content filtering** — block channels (persisted), hide watched videos, watched-threshold, shorts shelf toggles, dead-code-free quick-actions sheet
- **Per-channel remembered tab**, default navigation tab, and extensive theming / layout options
- **Import/export & RSS** — subscription backup/restore, RSS-based channel feeds, and notifications for subscribed channels
- **TV (Leanback) UI** alongside the phone/tablet UI

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

- `piTube-debug` artifact → `app-github-universal-debug.apk`
- `piTube-release` artifact → `app-github-universal-release.apk`

## Credits & Acknowledgements

piTube builds on the shoulders of the free-software YouTube ecosystem. Special thanks to:

- **[Flow](https://github.com/ColOrourke/Flow)** — the upstream project piTube is forked from (Compose UI, architecture)
- **[NewPipe / NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor)** — the extraction core (channels, streams, tabs, signed requests, signature timestamp handling)
- **[yt-dlp](https://github.com/yt-dlp/yt-dlp)** — watch-history beacon logic (`videostatsPlaybackUrl`/`videostatsWatchtimeUrl`) ported to report real partial positions
- **[SponsorBlock](https://sponsor.ajay.app)** — crowd-sourced segment skipping (10 categories incl. `poi_highlight`), DeArrow, and RYD data
- **[LibreTube](https://github.com/libre-tube/LibreTube)** — design patterns for settings and per-channel behavior
- **[Piped](https://github.com/TeamPiped/Piped)** — API-first client patterns
- **[Invidious](https://github.com/iv-org/invidious)** — feed/community insights
- **[Grayjay](https://gitlab.futo.org/videostreaming/grayjay)** — external player integration ideas
- **[ReVanced](https://gitlab.com/ReVanced/revanced-patches)** — content-filtering and SponsorBlock/DeArrow/RYD integration patterns
- **[ViewTube](https://github.com/ViewTube/viewtube)** — SponsorBlock server-side category handling incl. `poi_highlight`

## License

piTube is licensed under the [GPL-3.0](LICENSE) — same as Flow and NewPipeExtractor. Use, modify, and share freely.

*piTube is not affiliated with YouTube or Google. All product names, logos, and brands are property of their respective owners.*
