package ru.bogempty.client.module.visual;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import ru.bogempty.client.friend.FriendManager;
import ru.bogempty.client.module.Category;
import ru.bogempty.client.module.Module;
import ru.bogempty.client.setting.Setting;

/**
 * Direction indicators around the crosshair. Arrow direction is relative to
 * the camera/player yaw, so indicators rotate naturally when the player turns.
 */
public class Arrows extends Module {
    private final Setting.Num radius = add(new Setting.Num("Радиус", 90, 35, 220, 5),
            "Расстояние стрелок от центра экрана.");
    private final Setting.Num size = add(new Setting.Num("Размер", 8, 4, 18, 1),
            "Размер треугольников.");
    private final Setting.Num range = add(new Setting.Num("Дальность", 64, 8, 128, 4),
            "Максимальная дистанция до игрока.");
    private final Setting.Bool friends = add(new Setting.Bool("Друзья", true),
            "Показывать друзей зелёными стрелками.");
    private final Setting.Bool enemies = add(new Setting.Bool("Враги", true),
            "Показывать остальных игроков фиолетовыми стрелками.");

    public Arrows() {
        super("Arrows", "Стрелки направления на игроков вокруг прицела", Category.VISUAL);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.world == null || mc.options.hudHidden) return;

        final int cx = mc.getWindow().getScaledWidth() / 2;
        final int cy = mc.getWindow().getScaledHeight() / 2;
        final double arrowRadius = radius.get();
        final double maxDistance = range.get();
        final int arrowSize = (int) size.get();

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof PlayerEntity target) || target == mc.player
                    || !target.isAlive() || target.isSpectator()) continue;

            double distance = mc.player.distanceTo(target);
            if (distance > maxDistance) continue;

            boolean friend = FriendManager.isFriend(target.getName().getString());
            if (friend ? !friends.get() : !enemies.get()) continue;

            double dx = target.getX() - mc.player.getX();
            double dz = target.getZ() - mc.player.getZ();
            // Minecraft yaw 0 points toward +Z. Convert target bearing to screen angle.
            double bearing = Math.toDegrees(Math.atan2(dx, dz));
            double relative = Math.toRadians(bearing - mc.player.getYaw());

            double sx = cx + Math.sin(relative) * arrowRadius;
            double sy = cy - Math.cos(relative) * arrowRadius;
            double angle = relative;

            int color = friend ? 0xFF42E879 : 0xFF9B5CFF;
            drawArrow(context, sx, sy, angle, arrowSize, color);
        }
    }

    private static void drawArrow(DrawContext context, double x, double y,
                                  double angle, int size, int color) {
        // Open chevron with a dark outline and a soft purple/green halo.
        double tipX = x + Math.sin(angle) * size;
        double tipY = y - Math.cos(angle) * size;
        double backX = x - Math.sin(angle) * size * 0.72;
        double backY = y + Math.cos(angle) * size * 0.72;
        double side = size * 0.72;

        double leftX = backX + Math.cos(angle) * side;
        double leftY = backY + Math.sin(angle) * side;
        double rightX = backX - Math.cos(angle) * side;
        double rightY = backY - Math.sin(angle) * side;

        // Outer glow, outline, then the crisp colored chevron.
        line(context, tipX, tipY, leftX, leftY, (color & 0x00FFFFFF) | 0x50000000, 5);
        line(context, tipX, tipY, rightX, rightY, (color & 0x00FFFFFF) | 0x50000000, 5);
        line(context, tipX, tipY, leftX, leftY, 0xE8000000, 3);
        line(context, tipX, tipY, rightX, rightY, 0xE8000000, 3);
        line(context, tipX, tipY, leftX, leftY, color, 2);
        line(context, tipX, tipY, rightX, rightY, color, 2);
        // Small bright tip gives the arrow a cleaner, sharper finish.
        context.fill((int) Math.round(tipX) - 1, (int) Math.round(tipY) - 1,
                (int) Math.round(tipX) + 2, (int) Math.round(tipY) + 2, 0xFFFFFFFF);
    }

    private static void line(DrawContext context, double x1, double y1,
                             double x2, double y2, int color, int thickness) {
        int steps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1))));
        int half = thickness / 2;
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            int x = MathHelper.floor(x1 + (x2 - x1) * t);
            int y = MathHelper.floor(y1 + (y2 - y1) * t);
            context.fill(x - half, y - half, x - half + thickness, y - half + thickness, color);
        }
    }
}
