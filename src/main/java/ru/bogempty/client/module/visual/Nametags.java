package ru.bogempty.client.module.visual;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import ru.bogempty.client.friend.FriendManager;
import ru.bogempty.client.module.Category;
import ru.bogempty.client.module.Module;
import ru.bogempty.client.render.Fonts;
import ru.bogempty.client.render.RenderUtils;
import ru.bogempty.client.setting.Setting;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Ники над игроками. Проекция считается по настоящей камере и настоящему FOV (со спринтом и зумом),
 * поэтому плашка стоит ровно над головой.
 */
public class Nametags extends Module {
    private final Setting.Bool showHp = add(new Setting.Bool("ХП", true), "Показывать здоровье.");
    private final Setting.Bool showBar = add(new Setting.Bool("Полоска ХП", true), "Полоска здоровья под ником.");
    private final Setting.Bool showDist = add(new Setting.Bool("Метры", true), "Расстояние до игрока.");
    private final Setting.Bool showArmor = add(new Setting.Bool("Броня", true), "Очки брони.");
    private final Setting.Bool hideFriends = add(new Setting.Bool("Скрыть друзей", false), "Не рисовать друзей.");
    private final Setting.Num range = add(new Setting.Num("Дальность", 64, 16, 128, 4), "Макс. расстояние, на котором видны ники.");
    private final Setting.Num scale = add(new Setting.Num("Масштаб", 1.0, 0.6, 1.8, 0.05), "Размер плашки.");

    private static Method fovMethod;
    private static boolean fovSearched = false;

    public Nametags() {
        super("Nametags", "Ники игроков с ХП, бронёй и дистанцией", Category.VISUAL);
    }

    /** Настоящий вертикальный FOV, как у мира (со спринтом, зельями, зумом). */
    private double currentFov(Camera cam, float tickDelta) {
        try {
            if (!fovSearched) {
                fovSearched = true;
                for (Method m : GameRenderer.class.getDeclaredMethods()) {
                    Class<?>[] pt = m.getParameterTypes();
                    if (pt.length == 3 && pt[0] == Camera.class && pt[1] == float.class && pt[2] == boolean.class
                            && (m.getReturnType() == double.class || m.getReturnType() == float.class)) {
                        m.setAccessible(true);
                        fovMethod = m;
                        break;
                    }
                }
            }
            if (fovMethod != null) {
                return ((Number) fovMethod.invoke(mc.gameRenderer, cam, tickDelta, true)).doubleValue();
            }
        } catch (Throwable ignored) {
        }
        return mc.options.getFov().getValue();
    }

    @Override
    public void onRender2D(DrawContext ctx, float tickDelta) {
        if (mc.player == null || mc.world == null || mc.gameRenderer == null) return;

        Camera cam = mc.gameRenderer.getCamera();
        Vec3d camPos = cam.getPos();
        Quaternionf inv = new Quaternionf(cam.getRotation()).conjugate();

        double fov = currentFov(cam, tickDelta);
        double tanHalf = Math.tan(Math.toRadians(fov) * 0.5);
        double aspect = (double) mc.getWindow().getFramebufferWidth() / Math.max(1, mc.getWindow().getFramebufferHeight());
        int sw = ctx.getScaledWindowWidth();
        int sh = ctx.getScaledWindowHeight();
        double maxD = range.get();
        float sc = (float) scale.get();

        List<AbstractClientPlayerEntity> list = new ArrayList<>();
        for (AbstractClientPlayerEntity pl : mc.world.getPlayers()) {
            if (pl == mc.player || !pl.isAlive()) continue;
            if (mc.player.distanceTo(pl) > maxD) continue;
            list.add(pl);
        }
        // дальних рисуем первыми, чтобы ближние были сверху
        list.sort((x, y) -> Float.compare(mc.player.distanceTo(y), mc.player.distanceTo(x)));

        for (AbstractClientPlayerEntity pl : list) {
            String name = pl.getName().getString();
            boolean friend = FriendManager.isFriend(name);
            if (friend && hideFriends.get()) continue;

            double dist = mc.player.distanceTo(pl);
            if (dist < 1.0) continue;

            Vec3d lp = pl.getLerpedPos(tickDelta);
            Vector3f v = new Vector3f(
                    (float) (lp.x - camPos.x),
                    (float) (lp.y + pl.getHeight() + 0.30 - camPos.y),
                    (float) (lp.z - camPos.z));
            inv.transform(v);
            double depth = -v.z;
            if (depth < 0.1) continue;

            double ndcX = v.x / (depth * tanHalf * aspect);
            double ndcY = v.y / (depth * tanHalf);
            if (Math.abs(ndcX) > 1.4 || Math.abs(ndcY) > 1.4) continue;

            int sx = (int) Math.round((ndcX * 0.5 + 0.5) * sw);
            int sy = (int) Math.round((0.5 - ndcY * 0.5) * sh);

            float hp = pl.getHealth() + pl.getAbsorptionAmount();
            float maxHp = Math.max(1f, pl.getMaxHealth());
            float ratio = Math.max(0f, Math.min(1f, pl.getHealth() / maxHp));
            int hpCol = RenderUtils.lerp(0xFFE5484D, 0xFF4CE08A, ratio);

            String hpStr = String.format(Locale.ROOT, "%.1f", hp);
            String distStr = (int) dist + "м";
            int arm = pl.getArmor();
            String armStr = String.valueOf(arm);

            int gap = 6;
            int nameW = Fonts.width(name);
            int total = nameW;
            if (showHp.get()) total += gap + Fonts.width(hpStr);
            if (showDist.get()) total += gap + Fonts.width(distStr);
            if (showArmor.get() && arm > 0) total += gap + Fonts.width(armStr);

            MatrixStack ms = ctx.getMatrices();
            ms.push();
            ms.translate(sx, sy, 0);
            ms.scale(sc, sc, 1f);

            int pw = total + 10;
            int x0 = -pw / 2;
            int y0 = -17;
            RenderUtils.drawRoundedRect(ctx, x0 - 1, y0 - 1, x0 + pw + 1, y0 + 14, 4, 0x33FFFFFF);
            RenderUtils.drawRoundedRect(ctx, x0, y0, x0 + pw, y0 + 13, 3, 0xCC0A0A12);

            int tx = x0 + 5;
            Fonts.draw(ctx, name, tx, y0 + 2, friend ? 0xFF4CE08A : 0xFFFFFFFF);
            tx += nameW;
            if (showHp.get()) {
                tx += gap;
                Fonts.draw(ctx, hpStr, tx, y0 + 2, hpCol);
                tx += Fonts.width(hpStr);
            }
            if (showDist.get()) {
                tx += gap;
                Fonts.draw(ctx, distStr, tx, y0 + 2, 0xFF9A9AB4);
                tx += Fonts.width(distStr);
            }
            if (showArmor.get() && arm > 0) {
                tx += gap;
                Fonts.draw(ctx, armStr, tx, y0 + 2, 0xFF6CD0FF);
            }

            if (showBar.get()) {
                int by = y0 + 15;
                RenderUtils.drawRoundedRect(ctx, x0, by, x0 + pw, by + 3, 1, 0xCC0A0A12);
                int fw = Math.max(1, Math.round(pw * ratio));
                RenderUtils.drawRoundedRect(ctx, x0, by, x0 + fw, by + 3, 1, hpCol);
                float abs = pl.getAbsorptionAmount();
                if (abs > 0f) {
                    int aw = Math.min(pw, Math.round(pw * Math.min(1f, abs / maxHp)));
                    RenderUtils.drawRect(ctx, x0, by + 3, x0 + aw, by + 4, 0xFFFFC83D);
                }
            }
            ms.pop();
        }
    }
}