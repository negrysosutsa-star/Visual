package ru.bogempty.client.gps;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GpsManager {
    public static String name = null;
    public static double x, y, z;
    public static boolean active = false;

    /** ждём ответ чата после /event delay */
    public static boolean waitingEvent = false;
    private static long waitUntil = 0L;

    private static final Pattern COORDS = Pattern.compile(
            "(?:x\\s*[:=]?\\s*)?(-?\\d{1,7}(?:\\.\\d+)?)\\s*[ ,;|/]+\\s*" +
            "(?:y\\s*[:=]?\\s*)?(-?\\d{1,7}(?:\\.\\d+)?)\\s*[ ,;|/]+\\s*" +
            "(?:z\\s*[:=]?\\s*)?(-?\\d{1,7}(?:\\.\\d+)?)",
            Pattern.CASE_INSENSITIVE);

    public static void set(String n, double px, double py, double pz) {
        name = n == null || n.isBlank() ? "Точка" : n;
        x = px;
        y = py;
        z = pz;
        active = true;
        waitingEvent = false;
    }

    public static void clear() {
        active = false;
        name = null;
        waitingEvent = false;
    }

    public static void requestEvent() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.getNetworkHandler() == null) return;
        mc.getNetworkHandler().sendChatCommand("event delay");
        waitingEvent = true;
        waitUntil = System.currentTimeMillis() + 8000L;
    }

    public static void onChat(String raw) {
        if (!waitingEvent) return;
        if (System.currentTimeMillis() > waitUntil) {
            waitingEvent = false;
            return;
        }
        String plain = raw.replaceAll("§.", "");
        Matcher m = COORDS.matcher(plain);
        if (m.find()) {
            try {
                double cx = Double.parseDouble(m.group(1));
                double cy = Double.parseDouble(m.group(2));
                double cz = Double.parseDouble(m.group(3));
                set("Mistik", cx, cy, cz);
            } catch (NumberFormatException ignored) {}
        }
    }

    public static double distanceTo(Vec3d pos) {
        if (!active) return -1;
        double dx = x - pos.x;
        double dy = y - pos.y;
        double dz = z - pos.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** угол стрелки в градусах (0 = вверх экрана) относительно взгляда игрока */
    public static float arrowAngle(float playerYaw, Vec3d pos) {
        if (!active) return 0;
        double dx = x - pos.x;
        double dz = z - pos.z;
        double targetYaw = Math.toDegrees(Math.atan2(dz, dx)) - 90.0;
        return (float) wrap(targetYaw - playerYaw);
    }

    private static double wrap(double a) {
        a %= 360;
        if (a >= 180) a -= 360;
        if (a < -180) a += 360;
        return a;
    }

    public static String formatDist(double d) {
        if (d < 0) return "—";
        if (d >= 1000) return String.format(Locale.ROOT, "%.1fкм", d / 1000.0);
        return String.format(Locale.ROOT, "%dм", (int) d);
    }

    private GpsManager() {}
}