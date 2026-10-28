# Security and privacy

This experimental app hosts a widget selected and approved by the user. It reads the content myAudi supplies to that widget and sends it to the user's configured Home Assistant registration. It does not require root, accessibility, screen capture, overlays or an Audi password.

The HA webhook URL is a credential, not a harmless URL. Keep connection/registration files, exported logs and signing keys out of public repositories. `.gitignore` is a convenience, not a substitute for reviewing commits. Nothing in the example configuration is a usable credential.

The registration script uses an HA access token only in memory and refuses redirects. The app never receives that token. HTTPS certificates are verified; local HTTP routes are unencrypted. Application-layer Mobile App payload encryption is not implemented. Android app backup is disabled, but an unlocked/rooted device or exposed export may still reveal data.

Only the recognised myAudi provider is forwarded to HA. The synthetic test widget is excluded. All readable widget content is retained, which may include identifying information Audi adds in future. Review the raw attributes and logs before sharing.

Report security-sensitive findings privately through the repository owner's available contact channel or GitHub private vulnerability reporting if enabled. Do not post live tokens, webhook URLs, real registration files or unredacted vehicle logs in a public issue. Revoke an exposed bridge credential by deleting its Mobile App registration, then register again.
