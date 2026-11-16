package com.shadowsamurai;

import com.shadowsamurai.item.EclipseChainReaperItem;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.ItemAttributeModifiers;

public class ModItems {
    public static final Item ECLIPSE_CHAIN_REAPER = Registry.register(BuiltInRegistries.ITEM, ModEntities.id("eclipse_chain_reaper"),
            new EclipseChainReaperItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()
                    .component(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder()
                            .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, 10.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                            .add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, -2.6, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                            .build())));

    /** Render-only item used for the Eclipse Hook's cursed ring. */
    public static final Item ECLIPSE_RING = Registry.register(BuiltInRegistries.ITEM, ModEntities.id("eclipse_ring"), new Item(new Item.Properties()));

    public static final Item ECLIPSE_BLADE = Registry.register(BuiltInRegistries.ITEM, ModEntities.id("eclipse_blade"), new Item(new Item.Properties()));

    public static final Item SHADOW_SAMURAI_SPAWN_EGG = Registry.register(BuiltInRegistries.ITEM, ModEntities.id("shadow_samurai_spawn_egg"),
            new SpawnEggItem(ModEntities.SHADOW_SAMURAI, 0x1B1128, 0xA020F0, new Item.Properties()));

    public static void init() {}
}
