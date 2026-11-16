package com.shadowsamurai.item;

import com.shadowsamurai.entity.ChainBladeProjectile;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The Eclipse Chain Reaper.
 *  Right-click ........ Reaper Throw  (blade flies out, drags enemies back to you)
 *  Sneak + right-click  Eclipse Pull  (ring hook, grabs one target from afar)
 *  Melee hit .......... Shadow Sweep  (wide spinning chain sweep around the target)
 *  Kills .............. Soul Harvest (each soul = more damage + more particles)
 *  Right-click looking straight down with 10+ souls: Nightmare Storm
 */
public class EclipseChainReaperItem extends Item {
    public static final int MAX_SOULS = 50, STORM_COST = 10;

    public EclipseChainReaperItem(Properties props) { super(props); }

    public static int getSouls(ItemStack s) {
        return s.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getInt("Souls");
    }
    public static void addSouls(ItemStack s, int n) {
        int v = Math.max(0, Math.min(MAX_SOULS, getSouls(s) + n));
        CustomData.update(DataComponents.CUSTOM_DATA, s, tag -> tag.putInt("Souls", v));
    }
    private static float scale(int souls) { return 1F + souls * 0.03F; }

    @Override public boolean isFoil(ItemStack s) { return true; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(stack);
        if (level instanceof ServerLevel sl) {
            int souls = getSouls(stack);
            if (player.getXRot() > 75F && souls >= STORM_COST) {
                nightmareStorm(sl, player, stack, souls);
                player.getCooldowns().addCooldown(this, 100);
            } else if (player.isShiftKeyDown()) {
                throwChain(sl, player, souls, true);
                player.getCooldowns().addCooldown(this, 40);
            } else {
                throwChain(sl, player, souls, false);
                player.getCooldowns().addCooldown(this, 25);
            }
        }
        player.swing(hand);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private void throwChain(ServerLevel sl, Player p, int souls, boolean hook) {
        ChainBladeProjectile pr = new ChainBladeProjectile(sl);
        pr.setup(p, ChainBladeProjectile.handPos(p, 1F), p.getLookAngle(),
                hook ? 2.8F : 2.1F, hook ? 32F : 22F, (hook ? 4F : 12F) * scale(souls), true, hook, souls);
        sl.addFreshEntity(pr);
        sl.playSound(null, p.getX(), p.getY(), p.getZ(), hook ? SoundEvents.CHAIN_HIT : SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, 1F, hook ? 0.8F : 1.2F);
    }

    /** Nightmare Storm: chains burst out in every direction and a shadow shockwave rolls outward. */
    private void nightmareStorm(ServerLevel sl, Player p, ItemStack stack, int souls) {
        addSouls(stack, -STORM_COST);
        int n = 8 + souls / 10 * 2;
        Vec3 origin = p.position().add(0, p.getBbHeight() * 0.5, 0);
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            ChainBladeProjectile pr = new ChainBladeProjectile(sl);
            pr.setup(p, origin, new Vec3(Math.cos(a), 0.05, Math.sin(a)), 1.7F, 11F, 9F * scale(souls), false, false, souls);
            sl.addFreshEntity(pr);
        }
        float dmg = 10F * scale(souls);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(8, 3, 8), e -> e != p && !(e instanceof ArmorStand) && p.canAttack(e) && !e.isAlliedTo(p))) {
            e.hurt(sl.damageSources().playerAttack(p), dmg);
            Vec3 push = e.position().subtract(p.position()).multiply(1, 0, 1).normalize().scale(1.2);
            e.setDeltaMovement(push.x, 0.5, push.z); e.hurtMarked = true;
        }
        for (int ring = 1; ring <= 8; ring++) for (int i = 0; i < 28; i++) {
            double a = i * Math.PI * 2 / 28;
            sl.sendParticles(ring % 2 == 0 ? ParticleTypes.WITCH : ParticleTypes.SQUID_INK, p.getX() + Math.cos(a) * ring, p.getY() + 0.2, p.getZ() + Math.sin(a) * ring, 1, 0, 0.05, 0, 0.02);
        }
        sl.sendParticles(ParticleTypes.DRAGON_BREATH, p.getX(), p.getY() + 1, p.getZ(), 60, 3, 1, 3, 0.15);
        sl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WITHER_BREAK_BLOCK, SoundSource.PLAYERS, 1.5F, 0.6F);
        sl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1F, 1.2F);
    }

    /** Shadow Sweep + Soul Harvest bonus damage on every melee hit. */
    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (attacker instanceof Player p && p.level() instanceof ServerLevel sl) {
            int souls = getSouls(stack);
            float bonus = souls * 0.25F;
            if (bonus > 0) { target.invulnerableTime = 0; target.hurt(sl.damageSources().indirectMagic(p, p), bonus); }
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(3.5, 0.5, 3.5),
                    e -> e != p && e != target && !(e instanceof ArmorStand) && p.canAttack(e) && !e.isAlliedTo(p) && p.distanceToSqr(e) < 36)) {
                e.hurt(sl.damageSources().playerAttack(p), 5F + bonus * 0.5F);
                Vec3 push = e.position().subtract(target.position()).multiply(1, 0, 1).normalize().scale(0.5);
                e.setDeltaMovement(e.getDeltaMovement().add(push.x, 0.2, push.z)); e.hurtMarked = true;
            }
            for (int i = 0; i < 24; i++) {
                double a = i * Math.PI / 12;
                sl.sendParticles(i % 3 == 0 ? ParticleTypes.SWEEP_ATTACK : ParticleTypes.WITCH, target.getX() + Math.cos(a) * 2.5, target.getY() + 0.9, target.getZ() + Math.sin(a) * 2.5, 1, 0, 0, 0, 0);
            }
            sl.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1F, 0.7F);
        }
        return true;
    }

    /** Shadow trail while swinging + a living aura that grows with souls. */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!(level instanceof ServerLevel sl) || !(entity instanceof Player p) || !selected) return;
        int souls = getSouls(stack);
        if (p.swinging || p.attackAnim > 0) {
            Vec3 eye = p.getEyePosition(), look = p.getLookAngle(), right = new Vec3(-look.z, 0, look.x).normalize();
            for (int i = 0; i < 6; i++) {
                double t = (i - 2.5) / 2.5;
                Vec3 pt = eye.add(look.scale(2.0)).add(right.scale(t * 1.6)).add(0, -0.4 - Math.abs(t) * 0.3, 0);
                sl.sendParticles(ParticleTypes.SQUID_INK, pt.x, pt.y, pt.z, 1, 0.05, 0.05, 0.05, 0);
                sl.sendParticles(ParticleTypes.WITCH, pt.x, pt.y, pt.z, 1 + souls / 15, 0.1, 0.1, 0.1, 0.02);
            }
        } else if (souls > 0 && p.tickCount % Math.max(2, 12 - souls / 5) == 0) {
            sl.sendParticles(ParticleTypes.WITCH, p.getX(), p.getY() + 1.0, p.getZ(), 1, 0.5, 0.6, 0.5, 0.02);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        int s = getSouls(stack);
        tip.add(Component.literal("Souls: " + s + "/" + MAX_SOULS + "  (+" + (int) (s * 3) + "% damage)").withStyle(ChatFormatting.LIGHT_PURPLE));
        tip.add(Component.literal("Right-click: Reaper Throw").withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("Sneak + Right-click: Eclipse Pull").withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("Hit: Shadow Sweep").withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("Look down + Right-click: Nightmare Storm (" + STORM_COST + " souls)").withStyle(ChatFormatting.DARK_PURPLE));
        tip.add(Component.literal("Kills feed the blade.").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
    }
}
