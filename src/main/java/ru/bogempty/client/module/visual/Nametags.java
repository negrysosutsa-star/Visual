
package ru.bogempty.client.module.visual;

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

import net.minecraft.class_243;
import net.minecraft.class_332;
import net.minecraft.class_4184;
import net.minecraft.class_4587;
import net.minecraft.class_742;
import net.minecraft.class_757;

public class Nametags extends Module {

    private final Setting.Bool showHp =
            add(new Setting.Bool("ХП", true), "Показывать здоровье.");

    private final Setting.Bool showBar =
            add(new Setting.Bool("Полоска ХП", true), "Полоска здоровья.");

    private final Setting.Bool showDist =
            add(new Setting.Bool("Метры", true), "Расстояние до игрока.");

    private final Setting.Bool showArmor =
            add(new Setting.Bool("Броня", true), "Очки брони.");

    private final Setting.Bool showAbsorption =
            add(new Setting.Bool("Поглощение", true), "Учитывать поглощение в ХП.");

    private final Setting.Bool showBackground =
            add(new Setting.Bool("Фон", true), "Тёмная плашка за ником.");

    private final Setting.Bool showOutline =
            add(new Setting.Bool("Обводка", true), "Обводка плашки.");

    private final Setting.Bool hideFriends =
            add(new Setting.Bool("Скрыть друзей", false), "Не показывать друзей.");

    private final Setting.Num range =
            add(new Setting.Num("Дальность", 64, 16, 128, 4),
                    "Максимальная дальность неймтегов.");

    private final Setting.Num scale =
            add(new Setting.Num("Масштаб", 1.0, 0.6, 1.8, 0.05),
                    "Размер неймтега.");

    private static Method fovMethod;
    private static boolean fovSearched = false;

    public Nametags() {
        super("Nametags", "Настраиваемые ники игроков", Category.VISUAL);
    }

    private double currentFov(class_4184 cam, float tickDelta) {
        try {
            if (!fovSearched) {
                fovSearched = true;

                for (Method m : class_757.class.getDeclaredMethods()) {
                    Class<?>[] pt = m.getParameterTypes();

                    if (pt.length == 3
                            && pt[0] == class_4184.class
                            && pt[1] == float.class
                            && pt[2] == boolean.class
                            && (m.getReturnType() == double.class
                            || m.getReturnType() == float.class)) {
                        m.setAccessible(true);
                        fovMethod = m;
                        break;
                    }
                }
            }

            if (fovMethod != null) {
                return ((Number) fovMethod.invoke(
                        mc.field_1773, cam, tickDelta, true
                )).doubleValue();
            }
        } catch (Throwable ignored) {
        }

        return mc.field_1690.method_41808().method_41753();
    }

    @Override
    public void onRender2D(class_332 ctx, float tickDelta) {
        if (mc.field_1724 == null
                || mc.field_1687 == null
                || mc.field_1773 == null) {
            return;
        }

        class_4184 cam = mc.field_1773.method_19418();
        class_243 camPos = cam.method_19326();

        Quaternionf inv =
                new Quaternionf(cam.method_23767()).conjugate();

        double fov = currentFov(cam, tickDelta);
        double tanHalf = Math.tan(Math.toRadians(fov) * 0.5);

        double aspect = (double) mc.method_22683().method_4489()
                / Math.max(1, mc.method_22683().method_4506());

        int sw = ctx.method_51421();
        int sh = ctx.method_51443();

        double maxD = range.get();
        float sc = (float) scale.get();

        List<class_742> players = new ArrayList<>();

        for (class_742 pl : mc.field_1687.method_18456()) {
            if (pl == mc.field_1724 || !pl.method_5805()) {
                continue;
            }

            if (mc.field_1724.method_5739(pl) > maxD) {
                continue;
            }

            players.add(pl);
        }

        // Сначала дальние игроки, затем ближние.
        players.sort((a, b) ->
                Float.compare(
                        mc.field_1724.method_5739(b),
                        mc.field_1724.method_5739(a)
                )
        );

        for (class_742 pl : players) {
            String name = pl.method_5477().getString();
            boolean friend = FriendManager.isFriend(name);

            if (friend && hideFriends.get()) {
                continue;
            }

            double dist = mc.field_1724.method_5739(pl);

            if (dist < 1.0) {
                continue;
            }

            class_243 pos = pl.method_30950(tickDelta);

            Vector3f v = new Vector3f(
                    (float) (pos.field_1352 - camPos.field_1352),
                    (float) (pos.field_1351 + pl.method_17682()
                            + 0.30 - camPos.field_1351),
                    (float) (pos.field_1350 - camPos.field_1350)
            );

            inv.transform(v);

            double depth = -v.z;

            if (depth < 0.1) {
                continue;
            }

            double ndcX = v.x / (depth * tanHalf * aspect);
            double ndcY = v.y / (depth * tanHalf);

            if (Math.abs(ndcX) > 1.4 || Math.abs(ndcY) > 1.4) {
                continue;
            }

            int sx = (int) Math.round((ndcX * 0.5 + 0.5) * sw);
            int sy = (int) Math.round((0.5 - ndcY * 0.5) * sh);

            float health = pl.method_6032();
            float absorption = pl.method_6067();
            float maxHealth = Math.max(1f, pl.method_6063());

            float displayedHealth = health
                    + (showAbsorption.get() ? absorption : 0f);

            float ratio = Math.max(0f, Math.min(
                    1f, health / maxHealth
            ));

            int hpColor = RenderUtils.lerp(
                    0xFFE5484D,
                    0xFF4CE08A,
                    ratio
            );

            String hpText = String.format(
                    Locale.ROOT, "%.1f", displayedHealth
            );

            String distText = (int) dist + "м";

            int armor = pl.method_6096();
            String armorText = String.valueOf(armor);

            int gap = 6;
            int nameWidth = Fonts.width(name);

            int totalWidth = nameWidth;

            if (showHp.get()) {
                totalWidth += gap + Fonts.width(hpText);
            }

            if (showDist.get()) {
                totalWidth += gap + Fonts.width(distText);
            }

            if (showArmor.get() && armor > 0) {
                totalWidth += gap + Fonts.width(armorText);
            }

            class_4587 matrices = ctx.method_51448();

            matrices.method_22903();
            matrices.method_46416(sx, sy, 0);
            matrices.method_22905(sc, sc, 1f);

            int panelWidth = totalWidth + 10;
            int x0 = -panelWidth / 2;
            int y0 = -17;

            // Фон и обводка настраиваются отдельно.
            if (showBackground.get()) {
                RenderUtils.drawRoundedRect(
                        ctx,
                        x0, y0,
                        x0 + panelWidth, y0 + 13,
                        3, 0xCC0A0A12
                );
            }

            if (showOutline.get()) {
                RenderUtils.drawRoundedRect(
                        ctx,
                        x0 - 1, y0 - 1,
                        x0 + panelWidth + 1, y0 + 14,
                        4, 0x33FFFFFF
                );
            }

            int textX = x0 + 5;

            // Ник: зелёный для друга, белый для остальных.
            Fonts.draw(
                    ctx,
                    name,
                    textX,
                    y0 + 2,
                    friend ? 0xFF4CE08A : 0xFFFFFFFF
            );

            textX += nameWidth;

            if (showHp.get()) {
                textX += gap;

                Fonts.draw(
                        ctx, hpText, textX, y0 + 2, hpColor
                );

                textX += Fonts.width(hpText);
            }

            if (showDist.get()) {
                textX += gap;

                Fonts.draw(
                        ctx,
                        distText,
                        textX,
                        y0 + 2,
                        0xFF9A9AB4
                );

                textX += Fonts.width(distText);
            }

            if (showArmor.get() && armor > 0) {
                textX += gap;

                Fonts.draw(
                        ctx,
                        armorText,
                        textX,
                        y0 + 2,
                        0xFF6CD0FF
                );
            }

            // Полоска здоровья.
            if (showBar.get()) {
                int barY = y0 + 15;

                RenderUtils.drawRoundedRect(
                        ctx,
                        x0, barY,
                        x0 + panelWidth, barY + 3,
                        1, 0xCC0A0A12
                );

                int fillWidth = Math.round(panelWidth * ratio);

                if (fillWidth > 0) {
                    RenderUtils.drawRoundedRect(
                            ctx,
                            x0, barY,
                            x0 + fillWidth, barY + 3,
                            1, hpColor
                    );
                }

                // Жёлтая полоска поглощения.
                if (showAbsorption.get() && absorption > 0f) {
                    int absorptionWidth = Math.min(
                            panelWidth,
                            Math.round(
                                    panelWidth
                                            * Math.min(
                                            1f,
                                            absorption / maxHealth
                                    )
                            )
                    );

                    if (absorptionWidth > 0) {
                        RenderUtils.drawRect(
                                ctx,
                                x0, barY + 3,
                                x0 + absorptionWidth, barY + 4,
                                0xFFFFC83D
                        );
                    }
                }
            }

            matrices.method_22909();
        }
    }
}
