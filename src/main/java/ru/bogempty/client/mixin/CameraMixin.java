
package ru.bogempty.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import ru.bogempty.client.BogEmptyClient;
import ru.bogempty.client.module.Module;
import ru.bogempty.client.module.visual.Freecam;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Inject(method = "update", at = @At("TAIL"))
    private void bogempty$freecam(CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.player == null || BogEmptyClient.moduleManager == null) {
            return;
        }

        for (Module module : BogEmptyClient.moduleManager.modules) {
            if (!(module instanceof Freecam freecam) || !freecam.isEnabled()) {
                continue;
            }

            if (freecam.getCameraPosition() == null) {
                return;
            }

            Camera camera = (Camera) (Object) this;

            camera.setPos(
                    freecam.getCameraPosition().x,
                    freecam.getCameraPosition().y,
                    freecam.getCameraPosition().z
            );

            camera.setRotation(
                    freecam.getCameraYaw(),
                    freecam.getCameraPitch()
            );

            return;
        }
    }
}
