package com.shadowsamurai.client;

import com.shadowsamurai.ModEntities;
import com.shadowsamurai.entity.ShadowSamuraiEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.resources.ResourceLocation;

public class ShadowSamuraiRenderer extends HumanoidMobRenderer<ShadowSamuraiEntity, ShadowSamuraiModel> {
    private static final ResourceLocation TEXTURE = ModEntities.id("textures/entity/shadow_samurai.png");
    private static final ResourceLocation GLOW = ModEntities.id("textures/entity/shadow_samurai_glow.png");

    public ShadowSamuraiRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new ShadowSamuraiModel(ctx.bakeLayer(ShadowSamuraiModel.LAYER)), 0.7F);
        this.addLayer(new GlowLayer(this));
    }

    @Override public ResourceLocation getTextureLocation(ShadowSamuraiEntity e) { return TEXTURE; }

    /** Full-bright purple eyes + corrupted energy cracks. */
    private static class GlowLayer extends EyesLayer<ShadowSamuraiEntity, ShadowSamuraiModel> {
        private static final RenderType TYPE = RenderType.eyes(GLOW);
        GlowLayer(RenderLayerParent<ShadowSamuraiEntity, ShadowSamuraiModel> parent) { super(parent); }
        @Override public RenderType renderType() { return TYPE; }
    }
}
