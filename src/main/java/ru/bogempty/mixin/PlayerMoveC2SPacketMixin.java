package ru.bogempty.mixin;

import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Импорты под твой проект (поменяй пути, если менеджер модулей у тебя в другом месте):
import ru.bogempty.client.module.ModuleManager;
import ru.bogempty.client.module.ai.NeuralAimAssist;

@Mixin(PlayerMoveC2SPacket.class)
public abstract class PlayerMoveC2SPacketMixin {

    // Эти поля есть только в классе LookAndOnGround, но в 1.21.4 они вынесены в абстракцию или суперкласс.
    // Если компилятор не найдет их в PlayerMoveC2SPacket, а в PlayerMoveC2SPacket$LookAndOnGround,
    // миксин нужно нацелить на конкретный класс (смотри примечание ниже).
    @Shadow @Final @Mutable protected float yaw;
    @Shadow @Final @Mutable protected float pitch;

    @Inject(method = "<init>(DDDFFZ)V", at = @At("TAIL"))
    private void onInit(double x, double y, double z, float yaw, float pitch, boolean onGround, CallbackInfo ci) {
        // Получаем модуль
        NeuralAimAssist aim = ModuleManager.get(NeuralAimAssist.class);
        if (aim != null && aim.isEnabled()) {
            float[] out = new float[2];
            if (aim.getSilentAngles(out)) {
                // Подменяем поля на те, что посчитал аим
                this.yaw = out[0];
                this.pitch = out[1];
            }
        }
    }
}