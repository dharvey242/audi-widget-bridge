# Setup and troubleshooting

## Registration and credentials

Run `python3 tools/register_home_assistant.py --help`. You supply your own HA URL and token; the tool does not inspect another application's credentials or local configuration.

The script uses the token for `/api/config` and `/api/mobile_app/registrations` only, and never stores it. TLS validation stays enabled; redirects are refused to avoid forwarding credentials. Webhook traffic does not use that token. Registration currently sets `supports_encryption: false`: HTTPS protects transport on HTTPS routes, but the Mobile App payload-level encryption extension is not implemented. Local HTTP fallback is unencrypted and should only be used on a trusted network.

`.private/registration.json` contains the registration response and stable device ID. `.private/connection.json` is what the phone imports. Both are private credentials. The script writes files with owner-only permissions on POSIX systems; protect transferred copies and filesystem permissions appropriately on other platforms.

A successful rerun reuses the saved registration. If setup was interrupted after the server may have created a device but before its response was saved, the tool stops rather than silently creating a duplicate. Inspect the Mobile App integration, remove the incomplete bridge entry if present, then delete only the pending `.private/registration.json` and retry. Do not delete unrelated Companion App entries.

If you change servers or want another bridge device, use a different `--private-dir` and device name. Revoking access means removing this bridge's Mobile App registration in HA; also delete its connection file from the phone and private backups if no longer needed. Re-registering produces new credentials.

## Sensors and units

No sample readings are seeded. Sensors appear when the phone uploads its first recognised Audi widget reading. Default names are based on `Audi Widget Bridge`. HA may convert distance readings to its configured unit system; to retain miles, select the range and mileage entities and set their unit preference to `mi`.

The optional example dashboard references the default prefix. Adjust its entity IDs for a different device name or duplicate suffixes. The dashboard does not add remote-refresh control: refresh testing is currently initiated in the Android app.

## No widgets listed / configuration fails

Keep myAudi installed in the same Android profile as the bridge. This version enumerates widgets in the current profile; work-profile or manufacturer restrictions may prevent access. Complete myAudi login and vehicle setup first. The bridge creates its own widget instance, separate from your launcher widget.

## Text missing or login screen shown

The bridge reads TextViews and content descriptions from the supplied RemoteViews. Image-only values are not OCR'd; dynamic collection widgets may not expose all content. The fixed widget size is 320 × 180 dp. The parser expects English labels such as `Charge status`, `Range`, `Mileage`, `Vehicle status` and `Last vehicle contact:`. Unknown labels remain in raw attributes when the snapshot is otherwise recognised; completely unrecognised snapshots are logged and do not overwrite HA readings.

A missing/invalid numeric field becomes unknown, not zero. A stale prior HA reading may remain when an entire snapshot is unrecognised; always inspect receipt/contact times.

## Upload failed

Confirm the phone can reach your configured HA route. Imported webhook credentials may have been revoked by deleting/recreating the registration. HTTPS cloud/remote routes are tried before local fallback when supplied by HA. There is no certificate bypass and HTTP redirects are not followed.

The newest snapshot replaces an older pending snapshot; this is latest-state delivery, not a complete offline history. The queue is stored privately before sending. Retry occurs every five minutes while capture runs, or through **Retry queued upload**. A new callback also triggers a delivery attempt. Per-sensor HA responses are checked. Disabled entities can still be reported as disabled by HA; enable them from HA if needed.

## Background capture stops

Keep notifications enabled and exclude the bridge from aggressive battery management where appropriate. A foreground notification improves survivability but does not guarantee it. The Android service requests restart after process death and optionally resumes after normal reboot/first unlock; force-stop and installation updates may still require opening the app again. Stop capture stops listening and periodic retries; a network upload already in flight may finish.

The regular myAudi home-screen widget is not required once the bridge's own instance is configured. Do not uninstall myAudi or clear its login. The bridge cannot keep Audi sessions valid on your behalf.

## Logs

Use Export log to save NDJSON. Approximate retention is 4 MB across two rotating files. Logs contain vehicle text, device details, timestamps and lock/display state. They deliberately exclude the HA connection URLs and token. Review and redact before sharing. `tools/inspect_log.py` and `tools/inspect_refresh.py` can summarise exported logs locally.
