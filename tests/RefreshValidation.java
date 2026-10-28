package local.audi.widgetprobe;
import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;

public class RefreshValidation extends Instrumentation {
    public void onCreate(Bundle args) {super.onCreate(args);start();}
    void check(boolean value,String why) {if(!value)throw new AssertionError(why);}
    public void onStart() {
        Bundle result=new Bundle();Context c=getTargetContext();
        final int[] widget={-1};
        String phase="selector rules";
        try {
            runOnMainSync(() -> {
                check(RefreshAction.labelledRefresh("de.audi:id/buttonRefresh","",""),"camel case resource");
                check(!RefreshAction.labelledRefresh("de.refresh.app:id/open","",""),"package name must not match");
                check(!RefreshAction.labelledRefresh("de.audi:id/refresh_unlock","",""),"reject control action");
                check(!RefreshAction.labelledRefresh("","","Vehicle status"),"no unlabeled guessing");
                LinearLayout root=new LinearLayout(c);
                Button refresh=new Button(c);refresh.setText("Refresh");refresh.setOnClickListener(v -> {});root.addView(refresh);
                check(RefreshAction.targets(root).size()==1,"unique refresh");
                refresh.setEnabled(false);check(RefreshAction.targets(root).isEmpty(),"disabled target");
                refresh.setEnabled(true);root.setVisibility(View.GONE);check(RefreshAction.targets(root).isEmpty(),"hidden ancestor");
                root.setVisibility(View.VISIBLE);
                Button second=new Button(c);second.setText("Reload");second.setOnClickListener(v -> {});root.addView(second);
                check(RefreshAction.targets(root).size()==2,"ambiguous target preserved for rejection");
            });
            phase="bind control widget";
            startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            runOnMainSync(() -> {
                ProbeHost h=new ProbeHost(c);
                widget[0]=h.allocateAppWidgetId();
                ComponentName provider=new ComponentName(c,ControlWidget.class);
                check(AppWidgetManager.getInstance(c).bindAppWidgetIdIfAllowed(widget[0],provider),"grant binding in emulator before test");
                c.getSharedPreferences("probe",0).edit().putInt("widgetId",widget[0]).putBoolean("captureWanted",true).remove("lastRefreshAttempt").putInt("controlRefreshCount",0).commit();
                ControlWidget.send(c,widget[0],0);
                c.startForegroundService(new Intent(c,MonitorService.class));
            });
            for(int i=0;i<100 && !MonitorService.running;i++)Thread.sleep(100);
            check(MonitorService.running,"capture service running");
            phase="arm refresh and lock";
            runOnMainSync(() -> {
                check(!MonitorService.active.view.isAttachedToWindow(),"widget must be offscreen");
                check(RefreshAction.targets(MonitorService.active.view).size()==1,"real RemoteViews refresh target found");
                check(MonitorService.requestRefresh(true).contains("armed"),"locked test armed");
            });
            try(ParcelFileDescriptor ignored=getUiAutomation().executeShellCommand("input keyevent 223")) {Thread.sleep(1000);}
            for(int i=0;i<150 && !c.getSystemService(KeyguardManager.class).isDeviceLocked();i++)Thread.sleep(100);
            check(c.getSystemService(KeyguardManager.class).isDeviceLocked(),"emulator has secure lock");
            check(!c.getSystemService(PowerManager.class).isInteractive(),"screen off");
            phase="await locked PendingIntent";
            for(int i=0;i<500 && c.getSharedPreferences("probe",0).getInt("controlRefreshCount",0)==0;i++)Thread.sleep(100);
            check(c.getSharedPreferences("probe",0).getInt("controlRefreshCount",0)==1,"one control refresh received");
            boolean lockedReceipt=false;
            for(String line:Records.tail(c).split("\n")) {
                JSONObject event=new JSONObject(line);
                if("control_refresh_received".equals(event.optString("event")))
                    lockedReceipt=event.optBoolean("deviceLocked") && !event.optBoolean("screenInteractive");
            }
            check(lockedReceipt,"PendingIntent receiver ran locked with screen off");
            check(!c.getSharedPreferences("ha_bridge",0).contains("pending"),"synthetic control not queued for HA");
            runOnMainSync(() -> {
                c.stopService(new Intent(c,MonitorService.class));
            });
            Thread.sleep(500);
            check(!MonitorService.running,"capture stop cancels refresh observation");
            result.putString("result","PASS: selector excludes ambiguous, hidden, disabled and non-refresh controls; real offscreen RemoteViews refresh PendingIntent received once while PIN-locked/screen-off; control data excluded from HA; stop cancels observation.");
            finish(-1,result);
        } catch(Throwable e) {
            c.stopService(new Intent(c,MonitorService.class));
            result.putString("result","FAIL at "+phase+": "+e.getClass().getSimpleName()+"; "+(e instanceof AssertionError?e.getMessage():"inspect implementation"));
            finish(0,result);
        }
    }
}
