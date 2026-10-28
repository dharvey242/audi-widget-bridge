package local.audi.widgetprobe;

import java.util.*;
import java.util.regex.*;

/** Parses the observed English widget; unknown labels remain in raw_text. */
public final class AudiFields {
    public static Map<String,Object> parse(List<String> input) {
        List<String> text = new ArrayList<>();
        for (String value : input) {
            String s = value.replace('\u00a0',' ').replace('\u202f',' ').trim();
            if (!s.isEmpty()) text.add(s);
        }
        Map<String,Object> result = new LinkedHashMap<>();
        String contact = null, name = null;
        for (int i=0; i<text.size(); i++) {
            String s=text.get(i);
            if (s.startsWith("Last vehicle contact:")) {
                contact=s.substring("Last vehicle contact:".length()).trim();
                if(i>0) name=text.get(i-1);
                break;
            }
        }
        if (contact == null || (!text.contains("Charge status") && !text.contains("Range")))
            throw new IllegalArgumentException("Expected Audi vehicle readings were not present");
        result.put("vehicle_name", name);
        result.put("vehicle_status", following(text, "Vehicle status"));
        result.put("vehicle_contact", contact);
        numeric(result, "battery", following(text, "Charge status"), "%");
        numeric(result, "range", following(text, "Range"), null);
        numeric(result, "mileage", following(text, "Mileage"), null);
        return result;
    }
    private static String following(List<String> text, String label) {
        int i = text.indexOf(label);
        if (i<0 || i+1>=text.size()) return null;
        String value=text.get(i+1);
        if (Arrays.asList("Charge status","Range","Mileage","Vehicle status").contains(value)) return null;
        return value;
    }
    private static void numeric(Map<String,Object> out, String key, String value, String unit) {
        out.put(key, null);
        if (value == null) return;
        Matcher m=Pattern.compile("^([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]+)?)\\s*(%|mi|km)$").matcher(value);
        if (!m.matches()) return;
        String actual=m.group(2);
        if (unit != null && !unit.equals(actual)) return;
        if (unit == null && !Arrays.asList("mi","km").contains(actual)) return;
        double number=Double.parseDouble(m.group(1).replace(",", ""));
        if (number < 0 || ("%".equals(actual) && number > 100)) return;
        out.put(key, number); out.put(key+"_unit", actual);
    }
}
