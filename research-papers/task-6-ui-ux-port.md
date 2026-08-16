# Task 6 — UI/UX + Download-Management Port Pass (Seal / YTDLnis)

Research for the UI/UX + download-management improvement pass on piTube.
Sources: local clones of Seal (junkfood02/Seal) and YTDLnis (deniscerri/ytdlnis),
plus a full audit of piTube's own screens (strings + loading/empty/error states).

## 1. Mandatory fixes (from the task brief)

1. **Download queue management** — long-press multi-select with bulk actions
   (pause all, resume all, cancel selected, delete selected, retry
   failed/cancelled), per-item actions (pause/resume/retry/delete/open), clear
   status labels, retry for failed/cancelled items. Keep the two sections.
2. **Remove duplicated controls in the download sheet** — the top "Video / Ses"
   segmented toggle AND the two accordion rows present the same choice twice.
   Keep only the two expandable accordion sections; audio-only as one small
   toggle/row. One control per decision.
3. **Notification lifecycle** — exactly one notification per download; update
   the SAME notification for progress/pause/resume; never leave a stale
   "Download started..." notification; dismiss on complete/cancel.
4. **Consistent loading/empty/error states** — no bare infinite spinners;
   skeleton or spinner+text while loading, friendly empty state, error state
   with retry. Port patterns from Seal/YTDLnis.
5. **String consistency** — translate leftover English UI strings (e.g.
   settings header "Account" -> "Hesap"); audit ALL screens.

## 2. piTube audit results (strings)

### Hardcoded English literals bypassing localization
- `SettingsScreen.kt:805` uses `R.string.settings_header_account` but the key is
  MISSING from `values-tr/strings.xml` (falls back to English "Account").
- `DownloadsScreen.kt:544` — `"Merging audio & video…"` bypasses the existing
  translated `R.string.download_merging_audio_video`.
- `DownloadSheet.kt:544` — `"Video only"` / `"Muxed"` stream-type descriptors.
- `VideoPlayerDialogs.kt:316` — `"Audio Only"` section header.
- `SearchScreen.kt` — `"Loading more…"` (1222), `"End of results"` (1258),
  `"Search Failed"` (1737), `"Shared Video"` placeholder (284/285/336/337).
- `VideoPlayerUtils.kt:88` — storage-permission toast.
- `PlaylistDetailScreen.kt:795` — `"Downloading: ${title}"`; 1588/1590 —
  `"Queued $x/$y downloads"` / `"Could not queue any downloads from this playlist"`.
- `FlowNavigation.kt:633/681/701` — queue titles `"Playlist"` / `"Downloads"` /
  `"Local video"` (shown in queue dock + bottom sheet).
- `VideoPlayerViewModel.kt:1635/1985` — `"Live"` fallback title.
- `RecognitionViewModel.kt:140/151/181/183` — `"Recognition failed"` /
  `"No match found"` — bypasses existing translated `recognition_failure_no_match`.
- `AboutScreen.kt:163` — `"v$versionName"` hardcoded "v"; `UpdateDialog.kt:126` —
  `"Version ${...}"`.
- `FlowDownloadService.kt:1293-1297` — notification literals `"Download complete"`,
  `"Merging audio & video..."`, `"Download failed"`, `"Paused — tap to resume"`.
- Country-name maps (SettingsScreen ~1719, CategoriesScreen ~466): ~100 English
  country names for the trending-region picker — proper nouns, low priority.

### Fully localized screens (no literals)
HomeScreen, LibraryScreen + shelves, LocalMediaScreen, ChannelScreen,
HistoryScreen, LikedVideosScreen, NotificationScreen, PlaylistsScreen,
ShortsScreen, OnboardingScreen, SyncScreen, CrashReporterScreen, AccountSheet,
AccountSwitcherSheet, YouTubeLoginScreen, VideoQuickActionsSheet, all Flow*
sheets, SleepTimerSheet, SubtitleCustomizer, DonationsScreen, DiagnosticsScreen,
AppIconPicker, all other settings screens, DLNA dialog, AddToPlaylistDialog,
MediaInfoDialog.

## 3. piTube audit results (loading / empty / error)

Legend: good / partial / gap.

| Screen | Loading | Empty | Error |
|---|---|---|---|
| HomeScreen | shimmer skeleton; bare spinner load-more | **blank when feed empty** | ErrorState+retry; append errors silent |
| DownloadsScreen | `isLoading` never set/rendered | EmptyDownloadsState | no screen error; FAILED has no retry |
| Library shelves | none (pop-in) | none per shelf | n/a (DB) |
| PlaylistsScreen | bare spinner | EmptyPlaylistLibraryState | none (DB) |
| HistoryScreen | bare spinner | EmptyHistoryState x2 | import failure silent |
| SearchScreen | shimmer (best) | **blank when 0 results** | SearchErrorState+retry; append footer error+retry |
| CategoriesScreen | shimmer | **blank when 0** | ErrorContent+retry; idle footer spinner; append errors silent |
| ShortsScreen | spinner+text (best) | **black screen when 0** | ShortsErrorState+retry |
| LikedVideosScreen | bare spinner | EmptyLikesState | none (DB) |
| NotificationScreen | **empty-state flash** | EmptyNotificationsState | `refreshFailed` never read |
| LocalMediaScreen | spinner+text in empty state | good | scan failure silent |
| SavedShortsGridScreen | **empty-state flash** | good | none |
| ChannelScreen | bare spinners | text-only empties | paging tabs: **no Loading/Error UI** |
| PlaylistDetailScreen | none | EmptyPlaylistState | remote failures silent |
| FlowCommentsBottomSheet | CommentSkeleton | text-only | no error param |
| DownloadSheet | bare spinner | no_download_streams | no retry of stream fetch |

### Priority gaps
1. No empty-results state when a query/feed/tab succeeds with 0 items
   (Search, Categories, Home, Shorts) -> blank screens.
2. Channel paging tabs (Shorts/Playlists) render nothing for
   `LoadState.Loading` / `LoadState.Error` (no retry).
3. Bare infinite spinners (no text): PlaylistsScreen:96, HistoryScreen:233,
   LikedVideos:106, Home load-more 418, Categories footers, Channel tabs,
   CommunityPosts:53, Comments 630, LiveChat 61, DownloadSheet 248.
4. Empty-state flash on load: NotificationScreen, SavedShortsGridScreen,
   Library shelves, DownloadsScreen (until pull-refresh indicator).
5. Silent error channels: `DownloadsUiState.isLoading` never set,
   `NotificationViewModel.refreshFailed` never read, History import failure
   only logged, comment/lyrics/chapters failures silent.
6. Failed downloads have no retry (only status text + delete).
7. 5+ bespoke EmptyState/ErrorState composables, no shared component.

## 4. Seal patterns worth porting

- **Shared icon+text empty state** — `VideoListPage.kt:419-441`: one full-screen
  empty state per page (`rememberVectorPainter(DynamicColorImageVectors.videoSteaming())`
  + `no_downloaded_media`, max-width 360, centered). piTube has 5 near-copies.
- **Per-item status icon + text** — `VideoCardV2.kt:437-470`: CheckCircle done /
  Rounded.Error / CircularProgressIndicator for FetchingInfo/Running, always
  paired with a text label. Directly applicable to DownloadsScreen failed items
  and bare footers.
- **Error diagnostics with copy** — `DownloadPageV2.kt:192-227`: `CopyErrorReport`
  action + `getErrorReport()` + "error copied" snackbar (piTube already has
  ChannelRequestErrorState copy-logs; promote to shared component).
- **Inline button spinners** — `UpdatePage.kt:195-215`.

## 5. YTDLnis patterns worth porting

- **One reusable empty-view layout** — `res/layout/no_results.xml`: icon
  (`ic_no_results`) + bold `no_results` string; every list fragment toggles
  `noResults.isVisible = list.isEmpty()`.
- **Spinner-with-text dialog** — `res/layout/dialog_loading.xml`:
  CircularProgressIndicator + "Loading…" text.
- **Shimmer at item granularity** — `format_item_shimmer.xml` (piTube already
  has `ShimmerLoading.kt`).
- **Per-item retry in download cards** — download cards carry status + explicit
  retry actions per item.

## 6. Implementation plan (agreed)

- **Phase 0**: this research file.
- **Phase B1**: unify notification id in FlowDownloadService — start/resume
  foreground with `getNotificationId(videoId)` instead of the hardcoded 724;
  drop `FOREGROUND_NOTIFICATION_ID`; verify stopForeground interplay with the
  6s auto-dismiss of the complete notification.
- **Phase B2**: DownloadSheet — remove the Video/Ses segmented toggle and the
  `DownloadMode` gating; always show both accordions; audio-only as one small
  switch row inside the Ses kalitesi section; adapt build-plan + summary;
  remove `download_mode_video`/`download_mode_audio` strings.
- **Phase A**: queue management — selection mode (long-press), bulk
  pause/resume/cancel/delete/retry, per-item overflow menu, retry for
  FAILED/CANCELLED, "İndiriliyor" status label.
- **Phase C**: shared `EmptyState` component; fix the priority gaps above.
- **Phase D**: string pass per section 2.
- **Phase E** (all approved by user): SAF save-directory picker, thumbnail
  .jpg sidecar, per-language subtitle (.vtt) download, parallel download cap,
  speed limit, scheduled downloads (WorkManager), retry failed.
  NOT engine-viable (no yt-dlp/ffmpeg): split/trim, SponsorBlock burning,
  embedded subtitle/chapter tagging into MP4.