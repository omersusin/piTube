# Task 4 — Translation providers: live audit + fixes

Work log for auditing every keyless piTube translation provider against the
live service (2026-08-15), fixing the broken ones, and reporting which stay
broken and why. Everything below was verified with live HTTP requests
(`curl` / a Python replica), not just read from the code.

## Audited against live API (fixed / confirmed working)

| Provider | Contract | Verdict |
|---|---|---|
| **Mozhi** | GET `api/translate?engine&from&to&text`; GET `api/source_languages` / `api/target_languages` (no trailing slash) -> `[{"Name","Id"}]`; `from=auto` -> `detected` in body | **FIXED** — old multipart POST `/api/translate/` was gone (`invalid json`); ported to GET. Default switched to `https://trnslt.oddte.ch/` (default `mozhi.aryak.me` translate is broken server-side). `from` falls back to `auto`. |
| **Glosbe** | `POST /translateByLangDetect` (no suffix) with `Content-Type: text/plain` and the raw source text in the body | **FIXED** — piTube sent a JSON-quoted body -> HTTP 415. Now sends `text/plain` raw body, matching the Retrofit `@Body String` reference. Verified: `Bonjour` -> `{"translation":"Good afternoon","suggestedLanguage":"fr"}`. |
| **DeepL (Browser)** | `POST https://www.deepl.com/Powertranslator` web satellite endpoint, title + `content-type: application/x-www-form-urlencoded` params, spacing rule `"method" : "LMT_handle_texts"` when `(id+3)%13==0 || (id+5)%29==0`, fresh `id` per request | **HARDENED** — request is byte-identical to the reference and proven working (all commas encodings, both spacing variants return HTTP 200 with real translation via curl/Python). The user-visible "answered without a translation" is a transient DeepL soft-ban/rate-limit, so added a 3-attempt retry with a fresh id + friendly rate-limit message instead of changing the payload. |
| **MinT** | `POST .../api/translate/{from}/{to}` with `{"text":...}`; languages = nested `{"as":{"en":[...]}}` | Works (body shape matches reference `WmTranslationRequest(text)`); `api/languages` shape handled. |
| **SimplyTranslate** | `GET /api/translate/?engine=google&from&to&text` | Works (`source_language`/`translated_text`). |
| **LaraTranslate** | `POST https://webapi.laratranslate.com/translate` with `{"q","source","target"}` | Works (`{"status":200,"content":{"translation":...}}`). |
| **Pons** | `POST /text-translation-web/v4/translate?locale=en` with `{"sourceLanguage","targetLanguage","text"}` | Works live (earlier failure was a transient timeout). |
| **MyMemory** | `GET /api/get?...` | Works. |
| **Apertium** | `GET /listPairs` + `translate?langpair=` | Works for installed pairs; some `fra|eng` pair-names 400 ("That pair is not installed") — pair selection uses `listPairs`. |

## Still broken (server-side / by design) — needs a decision

| Provider | Symptom | Root cause |
|---|---|---|
| **Yandex** | `JSON` POST -> 415; form/query-param POST/GET -> `{"code":405,"message":"Session is invalid"}` | The reference port is identical and equally stuck: the `tr.json/translate` API needs a `sid` from a prior `login`/session flow. Not fixable keyless from client code alone. |
| **Lingva** | all public instances down/errored | `lingva.ml` internal error, `lingva.nexus.foo` DNS, `lingva.thedaviddelta.com` `DEPLOYMENT_PAUSED`, `lingva.www.madiator.cloud` TLS, `lingva.lunar.icu` timeout. No relay this codebase can reach. |
| **LibreTranslate** | `libretranslate.com` -> "Visit https://portal.libretranslate.com to get an API key"; `lt.psf.lt` and `libretranslate.argosopentech.com` DNS-dead | Key-gated official instance; free mirrors unreachable. |
| **Kagi / OneRing** | key or self-host required | by design (keyed). |
| **LLM family** (OpenRouter/OpenAI/Perplexity/Claude/Gemini/XAi/Mistral/Custom) | key required | by design; request/response shapes match current chat-completions + Anthropic Messages API docs (unverified live without keys). |

## Friendly error mapping (TranslationController)

Both failure sinks now call `friendlyMessage(e, engine)`:

- Serialization / JSON conversion -> "$name returned an unexpected response.
  The service may be down or its API changed."
- UnknownHost / ConnectException -> "Couldn't reach $name. Check your
  connection or the instance URL."
- Timeout -> "$name is taking too long to respond. Try again."
- Blank message -> "Translation failed"
- anything else -> the original message.

## Double-tap to show original (FIX 2)

Not provider work, but shipped alongside: a Translation-settings toggle
(default ON) lets a double tap flip any actually-translated inline text
(video title/channel, description, comments/replies, playlist title) back to
the original and back. Wired into the existing `combinedClickable` /
`detectTapGestures` handlers so single-tap and long-press behaviour are
unchanged; subtitles are out of scope (native `tlang` path never touches the
engines).

### SelectionContainer gap (FIX 3)

Description, comment and reply bodies are wrapped in a `SelectionContainer`
so their text (with timestamp/URL annotations) can be copied. `SelectionContainer`
claims the double-tap for word-select and consumes the down/up events, so the
plain `detectTapGestures(onDoubleTap = ...)` handler never saw a second tap and
the toggle did nothing on those surfaces. Added `toggleOriginalOnDoubleTapInSelection`
(`TranslationHooks.kt`): a raw `awaitEachGesture` detector that reads the first
down+up with `requireUnconsumed = false` and toggles when a second tap lands
inside `viewConfiguration.doubleTapTimeoutMillis`. Trade-off (accepted): the
container can still select the tapped word while the original flips in.
Single-tap timestamp/URL handling and "Read more" are untouched.

## Slow-loading fixes (PERF, 2026-08-15)

Everything incl. startup was flagged as slow; root-caused to InnerTube retrying
ineligible requests and serial/N+1 request storms. All six landed:

| # | Fix | Files |
|---|---|---|
| **A** | HTTP status errors (4xx/5xx) are `ResponseException`s — a subclass of `IOException` — so the generic retry wrapper replayed throttled/bot-walled requests 3x with two ~1.5s backoff sleeps. `withRetry(new)` now rethrows `ResponseException` immediately; only genuine transport `IOException`s get another attempt. | `InnerTube.kt:277` |
| **B** | Channel Videos/Live tabs fired ALL pages (~50) upfront with an 800ms artificial sleep between each. Now page 1 loads instantly and remaining pages are fetched lazily as the user scrolls near the bottom (`lastVisible >= total - 5`), gated on `pagerState.settledPage` + `!searchActive`. Continuation stashed per-list; merged with `mergeDistinctByNonBlankKey`; `MAX_PAGES=50` cap + `isLoadingMore*`/`hasMore*` flows. Videos/Live keep the accumulated-list model so `VideoFilter` latest/popular/oldest still sorts everything loaded. | `ChannelViewModel.kt`, `ChannelScreen.kt` |
| **C** | Translation requests capped at 4 concurrent provider calls (`Semaphore`) and identical in-flight calls (same cacheId) collapsed into one request via an app-scoped `Deferred` map — an 80-item feed no longer fires 80 identical HTTP calls. All translation client timeouts cut from 120s to 30s so a dead endpoint stalls a screen for at most ~30s instead of ~2min. | `TranslationController.kt` (`MAX_CONCURRENT_TRANSLATIONS=4`, `appScope`, `inFlight`), `TranslationHttpClient.kt` |
| **D** | Collaborator/avatar-stack resolution ran one HTTP call at a time (up to 10 videos × up to 4s each serial). Now resolved concurrently via `coroutineScope { map { async { ... } }.awaitAll() }` into a `ConcurrentHashMap`; each call keeps its own 4s timeout. | `SearchPagingSource.kt` (`enrichCollabVideoResults`), `ChannelVideosPagingSource.kt` (`withCollabAvatarStacks`) |
| **E** | Home wave1 discovery queries had no timeout and the fast "quick feed" first-paint only fired when `userSubs.isEmpty()`, so signed-in users stared at a skeleton. Discovery is now bounded at 6s (mirrors wave2/load-more) and the viral quick-feed paints for everyone, signed in or not. | `HomeViewModel.kt` |
| **F** | The BotGuard `WebPoTokenSession.prewarm()` was already off the main thread (IO scope + `withContext(IO)`), so no change needed there; trimmed the splash's fixed 1.5s hold to 700ms — home no longer waits on a cosmetic timer. | `FlowSplashScreen.kt:104` |

Build note: the first push of A–F failed CI on three compile errors
(nullable `ListLinkHandler`/`ChannelInfo`/`Page` smart-casts lost during the
B/D refactors + a `MaskedText` vs `Masked` type mismatch) — fixed in
`fix(perf)`: re-added the `videosTab` guard, parenthesised the `?:` fallbacks,
and used the top-level `MaskedText` in `performTranslation`.

## Soft-deprecation of dead providers (FIX 4, 2026-08-15)

Yandex / Lingva / LibreTranslate stay selectable (some users may still reach
self-hosted Libretranslate/lingva or have the old Yandex behaviour) but are
flagged in the picker:

- `TranslationEngine.statusNote` (`open val`, null by default) — every engine can
  describe a caveat.
- Yandex: "The web endpoint rotates its session key; translations often fail.
  Prefer Mozhi or a keyed provider."
- Lingva: "Default lingva.ml instance is often down; use a self-hosted instance."
- LibreTranslate: "Public instances are key-gated or rate-limited; self-host
  LibreTranslate for reliable use."
- `TranslationSettingsScreen` shows the note as an error-tinted banner under the
  active provider row and as a muted error line under each option in the picker
  dialog.

## Open questions for whoever picks this up

1. **Yandex**: implement the session/login flow (`register`/`login` + `sid`)
   to make the keyless API usable, or remove the provider? The request port is
   correct; the blocker is entirely the missing pre-step.
2. **Lingva**: point the default at the (repo's) `translator.libreitalia.org`/
   similar instance if any come back up, replace with another GT wrapper, or
   drop it?
3. **LibreTranslate**: keep as keyed-only, or hardcode a self-host instance URL
   the user can override?
4. **Mozhi default instance**: kept `trnslt.oddte.ch` (verified working); the
   dashboard default (`mozhi.aryak.me`) responds `invalid json` on translate —
   revisit if it recovers.

## Related commits

- `809a697` — fix(translation): repair Mozhi, Glosbe and DeepL browser providers + friendly error mapping
- `5809d77` — feat(translation): double-tap translated text to show the original
- `70a5f3c` — docs(translation): task-4 provider audit - working vs broken matrix
- `917151f` — fix(perf): don't retry HTTP status errors in InnerTube
- `e8a23ad` — perf(channel): lazy on-scroll video and live feeds
- `85d3b0d` — perf(translation): 30s timeout, concurrency cap, in-flight dedup
- `98cd699` — perf: parallel collaborator and avatar-stack resolution
- `8224e4c` — perf(home): time-box discovery, instant viral first paint
- `189a38f` — perf(startup): trim splash hold before fade
- `79311c8` — fix(perf): resolve compile errors in lazy pagination and translation dedup
- `b5e6a50` — feat(translation): soft-deprecate broken providers with picker status notes