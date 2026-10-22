package ru.bogempty.client.module.visual;

import ru.bogempty.client.friend.FriendManager;
import ru.bogempty.client.module.Category;
import ru.bogempty.client.module.Module;
import ru.bogempty.client.render.Fonts;
import ru.bogempty.client.render.RenderUtils;
import ru.bogempty.client.setting.Setting;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class Nametags extends Module {

    private final Setting.Bool showHp = add(new Setting.Bool("ХП", true), "Показывать здоровье.");
    private final Setting.Bool showDist = add(new Setting.Bool("Дистанция", true), "Метры до игрока.");
    private final Setting.Bool showArmor = add(new Setting.Bool("Броня", true), "Очки брони.");
    private final Setting.Bool hideFriends = add(new Setting.Bool("Скрыть друзей", false), "Не рисовать друзей.");
    private final Setting.Num range = add(new Setting.Num("Дистанция", 64, 16, 128, 4), "Дальность.");
    private final Setting.Num scale = add(new Setting.Num("Масштаб", 1.0, 0.6, 1.6, 0.05), "Размер текста.");

    public Nametags() {
        super("Nametags", "Ники игроков с ХП и дистанцией", Category.VISUAL);
    }

    @Override
    public void onRender2D(DrawContext ctx, float tickDelta) {
        if (mc.player == null || mc.world == null || mc.gameRenderer == null) return;

        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        float camYaw = mc.gameRenderer.getCamera().getYaw();
        float camPitch = mc.gameRenderer.getCamera().getPitch();
        Vec3d cam = mc.gameRenderer.getCamera().getPos();
        double fovHalf = Math.toRadians(mc.options.getFov().getValue() * 0.5);
        double maxD = range.get();
        float sc = (float) scale.get();

        for (Entity e : mc.world.getPlayers()) {
            if (!(e instanceof AbstractClientPlayerEntity pl) || pl == mc.player) continue;
            if (!pl.isAlive()) continue;
            if (hideFriends.get() && FriendManager.isFriend(pl.getName().getString())) continue;

            double dist = mc.player.distanceTo(pl);
            if (dist > maxD || dist < 1.2) continue;

            Vec3d pos = pl.getLerpedPos(tickDelta).add(0, pl.getHeight() + 0.35, 0);
            double x = pos.x - cam.x;
            double y = pos.y - cam.y;
            double z = pos.z - cam.z;

            double cos = Math.cos(Math.toRadians(-camYaw - 90));
            double sin = Math.sin(Math.toRadians(-camYaw - 90));
            double rx = x * cos + z * sin;
            double rz = -(x * sin) + z * cos;
            if (rz < 1.0) continue;

            double ry = y - Math.tan(Math.toRadians(camPitch)) * rz;
            double proj = (sh * 0.5) / (Math.tan(fovHalf) * rz);
            int sx = (int) (sw * 0.5 + rx * proj);
            int sy = (int) (sh * 0.5 - ry * proj);

            if (sx < -40 || sx > sw + 40 || sy < -20 || sy > sh + 20) continue;

            boolean friend = FriendManager.isFriend(pl.getName().getString());
            StringBuilder sb = new StringBuilder();
            if (friend) sb.append("§a");
            else sb.append("§f");
            sb.append(pl.getName().getString());
            if (showDist.get()) sb.append(" §7").append((int) dist).append("m");
            if (showHp.get()) {
                int hp = MathHelper.ceil(pl.getHealth() + pl.getAbsorptionAmount());
                sb.append(" §c").append(hp).append("❤");
            }
            if (showArmor.get()) {
                int arm = pl.getArmor();
                if (arm > 0) sb.append(" §b").append(arm).append("⚒");
            }

            String label = sb.toString();
            int tw = (int) (Fonts.width(label) * sc);
            int th = (int) (10 * sc);
            int bx = sx - tw / 2 - 3;
            int by = sy - th - 2;

            RenderUtils.drawRoundedRect(ctx, bx, by, bx + tw + 6, by + th + 4, 3, 0x990A0A12);
            // лёгкий scale через matrix если Fonts не скейлит — рисуем как есть
            Fonts.draw(ctx, label, sx - Fonts.width(label) / 2, by + 2, 0xFFFFFFFF);
        }
    }
}