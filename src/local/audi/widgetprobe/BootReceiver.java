package local.audi.widgetprobe;
import android.content.*;
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent intent) {
        SharedPreferences p=c.getSharedPreferences("probe",0);
        if(Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) && p.getBoolean("resumeBoot",false) && p.getBoolean("captureWanted",false)) {
            try { c.startForegroundService(new Intent(c,MonitorService.class)); }
            catch(Exception e) { Records.note(c,"boot_start_error",e.getClass().getSimpleName()); }
        }
    }
}
