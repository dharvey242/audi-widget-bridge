# Home Assistant configuration

The bridge uses the built-in Mobile App integration. No custom integration or server-side Python installation is required. The Android app sends the sensors; dashboard configuration displays them.

## Connect your own installation

1. Enable `mobile_app` in Home Assistant (normally included by `default_config`).
2. On your computer, run `python3 tools/register_home_assistant.py` from the repository. Supply **your own Home Assistant URL and token** when prompted. See [setup](SETUP.md) for registration, private files and troubleshooting.
3. Transfer the generated `.private/connection.json` privately to your Android phone and import it in Audi Widget Bridge. Never publish that file or `.private/registration.json`.
4. Select/configure your myAudi widget and start capture. Sensors appear after the first recognised widget upload.
5. Find your entities under Settings → Devices & services → Mobile App → Audi Widget Bridge. Default IDs begin `sensor.audi_widget_bridge_`; duplicates or a different device name can change them.

The placeholder [connection example](../examples/connection.example.json) is documentation, not a working connection file. Generate your own webhook using the registration tool.

## Complete dashboard

[dashboard.example.json](../examples/dashboard.example.json) includes battery, range, mileage, raw vehicle status, Audi contact text, capture/delivery timestamps, history, widget diagnostics and the six-hour bridge-health banner.

Create an empty manual dashboard, open its raw configuration editor and paste the JSON. JSON is valid YAML. Replace every default entity ID with your actual entity ID if needed. Save and open the Vehicle view. Do not paste the complete dashboard over an existing dashboard you want to preserve.

## Add only the health banner to an existing dashboard

Open your dashboard editor, add a **Manual** card, and paste [bridge-health-card.example.json](../examples/bridge-health-card.example.json). Replace `sensor.audi_widget_bridge_bridge_delivery` with your bridge's actual delivery timestamp sensor if necessary. Save the card near the top of the dashboard. No extra helper, automation, custom card or HA restart is needed.

## Six-hour warning behaviour

| Colour | Meaning |
|---|---|
| Green | A valid delivery timestamp is no more than six hours old |
| Red | The latest delivery timestamp is more than six hours old |
| Amber | Missing/invalid timestamp or phone timestamp over five minutes in the future |

The banner uses `now()` so Home Assistant recalculates the age as time passes while the dashboard is displayed, including when the phone stops sending updates. Expect the threshold display to change on the next minute refresh, not at an exact second. A new delivery clears the warning automatically.

This monitors the delivery timestamp received from the bridge. It does **not** prove that the car supplied fresh data: Audi may return cached values, and a successful retry can deliver an older widget reading. Capture time and Audi contact text remain visible separately. The delivery timestamp is produced by the phone, so keep its clock automatic/accurate.

This is a dashboard indicator only. It does not create phone notifications, email, persistent notifications or a background alert automation. Missing data shows amber immediately rather than inventing a last-success time.

To choose another threshold, edit `age > 21600` in the card (seconds) and update its six-hour wording together. The default is strictly **over** six hours.

## Versions and validation

Repository release **v0.4.1** updates Home Assistant examples/documentation. The Android app remains **v0.4**; existing installations do not need a new APK. New users can download the [v0.4 APK](https://github.com/dharvey242/audi-widget-bridge/releases/tag/v0.4).

The template was rendered against Home Assistant with healthy, just-over-six-hours, missing and future timestamp cases. The complete dashboard was saved and read back successfully. Examples contain entity references and templates, not personal readings or credentials.

References: [Home Assistant Markdown cards](https://www.home-assistant.io/dashboards/markdown/) and [template time functions](https://www.home-assistant.io/docs/configuration/templating/#time).
