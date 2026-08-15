# Google Meet Call Provider — Setup

## What this is

An alternative to the Jitsi bot for video calls, using Google's Calendar API to create real Google
Meet links — free, no Google Workspace subscription required. See `docs/jitsi-bot-setup.md` for
the problem this exists alongside (Jitsi's free tier requiring an authenticated moderator).

## Why per-teacher, not one shared account

Unlike the Jitsi bot (one shared account "warms" any room), Google Meet **cannot** work with a
single shared account here. A Google Meet's non-account guests ("ask to join" without signing in)
can only be admitted by the meeting's **creator** — there's no delegated co-host permission on a
personal (non-Workspace) Google account. If one shared account created every meeting, nobody would
ever be present to admit a knocking guest, since that account never actually joins the call.

So: **each teacher connects their own Google account, once.** Meetings they host are created under
their own identity, so they — as creator — can personally admit students/parents who join without
a Google account. Students and parents never need a Google account themselves.

## One-time setup per environment

1. **Create a Google Cloud project** (or reuse the one already used for `app.google.client-id` /
   Google Sign-In) at [console.cloud.google.com](https://console.cloud.google.com).
2. **Enable the Google Calendar API** for that project (APIs & Services → Library).
3. **Create an OAuth 2.0 Client ID** (APIs & Services → Credentials → Create Credentials → OAuth
   client ID → Web application). This is a **distinct** client from the one used for Google
   Sign-In — this one needs a client **secret**, since it performs a server-side
   authorization-code exchange (Sign-In only verifies an already-issued ID token, no secret
   needed).
4. **Add an authorized redirect URI**: `https://<your-backend-host>/api/v1/calls/google/callback`.
   Google rejects bare IP addresses here — the backend needs a real domain with HTTPS. In
   production this is `https://api.smartgurukul.org/api/v1/calls/google/callback`, served via an
   nginx reverse proxy (`80`/`443` → `127.0.0.1:8080`) with a Let's Encrypt certificate
   auto-renewed by a systemd timer (`certbot-renew.timer`) — Amazon Linux 2023 has no `cron.d`, so
   a plain crontab entry won't run.
5. **Set these environment variables** on the backend deployment:

   | Variable | Value |
   |---|---|
   | `GOOGLE_MEET_CLIENT_ID` | from step 3 |
   | `GOOGLE_MEET_CLIENT_SECRET` | from step 3 |
   | `GOOGLE_MEET_REDIRECT_URI` | the exact URI from step 4 |
   | `CALL_PREFERRED_PROVIDER` | `GOOGLE_MEET` to prefer it, or leave unset/`JITSI` to keep today's behavior |
   | `APP_ENCRYPTION_KEY` | a base64 256-bit key — generate with `openssl rand -base64 32` (used to encrypt teachers' refresh tokens at rest; **do not** reuse across environments) |

6. **Restart the backend.**

## Per-teacher connection flow

Once the above is configured, any teacher/admin can connect their own Google account:

1. `POST /api/v1/calls/google/connect` → returns a Google consent URL.
2. Open that URL in a browser and sign in / grant access.
3. Google redirects to `GET /api/v1/calls/google/callback` — handled automatically, no manual
   step.
4. `GET /api/v1/calls/google/status` confirms `{connected: true, googleEmail: "..."}`.

To disconnect: `DELETE /api/v1/calls/google/disconnect`.

## Operating notes

- **Nothing changes for a teacher who never connects.** `CALL_PREFERRED_PROVIDER=GOOGLE_MEET`
  system-wide does not force Google Meet on every call — `CallProviderResolver` only uses it for a
  specific call when that call's host is an employee who has personally connected their account;
  every other call transparently falls back to the existing Jitsi flow.
- **Immediate calls**: the caller is treated as the "host" for Google Meet purposes.
- **Scheduled calls**: the real Calendar event (with its real join link) is created **at scheduling
  time**, not when the call is started — unlike Jitsi, there's no "warm the room later" step for a
  real calendar-backed meeting. If Google's refresh token exchange or the event-creation call
  fails, scheduling fails loudly with a clear error rather than silently falling back mid-flow.
- **Refresh token storage**: encrypted at rest (AES-GCM, `APP_ENCRYPTION_KEY`) in
  `teacher_google_credential`, one row per employee. If a teacher revokes access from
  [myaccount.google.com/permissions](https://myaccount.google.com/permissions), the next meeting
  creation attempt for them will fail — they'll need to reconnect via the flow above.
- **`roomName` field semantics differ by provider** (see `provider` field on the same response):
  for `JITSI` it's a bare room slug (join via `meet.jit.si/<roomName>`); for `GOOGLE_MEET` it **is**
  the full `https://meet.google.com/...` join URL directly. FE must branch on `provider` when
  building the actual join action.
