# Task 2 — True multi-account in piTube

Research + work log for making piTube hold several simultaneous YouTube accounts
(per-account cookie/token + DATASYNC id + handle), switching instantly without
signing anyone out.

## Premise check (important)

The task briefing said: *"Koda is single-account - do NOT copy its persistence."*

Verified against the clone on disk
(`~/.cache/opencode/tmp/Koda`, `github.com/Ivorisnoob/Koda`, HEAD `c6ab020`) this
is **false**. DevTools inspection:

- Koda has a multi-profile roster since commit `596a272` *"Hold several profiles
  instead of one session"* (2026-08-05), an ancestor of its HEAD.
- Encrypted per-profile credential store: `ProfileManager.kt`
  (EncryptedSharedPreferences file `yt_music_session`), keys
  `profiles`, `active_profile`, `cookies_<profileId>` — the cookie blob is
  keyed per profile, not global.
- `Profile` carries `id`, `kind` (YOUTUBE/LOCAL), `name`, `handle`, `avatarUrl`,
  `datasyncId`, `addedAt`, `expired` (`ProfileManager.kt:33-49`), serialized with
  handle + datasyncId explicitly (`:337-362`), datasyncId used to dedupe re-added
  accounts (`addYouTubeProfile`, `:116-142`).
- Switching is a single preference write (`setActive`, `:227-233`) + cache
  invalidation (`AccountSwitcher.switchTo`, `:62-87`); long-press quick flip
  (`:118-136`). No re-auth, no sign-out, works offline.

## What piTube already has (on main, all ancestors of HEAD)

The Koda multi-account backend was already ported into piTube:

- `2c0690d` — `ProfileManager`, `SessionManager`, `AccountSwitcher`,
  encrypted roster + per-profile cookies + legacy migration.
- `6ce467d` — cookie-paste login, per-profile expired verdict + badges.
- `91862ae` — persist channel handle + poToken per profile.
- `23eead2` — token/cookie login screen, redesigned account sheet.

So the embarassing part of this task is that the "single-account" symptom is not
absence of the roster: piTube already holds N profiles with independent encrypted
sessions and an instant offline switcher. The reported failure ("adding another
account signs the first one out", "chooser shows 'Oturum kapatıldı'") comes from
regressions/gaps around the roster, not from a missing roster.

## Other client survey (nothing to copy from)

| Client | Multi-account | Persistence |
|---|---|---|
| Koda | YES (true) | encrypted per-profile cookies/datasyncId/handle |
| InnerTune | no | single `innerTubeCookie` DataStore key |
| ViMusic | no login at all | — |
| OuterTune (cur.) | no login | keys commented out |
| OuterTune-dev | no | single cookie + dataSyncId |
| Metrolist | no | single cookie/datasync/handle |
| LibreTube | no (Piped token only) | unencrypted token+username |
| ytmusicapi | no roster (per-instance) | auth file per YTMusic() |
| Flow / flowfork | no | single `youtube_cookie` DataStore key |

## Root causes found (read/grep, not guessed)

1. **Subscriptions are global, not per-profile.**
   `data/local/SubscriptionRepository.kt:14` opens a single `"subscriptions"`
   DataStore and keys rows `channel_<id>` + a single `subscriptions_order`.
   `AccountSwitcher.invalidateForProfileChange()` clears feed caches but never
   re-scopes subscription rows, so subscriptions bleed across accounts and
   switching does not refresh the subscriptions source. This is the biggest
   latent gap for "true" per-account behavior.

2. **`SessionManager.startSession` overwrites the active account in place.**
   `data/local/SessionManager.kt:91-101`: when the active profile is already a
   YOUTUBE profile it does `profileManager.saveCookiesFor(active.id, cookies)`,
   which would replace account A's cookies with account B's. That is exactly the
   "adding a second account signs out / replaces the first" symptom if any login
   path routes through it. All current login entry points go via
   `AccountSwitcher.addYouTubeProfileAndSwitch` (which dedupes + adds), so the
   footgun is dormant-but-live; it should be neutralised so nothing can clobber
   a stored account.

3. **Red "WebView girişi engellendi mi?" text.**
   `login_use_cookies_alt` is a dead string (EN `:1974`, TR `:1943`, not
   referenced from HEAD Java), and `cookie_paste_security_*` (`:1969-1970`,
   TR `:1938-1939`) render an `errorContainer` card shown below **both** login
   tabs (`TokenHelpSection`, `YouTubeLoginScreen.kt:800-830`). Requirement: no
   red WebView-blocked tappable text anywhere; neutral helper text inside the
   token tab only.

## Changes made

- `SubscriptionRepository`: profile-scoped keys (`<profileId>|<channelId>`,
  `<profileId>|order`) so each account has its own subscription list; the
  repository resolves the active profile id per call (via `ProfileManager`).
- `SessionManager`: `startSession` no longer overwrites the active account —
  it always adds/updates a distinct account row (dedupe semantics), the same as
  `AccountSwitcher.addYouTubeProfileAndSwitch`.
- `YouTubeLoginScreen` / strings: removed the dead `login_use_cookies_alt`
  strings; neutralised the security card so helper text lives inside the token
  tab only (no global red card on both tabs).

## Unresolved / open questions

- Whether per-profile subscriptions should also snapshot remote subscriptions per
  account or just namespace local rows: current fix namespaces local rows, which
  is the correct single-source-of-truth behaviour without introducing a network
  sync per account on every switch. Can be revisited if cross-account remote
  sync is wanted later.
- Cookie rotation arrives on the WebView's `Set-Cookie`; RotationInterceptor
  merges into the *active* profile cookie. With N accounts the rotated cookie is
  correctly attributed to the active account because the merge happens against
  whatever the runtime session points at.