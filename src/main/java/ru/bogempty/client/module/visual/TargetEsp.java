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
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

public class TargetEsp extends Module {

    private final Setting.Bool boxes = add(new Setting.Bool("2D боксы", true), "Рамки на игроках.");
    private final Setting.Bool corners = add(new Setting.Bool("Уголки", true), "Вместо полной рамки — углы.");
    private final Setting.Bool names = add(new Setting.Bool("Ники", true), "Имя + дистанция.");
    private final Setting.Bool hp = add(new Setting.Bool("ХП бар", true), "Полоска здоровья.");
    private final Setting.Bool armor = add(new Setting.Bool("Броня", true), "Кол-во брони.");
    private final Setting.Bool hideFriends = add(new Setting.Bool("Скрыть друзей", false), "Не рисовать друзей.");
    private final Setting.Num range = add(new Setting.Num("Дистанция", 64, 16, 128, 4), "Дальность ESP.");
    private final Setting.Num color = add(new Setting.Num("Цвет RRGGBB", 0x7C6CFF, 0, 0xFFFFFF, 1),
            "Базовый цвет (десятичный).");

    public TargetEsp() {
        super("TargetEsp", "2D ESP и неймтеги", Category.VISUAL);
    }

    @Override
    public void onRender2D(DrawContext ctx, float tickDelta) {
        if (mc.player == null || mc.world == null || mc.gameRenderer == null) return;

        int base = 0xFF000000 | ((int) color.get() & 0xFFFFFF);
        double maxD = range.get();

        for (Entity e : mc.world.getPlayers()) {
            if (!(e instanceof AbstractClientPlayerEntity pl) || pl == mc.player) continue;
            if (hideFriends.get() && FriendManager.isFriend(pl.getName().getString())) continue;

            double dist = mc.player.distanceTo(pl);
            if (dist > maxD) continue;

            // world -> screen (простая проекция через camera)
            Vec3d cam = mc.gameRenderer.getCamera().getPos();
            Vec3d pos = pl.getLerpedPos(tickDelta);
            double x = pos.x - cam.x;
            double y = pos.y - cam.y;
            double z = pos.z - cam.z;

            // грубый frustum: за спиной не рисуем
            Vec3d look = Vec3d.fromPolar(0, mc.gameRenderer.getCamera().getYaw());
            if (new Vec3d(x, 0, z).normalize().dotProduct(look) < -0.2 && dist > 4) continue;

            // screen approx via scale by distance (лёгкий 2D, без matrix hell — стабильно и не жрёт FPS)
            int sw = mc.getWindow().getScaledWidth();
            int sh = mc.getWindow().getScaledHeight();
            float yaw = mc.gameRenderer.getCamera().getYaw();
            float pitch = mc.gameRenderer.getCamera().getPitch();

            double cos = Math.cos(Math.toRadians(-yaw - 90));
            double sin = Math.sin(Math.toRadians(-yaw - 90));
            double rx = x * cos + z * sin;
            double rz = -x * sin + z * cos;
            if (rz < 0.3) continue;

            double ry = y - Math.tan(Math.toRadians(pitch)) * rz;
            double scale = (sh / 2.0) / (Math.tan(Math.toRadians(mc.options.getFov().getValue() / 2.0)) * rz);
            int sx = (int) (sw / 2.0 + rx * scale);
            int syBottom = (int) (sh / 2.0 - ry * scale);
            int h = (int) (pl.getHeight() * scale);
            int w = Math.max(8, (int) (pl.getWidth() * scale * 1.1));
            int sy = syBottom - h;
            int left = sx - w / 2;
            int right = sx + w / 2;

            if (right < 0 || left > sw || syBottom < 0 || sy > sh) continue;

            int col = FriendManager.isFriend(pl.getName().getString()) ? 0xFF4CE08A : base;

            if (boxes.get()) {
                if (corners.get()) {
                    int len = Math.max(3, w / 4);
                    // top-left
                    ctx.fill(left, sy, left + len, sy + 1, col);
                    ctx.fill(left, sy, left + 1, sy + len, col);
                    // top-right
                    ctx.fill(right - len, sy, right, sy + 1, col);
                    ctx.fill(right - 1, sy, right, sy + len, col);
                    // bottom-left
                    ctx.fill(left, syBottom - 1, left + len, syBottom, col);
                    ctx.fill(left, syBottom - len, left + 1, syBottom, col);
                    // bottom-right
                    ctx.fill(right - len, syBottom - 1, right, syBottom, col);
                    ctx.fill(right - 1, syBottom - len, right, syBottom, col);
                } else {
                    ctx.fill(left, sy, right, sy + 1, col);
                    ctx.fill(left, syBottom - 1, right, syBottom, col);
                    ctx.fill(left, sy, left + 1, syBottom, col);
                    ctx.fill(right - 1, sy, right, syBottom, col);
                }
            }

            int ty = sy - 12;
            if (names.get()) {
                String label = pl.getName().getString() + " §7" + (int) dist + "m";
                int tw = Fonts.width(label);
                Fonts.draw(ctx, label, sx - tw / 2, ty, 0xFFFFFFFF);
                ty -= 10;
            }

            if (hp.get()) {
                float health = pl.getHealth() + pl.getAbsorptionAmount();
                float max = pl.getMaxHealth();
                float ratio = MathHelper.clamp(health / max, 0f, 1f);
                int bw = w;
                int bx = left;
                int by = syBottom + 2;
                ctx.fill(bx, by, bx + bw, by + 3, 0xAA000000);
                int hpCol = ratio > 0.5f ? 0xFF4CE08A : ratio > 0.25f ? 0xFFE0C070 : 0xFFE05050;
                ctx.fill(bx, by, bx + Math.max(1, (int) (bw * ratio)), by + 3, hpCol);
            }

            if (armor.get()) {
                int a = pl.getArmor();
                if (a > 0) {
                    String ar = "§b" + a + " arm";
                    Fonts.draw(ctx, ar, left, syBottom + 6, 0xFF8F86FF);
                }
            }
        }
    }
}