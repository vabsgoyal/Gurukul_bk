# Razorpay setup (fee payments)

One-time manual setup a human has to do in the Razorpay dashboard. Nothing in the code can do it for
you. Until it's done, the app keeps working — students are served the older UPI-intent path instead
(see [Fallback behaviour](#fallback-behaviour)).

## Why a gateway at all

The pre-existing UPI-intent path opens a `upi://pay` deep link and gets **nothing** back. Android's
`Linking` API can only fire-and-forget `startActivity`; there is no callback, no transaction id, no
proof. The app's only way to learn what happened was to ask the payer — a claim that can be neither
verified nor disproved, and which anyone could simply lie about to mark their fee paid.

Razorpay replaces that with a server-verified answer:

```
1. CREATE ORDER   backend → Razorpay      amount fixed server-side from the assessment
2. CHECKOUT       app                     Razorpay's hosted page; card/UPI details never touch us
3. CALLBACK       app → backend           {order_id, payment_id, signature}
4. VERIFY         backend                 re-derive HMAC, then re-fetch the payment from Razorpay
5. WEBHOOK        Razorpay → backend      independent confirmation, arrives even if the app died
```

Steps 4 and 5 are **both** required and are not redundant. Step 4 answers while the student is
watching the screen. Step 5 is what saves a student who force-quits, loses signal, or whose battery
dies between paying and returning — without it, their money is taken and their fee stays unpaid.

## 1. Account and KYC

1. Sign up at <https://razorpay.com>.
2. Complete KYC (PAN, bank proof, school registration certificate; GST if applicable).

KYC blocks **live** mode only. Test mode works immediately, so do all the development below while
KYC is pending.

## 2. API keys

**Dashboard → Settings → API Keys → Generate Key.**

| Value | Secret? | Goes where |
|---|---|---|
| `rzp_test_…` / `rzp_live_…` (Key Id) | No — public by design | `RAZORPAY_KEY_ID`. Served to the app by the backend, never hardcoded in the bundle |
| Key Secret | **Yes** | `RAZORPAY_KEY_SECRET`, server-only |

The key secret is shown **once**. If you lose it, regenerate the pair — it cannot be read back.

Never put the key secret (or the webhook secret) into the app's `.env` or any `EXPO_PUBLIC_*`
variable: those are inlined into the bundle and readable by anyone with the APK.

## 3. Enable auto-capture

**Dashboard → Settings → Payment Capture → Automatic.**

With auto-capture off, payments settle as `authorized` (money held) rather than `captured` (money
taken). The backend deliberately does **not** credit an `authorized` payment — crediting money that
may never be collected would mark fees paid against nothing — so with this misconfigured, payments
succeed for the student and no fee ever gets marked paid. `RazorpayPaymentService` logs a loud
warning if it ever sees one.

## 4. Webhook

**Dashboard → Settings → Webhooks → Add New Webhook.**

- **URL:** `https://<your-host>/api/v1/webhooks/razorpay`
  (production: `https://api.smartgurukul.org/api/v1/webhooks/razorpay`)
- **Secret:** choose one, and put the same value in `RAZORPAY_WEBHOOK_SECRET`.
  This is a **different** credential from the key secret above.
- **Active events:** `payment.captured` and `payment.failed`.

The endpoint must be publicly reachable over HTTPS. For local development, expose your machine with
ngrok (already a devDependency in the frontend) and register that URL instead:

```
npx ngrok http 8080
# then register https://<generated>.ngrok-free.app/api/v1/webhooks/razorpay
```

If `RAZORPAY_WEBHOOK_SECRET` is blank, every webhook is **rejected** rather than trusted — failing
closed is deliberate, but it does mean payments made while the app was closed will never be
credited. Configure it.

## 5. Configuration

Local: `backend/.env.local` (gitignored). Production: `/etc/gurukul/backend.env`, written by GitHub
Actions from repo secrets — see the `ANTHROPIC_API_KEY` block in `.github/workflows/deploy.yml` for
the mechanism to copy.

```properties
RAZORPAY_ENABLED=true
RAZORPAY_KEY_ID=rzp_test_xxxxxxxxxxxx
RAZORPAY_KEY_SECRET=xxxxxxxxxxxxxxxxxxxxxxxx
RAZORPAY_WEBHOOK_SECRET=whatever-you-chose
```

Once these are set, also set:

```properties
APP_FEES_UNVERIFIED_UPI_AUTO_MARK_PAID=false
```

That flag belongs to the old UPI-intent path and lets a self-reported "it worked" mark a fee paid.
It exists only so the app was usable before a gateway existed. With Razorpay live, leaving it `true`
keeps a way to mark fees paid by asserting it.

## 6. Testing

Test mode card: `4111 1111 1111 1111`, any future expiry, any CVV. Test UPI offers explicit
success/failure buttons.

Worth exercising deliberately, because each has burned someone:

| Scenario | Expected |
|---|---|
| Pay successfully | Attempt `VERIFIED`, fee `PAID`, receipt shows the `pay_…` id |
| Kill the app immediately after paying | Webhook still credits it within seconds |
| Cancel on the checkout sheet | Attempt stays open, fee unchanged, no ledger entry |
| Card declined | Attempt `FAILED` with the bank's reason, nothing credited |
| Replay the same webhook (dashboard → Resend) | Exactly **one** `FeePayment` row |
| `POST /api/v1/payment-attempts/{ref}/result` on a Razorpay attempt | Rejected — gateway attempts cannot be self-reported |

## Fallback behaviour

With `RAZORPAY_ENABLED=false` or the credentials blank, `GET /api/v1/fee-payments/gateway` answers
`UPI_INTENT` and the app serves the old deep-link path unchanged. Nothing breaks; payments simply go
back to being unverified. This is why the keys can be added later without an app release.

## Multi-tenancy — read before going live

This is a **single merchant account**: every school's fees land in the one Razorpay account
configured above. That is fine for a pilot or a single school. For many independent schools it means
you are holding other people's money, which is generally not what a standard Razorpay account is
licensed for.

The two real alternatives:

- **Razorpay Route** — each school becomes a linked account and Razorpay splits funds automatically.
  Needs `razorpay_linked_account_id` on `school` and a `transfers` block on order creation.
- **Per-school credentials** — each school brings its own account; store their key id/secret
  encrypted on the `school` row (`app.security.encryption-key` and `TokenCipher` already exist for
  exactly this, used for Google refresh tokens).

Both are localized changes: everything downstream of credential lookup already carries the
`schoolId`, and `payment_attempt.school_id` is already populated per school.

## Costs

Roughly 2% + GST per transaction on standard plans; UPI is often cheaper or free depending on tier.
Settlement is T+2 by default. **Check your own dashboard** — published rates change and vary by
account.
