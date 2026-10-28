# Contributing

Use synthetic data in tests and screenshots. Never commit a real Home Assistant connection, registration response, access token, signing key or exported phone log.

Run `bash tests/run.sh` and `bash build.sh` before proposing changes. Describe the Android/myAudi versions tested without identifying the account or vehicle. Distinguish startup snapshots from background callbacks, and receipt/delivery times from Audi's source contact text.

Parser changes should retain unknown raw text and reject invalid numeric values without converting them to zero. Refresh changes must preserve unique-target checks, explicit user initiation and lock-state checks. Do not introduce guessed taps, vehicle-control actions, forced polling or credential extraction as incidental changes.

For a bug report, provide steps, app version, general Android/OEM version, expected/actual behaviour and a minimal redacted excerpt. Avoid VINs, location, full odometer histories, account details or private infrastructure addresses. Do not attach a connection JSON.
