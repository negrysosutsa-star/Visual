package ru.bogempty.client.module.ai;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import ru.bogempty.client.ai.AiBrain;
import ru.bogempty.client.module.Category;
import ru.bogempty.client.module.Module;
import ru.bogempty.client.render.Fonts;
import ru.bogempty.client.render.RenderUtils;
import ru.bogempty.client.setting.Setting;
import ru.bogempty.client.util.SprintResetter;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public class AiTriggerBot extends Module {
    private final Setting.Mode mode = add(new Setting.Mode("Режим", "Бой + обучение", "Только бой", "Только обучение"),
            "Бой + обучение: бьёт сам и учится на тебе и на своих ударах. Только бой: бьёт, но НЕ учится. Только обучение: сам не бьёт, учится на тебе.");
    private final Setting.Mode targets = add(new Setting.Mode("Цели", "Все", "Игроки", "Мобы"),
            "Каких существ атаковать.");
    private final Setting.Mode crits = add(new Setting.Mode("Криты", "Умные", "Только криты", "Не ждать"),
            "Умные: в падении бьёт критом, на земле обычным, в подъёме ждёт. Только криты: бьёт лишь в падении. Не ждать: бьёт сразу в любой ситуации.");
    private final Setting.Num reach = add(new Setting.Num("Дистанция", 3.0, 1.5, 3.0, 0.1),
            "Максимальная дистанция удара.");
    private final Setting.Num threshold = add(new Setting.Num("Порог уверенности %", 60, 30, 95, 1),
            "Бьёт, только если модель этой ситуации уверена не меньше этого процента.");
    private final Setting.Num minCd = add(new Setting.Num("Мин. заряд %", 70, 30, 100, 1),
            "Минимальный заряд для удара не в падении. Дальше бот берёт заряд, с которым бьёшь ты.");
    private final Setting.Bool safety = add(new Setting.Bool("Страховка", true),
            "Чистое падение с полным зарядом и почти полный заряд на земле бьёт даже при сомнении модели.");
    private final Setting.Bool dropSprint = add(new Setting.Bool("Спринт стоп перед критом", true),
            "Со спринтом крита нет. Перед ударом в падении бот отпускает спринт, и удар становится критом.");
    private final Setting.Bool autoJump = add(new Setting.Bool("Авто-прыжок", false),
            "Сам прыгает при заряженном оружии и бьёт критом на спуске.");
    private final Setting.Mode sprintMode = add(new Setting.Mode("Сброс спринта", "После удара", "Выкл"),
            "После удара ненадолго отпускает «вперёд» (W-tap), чтобы спринт начался заново.");
    private final Setting.Num sprintTicks = add(new Setting.Num("Пауза сброса, тики", 1, 1, 3, 1),
            "На сколько тиков отпустить «вперёд» при сбросе спринта.");
    private final Setting.Num delayMin = add(new Setting.Num("Реакция от, мс", 0, 0, 200, 5),
            "Минимальная задержка реакции перед ударом.");
    private final Setting.Num delayMax = add(new Setting.Num("Реакция до, мс", 40, 0, 300, 5),
            "Максимальная задержка реакции (случайно между «от» и «до»). В последний тик крита задержки нет.");
    private final Setting.Num lr = add(new Setting.Num("Скорость обучения", 0.12, 0.02, 0.5, 0.01),
            "Как резко модель меняется после каждого примера. С опытом снижается сама.");
    private final Setting.Num imitation = add(new Setting.Num("Влияние твоих ударов %", 70, 0, 100, 5),
            "Насколько сильно бот копирует твой стиль: когда и в какой ситуации ты бьёшь.");
    private final Setting.Bool selfLearn = add(new Setting.Bool("Самообучение", true),
            "Бот оценивает свои удары (попал, полный заряд, крит) и учится на них.");
    private final Setting.Bool hud = add(new Setting.Bool("Панель статистики", true),
            "Счётчик обучения по ситуациям, точность и причина, почему бот сейчас не бьёт.");
    private final Setting.Bool reset = add(new Setting.Bool("Сбросить обучение", false),
            "Включи, чтобы стереть весь опыт и начать заново (переключатель вернётся сам).");

    private final AiBrain brain = AiBrain.get();

    private boolean prevAttack = false;
    private double[] lastFeat = null;
    private int lastSit = AiBrain.GROUND;
    private int sitNow = AiBrain.GROUND;
    private double lastProb = 0.0;
    private long tick = 0L;
    private long lastUserHit = -1000L;
    private int groundTicks = 0;
    private boolean armed = false;
    private long fireAt = 0L;
    private boolean jumpHeld = false;
    private boolean landedSeen = false;
    private String why = "—";

    private LivingEntity pTarget = null;
    private double[] pFeat = null;
    private int pSit = 0;
    private long pTick = 0L;
    private int pHurt0 = 0;

    private long shownSamples = -1L;
    private float flash = 0f;

    public AiTriggerBot() {
        super("AiTriggerBot", "Триггербот с самообучением по ситуациям", Category.AI);
    }

    @Override
    public void onDisable() {
        releaseJump();
        lastFeat = null;
        pTarget = null;
        armed = false;
        brain.save();
    }

    /** Земля / подъём / падение / "нельзя критовать" (вода, лестница, транспорт, слепота, полёт). */
    private int situation(ClientPlayerEntity p) {
        boolean noCrit = p.isTouchingWater() || p.isClimbing() || p.hasVehicle() || p.isFallFlying()
                || p.getAbilities().flying || p.hasStatusEffect(StatusEffects.BLINDNESS);
        if (noCrit) return AiBrain.OTHER;
        if (p.isOnGround()) return AiBrain.GROUND;
        if (p.fallDistance > 0.0 || p.getVelocity().y < -0.01) return AiBrain.FALL;
        return AiBrain.RISE;
    }

    @Override
    public void onTick() {
        tick++;
        if (reset.get()) {
            reset.value = false;
            brain.reset();
            lastFeat = null;
            pTarget = null;
            landedSeen = false;
        }

        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.interactionManager == null) {
            releaseJump();
            lastFeat = null;
            pTarget = null;
            why = "Нет мира";
            return;
        }

        groundTicks = p.isOnGround() ? groundTicks + 1 : 0;
        sitNow = situation(p);

        boolean pressed = mc.options.attackKey.isPressed();
        boolean userClick = pressed && !prevAttack && mc.currentScreen == null;
        prevAttack = pressed;

        boolean fight = !mode.is("Только обучение");
        boolean learnUser = !mode.is("Только бой");
        boolean learnSelf = mode.is("Бой + обучение") && selfLearn.get();
        double lrv = lr.get();
        double imi = imitation.get() / 100.0;

        evalPending(learnSelf, lrv);

        if (mc.currentScreen != null) {
            releaseJump();
            lastFeat = null;
            armed = false;
            why = "Открыт экран";
            return;
        }

        if (jumpHeld && !p.isOnGround()) releaseJump();

        LivingEntity t = findTarget(p);
        if (t == null) {
            lastFeat = null;
            lastProb = 0.0;
            armed = false;
            releaseJump();
            return;
        }

        int sit = sitNow;
        double dist = mc.crosshairTarget.getPos().distanceTo(p.getEyePos());
        double[] x = features(p, t, dist, sit);

        // твой удар: пример для той ситуации, в которой ты был ДО удара
        if (userClick) {
            if (learnUser && lastFeat != null) {
                if (imi > 0) brain.train(lastSit, lastFeat, 1.0, imi, lrv);
                brain.noteUserHit(lastSit, lastFeat[1]);
            }
            lastUserHit = tick;
            lastFeat = x;
            lastSit = sit;
            why = "Ты бьёшь сам (" + AiBrain.NAMES[lastSit] + ")";
            return;
        }

        lastProb = brain.predict(sit, x);
        lastFeat = x;
        lastSit = sit;

        if (!fight) {
            why = "Режим: только обучение";
            // ждёт вместе с тобой: когда ты в бою и не бьёшь, это пример "не бить сейчас"
            if (learnUser && imi > 0 && tick - lastUserHit < 40 && tick - lastUserHit > 2) {
                brain.train(sit, x, 0.0, 0.05 * imi, lrv);
            }
            return;
        }

        if (mc.interactionManager.isBreakingBlock() || p.isUsingItem()) {
            armed = false;
            why = "Занят (блок/предмет)";
            return;
        }

        boolean noWait = crits.is("Не ждать");
        boolean onlyCrit = crits.is("Только криты");
        boolean heavyHurt = t.hurtTime > 2;
        double cdv = x[1];
        boolean land = sit == AiBrain.FALL && x[5] > 0.5;

        // авто-прыжок: на земле заряженным прыгаем и бьём критом на спуске
        if (autoJump.get() && sit == AiBrain.GROUND) {
            if (cdv >= 0.85 && !jumpHeld && !mc.options.jumpKey.isPressed()) {
                mc.options.jumpKey.setPressed(true);
                jumpHeld = true;
                why = "Прыгаю ради крита";
            } else {
                why = "Авто-прыжок: жду заряд";
            }
            armed = false;
            return;
        }

        if (sit == AiBrain.RISE && !noWait) {
            armed = false;
            why = "Подъём: жду падение (крит)";
            return;
        }
        if (sit == AiBrain.GROUND && onlyCrit) {
            armed = false;
            why = "Только криты: прыгни или включи авто-прыжок";
            return;
        }
        if (sit == AiBrain.GROUND && !noWait && x[9] > 0.5) {
            armed = false;
            why = "Прыжок зажат: жду крит";
            return;
        }

        // минимальный заряд: для крита нужно больше 90%, иначе твой привычный
        double gate;
        if (sit == AiBrain.FALL) {
            gate = 0.91;
        } else {
            gate = minCd.get() / 100.0;
            if (brain.userHitsBy[sit] >= 5) gate = Math.max(gate, Math.min(0.98, brain.cdEma[sit] - 0.03));
        }
        if (cdv < gate) {
            armed = false;
            why = (sit == AiBrain.FALL ? "Падаю, жду заряд " : "Заряд ") + Math.round(cdv * 100) + "% < " + Math.round(gate * 100) + "%";
            return;
        }

        boolean forced = false;
        if (safety.get() && !heavyHurt) {
            if (sit == AiBrain.FALL) forced = cdv >= 0.93 && x[4] > 0.1;
            else if (sit != AiBrain.RISE) forced = cdv >= 0.97;
        }
        if (land) forced = true; // последний шанс на крит

        double thr = threshold.get() / 100.0;
        if (lastProb < thr && !forced) {
            armed = false;
            why = AiBrain.NAMES[sit] + ": модель не уверена " + Math.round(lastProb * 100) + "% < " + Math.round(thr * 100) + "%";
            return;
        }

        long now = System.currentTimeMillis();
        if (!armed) {
            armed = true;
            int lo = (int) delayMin.get();
            int hi = Math.max(lo, (int) delayMax.get());
            fireAt = now + (hi > 0 ? ThreadLocalRandom.current().nextInt(lo, hi + 1) : 0);
        }
        if (land) fireAt = now;
        if (now < fireAt) {
            why = "Реакция…";
            return;
        }

        // крит невозможен со спринтом: отпускаем его перед ударом и говорим об этом серверу
        if (sit == AiBrain.FALL && dropSprint.get() && p.isSprinting()) {
            p.setSprinting(false);
            if (mc.getNetworkHandler() != null) {
                mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(p, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
            }
        }

        mc.interactionManager.attackEntity(p, t);
        p.swingHand(Hand.MAIN_HAND);
        releaseJump();
        armed = false;
        brain.botHits++;
        why = "Удар! (" + AiBrain.NAMES[sit] + ")";

        if (sprintMode.is("После удара")) SprintResetter.trigger((int) sprintTicks.get());

        pTarget = t;
        pFeat = x.clone();
        pSit = sit;
        pTick = tick;
        pHurt0 = t.hurtTime;
    }

    // итог собственного удара: попал ли, с полным ли зарядом, был ли это крит
    private void evalPending(boolean learnSelf, double lrv) {
        if (pTarget == null || pFeat == null) return;
        long age = tick - pTick;
        boolean dead = !pTarget.isAlive();
        boolean landed = pTarget.hurtTime > 0 && pHurt0 == 0;
        if (!landed && !dead && age < 8) return;
        if (pHurt0 > 0 && !dead) {
            pTarget = null;
            pFeat = null;
            return;
        }
        if (dead || landed) landedSeen = true;
        if (!landedSeen) {
            pTarget = null;
            pFeat = null;
            return;
        }
        boolean good;
        double weight;
        if (dead || landed) {
            good = pFeat[1] > 0.9 && pSit != AiBrain.RISE;
            weight = good ? (pSit == AiBrain.FALL ? 1.4 : 1.0) : 0.9;
        } else {
            good = false;
            weight = 0.7;
        }
        if (good) brain.botGood++;
        else brain.botBad++;
        if (learnSelf) brain.train(pSit, pFeat, good ? 1.0 : 0.0, weight, lrv);
        pTarget = null;
        pFeat = null;
    }

    private LivingEntity findTarget(ClientPlayerEntity p) {
        HitResult hr = mc.crosshairTarget;
        if (!(hr instanceof EntityHitResult ehr)) {
            why = "Нет цели в прицеле";
            return null;
        }
        if (!(ehr.getEntity() instanceof LivingEntity le)) {
            why = "В прицеле не живое существо";
            return null;
        }
        if (le == p || !le.isAlive() || le instanceof ArmorStandEntity || le.isSpectator()) {
            why = "Цель не подходит";
            return null;
        }
        boolean isPlayer = le instanceof PlayerEntity;
        if (targets.is("Игроки") && !isPlayer) {
            why = "Цель не игрок (смени «Цели»)";
            return null;
        }
        if (targets.is("Мобы") && !(le instanceof MobEntity)) {
            why = "Цель не моб (смени «Цели»)";
            return null;
        }
        double d = hr.getPos().distanceTo(p.getEyePos());
        if (d > reach.get() + 0.05) {
            why = "Далеко: " + String.format(Locale.ROOT, "%.1f", d);
            return null;
        }
        return le;
    }

    private double[] features(ClientPlayerEntity p, LivingEntity t, double dist, int sit) {
        double cd = Math.max(0.0, Math.min(1.0, p.getAttackCooldownProgress(0.5f)));
        double vy = p.getVelocity().y;

        boolean landing = false;
        if (sit == AiBrain.FALL && vy < 0.0 && mc.world != null) {
            landing = !mc.world.isSpaceEmpty(p, p.getBoundingBox().stretch(0.0, vy, 0.0));
        }

        double[] x = new double[AiBrain.N];
        x[0] = 1.0;
        x[1] = cd;
        x[2] = cd > 0.9 ? 1.0 : 0.0;
        x[3] = dist / 6.0;
        x[4] = Math.max(0.0, Math.min(1.0, -vy / 0.9));
        x[5] = landing ? 1.0 : 0.0;
        x[6] = Math.min(t.hurtTime, 10) / 10.0;
        x[7] = p.isSprinting() ? 1.0 : 0.0;
        x[8] = Math.min(groundTicks, 6) / 6.0;
        x[9] = mc.options.jumpKey.isPressed() ? 1.0 : 0.0;
        return x;
    }

    private void releaseJump() {
        if (jumpHeld) {
            if (mc.options != null) mc.options.jumpKey.setPressed(false);
            jumpHeld = false;
        }
    }

    // ---------- панель на экране ----------
    @Override
    public void onRender2D(DrawContext c, float tickDelta) {
        if (!hud.get() || mc.player == null) return;

        if (brain.samples != shownSamples) {
            if (brain.samples > shownSamples && shownSamples >= 0) flash = 1f;
            shownSamples = brain.samples;
        }
        flash *= 0.93f;

        int x = 8;
        int y = mc.getWindow().getScaledHeight() / 2 - 70;
        int w = 160, h = 168;
        RenderUtils.drawRoundedRect(c, x, y, x + w, y + h, 6, 0xAA0C0C14);
        Fonts.draw(c, "ИИ ТриггерБот", x + 8, y + 6, 0xFFFFFFFF);
        int modeCol = mode.is("Только бой") ? 0xFFE0A050 : 0xFF8F86FF;
        Fonts.draw(c, mode.get() + (mode.is("Только бой") ? "  (не учится)" : ""), x + 8, y + 17, modeCol);

        String big = String.valueOf(brain.samples);
        MatrixStack ms = c.getMatrices();
        ms.push();
        ms.translate(x + 8, y + 30, 0);
        ms.scale(2.0f, 2.0f, 1f);
        Fonts.draw(c, big, 0, 0, RenderUtils.lerp(0xFFFFFFFF, 0xFF4CE08A, flash));
        ms.pop();
        Fonts.draw(c, "обучений", x + 8 + Fonts.width(big) * 2 + 6, y + 40, 0xFF6E6E86);

        Fonts.draw(c, "Твои удары: " + brain.userHits + "   Свои: " + brain.botHits, x + 8, y + 52, 0xFFB8B8D0);
        Fonts.draw(c, "Земля " + brain.userHitsBy[AiBrain.GROUND] + "   Падение " + brain.userHitsBy[AiBrain.FALL],
                x + 8, y + 63, 0xFFB8B8D0);
        Fonts.draw(c, "Подъём " + brain.userHitsBy[AiBrain.RISE] + "   Вода/лестн. " + brain.userHitsBy[AiBrain.OTHER],
                x + 8, y + 74, 0xFFB8B8D0);
        String acc = (brain.botGood + brain.botBad) == 0 ? "—" : Math.round(brain.accuracy() * 100) + "%";
        Fonts.draw(c, "Точность своих ударов: " + acc, x + 8, y + 85, 0xFFB8B8D0);

        bar(c, x + 8, y + 100, w - 16, brain.maturity(), "Обученность");
        bar(c, x + 8, y + 116, w - 16, lastProb, "Уверенность");

        Fonts.draw(c, "Сейчас: " + AiBrain.NAMES[sitNow], x + 8, y + 134, 0xFF8F86FF);
        Fonts.draw(c, why, x + 8, y + 148, 0xFFE0C070);
    }

    private void bar(DrawContext c, int x, int y, int w, double v, String label) {
        float f = (float) Math.max(0.0, Math.min(1.0, v));
        Fonts.draw(c, label, x, y - 1, 0xFF6E6E86);
        String pct = Math.round(f * 100) + "%";
        Fonts.draw(c, pct, x + w - Fonts.width(pct), y - 1, 0xFF8F86FF);
        RenderUtils.drawRoundedRect(c, x, y + 9, x + w, y + 12, 1, 0xFF22222E);
        RenderUtils.drawRoundedRect(c, x, y + 9, x + Math.max(2, Math.round(w * f)), y + 12, 1, 0xFF7C6CFF);
    }
}