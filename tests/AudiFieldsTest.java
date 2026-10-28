package local.audi.widgetprobe;
import java.util.*;
import java.nio.file.*;
public class AudiFieldsTest {
    static void check(boolean value,String why) {if(!value)throw new AssertionError(why);}
    public static void main(String[] args) throws Exception {
        List<String> fixture=Files.readAllLines(Paths.get(args[0]));
        Map<String,Object> parsed=AudiFields.parse(fixture);
        check(Double.valueOf(72).equals(parsed.get("battery")),"NBSP battery");
        check(Double.valueOf(180).equals(parsed.get("range")),"range");
        check(Double.valueOf(12345).equals(parsed.get("mileage")),"comma thousands");
        check("Parked safely".equals(parsed.get("vehicle_status")),"raw vehicle status");
        check("Today, 09:15".equals(parsed.get("vehicle_contact")),"raw contact");
        List<String> km=Arrays.asList("My vehicle","Last vehicle contact: Yesterday, 09:15","Charge status","101 %","Range","250 km","Vehicle status","Unlocked","Mileage","20,000 km");
        Map<String,Object> p=AudiFields.parse(km);
        check(p.get("battery")==null,"impossible battery rejected");
        check("km".equals(p.get("range_unit")),"km unit retained");
        check("Unlocked".equals(p.get("vehicle_status")),"status never converted to boolean");
        check("Yesterday, 09:15".equals(p.get("vehicle_contact")),"no invented date");
        p=AudiFields.parse(Arrays.asList("Car","Last vehicle contact: Today, 01:00","Charge status","Range","unknown"));
        check(p.get("battery")==null && p.get("range")==null,"missing values unknown not zero");
        try {AudiFields.parse(Arrays.asList("Please log in"));throw new AssertionError("login screen accepted");}
        catch(IllegalArgumentException expected) {}
        System.out.println("PASS: synthetic fixture, NBSP, thousands, km, invalid/missing numbers, raw statuses, raw timestamps, non-data screen rejection");
    }
}
