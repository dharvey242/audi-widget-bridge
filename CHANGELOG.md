# 0.4

- Automatic widget refresh after 60 minutes without a successful HA transfer while capture runs. Successful transfers reset the countdown; no immediate transfer/refresh loop.
- Persisted deadline, screen-wake catch-up, and next-due status. Android sleep can delay execution.

# Changelog

## 0.3 — public source preparation

- Offscreen myAudi widget hosting and background capture diagnostics.
- Home Assistant Mobile App sensor delivery, latest-snapshot queue and retries.
- Raw status/contact text and all readable widget content retained.
- Explicit manual refresh and one-shot locked-phone refresh test.
- Generic registration tool; private generated configuration stays out of source.
- Synthetic fixtures, host-side tests, pinned GitHub Actions build workflow.
- Build/signing, setup, privacy and refresh-limitations documentation.

Public source builds use each builder's own signing key. They are not automatically compatible with privately signed prototype installations.
