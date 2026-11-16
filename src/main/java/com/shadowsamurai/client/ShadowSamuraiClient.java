package com.shadowsamurai.client;

import com.shadowsamurai.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class ShadowSamuraiClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityModelLayerRegistry.registerModelLayer(ShadowSamuraiModel.LAYER, ShadowSamuraiModel::createBodyLayer);
        EntityRendererRegistry.register(ModEntities.SHADOW_SAMURAI, ShadowSamuraiRenderer::new);
        EntityRendererRegistry.register(ModEntities.CHAIN_BLADE, ChainBladeRenderer::new);
    }
}
