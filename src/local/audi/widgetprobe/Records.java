package local.audi.widgetprobe;

import android.app.KeyguardManager;
import android.content.Context;
import android.os.PowerManager;
import android.os.SystemClock;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

final class Records {
    static JSONObject event(Context c, String type) {
        JSONObject o = new JSONObject();
        try {
            o.put("event", type);
            o.put("receivedAt", Instant.now().toString());
            o.put("elapsedRealtimeMs", SystemClock.elapsedRealtime());
            o.put("screenInteractive", c.getSystemService(PowerManager.class).isInteractive());
            KeyguardManager k = c.getSystemService(KeyguardManager.class);
            o.put("keyguardLocked", k.isKeyguardLocked());
            o.put("deviceLocked", k.isDeviceLocked());
        } catch (JSONException ignored) { }
        return o;
    }
    static File file(Context c) { return new File(c.getFilesDir(), "events.ndjson"); }
    static synchronized void append(Context c, JSONObject o) {
        try {
            File f = file(c);
            if (f.length() > 2 * 1024 * 1024) {
                File old = new File(c.getFilesDir(), "previous.ndjson");
                if (old.exists() && !old.delete()) throw new IOException("Cannot rotate old log");
                if (!f.renameTo(old)) throw new IOException("Cannot rotate log");
            }
            try (FileOutputStream out = new FileOutputStream(f, true)) {
                out.write((o.toString() + "\n").getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) { MonitorService.lastError = "Log write failed: " + e; }
    }
    static void note(Context c, String type, String detail) {
        JSONObject o = event(c, type);
        try { o.put("detail", detail); } catch (JSONException ignored) { }
        append(c, o);
    }
    static synchronized String tail(Context c) {
        File f = file(c);
        if (!f.exists()) return "No events yet.";
        try (RandomAccessFile in = new RandomAccessFile(f, "r")) {
            long start = Math.max(0, in.length() - 18000);
            in.seek(start);
            if (start > 0) in.readLine();
            byte[] b = new byte[(int)(in.length() - in.getFilePointer())];
            in.readFully(b);
            return new String(b, StandardCharsets.UTF_8);
        } catch (IOException e) { return e.toString(); }
    }
    static synchronized void export(Context c, OutputStream out) throws IOException {
        for (String name : new String[]{"previous.ndjson", "events.ndjson"}) {
            File f = new File(c.getFilesDir(), name);
            if (!f.exists()) continue;
            try (InputStream in = new FileInputStream(f)) {
                byte[] b = new byte[8192]; int n;
                while ((n = in.read(b)) != -1) out.write(b, 0, n);
            }
        }
    }
}
