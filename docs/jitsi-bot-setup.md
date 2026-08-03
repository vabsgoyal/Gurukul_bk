# Jitsi Moderator Bot — Setup

## The problem this fixes

Since August 2023, `meet.jit.si` requires an **authenticated** participant (Google, GitHub, or
Facebook account) to *create/start* a brand-new room. Anyone else who joins before that happens
sees:

> The conference has not yet started because no moderators have yet arrived. If you'd like to
> become a moderator please log-in. Otherwise, please wait.

This app generates a fresh, cryptographically random room name for every single call, so **every
call hits this wall** — confirmed by manually driving a headless browser against a brand-new
anonymous room during development of this fix.

## Why not just script the login?

Google and GitHub actively fight automated sign-in (CAPTCHAs, "this sign-in attempt was
blocked", forced 2FA/device verification) — scripting the OAuth flow itself, especially from a
server IP, is unreliable and risks the bot account getting flagged or locked. It's also not
possible from the mobile app's WebView at all: Google explicitly blocks OAuth sign-in inside
embedded WebViews as a security policy.

Instead: a human logs in **once**, in a real desktop browser, and that already-authenticated
browser profile (cookies/local storage) is saved and reused headlessly by the backend
(`JitsiBotService`) to visit — and thereby "start" — each new room a moment before real
participants join. No login flow runs per call, just an authenticated page visit.

## One-time setup

1. **Create a dedicated bot account.** Don't use anyone's personal Google/GitHub account — create
   a new one just for this (e.g. `gurukul-callbot@<yourdomain>`). This account's only job is
   logging into meet.jit.si; treat its password like any other production secret.

2. **On your own machine**, launch Chrome with a fresh, empty profile directory:

   ```bash
   # macOS/Linux
   mkdir -p ~/jitsi-bot-profile
   google-chrome --user-data-dir=$HOME/jitsi-bot-profile

   # Windows
   mkdir %USERPROFILE%\jitsi-bot-profile
   "C:\Program Files\Google\Chrome\Application\chrome.exe" --user-data-dir=%USERPROFILE%\jitsi-bot-profile
   ```

3. In that Chrome window, go to `https://meet.jit.si`, start any room, click **"I am the host"** /
   **"Log-in"**, and complete the OAuth flow with the bot account. Confirm you land in an active
   conference as the moderator, then close Chrome normally (so the session cookie is flushed to
   disk).

4. **Copy that profile directory to the production server**, e.g.
   `/opt/gurukul/jitsi-bot-profile`. This directory *is* the credential from this point on — back
   it up, and restrict its file permissions the same way you would an API key.

5. Set these environment variables on the backend deployment (see `application.properties` for
   the full list and defaults):

   | Variable | Value |
   |---|---|
   | `JITSI_BOT_ENABLED` | `true` |
   | `JITSI_BOT_PROFILE_DIR` | `/opt/gurukul/jitsi-bot-profile` |
   | `JITSI_BOT_CHROME_BINARY` | already set by the Dockerfile (`/usr/bin/chromium-browser`) — override only for non-Docker deploys |
   | `JITSI_BOT_CHROMEDRIVER_PATH` | already set by the Dockerfile (`/usr/bin/chromedriver`) — override only for non-Docker deploys |
   | `JITSI_BOT_WARM_TIMEOUT_SECONDS` | `20` (default) — how long to wait for the room to start before giving up |

6. Mount that profile directory into the container at the same path (it must persist across
   deploys/restarts — a fresh container without it behaves exactly as if the bot were disabled).

## Operating notes

- **Fails safe, not silent.** If `JITSI_BOT_ENABLED` is `false` or `JITSI_BOT_PROFILE_DIR` is
  blank, `JitsiBotService.warmRoom()` no-ops immediately — calls behave exactly as they did before
  this feature existed. If the bot *is* configured but the saved session has expired (Google
  periodically invalidates long-idle sessions), warming will time out and log a warning
  (`"Jitsi bot could not confirm room ... started"`) rather than throwing — the call still
  proceeds, it may just show "waiting for moderator" to real participants until you redo step 2-4.
- **Added latency.** Starting a call now waits for the bot to confirm the room started (up to
  `JITSI_BOT_WARM_TIMEOUT_SECONDS`) before notifying the callee/invitees — this is intentional
  (see `CallSessionService`/`ScheduledCallService`'s ordering comments), to avoid a fast joiner
  beating the bot into an unstarted room.
- **Re-authentication.** If the saved session does expire, repeat steps 2-4 with the same bot
  account and replace the profile directory on the server.
- **This only needs the Docker image built by this repo's own `Dockerfile`.** If you deploy some
  other way, you're responsible for installing a Chromium build compatible with the packaged
  `selenium-java` version and pointing `JITSI_BOT_CHROME_BINARY`/`JITSI_BOT_CHROMEDRIVER_PATH` at
  it.
