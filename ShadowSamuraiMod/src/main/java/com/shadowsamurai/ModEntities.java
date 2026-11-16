package com.shadowsamurai;

import com.shadowsamurai.entity.ChainBladeProjectile;
import com.shadowsamurai.entity.ShadowSamuraiEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public class ModEntities {
    public static final EntityType<ShadowSamuraiEntity> SHADOW_SAMURAI = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, id("shadow_samurai"),
            EntityType.Builder.of(ShadowSamuraiEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F).fireImmune().clientTrackingRange(12).build("shadow_samurai"));

    public static final EntityType<ChainBladeProjectile> CHAIN_BLADE = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, id("chain_blade"),
            EntityType.Builder.<ChainBladeProjectile>of(ChainBladeProjectile::new, MobCategory.MISC)
                    .sized(0.8F, 0.8F).noSave().fireImmune().clientTrackingRange(10).updateInterval(1).build("chain_blade"));

    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(ShadowSamuraiMod.MOD_ID, path); }
    public static void init() {}
}
