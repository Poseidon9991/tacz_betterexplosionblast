package com.poseidon.tacz_betterexplosion.client.particle;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;

/**
 * Custom particle render types. Forge sorts types that are not in vanilla's render order after all vanilla
 * ones, so {@link #ADDITIVE} is drawn after the translucent smoke.
 */
public final class FxRenderTypes {

    /**
     * Additive glow on the particle atlas: depth test stays on (set by the engine), depth writes off,
     * blend {@code SRC_ALPHA, ONE}. {@code end} restores the vanilla blend function and depth mask so the
     * types drawn after it are unaffected.
     */
    public static final ParticleRenderType ADDITIVE = new ParticleRenderType() {
        // LOCATION_PARTICLES is deprecated in 1.20.1 but is what the vanilla render types bind.
        @SuppressWarnings("deprecation")
        @Override
        public void begin(BufferBuilder builder, TextureManager textureManager) {
            RenderSystem.depthMask(false);
            RenderSystem.setShader(GameRenderer::getParticleShader);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public void end(Tesselator tesselator) {
            tesselator.end();
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(true);
        }

        @Override
        public String toString() {
            return "tacz_betterexplosion:additive";
        }
    };

    private FxRenderTypes() {}
}
