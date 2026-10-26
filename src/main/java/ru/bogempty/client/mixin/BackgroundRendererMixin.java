package ru.bogempty.client.mixin;

import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.BackgroundRenderer.FogType;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import ru.bogempty.client.BogEmptyClient;
import ru.bogempty.client.module.Module;
import ru.bogempty.client.module.visual.NoRender;

@Mixin(BackgroundRenderer.class)
public abstract class BackgroundRendererMixin {
    @ModifyVariable(method = "applyFog", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static float visual$removeFog(float viewDistance, Camera camera, FogType fogType,
                                          Vector4f color, boolean thickFog, float tickDelta) {
        if (BogEmptyClient.moduleManager == null) return viewDistance;
        for (Module module : BogEmptyClient.moduleManager.modules) {
            if (module instanceof NoRender noRender && noRender.isFogDisabled()) {
                return 100000.0F;
            }
        }
        return viewDistance;
    }
}
