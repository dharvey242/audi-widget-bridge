package local.audi.widgetprobe;

import android.view.*;
import android.widget.TextView;
import java.util.*;
import java.util.regex.Pattern;
import org.json.*;

/** Operates only a unique, explicitly refresh-labelled click target. No guessed coordinates. */
final class RefreshAction {
    static String resourceName(View v) {
        if(v.getId()==View.NO_ID) return "";
        try { return v.getResources().getResourceName(v.getId()); }
        catch(Exception e) { return ""; }
    }
    private static String words(String s) {
        return s.replaceAll("([a-z0-9])([A-Z])","$1 $2").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+"," ").trim();
    }
    static boolean labelledRefresh(String resource,String description,String text) {
        // Strip package names: only the view's own resource entry is evidence.
        int slash=resource.lastIndexOf('/');
        if(slash>=0)resource=resource.substring(slash+1);
        String all=words(resource+" "+description+" "+text);
        Set<String> tokens=new HashSet<>(Arrays.asList(all.split(" +")));
        if(!tokens.contains("refresh") && !tokens.contains("reload"))return false;
        // Never invoke a control that also names a vehicle-control action.
        return Collections.disjoint(tokens,Arrays.asList("unlock","lock","start","stop","climate","horn","lights","delete","remove","settings"));
    }
    static List<View> targets(View root) {
        List<View> result=new ArrayList<>();scan(root,true,true,result,0);return result;
    }
    private static void scan(View v,boolean parentVisible,boolean parentEnabled,List<View> result,int depth) {
        if(depth>40)return;
        boolean visible=parentVisible && v.getVisibility()==View.VISIBLE;
        boolean enabled=parentEnabled && v.isEnabled();
        String description=v.getContentDescription()==null?"":v.getContentDescription().toString();
        String text=v instanceof TextView?((TextView)v).getText().toString():"";
        if(visible && enabled && v.isClickable() && v.hasOnClickListeners()
            && labelledRefresh(resourceName(v),description,text))result.add(v);
        if(v instanceof ViewGroup) {
            ViewGroup group=(ViewGroup)v;
            for(int i=0;i<group.getChildCount();i++)scan(group.getChildAt(i),visible,enabled,result,depth+1);
        }
    }
    static String summary(View root) {
        List<View> matches=targets(root);
        if(matches.isEmpty())return "No clearly labelled refresh button found. Export the log for inspection.";
        if(matches.size()>1)return "More than one refresh target found. No action will be invoked.";
        return "Refresh button identified: "+label(matches.get(0));
    }
    static String label(View v) {
        String name=resourceName(v);
        String desc=v.getContentDescription()==null?"":v.getContentDescription().toString();
        return name+(desc.isEmpty()?"":" ("+desc+")");
    }
    static void collect(View v,JSONArray nodes,boolean parentVisible,int depth) throws JSONException {
        if(depth>40 || nodes.length()>=300)return;
        boolean visible=parentVisible && v.getVisibility()==View.VISIBLE;
        CharSequence text=v instanceof TextView?((TextView)v).getText():null;
        CharSequence description=v.getContentDescription();
        // Include unlabeled clickable icons in diagnostics even when they cannot be invoked.
        if((text!=null && text.length()>0) || (description!=null && description.length()>0) || v.isClickable()) {
            JSONObject n=new JSONObject().put("class",v.getClass().getSimpleName())
                .put("visibleInLayout",visible).put("enabled",v.isEnabled())
                .put("clickable",v.isClickable()).put("hasClickListener",v.hasOnClickListeners())
                .put("resource",resourceName(v));
            if(text!=null && text.length()>0)n.put("text",text.toString());
            if(description!=null && description.length()>0)n.put("description",description.toString());
            nodes.put(n);
        }
        if(v instanceof ViewGroup) {
            ViewGroup group=(ViewGroup)v;
            for(int i=0;i<group.getChildCount();i++)collect(group.getChildAt(i),nodes,visible,depth+1);
        }
    }
}
