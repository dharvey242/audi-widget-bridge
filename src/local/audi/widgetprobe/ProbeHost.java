package local.audi.widgetprobe;

import android.appwidget.*;
import android.content.Context;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import org.json.*;

final class ProbeHost extends AppWidgetHost {
    static final int HOST_ID = 240128;
    long startedAt;
    String session = "";
    String previous = null;
    int callbacks;
    ProbeHost(Context c) { super(c, HOST_ID); }
    @Override protected AppWidgetHostView onCreateView(Context context, int id, AppWidgetProviderInfo info) {
        return new AppWidgetHostView(context) {
            @Override public void updateAppWidget(RemoteViews views) {
                JSONObject event = Records.event(context, "widget_callback");
                try {
                    boolean warmup = SystemClock.elapsedRealtime() - startedAt < 5000;
                    event.put("phase", warmup ? "startup_snapshot" : "live_callback");
                    event.put("session", session);
                    event.put("widgetId", id);
                    event.put("provider", info.provider.flattenToString());
                    event.put("sequence", ++callbacks);
                    event.put("remoteViewsPresent", views != null);
                    super.updateAppWidget(views);
                    // No attached window, screenshot, accessibility service or hidden API.
                    // Force synchronous layout of the supplied RemoteViews before reading text.
                    int width = Math.round(320 * getResources().getDisplayMetrics().density);
                    int height = Math.round(180 * getResources().getDisplayMetrics().density);
                    measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
                    layout(0, 0, width, height);
                    JSONArray nodes = new JSONArray();
                    RefreshAction.collect(this, nodes, true, 0);
                    MonitorService.refreshTarget = RefreshAction.summary(this);
                    String content = nodes.toString();
                    event.put("nodes", nodes);
                    event.put("changedSincePrevious", previous != null && !previous.equals(content));
                    event.put("hasPrevious", previous != null);
                    previous = content;
                    MonitorService.lastContent = nodes.toString(2);
                    MonitorService.callbackCount = callbacks;
                    if (!warmup && event.optBoolean("deviceLocked") && !event.optBoolean("screenInteractive")) {
                        MonitorService.lockedCallbacks++;
                    }
                } catch (Exception e) {
                    try { event.put("extractionError", e.toString()); } catch (JSONException ignored) { }
                    MonitorService.lastError = e.toString();
                }
                Records.append(context, event);
                HaBridge.accept(context, event);
                if (MonitorService.active != null) MonitorService.active.observeRefresh(event);
            }
        };
    }
    @Override protected void onProviderChanged(int id, AppWidgetProviderInfo info) {
        super.onProviderChanged(id, info);
    }
}
