
package ru.bogempty.client.module.visual;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import ru.bogempty.client.friend.FriendManager;
import ru.bogempty.client.module.Category;
import ru.bogempty.client.module.Module;
import ru.bogempty.client.render.Fonts;
import ru.bogempty.client.render.RenderUtils;
import ru.bogempty.client.setting.Setting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class Nametags extends Module {

    private final Setting.Bool showHp =
            add(new Setting.Bool("ХП", true),
                    "Показывать здоровье");

    private final Setting.Bool showBar =
            add(new Setting.Bool("Полоска ХП", true),
                    "Полоска здоровья");

    private final Setting.Bool showDist =
            add(new Setting.Bool("Дистанция", true),
                    "Расстояние до игрока");

    private final Setting.Bool showArmor =
            add(new Setting.Bool("Броня", true),
                    "Очки брони");

    private final Setting.Bool showAbsorption =
            add(new Setting.Bool("Поглощение", true),
                    "Учитывать поглощение");

    private final Setting.Bool showBackground =
            add(new Setting.Bool("Фон", true),
                    "Фон плашки");

    private final Setting.Bool showOutline =
            add(new Setting.Bool("Обводка", true),
                    "Обводка плашки");

    private final Setting.Bool hideFriends =
            add(new Setting.Bool("Скрыть друзей", false),
                    "Скрывать друзей");

    private final Setting.Num range =
            add(new Setting.Num("Дальность", 64, 8, 128, 4),
                    "Дальность отображения");

    private final Setting.Num scale =
            add(new Setting.Num("Масштаб", 1.0, 0.6, 1.8, 0.05),
                    "Размер неймтега");

    public Nametags() {
        super(
                "Nametags",
                "Ники игроков с HP и бронёй",
                Category.VISUAL
        );
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null
                || mc.world == null
                || mc.gameRenderer == null) {
            return;
        }

        Camera camera = mc.gameRenderer.getCamera();
        Vec3d cameraPos = camera.getPos();

        Quaternionf inverseRotation =
                new Quaternionf(camera.getRotation()).conjugate();

        double fov = mc.options.getFov().getValue();
        double tanHalf = Math.tan(Math.toRadians(fov) / 2.0);

        int screenWidth = mc.getWindow().getScaledWidth();
        int screenHeight = mc.getWindow().getScaledHeight();

        if (tanHalf <= 0.0 || screenHeight <= 0) {
            return;
        }

        double aspect = (double) screenWidth / screenHeight;
        double maxDistance = range.get();
        float tagScale = (float) scale.get();

        List<PlayerEntity> players = new ArrayList<>();

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || !player.isAlive()) {
                continue;
            }

            if (mc.player.distanceTo(player) > maxDistance) {
                continue;
            }

            players.add(player);
        }

        // Сначала дальние игроки, потом ближние.
        players.sort((a, b) -> Float.compare(
                mc.player.distanceTo(b),
                mc.player.distanceTo(a)
        ));

        for (PlayerEntity player : players) {
            String name = player.getName().getString();
            boolean friend = FriendManager.isFriend(name);

            if (friend && hideFriends.get()) {
                continue;
            }

            float distance = mc.player.distanceTo(player);

            Vec3d interpolated = player.getLerpedPos(tickDelta);

            Vector3f relative = new Vector3f(
                    (float) (interpolated.x - cameraPos.x),
                    (float) (interpolated.y
                            + player.getHeight() + 0.30
                            - cameraPos.y),
                    (float) (interpolated.z - cameraPos.z)
            );

            inverseRotation.transform(relative);

            double depth = -relative.z;

            if (depth < 0.1) {
                continue;
            }

            double ndcX = relative.x / (depth * tanHalf * aspect);
            double ndcY = relative.y / (depth * tanHalf);

            if (Math.abs(ndcX) > 1.3 || Math.abs(ndcY) > 1.3) {
                continue;
            }

            int screenX = (int) ((ndcX * 0.5 + 0.5) * screenWidth);
            int screenY = (int) ((0.5 - ndcY * 0.5) * screenHeight);

            float health = Math.max(0, player.getHealth());
            float absorption = player.getAbsorptionAmount();
            float maxHealth = Math.max(1, player.getMaxHealth());

            float healthRatio = Math.max(
                    0,
                    Math.min(1, health / maxHealth)
            );

            float displayedHealth = health
                    + (showAbsorption.get() ? absorption : 0);

            int healthColor = RenderUtils.lerp(
                    0xFFE5484D,
                    0xFF4CE08A,
                    healthRatio
            );

            String healthText = String.format(
                    Locale.ROOT,
                    "%.1f",
                    displayedHealth
            );

            String distanceText = String.format(
                    Locale.ROOT,
                    "%.0fm",
                    distance
            );

            int armor = player.getArmor();
            String armorText = String.valueOf(armor);

            int gap = 5;
            int nameWidth = Fonts.width(name);
            int totalWidth = nameWidth;

            if (showHp.get()) {
                totalWidth += gap + Fonts.width(healthText);
            }

            if (showDist.get()) {
                totalWidth += gap + Fonts.width(distanceText);
            }

            if (showArmor.get() && armor > 0) {
                totalWidth += gap + Fonts.width(armorText);
            }

            int panelWidth = totalWidth + 10;
            int left = -panelWidth / 2;
            int top = -17;

            var matrices = context.getMatrices();

            matrices.push();
            matrices.translate(screenX, screenY, 0);
            matrices.scale(tagScale, tagScale, 1.0f);

            if (showBackground.get()) {
                RenderUtils.drawRoundedRect(
                        context,
                        left,
                        top,
                        left + panelWidth,
                        top + 13,
                        3,
                        0xCC0A0A12
                );
            }

            if (showOutline.get()) {
                RenderUtils.drawRect(
                        context,
                        left - 1,
                        top - 1,
                        left + panelWidth + 1,
                        top,
                        0x66FFFFFF
                );

                RenderUtils.drawRect(
                        context,
                        left - 1,
                        top + 13,
                        left + panelWidth + 1,
                        top + 14,
                        0x66FFFFFF
                );
            }

            int textX = left + 5;

            Fonts.draw(
                    context,
                    name,
                    textX,
                    top + 2,
                    friend ? 0xFF4CE08A : 0xFFFFFFFF
            );

            textX += nameWidth;

            if (showHp.get()) {
                textX += gap;

                Fonts.draw(
                        context,
                        healthText,
                        textX,
                        top + 2,
                        healthColor
                );

                textX += Fonts.width(healthText);
            }

            if (showDist.get()) {
                textX += gap;

                Fonts.draw(
                        context,
                        distanceText,
                        textX,
                        top + 2,
                        0xFFB0B0C0
                );

                textX += Fonts.width(distanceText);
            }

            if (showArmor.get() && armor > 0) {
                textX += gap;

                Fonts.draw(
                        context,
                        armorText,
                        textX,
                        top + 2,
                        0xFF6CD0FF
                );
            }

            if (showBar.get()) {
                int barY = top + 15;

                RenderUtils.drawRoundedRect(
                        context,
                        left,
                        barY,
                        left + panelWidth,
                        barY + 3,
                        1,
                        0xCC0A0A12
                );

                int filledWidth = Math.round(
                        panelWidth * healthRatio
                );

                if (filledWidth > 0) {
                    RenderUtils.drawRoundedRect(
                            context,
                            left,
                            barY,
                            left + filledWidth,
                            barY + 3,
                            1,
                            healthColor
                    );
                }

                if (showAbsorption.get() && absorption > 0) {
                    int absorptionWidth = Math.min(
                            panelWidth,
                            Math.round(
                                    panelWidth * Math.min(
                                            1f,
                                            absorption / maxHealth
                                    )
                            )
                    );

                    if (absorptionWidth > 0) {
                        RenderUtils.drawRect(
                                context,
                                left,
                                barY + 3,
                                left + absorptionWidth,
                                barY + 4,
                                0xFFFFC83D
                        );
                    }
                }
            }

            matrices.pop();
        }
    }
}
