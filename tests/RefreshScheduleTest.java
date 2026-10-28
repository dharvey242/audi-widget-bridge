package local.audi.widgetprobe;
public class RefreshScheduleTest {
    static void check(boolean value) { if (!value) throw new AssertionError(); }
    public static void main(String[] args) {
        long start = 10000000L, hour = RefreshSchedule.INTERVAL;
        long due = RefreshSchedule.normalize(start, 0);
        check(due == start + hour);
        check(!RefreshSchedule.shouldRefresh(due - 1, due, false));
        check(RefreshSchedule.shouldRefresh(due, due, false));
        check(!RefreshSchedule.shouldRefresh(due, due, true));
        // New transfer postpones the deadline; an overdue restart catches up once.
        long reset = start + hour / 2 + hour;
        check(!RefreshSchedule.shouldRefresh(due, reset, false));
        check(RefreshSchedule.shouldRefresh(due + hour, due, false));
        check(!RefreshSchedule.shouldRefresh(due + hour, due + 2 * hour, false));
        check(RefreshSchedule.normalize(start, start + 5 * hour) == start + hour);
        System.out.println("PASS: refresh deadline, transfer reset, pending suppression, restart catch-up, clock rollback");
    }
}
