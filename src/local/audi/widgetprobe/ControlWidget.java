package local.audi.widgetprobe;
import android.appwidget.*;
import android.content.Context;
import android.content.Intent;
import android.app.PendingIntent;
import android.widget.RemoteViews;
import java.time.Instant;

public class ControlWidget extends AppWidgetProvider {
    @Override public void onReceive(Context c, Intent intent) {
        if("local.audi.widgetprobe.CONTROL_REFRESH".equals(intent.getAction())) {
            int id=intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,-1);
            AppWidgetProviderInfo info=AppWidgetManager.getInstance(c).getAppWidgetInfo(id);
            if(info==null || !info.provider.getClassName().equals(ControlWidget.class.getName()))return;
            int count=c.getSharedPreferences("probe",0).getInt("controlRefreshCount",0)+1;
            c.getSharedPreferences("probe",0).edit().putInt("controlRefreshCount",count).commit();
            Records.note(c,"control_refresh_received","Synthetic refresh action received; count="+count);
            send(c,id,count);
            return;
        }
        super.onReceive(c,intent);
    }

    @Override public void onUpdate(Context c, AppWidgetManager manager, int[] ids) {
        for (int id : ids) send(c, id, 0);
    }
    static void send(Context c, int id, int sequence) {
        RemoteViews r = new RemoteViews(c.getPackageName(), R.layout.control);
        r.setTextViewText(R.id.control_text, "CONTROL DATA — NOT AUDI\n" + (50 + sequence % 40)
            + "% · " + (120 + sequence) + " miles\n" + Instant.now() + "\nSequence " + sequence);
        Intent refresh=new Intent(c,ControlWidget.class).setAction("local.audi.widgetprobe.CONTROL_REFRESH")
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id);
        r.setOnClickPendingIntent(R.id.control_refresh,PendingIntent.getBroadcast(c,id,refresh,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
        Records.note(c, "control_sent", "widget=" + id + ", sequence=" + sequence);
        AppWidgetManager.getInstance(c).updateAppWidget(id, r);
    }
}
