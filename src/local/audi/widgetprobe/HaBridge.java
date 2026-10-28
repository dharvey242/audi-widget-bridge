package local.audi.widgetprobe;

import android.content.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

final class HaBridge {
    private static final ExecutorService WORKER=Executors.newSingleThreadExecutor();
    private static final AtomicBoolean BUSY=new AtomicBoolean();
    private static volatile String status="Ready to send widget updates.";
    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("ha_bridge",0); }
    static String status(Context c) {
        SharedPreferences p=prefs(c);
        if(!p.contains("config")) return "Home Assistant is not configured.";
        return status + "\nLast successful upload: " + p.getString("last_success","none")
            + "\nPending reading: " + (p.contains("pending") ? "yes" : "no");
    }
    static void configure(Context c, String data) throws Exception {
        if(BUSY.get()) throw new IllegalStateException("Wait for the current upload to finish, then import again.");
        JSONObject config=new JSONObject(data);
        if(config.optInt("format")!=1) throw new IOException("Unsupported connection file");
        JSONArray urls=config.getJSONArray("webhook_urls");
        if(urls.length()<1 || urls.length()>3) throw new IOException("Invalid connection file");
        for(int i=0;i<urls.length();i++) validateUrl(urls.getString(i));
        prefs(c).edit().putString("config", config.toString()).remove("registered").commit();
        status="Connection imported. Waiting for an Audi reading.";
        retry(c);
    }
    static URL validateUrl(String value) throws Exception {
        if(value.contains("YOUR_") || value.contains("REPLACE_")) throw new IOException("Replace example placeholders with your own registration");
        URL url=new URL(value);
        if(url.getUserInfo()!=null || url.getRef()!=null || url.getQuery()!=null) throw new IOException("Invalid webhook address");
        boolean local=url.getHost().matches("192\\.168\\.\\d{1,3}\\.\\d{1,3}|10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|172\\.(1[6-9]|2\\d|3[01])\\.\\d{1,3}\\.\\d{1,3}")
            || url.getHost().endsWith(".local") || url.getHost().equals("localhost") || url.getHost().equals("127.0.0.1");
        if(!"https".equals(url.getProtocol()) && !("http".equals(url.getProtocol()) && local)) throw new IOException("Use HTTPS or a local-network HTTP address");
        if(url.getPath().length()<20) throw new IOException("Invalid webhook address");
        return url;
    }
    static void accept(Context c, JSONObject event) {
        if(!event.optString("provider").startsWith("de.myaudi.mobile.assistant/")
            || !event.optBoolean("remoteViewsPresent") || event.has("extractionError")) return;
        try {
            JSONArray nodes=event.getJSONArray("nodes");
            List<String> texts=new ArrayList<>();
            for(int i=0;i<nodes.length();i++) {
                JSONObject n=nodes.getJSONObject(i);
                if(n.optBoolean("visibleInLayout") && n.has("text")) texts.add(n.getString("text"));
            }
            Map<String,Object> parsed=AudiFields.parse(texts);
            JSONObject snapshot=new JSONObject(parsed);
            // JSONObject(Map) retains nulls as JSON null; explicitly keep unknown fields.
            for(String key:new String[]{"battery","range","mileage","vehicle_name","vehicle_status","vehicle_contact"})
                if(!snapshot.has(key)) snapshot.put(key,JSONObject.NULL);
            snapshot.put("received_at",event.getString("receivedAt"));
            snapshot.put("raw_nodes",nodes);
            snapshot.put("raw_text",new JSONArray(texts));
            snapshot.put("provider",event.getString("provider"));
            snapshot.put("phase",event.optString("phase"));
            snapshot.put("device_locked",event.optBoolean("deviceLocked"));
            snapshot.put("screen_interactive",event.optBoolean("screenInteractive"));
            snapshot.put("snapshot_id",UUID.randomUUID().toString());
            synchronized(HaBridge.class) { prefs(c).edit().putString("pending",snapshot.toString()).commit(); }
            retry(c);
        } catch(Exception e) {
            status="Widget content was not recognised; previous HA reading retained.";
            Records.note(c,"ha_parse_error",e.getClass().getSimpleName());
        }
    }
    static void retry(Context c) {
        Context app=c.getApplicationContext();
        SharedPreferences p=prefs(app);
        if(!p.contains("config") || !p.contains("pending") || !BUSY.compareAndSet(false,true)) return;
        WORKER.execute(() -> {
            boolean delivered=false;
            try {
                String configText=p.getString("config","");
                JSONObject config=new JSONObject(configText);
                String payload=p.getString("pending", "");
                JSONObject snapshot=new JSONObject(payload);
                JSONArray definitions=definitions(snapshot);
                String signature=registrationSignature(definitions);
                if(!signature.equals(p.getString("registered",""))) {
                    status="Registering Home Assistant sensors…";
                    for(int i=0;i<definitions.length();i++) {
                        JSONObject reply=post(config,new JSONObject().put("type","register_sensor").put("data",definitions.getJSONObject(i)));
                        if(!reply.optBoolean("success")) throw new IOException("Sensor registration rejected");
                    }
                    p.edit().putString("registered",signature).commit();
                }
                JSONArray updates=new JSONArray();
                for(int i=0;i<definitions.length();i++) {
                    JSONObject d=definitions.getJSONObject(i);
                    JSONObject u=new JSONObject().put("unique_id",d.getString("unique_id"))
                        .put("type", "sensor").put("state",d.get("state")).put("attributes",d.getJSONObject("attributes"));
                    updates.put(u);
                }
                status="Sending latest Audi reading…";
                JSONObject result=post(config,new JSONObject().put("type","update_sensor_states").put("data",updates));
                int disabled=0;
                for(int i=0;i<updates.length();i++) {
                    String id=updates.getJSONObject(i).getString("unique_id");
                    JSONObject item=result.optJSONObject(id);
                    if(item==null || !item.optBoolean("success")) {
                        p.edit().remove("registered").commit();
                        throw new IOException("Sensor update rejected");
                    }
                    if(item.optBoolean("is_disabled")) disabled++;
                }
                SharedPreferences.Editor editor=p.edit().putString("last_success",Instant.now().toString());
                // Do not discard a newer callback that arrived during this upload.
                synchronized(HaBridge.class) {
                    if(payload.equals(p.getString("pending",""))) editor.remove("pending");
                    editor.commit();
                }
                status="Connected — all sensor updates accepted."+(disabled>0 ? " "+disabled+" sensors are disabled in HA." : "");
                Records.note(app,"ha_delivery", "Sensor updates accepted; widget receipt="+snapshot.getString("received_at"));
                delivered=true;
            } catch(Exception e) {
                // Exception messages and URLs may contain webhook credentials. Never log them.
                status="Upload failed ("+e.getClass().getSimpleName()+"). Latest reading queued for retry.";
                Records.note(app,"ha_delivery_error",e.getClass().getSimpleName());
            } finally {
                BUSY.set(false);
                if(delivered && p.contains("pending")) retry(app);
            }
        });
    }
    private static String registrationSignature(JSONArray definitions) throws JSONException {
        JSONArray metadata=new JSONArray();
        for(int i=0;i<definitions.length();i++) {
            JSONObject d=new JSONObject(definitions.getJSONObject(i).toString());
            d.remove("state");d.remove("attributes");metadata.put(d);
        }
        return metadata.toString();
    }
    private static JSONObject post(JSONObject config,JSONObject body) throws Exception {
        JSONArray urls=config.getJSONArray("webhook_urls");
        for(int i=0;i<urls.length();i++) {
            HttpURLConnection connection=null;
            try {
                connection=(HttpURLConnection)validateUrl(urls.getString(i)).openConnection();
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(12000);connection.setReadTimeout(15000);
                connection.setRequestMethod("POST");connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type","application/json");
                byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(bytes.length);
                try(OutputStream out=connection.getOutputStream()) {out.write(bytes);}
                int code=connection.getResponseCode();
                if(code<200 || code>=300) throw new IOException("Webhook HTTP failure");
                ByteArrayOutputStream response=new ByteArrayOutputStream();
                try(InputStream in=connection.getInputStream()) {
                    byte[] buf=new byte[4096];int n;
                    while((n=in.read(buf))!=-1) {
                        if(response.size()+n>262144) throw new IOException("Oversized webhook response");
                        response.write(buf,0,n);
                    }
                }
                return new JSONObject(new String(response.toByteArray(),StandardCharsets.UTF_8));
            } catch(Exception e) {
                if(i==urls.length()-1) throw new IOException("All configured webhook routes failed");
            } finally {if(connection!=null) connection.disconnect();}
        }
        throw new IOException("No webhook route");
    }
    static JSONArray definitions(JSONObject s) throws JSONException {
        JSONArray out=new JSONArray();
        add(out,s,"battery","Battery", "battery", "%", "measurement", "mdi:battery");
        add(out,s,"range","Range", "distance", s.optString("range_unit","mi"), "measurement", "mdi:map-marker-distance");
        add(out,s,"mileage","Mileage", "distance", s.optString("mileage_unit","mi"), "total_increasing", "mdi:counter");
        add(out,s,"vehicle_status","Vehicle status",null,null,null,"mdi:car-info");
        add(out,s,"vehicle_name","Vehicle name",null,null,null,"mdi:car");
        add(out,s,"vehicle_contact","Last vehicle contact",null,null,null,"mdi:clock-outline");
        s.put("widget_received",s.getString("received_at"));
        add(out,s,"widget_received","Widget received","timestamp",null,null,"mdi:clock-check-outline");
        s.put("widget_data","captured");
        add(out,s,"widget_data","Widget data",null,null,null,"mdi:text-box-outline");
        s.put("bridge_delivery",Instant.now().toString());
        add(out,s,"bridge_delivery","Bridge delivery","timestamp",null,null,"mdi:cloud-upload-outline");
        return out;
    }
    private static void add(JSONArray out,JSONObject s,String key,String name,String deviceClass,String unit,String stateClass,String icon) throws JSONException {
        JSONObject attributes=new JSONObject().put("widget_received_at",s.getString("received_at"))
            .put("audi_contact_raw",s.opt("vehicle_contact"));
        if("widget_data".equals(key)) {
            attributes.put("raw_text",s.getJSONArray("raw_text")).put("raw_nodes",s.getJSONArray("raw_nodes"))
                .put("provider",s.getString("provider")).put("capture_phase",s.getString("phase"))
                .put("phone_locked",s.getBoolean("device_locked")).put("screen_interactive",s.getBoolean("screen_interactive"));
        }
        JSONObject sensor=new JSONObject().put("unique_id",key).put("name",name).put("type","sensor")
            .put("state",s.has(key)?s.get(key):JSONObject.NULL).put("attributes",attributes).put("icon",icon);
        if(deviceClass!=null)sensor.put("device_class",deviceClass);
        if(unit!=null)sensor.put("unit_of_measurement",unit);
        if(stateClass!=null)sensor.put("state_class",stateClass);
        if(Arrays.asList("widget_received","widget_data","bridge_delivery").contains(key))sensor.put("entity_category","diagnostic");
        out.put(sensor);
    }
}
