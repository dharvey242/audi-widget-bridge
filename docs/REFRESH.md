# Automatic refresh (v0.4)

While capture is running, the bridge attempts the widget refresh action after 60 minutes without a successful Home Assistant data transfer. A successful transfer, including unchanged or retried readings, restarts the 60-minute countdown. It never triggers an immediate refresh from a transfer, avoiding feedback loops. Manual refresh attempts also restart the countdown.

The deadline persists across process restarts. Stopping capture disables execution; restarting capture checks an overdue deadline. A missing or ambiguous refresh control is logged and retried after another hour. Pending manual/locked tests are allowed to finish first. No alarm or wake lock is used: Android sleep may delay execution, and screen events check overdue work. This is not an exact hourly wake-up guarantee. Cached Audi data may still be returned.

# Refresh-button experiment

The app can invoke the click listener Android installed on a uniquely identified refresh/reload control in the hosted myAudi widget. It does not guess coordinates, extract private PendingIntents, use reflection, send guessed broadcasts or bypass the lock screen.

Start capture and inspect the refresh status. If no unique visible/enabled refresh control is found, export a redacted log for investigation. Clickable controls and resource IDs are included even if their icons have no text.

## Manual test

Tap **Refresh Audi now**. The app logs the attempted action and observes subsequent widget callbacks for up to two minutes. Requests are limited to at least one minute apart, and a second test is refused while another is pending.

## Locked-phone test

1. Start capture.
2. Tap **Test refresh after locking** and lock the phone normally within 30 seconds.
3. Leave it locked for about three minutes.
4. Reopen the bridge and export its log before opening myAudi.

This makes one attempt only if the device is securely locked and its screen is off when the timer fires. Android sleep may delay the timer. It does not wake/unlock the phone. Stop capture or a process restart cancels the pending test. The automatic inactivity schedule operates separately while capture runs.

## Interpret the evidence correctly

- `refresh_requested`: target, time, lock/display state and prior Audi contact text.
- `refresh_click_result`: whether the installed listener was called; **not** proof of an accepted network request.
- `refresh_callback_observed`: later callback and whether contact text differs. Formatting or relative-date changes can differ without establishing a newer vehicle report.

A refresh may reload cached data or launch the official app rather than poll the car. A naturally scheduled update can coincide with the request. The initial device test demonstrated a locked refresh callback but returned an older contact timestamp; forced fresh vehicle polling remains unproven. Comparing after a drive/charging change is more informative than repeatedly refreshing an unchanged parked vehicle.
