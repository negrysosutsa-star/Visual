
package ru.bogempty.client.mixin;

import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
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
    private void bogempty$updateCamera(
            CallbackInfo ci
    ) {
        if (BogEmptyClient.moduleManager == null) {
            return;
        }

        Freecam freecam = null;

        for (Module module : BogEmptyClient.moduleManager.modules) {
            if (module instanceof Freecam) {
                freecam = (Freecam) module;
                break;
            }
        }

        if (freecam == null || !freecam.isEnabled()) {
            return;
        }

        Vec3d pos = freecam.getCameraPosition();

        if (pos == null) {
            return;
        }

        CameraAccessor accessor = (CameraAccessor) (Object) this;

        accessor.bogempty$invokeSetPos(pos.x, pos.y, pos.z);
        accessor.bogempty$invokeSetRotation(
                freecam.getCameraYaw(),
                freecam.getCameraPitch()
        );
    }
}
