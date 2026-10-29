package local.audi.widgetprobe;
public class RefreshScheduleTest {
    static void check(boolean value) { if (!value) throw new AssertionError(); }
    public static void main(String[] args) {
        long start = 10000000L, interval = RefreshSchedule.INTERVAL;
        check(interval == 1200000L);
        // A persisted v0.4 deadline must be shortened on upgrade.
        check(RefreshSchedule.normalize(start, start + 3600000L) == start + interval);
        long due = RefreshSchedule.normalize(start, 0);
        check(due == start + interval);
        check(!RefreshSchedule.shouldRefresh(due - 1, due, false));
        check(RefreshSchedule.shouldRefresh(due, due, false));
        check(!RefreshSchedule.shouldRefresh(due, due, true));
        // New transfer postpones the deadline; an overdue restart catches up once.
        long reset = start + interval / 2 + interval;
        check(!RefreshSchedule.shouldRefresh(due, reset, false));
        check(RefreshSchedule.shouldRefresh(due + interval, due, false));
        check(!RefreshSchedule.shouldRefresh(due + interval, due + 2 * interval, false));
        check(RefreshSchedule.normalize(start, start + 5 * interval) == start + interval);
        System.out.println("PASS: refresh deadline, transfer reset, pending suppression, restart catch-up, clock rollback");
    }
}
