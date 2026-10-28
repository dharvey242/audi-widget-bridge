package local.audi.widgetprobe;

import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.os.*;
import java.util.UUID;
import java.util.List;
import android.view.View;
import org.json.*;

public class MonitorService extends Service {
    static boolean running;
    static MonitorService active;
    static String refreshTarget = "Start capture to inspect the widget's refresh control.";
    static String refreshStatus = "No refresh requested.";
    String lastAudiContact = "", refreshBefore = "", refreshId = null;
    long refreshAttemptAt;
    boolean delayedRefresh = false;
    int refreshCallbacks;
    Runnable refreshTimeout;
    @Override public void onCreate() { super.onCreate(); active = this; }

    static String requestRefresh(boolean afterLock) {
        MonitorService service = active;
        if(service == null || !running || service.view == null) return "Start capture first.";
        if(service.delayedRefresh || service.refreshId != null) return "A refresh test is already pending. Wait for its result.";
        if(afterLock) {
            service.delayedRefresh = true;
            refreshStatus = "Refresh test armed. Lock your phone now; one attempt in about 30 seconds.";
            Records.note(service,"refresh_test_armed","One attempt after 30 seconds, only if screen off and device locked. Timers may be delayed by sleep.");
            service.handler.postDelayed(() -> {
                service.delayedRefresh = false;
                if(!running)return;
                boolean locked = service.getSystemService(KeyguardManager.class).isDeviceLocked();
                boolean interactive = service.getSystemService(PowerManager.class).isInteractive();
                if(!locked || interactive) {
                    refreshStatus = "Test skipped: phone was not locked with its screen off.";
                    Records.note(service,"refresh_test_skipped",refreshStatus);
                    return;
                }
                service.performRefresh("locked_test");
            },30000);
            return refreshStatus;
        }
        return service.performRefresh("manual");
    }

    String performRefresh(String mode) {
        if(view == null || view.getAppWidgetInfo() == null)return refreshStatus = "No hosted widget is available.";
        String provider=view.getAppWidgetInfo().provider.getPackageName();
        if(!provider.equals("de.myaudi.mobile.assistant") && !provider.equals(getPackageName()))
            return refreshStatus = "Refresh is only supported for myAudi or the test widget.";
        List<View> matches=RefreshAction.targets(view);
        if(matches.size()!=1) {
            refreshStatus=RefreshAction.summary(view);
            Records.note(this,"refresh_target_not_unique",refreshStatus);
            return refreshStatus;
        }
        long now=System.currentTimeMillis();
        long last=getSharedPreferences("probe",0).getLong("lastRefreshAttempt",0);
        if(now>=last && now-last<60000)return refreshStatus = "Wait at least one minute between refresh attempts.";
        View target=matches.get(0);
        refreshId=UUID.randomUUID().toString();refreshBefore=lastAudiContact;
        refreshAttemptAt=SystemClock.elapsedRealtime();refreshCallbacks=0;
        JSONObject request=Records.event(this,"refresh_requested");
        try {request.put("request_id",refreshId).put("mode",mode).put("target",RefreshAction.label(target)).put("audi_contact_before",refreshBefore);}
        catch(JSONException ignored) { }
        Records.append(this,request);
        getSharedPreferences("probe",0).edit().putLong("lastRefreshAttempt",now).apply();
        String attemptedId=refreshId;
        try {
            // Invoke the listener Android installed from Audi's RemoteViews, without
            // coordinates, reflection, extracting PendingIntents, or unlocking the phone.
            boolean handled=target.callOnClick();
            JSONObject result=Records.event(this,"refresh_click_result");
            result.put("request_id",attemptedId).put("listener_called",handled);
            Records.append(this,result);
            if(!handled) {refreshId=null;return refreshStatus="Refresh control had no callable action.";}
            if(refreshId==null)return refreshStatus;
            refreshStatus="Refresh action invoked. Waiting up to two minutes for a widget response.";
            refreshTimeout=() -> {
                if(refreshId==null)return;
                refreshStatus=refreshCallbacks>0 ? "Widget responded; Audi contact text did not change during the test." : "No widget callback observed within two minutes of the refresh attempt.";
                Records.note(this,"refresh_observation_finished","request="+refreshId+"; "+refreshStatus);
                refreshId=null;
            };
            handler.postDelayed(refreshTimeout,120000);
        } catch(Exception e) {
            Records.note(this,"refresh_action_error",e.getClass().getSimpleName());
            refreshId=null;refreshStatus="Refresh action failed ("+e.getClass().getSimpleName()+"). Export the log.";
        }
        return refreshStatus;
    }

    void observeRefresh(JSONObject event) {
        if(!event.optBoolean("remoteViewsPresent") || event.has("extractionError"))return;
        String contact="";
        JSONArray nodes=event.optJSONArray("nodes");
        if(nodes!=null)for(int i=0;i<nodes.length();i++) {
            JSONObject node=nodes.optJSONObject(i);
            if(node==null || !node.optBoolean("visibleInLayout"))continue;
            String text=node.optString("text","");
            if(text.startsWith("Last vehicle contact:"))contact=text.substring("Last vehicle contact:".length()).trim();
        }
        if(!contact.isEmpty())lastAudiContact=contact;
        if(refreshId==null)return;
        refreshCallbacks++;
        boolean changed=!refreshBefore.isEmpty() && !contact.isEmpty() && !refreshBefore.equals(contact);
        JSONObject observed=Records.event(this,"refresh_callback_observed");
        try {observed.put("request_id",refreshId).put("audi_contact_before",refreshBefore)
            .put("audi_contact_after",contact).put("contact_text_changed",changed)
            .put("elapsed_since_attempt_ms",SystemClock.elapsedRealtime()-refreshAttemptAt);}
        catch(JSONException ignored) { }
        Records.append(this,observed);
        if(changed) {
            refreshStatus="Audi contact text changed after the refresh attempt: "+contact+". See the log for timing.";
            refreshId=null;
            if(refreshTimeout!=null)handler.removeCallbacks(refreshTimeout);
        } else refreshStatus="Widget update received after refresh; waiting for Audi contact text to change.";
    }

    static int callbackCount, lockedCallbacks;
    static String lastContent = "No widget content received.", lastError = "";
    ProbeHost host;
    AppWidgetHostView view;
    final Handler handler = new Handler(Looper.getMainLooper());
    BroadcastReceiver screenReceiver;
    int controlSequence;
    @Override public IBinder onBind(Intent i) { return null; }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP".equals(intent.getAction())) {
            getSharedPreferences("probe",0).edit().putBoolean("captureWanted",false).apply();
            stopSelf(); return START_NOT_STICKY;
        }
        if (running) return START_STICKY;
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("monitor", "Widget observation", NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, MonitorService.class).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, "monitor")
            .setSmallIcon(android.R.drawable.ic_menu_view).setContentTitle("Audi Widget Bridge running")
            .setContentText("Capturing Audi widget updates and sending to Home Assistant.")
            .setContentIntent(open).setOngoing(true).setVisibility(Notification.VISIBILITY_PRIVATE)
            .addAction(new Notification.Action.Builder(null, "Stop", stop).build()).build();
        startForeground(1, n);
        try {
            int id = getSharedPreferences("probe", 0).getInt("widgetId", -1);
            AppWidgetProviderInfo info = AppWidgetManager.getInstance(this).getAppWidgetInfo(id);
            if (info == null) throw new IllegalStateException("Select and configure a widget first.");
            callbackCount = lockedCallbacks = 0; lastError = "";
            host = new ProbeHost(getApplicationContext());
            host.startedAt = SystemClock.elapsedRealtime();
            host.session = UUID.randomUUID().toString();
            Records.note(this, "session_start", "session=" + host.session + "; provider=" + info.provider.flattenToString()
                + "; Android=" + Build.VERSION.RELEASE + "; device=" + Build.MANUFACTURER + " " + Build.MODEL);
            view = host.createView(getApplicationContext(), id, info);
            host.startListening();
            running = true;
            handler.post(new Runnable() {
                @Override public void run() {
                    if (!running) return;
                    HaBridge.retry(MonitorService.this);
                    handler.postDelayed(this, 300000);
                }
            });
            screenReceiver = new BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent i) { Records.note(c, "screen_event", i.getAction()); }
            };
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_SCREEN_OFF); filter.addAction(Intent.ACTION_SCREEN_ON); filter.addAction(Intent.ACTION_USER_PRESENT);
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(screenReceiver, filter);
            if (info.provider.getClassName().equals(ControlWidget.class.getName())) {
                handler.postDelayed(new Runnable() {
                    @Override public void run() {
                        if (!running) return;
                        ControlWidget.send(MonitorService.this, id, ++controlSequence);
                        handler.postDelayed(this, 60000);
                    }
                }, 30000);
            }
        } catch (Exception e) {
            lastError = e.toString();
            Records.note(this, "monitor_error", lastError);
            stopSelf();
        }
        return running ? START_STICKY : START_NOT_STICKY;
    }
    @Override public void onDestroy() {
        if(delayedRefresh || refreshId != null) Records.note(this,"refresh_cancelled","Capture stopped during refresh test.");
        delayedRefresh=false; refreshId=null;
        if(active==this)active=null;
        handler.removeCallbacksAndMessages(null);
        if (host != null) host.stopListening();
        if (screenReceiver != null) { try { unregisterReceiver(screenReceiver); } catch (IllegalArgumentException ignored) { } }
        running = false;
        Records.note(this, "session_stop", "Monitoring stopped. A process kill may not produce this event.");
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }
}
