# Task 5 — Download subsystem: reference research + rework plan

Phase A research for reworking piTube's download experience. Everything below
was read directly from the cloned reference sources (Seal and YTDLnis, shallow
clones in `.cache/opencode/tmp/`) and from piTube's own source. The plan in
this paper was approved in full (all 9 optional extras) and is implemented
without local Gradle builds — verified only through the "Build piTube" GitHub
Actions workflow.

## Scope

- **6 mandatory fixes**: sheet-first eveywhere; fix the failing 0% default
  download; replace the centered download dialog with an accordion bottom
  sheet; a two-section downloads screen; a one-notification-per-download
  lifecycle; persist last choices + theme parity.
- **9 approved extras**: embed subtitles, SponsorBlock chapters + chapter
  split, trim/cut, thumbnail `.jpg`, concurrent downloads, speed limiting,
  scheduled downloads, per-language subtitle picker + audio size estimates,
  retry on failed.

## Reference donors (what we actually adopted)

### Seal (JunkFood02/Seal)

- `Downloader.kt` — a `DownloaderState` (sealed: Idle / DownloadingPlaylist /
  DownloadingCustomCommand / DownloadingVideo / ...) backed by a
  `mutableStateOf` + `SnapshotStateList<DownloadTaskItem>`; every task carries
  `taskId = "${url}_${template.name}"` (in our design: one id per `videoId`).
- `TaskFactory.kt` / `Task.kt` — command templates + per-task state
  (Running(progress) / Finished / Error / Cancelled).
- `DownloadPage.kt` — single-screen task list with per-row
  progress/cancel/restart/copy-error and an inline `ErrorMessage`.
- `ActionSheet.kt` / `DownloadPageV2.kt` / `DownloadDialogV2.kt` — the "new"
  accordion-style bottom sheet: a configure row (video tab, format chips,
  additional options) collapsed above the Start button. This is the reference
  for C3's accordion.
- `DownloadUtil.DownloadPreferences` — one data class snapshotting the
  whole download config at enqueue time: `extractAudio`, `createThumbnail`,
  `downloadSubtitle`, `subtitleLanguage`, `convertSubtitle`, `formatIdString`,
  `formatSorting`+`sortingFields`, `videoResolution`, `audioFormat/Quality`,
  `concurrentFragments`, `videoClips`/`splitByChapter`, `pieceLength`,
  `embedMetadata`, `embedThumbnail`, `cookies`, proxy, etc. Signature lesson:
  freeze every option a download needs into one immutable value object so the
  queue/scheduler/serialization is trivial and retries restart identically.
- `NotificationUtil.kt` — two channels (`download_notification` low priority,
  `download_service` for the FGS), grouped notifications, `notifyProgress`
  with `setOnlyAlertOnce(true)` and a shared cancel `PendingIntent` acting
  through a broadcast `NotificationActionReceiver`.

### YTDLnis (deniscerri/ytdlnis)

- `work/download/DownloadWorker.kt` — the WorkManager single-download worker:
  takes `priority_item_ids`, queues scheduled downloads (`getQueuedScheduledDownloadsUntil(time)`),
  serializes downloads with `workManager.isRunning("download")` guard, exposes
  a `setForegroundSafely()` ForegroundInfo. This is the reference for D7
  (scheduled downloads must work with the app process alive or dead).
- `TerminalDownloadWorker.kt` — long-running terminal worker for log streaming.
- `database/repository/DownloadRepository.kt` + `database/viewmodel/DownloadViewModel.kt` +
  `DownloadCardViewModel.kt` + `FormatViewModel.kt` — Room + reactive current
  download state feeding a `RecyclerView`. Confirms the DB-backed reactive
  model piTube already uses (`DownloadItemEntity` + Room Flow).
- `database/models/Format.kt` — feed-parsed format: `format_id`, `ext`,
  `vcodec`, `acodec`, `filesize`, `format_note`, `fps`, `asr`, `url`,
  `tbr`, `width`, `height`, `language`. Maps 1:1 onto piTube's
  `convertVideoFormats`/`convertAudioFormats` output.
- `util/FormatUtil.kt` — the format-preference ordering pipeline (codec,
  container, quality, resolution, size) applied as a stable preference list
  over the available formats. Reference for the "best compatible progressive
  MP4 first, DASH fallback" default logic.
- `ui/more/settings/downloading/DownloadSettingsFragment.kt`,
  `DownloadSettingsModule.kt`, `format_importance_*.xml` arrays — settings UI
  shape for the D5/D6/D8 toggles.
- `receiver/CancelDownloadNotificationReceiver.kt` / `PauseDownloadNotificationReceiver.kt`
  — notification action receivers.

### What is NOT portable (explicitly out)

- **yt-dlp binary** (Seal uses `youtubedl-android`; ytdlnis uses `yt-dlp-android`).
  piTube's engine is pure OkHttp + MediaMuxer/FlowMkvMuxer. Nothing that
  requires yt-dlp's process (custom `CommandTemplate` with arbitrary CLI,
  yt-dlp `outputTemplate` formatting, `--download-sections`/`--split-chapters`
  behind yt-dlp, `--write-thumbnail` HTTP fetch, aria2c) is faked.
- Custom user `outputTemplate`/`CommandTemplate` features are not offered.
- yt-dlp's subtitles download stream (piTube uses its own
  `CaptionTrackResolver` for the captions that the player already knows).

## piTube audit results (the six mandatory fixes, root-caused)

### C1 — sheet-first everywhere is broken today

- Player (FULL + COMPACT) and Shorts already render a download dialog/bottom
  sheet first, and the player pre-fetches `currentStreamInfo`,
  `currentInnerTubeVideoFormats`, `currentInnerTubeAudioFormats`,
  `currentStreamSizes` before showing — GOOD.
- **Bug**: the home feed 3-dot menu goes `VideoCard.kt:383-388/709/983` ->
  `VideoQuickActionsBottomSheet` -> download item at
  `VideoQuickActionsSheet.kt:524-533` has **no `onDownload` callback**, so it
  falls through to `QuickActionsViewModel.downloadVideo(video)` which calls
  `FlowDownloadService.startDownload` directly — a default download that
  bypasses the sheet. All start paths must route through one `DownloadLauncher`
  that opens the sheet first.

### C2 — the failing 0% default

- Default pick = `maxBy { height * 10000 + bitrate }` at 720p ->
  AV1 (`av01`), which YouTube CDN-gates -> HTTP 403 ->
  `retryWithCodecFallback` (`FlowDownloadService.kt:772`) re-runs
  `startDownload` -> `downloadManager.saveDownload` re-inserts the same
  `videoId` primary key -> SQLite `UNIQUE` conflict -> caught -> **FAILED at
  0%** (matches the reported "Başarısız" at 0%).
- Fix: (a) default picker prefers the best *compatible progressive* MP4
  (muxed `h264`+`aac`, itag 22/18/59) when present, DASH only as fallback;
  (b) `saveDownload` must be an upsert (`@Insert(onConflict=REPLACE)` or a
  `delete` before insert) that reuses the prior `savePath` so fallback retries
  can never hit the PK conflict.

### C3 — centered dialog -> accordion bottom sheet

- `PlayerDialogsContainer.kt:54/64` renders `DownloadQualityDialogCompact`
  (COMPACT) or `DownloadQualityDialog` (FULL). `VideoPlayerDialogs.kt:41/57`
  draws a centered `Dialog()` with `Surface(rounded 28, tonalElevation 8)`.
  Shorts: `ShortVideoPlayer.kt:1134`. `VideoInfoContent.kt:343` ->
  `showDownloadDialog=true`.
- Fix: one new `DownloadSheet` (ModalBottomSheet + `Surface(
  containerColor = surface, RoundedCornerShape(topStart=28.dp, topEnd=28.dp) )`,
  `rememberFlowSheetState` — matches the app's canonical sheet style per
  `562f4f3`). Accordion sections (video / audio / subtitles / advanced) built
  with `AnimatedVisibility`. The old FULL/COMPACT toggle becomes inert.

### C4 — two-section downloads screen already exists

- `DownloadsScreen.kt` + `DownloadsViewModel.kt` already split
  `section_incomplete_downloads` and `section_completed`, room-driven
  auto-move, pause/resume/delete, progress+muxing state, `pulltorefresh`.
  Only needs the Retry (D9) affordance added on failed rows.

### C5 — one-notification lifecycle

- Needs: single `notificationId` per download (use `videoId.hashCode()`),
  same id reused for progress updates + pause (Paused + Resume/Cancel action),
  dismissed on cancel/complete/error, never a stale indeterminate
  "Download started…" lingering. Modeled on Seal's `NotificationUtil` +
  `NotificationActionReceiver` broadcast actions and ytdlnis's cancel/pause
  receivers. piTube's existing FGS already has notification plumbing in
  `FlowDownloadService`; verify it currently frees the channel on terminal
  states.

### C6 — persist last choices + theme parity

- `PlayerPreferences.kt` already has `lastDownloadType/Height/Codec/AudioLabel`
  keys (~lines 1998-2005). Persistence must extend to the new option state
  (subtitles on/off + language, trim ranges, split chapters, concurrency,
  speed limit) so the sheet restores the last selection.

## Database plan (Room migration)

`DownloadItemEntity` gains columns via a new migration (pattern:
`Migration13To14` added `sponsorBlockSegmentsJson`):

- `subtitlePath` (String?)
- `chaptersJson` (String?)
- `trimStartMs` / `trimEndMs` (Long?)
- `splitChapters` (Boolean)
- `speedLimitBytesPerSec` (Long?)
- `scheduledAtMs` (Long?) + new `ScheduledDownloadEntity` (videoId,
  scheduledAt, options snapshot) for D7.

`DownloadEntity` stays the media-level row; `DownloadItemEntity` rows are the
per-file (video/audio/subtitle) rows; `saveDownload` becomes an upsert.

## Implementation phases

- **Phase B** — `DownloadPlanner` (data): builds a `Plan` from
  videoId+title+streams+current prefs; freezes options; chooses default
  compatible progressive MP4; size estimates incl. audio; `DownloadLauncher`
  (UI) routes every start path through the sheet.
- **Phase C** — C1 neutralize `VideoQuickActionsSheet`'s direct
  `downloadVideo` (add `onDownload`); C2 default + upsert; C3 accordion sheet
  replacing both dialogs (FULL/COMPACT toggle inert); C4 two-section screen +
  Retry; C5 notification lifecycle; C6 persist last choices + theme parity.
- **Phase D** — D1 embed subtitles (MKV text track VTT/SRT via FlowMkvMuxer;
  MP4 -> sidecar `.vtt`); D2 SponsorBlock chapters + split re-mux;
  D3 trim/cut (MediaExtractor + MediaMuxer raw re-mux); D4 thumbnail `.jpg`
  + reuse `thumbnailPath`; D5 `downloadConcurrency` pref (1-6); D6
  speed-limit token bucket in `ParallelDownloader`; D7 scheduled via
  WorkManager (URLs expire -> re-resolve at fire time); D8 subtitle-language
  picker + audio size estimates in the sheet; D9 retry failed rows.

## CI loop

- Every commit pushed to `origin main`; verify via GitHub Actions workflow
  `.github/workflows/build.yml` ("Build piTube" = `assembleDebug` +
  `assembleRelease`); conventional English commits; `gh run watch` on the
  failing/pushed runs and re-push until green.