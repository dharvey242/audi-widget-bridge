package local.audi.widgetprobe;

import android.Manifest;
import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.os.*;
import android.widget.*;
import android.graphics.Typeface;
import java.io.OutputStream;
import java.util.*;

public class MainActivity extends Activity {
    static final int BIND = 10, CONFIGURE = 11, EXPORT = 12, IMPORT = 13;
    TextView status, content;
    final Handler handler = new Handler(Looper.getMainLooper());
    final Runnable refresh = new Runnable() {
        @Override public void run() { render(); handler.postDelayed(this, 2000); }
    };
    SharedPreferences prefs() { return getSharedPreferences("probe", 0); }
    ProbeHost setupHost() { return new ProbeHost(getApplicationContext()); }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        ScrollView scroll = new ScrollView(this);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL); column.setPadding(24, 32, 24, 32);
        scroll.addView(column); setContentView(scroll);
        TextView title = new TextView(this); title.setText("Audi Widget Bridge · 0.4"); title.setTextSize(27); column.addView(title);
        TextView intro = new TextView(this);
        intro.setText("Select a widget, start capture, then lock your phone. Updates are recorded locally without displaying the widget. All readable Audi fields are sent to your configured Home Assistant device. Vehicle status is preserved as text.\n\nNo Audi login, root or accessibility access is needed. Import your connection file below. A notification stays visible during capture.\n");
        intro.setTextSize(16); column.addView(intro);
        button(column, "Home Assistant connection", () -> startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), IMPORT));
        button(column, "Retry queued upload", () -> HaBridge.retry(this));
        Switch resume = new Switch(this); resume.setText("Resume capture after phone restart");
        resume.setChecked(prefs().getBoolean("resumeBoot",false));
        resume.setOnCheckedChangeListener((b,checked) -> prefs().edit().putBoolean("resumeBoot",checked).apply());
        column.addView(resume);
        button(column, "1. Select widget", this::choose);
        button(column, "2. Start capture", () -> {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 15);
            prefs().edit().putBoolean("captureWanted",true).apply();
            try { startForegroundService(new Intent(this, MonitorService.class)); }
            catch (Exception e) { message(e.toString()); }
        });
        button(column, "Refresh Audi now", () -> message(MonitorService.requestRefresh(false)));
        button(column, "Test refresh after locking", () -> message(MonitorService.requestRefresh(true)));
        button(column, "Stop capture", () -> { prefs().edit().putBoolean("captureWanted",false).apply(); stopService(new Intent(this, MonitorService.class)); render(); });
        button(column, "Export log", () -> startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE).setType("application/octet-stream")
            .putExtra(Intent.EXTRA_TITLE, "widget-probe-" + System.currentTimeMillis() + ".ndjson"), EXPORT));
        button(column, "View recent events", () -> new AlertDialog.Builder(this).setTitle("Recent events")
            .setMessage(Records.tail(this)).setPositiveButton("Close", null).show());
        button(column, "Clear logs", () -> {
            if (MonitorService.running) { message("Stop capture before clearing logs."); return; }
            new AlertDialog.Builder(this).setMessage("Delete the local diagnostic logs?")
                .setNegativeButton("Cancel", null).setPositiveButton("Delete", (d,w) -> {
                    new java.io.File(getFilesDir(), "events.ndjson").delete();
                    new java.io.File(getFilesDir(), "previous.ndjson").delete();
                    MonitorService.lastContent = "Logs cleared."; render();
                }).show();
        });
        status = new TextView(this); status.setTextSize(16); column.addView(status);
        content = new TextView(this); content.setTextSize(13); content.setTypeface(Typeface.MONOSPACE);
        content.setTextIsSelectable(true); column.addView(content);
    }
    void button(LinearLayout column, String label, Runnable action) {
        Button b = new Button(this); b.setText(label); b.setOnClickListener(v -> action.run()); column.addView(b);
    }
    void message(String text) { new AlertDialog.Builder(this).setMessage(text).setPositiveButton("OK", null).show(); }
    void render() {
        if (status == null) return;
        AppWidgetProviderInfo info = AppWidgetManager.getInstance(this).getAppWidgetInfo(prefs().getInt("widgetId", -1));
        status.setText("\nWidget: " + (info == null ? "none" : info.loadLabel(getPackageManager()))
            + "\nCapture: " + (MonitorService.running ? "RUNNING" : "STOPPED")
            + "\nCallbacks this session: " + MonitorService.callbackCount
            + "\nAfter startup, screen off + device locked: " + MonitorService.lockedCallbacks
            + "\n" + MonitorService.lastError + "\n\n" + MonitorService.refreshTarget + "\n" + MonitorService.refreshStatus + "\n" + MonitorService.automaticStatus(this) + "\n\n" + HaBridge.status(this)
            + "\n\nLatest extracted content (raw; timestamps are not interpreted):\n");
        content.setText(MonitorService.lastContent);
    }
    void choose() {
        if (MonitorService.running) { message("Stop capture before changing widgets."); return; }
        List<AppWidgetProviderInfo> all = new ArrayList<>(AppWidgetManager.getInstance(this).getInstalledProviders());
        all.sort(Comparator.comparing(i -> i.loadLabel(getPackageManager()).toLowerCase(Locale.ROOT)));
        String[] labels = new String[all.size()];
        for (int n = 0; n < all.size(); n++) labels[n] = all.get(n).loadLabel(getPackageManager()) + "\n" + all.get(n).provider.getPackageName();
        new AlertDialog.Builder(this).setTitle("Choose myAudi or Probe control widget")
            .setItems(labels, (d, which) -> bind(all.get(which))).setNegativeButton("Cancel", null).show();
    }
    void bind(AppWidgetProviderInfo info) {
        cleanPending();
        ProbeHost h = setupHost(); int id = h.allocateAppWidgetId();
        prefs().edit().putInt("pendingId", id).apply();
        Bundle options = new Bundle();
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 320);
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 320);
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 180);
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 180);
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN);
        try {
            if (AppWidgetManager.getInstance(this).bindAppWidgetIdIfAllowed(id, info.getProfile(), info.provider, options)) configure(id);
            else startActivityForResult(new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.getProfile())
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, options), BIND);
        } catch (Exception e) { cleanPending(); message("Widget binding failed: " + e); }
    }
    void configure(int id) {
        AppWidgetProviderInfo info = AppWidgetManager.getInstance(this).getAppWidgetInfo(id);
        if (info == null) { cleanPending(); message("Widget was not bound."); return; }
        if (info.configure != null) {
            try { setupHost().startAppWidgetConfigureActivityForResult(this, id, 0, CONFIGURE, null); }
            catch (Exception e) { cleanPending(); message("Widget configuration failed: " + e); }
        } else commit(id);
    }
    void commit(int id) {
        int previous = prefs().getInt("widgetId", -1);
        prefs().edit().putInt("widgetId", id).remove("pendingId").apply();
        if (previous != -1 && previous != id) setupHost().deleteAppWidgetId(previous);
        MonitorService.lastContent = "Widget selected. Start capture to read it.";
        Records.note(this, "widget_selected", "widget=" + id); render();
    }
    void cleanPending() {
        int id = prefs().getInt("pendingId", -1);
        if (id != -1) setupHost().deleteAppWidgetId(id);
        prefs().edit().remove("pendingId").apply();
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == IMPORT) {
            if (result == RESULT_OK && data != null && data.getData() != null) {
                try (java.io.InputStream in = getContentResolver().openInputStream(data.getData())) {
                    if (in == null) throw new java.io.IOException("Cannot open file");
                    java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                    byte[] buffer = new byte[4096]; int n;
                    while ((n = in.read(buffer)) != -1) {
                        if (bytes.size() + n > 32768) throw new java.io.IOException("Connection file too large");
                        bytes.write(buffer,0,n);
                    }
                    HaBridge.configure(this,new String(bytes.toByteArray(),java.nio.charset.StandardCharsets.UTF_8));
                    message("Home Assistant connection imported. Start capture to send readings.");
                } catch (IllegalStateException e) { message("An upload is in progress. Wait for it to finish, then import the connection file again.");
                } catch (Exception e) { message("Could not import connection (" + e.getClass().getSimpleName() + "). Select the supplied bridge connection JSON file."); }
            }
            return;
        }
        if (request == EXPORT) {
            if (result == RESULT_OK && data != null && data.getData() != null) {
                try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
                    if (out == null) throw new java.io.IOException("No output stream");
                    Records.export(this, out); message("Log exported.");
                } catch (Exception e) { message("Export failed: " + e); }
            }
            return;
        }
        if (request != BIND && request != CONFIGURE) return;
        int id = prefs().getInt("pendingId", -1);
        if (result != RESULT_OK || id == -1) { cleanPending(); return; }
        if (request == BIND) configure(id); else commit(id);
    }
    @Override protected void onResume() { super.onResume(); handler.post(refresh); }
    @Override protected void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
}
