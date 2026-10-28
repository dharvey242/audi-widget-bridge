package local.audi.widgetprobe;

final class RefreshSchedule {
    static final long INTERVAL = 3600000L;
    static long normalize(long now, long due) {
        return due == 0 || due > now + INTERVAL ? now + INTERVAL : due;
    }
    static boolean shouldRefresh(long now, long due, boolean pending) {
        return !pending && now >= normalize(now, due);
    }
}
