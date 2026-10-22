package ru.bogempty.client.mixin;

import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.bogempty.client.module.Module;
import ru.bogempty.client.module.ModuleManager;

@Mixin(PlayerMoveC2SPacket.class)
public class PlayerMoveC2SPacketMixin {

    @Inject(method = "<init>(DDDFFZ)V", at = @At("RETURN"))
    private void onInit(double x, double y, double z, float yaw, float pitch, boolean onGround, CallbackInfo ci) {
        Module aim = null;
        try {
            for (java.lang.reflect.Field f : ModuleManager.class.getDeclaredFields()) {
                if (java.util.List.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    Object val = f.get(null);
                    if (val == null) continue;
                    for (Object obj : (java.util.List<?>) val) {
                        if (obj instanceof Module m && obj.getClass().getSimpleName().equals("NeuralAimAssist")) {
                            aim = m;
                            break;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        if (aim != null && aim.isEnabled()) {
            try {
                java.lang.reflect.Method method = aim.getClass().getMethod("getSilentAngles", float[].class);
                float[] out = new float[2];
                if ((boolean) method.invoke(aim, (Object) out)) {
                    java.lang.reflect.Field fy = PlayerMoveC2SPacket.class.getDeclaredField("yaw");
                    java.lang.reflect.Field fp = PlayerMoveC2SPacket.class.getDeclaredField("pitch");
                    fy.setAccessible(true);
                    fp.setAccessible(true);
                    fy.set(this, out[0]);
                    fp.set(this, out[1]);
                }
            } catch (Exception ignored) {}
        }
    }
}