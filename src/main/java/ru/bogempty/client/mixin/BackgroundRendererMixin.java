package ru.bogempty.client.mixin;

import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.BackgroundRenderer.FogType;
import net.minecraft.client.MinecraftClient;
import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.bogempty.client.BogEmptyClient;
import ru.bogempty.client.module.Module;
import ru.bogempty.client.module.visual.NoRender;

@Mixin(BackgroundRenderer.class)
public abstract class BackgroundRendererMixin {
    @Inject(method = "applyFog", at = @At("TAIL"))
    private static void visual$removeFog(Camera camera, FogType fogType, Vector4f color,
                                         float viewDistance, boolean thickFog, float tickDelta,
                                         CallbackInfo ci) {
        if (BogEmptyClient.moduleManager == null) return;
        for (Module module : BogEmptyClient.moduleManager.modules) {
            if (module instanceof NoRender noRender && noRender.isFogDisabled()) {
                RenderSystem.setShaderFogStart(0.0F);
                RenderSystem.setShaderFogEnd(Math.max(1024.0F, viewDistance * 100.0F));
                return;
            }
        }
    }
}
