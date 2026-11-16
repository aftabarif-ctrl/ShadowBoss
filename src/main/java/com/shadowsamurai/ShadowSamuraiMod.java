package com.shadowsamurai;

import com.shadowsamurai.entity.ShadowSamuraiEntity;
import com.shadowsamurai.item.EclipseChainReaperItem;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;

public class ShadowSamuraiMod implements ModInitializer {
    public static final String MOD_ID = "shadowsamurai";

    @Override
    public void onInitialize() {
        ModEntities.init();
        ModItems.init();
        FabricDefaultAttributeRegistry.register(ModEntities.SHADOW_SAMURAI, ShadowSamuraiEntity.createAttributes());

        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.COMBAT).register(e -> e.accept(ModItems.ECLIPSE_CHAIN_REAPER));
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.SPAWN_EGGS).register(e -> e.accept(ModItems.SHADOW_SAMURAI_SPAWN_EGG));

        // Soul Harvest: every kill with the reaper charges it.
        ServerLivingEntityEvents.AFTER_DEATH.register((victim, source) -> {
            if (!(source.getEntity() instanceof ServerPlayer player) || victim instanceof Player) return;
            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack stack = player.getItemInHand(hand);
                if (stack.getItem() instanceof EclipseChainReaperItem) {
                    EclipseChainReaperItem.addSouls(stack, victim instanceof ShadowSamuraiEntity ? 25 : 1);
                    ServerLevel sl = player.serverLevel();
                    sl.sendParticles(ParticleTypes.SOUL, victim.getX(), victim.getY() + 1, victim.getZ(), 10, 0.3, 0.5, 0.3, 0.08);
                    sl.sendParticles(ParticleTypes.WITCH, player.getX(), player.getY() + 1, player.getZ(), 12, 0.5, 0.8, 0.5, 0.1);
                    break;
                }
            }
        });
    }
}
