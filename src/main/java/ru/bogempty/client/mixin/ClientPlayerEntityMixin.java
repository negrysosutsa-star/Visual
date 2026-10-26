
package ru.bogempty.client.mixin;

import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import ru.bogempty.client.BogEmptyClient;
import ru.bogempty.client.module.Module;
import ru.bogempty.client.module.visual.Freecam;

@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {

    @Inject(method = "tickMovement", at = @At("HEAD"), cancellable = true)
    private void bogempty$freezePlayer(CallbackInfo ci) {
        if (BogEmptyClient.moduleManager == null) {
            return;
        }

        for (Module module : BogEmptyClient.moduleManager.modules) {
            if (module instanceof Freecam && module.isEnabled()) {
                ci.cancel();
                return;
            }
        }
    }
}
