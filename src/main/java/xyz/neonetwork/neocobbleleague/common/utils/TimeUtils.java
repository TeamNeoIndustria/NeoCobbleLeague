package xyz.neonetwork.neocobbleleague.common.utils;

public final class TimeUtils {
    public static String formatTicks(long ticks) {
        long totalSeconds = Math.max(0, (ticks + 19) / 20);

        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        StringBuilder builder = new StringBuilder();

        if (hours > 0) {
            builder.append(hours).append("h ");
        }

        if (minutes > 0) {
            builder.append(minutes).append("m ");
        }

        if (seconds > 0 || builder.isEmpty()) {
            builder.append(seconds).append("s");
        }

        return builder.toString().trim();
    }
}