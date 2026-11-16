package com.shadowsamurai.entity;

import com.shadowsamurai.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class ShadowSamuraiEntity extends Monster {
    public static final int NONE = 0, THROW = 1, SPIRAL = 2, HOOK = 3, MOON = 4, STORM = 5, INTRO = 6;

    private static final EntityDataAccessor<Integer> ATTACK = SynchedEntityData.defineId(ShadowSamuraiEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ATTACK_TICK = SynchedEntityData.defineId(ShadowSamuraiEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> ENRAGED = SynchedEntityData.defineId(ShadowSamuraiEntity.class, EntityDataSerializers.BOOLEAN);

    private final ServerBossEvent bossEvent = new ServerBossEvent(
            Component.literal("SHADOW SAMURAI").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD),
            BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);

    private int introTicks = 0, attackTick = 0, cooldown = 40, lastAttack = NONE, forcedNext = NONE;
    private final List<Wave> waves = new ArrayList<>();
    private final List<Strike> strikes = new ArrayList<>();

    private static class Wave { Vec3 pos; final Vec3 dir; int life = 20; final Set<Integer> hit = new HashSet<>(); Wave(Vec3 p, Vec3 d) { pos = p; dir = d; } }
    private static class Strike { final Vec3 pos; int delay; Strike(Vec3 p, int d) { pos = p; delay = d; } }

    public ShadowSamuraiEntity(EntityType<? extends ShadowSamuraiEntity> type, Level level) {
        super(type, level);
        this.xpReward = 500;
        this.setPersistenceRequired();
        this.bossEvent.setDarkenScreen(true).setCreateWorldFog(true);
        ItemStack reaper = new ItemStack(ModItems.ECLIPSE_CHAIN_REAPER);
        this.setItemSlot(EquipmentSlot.MAINHAND, reaper);
        this.setDropChance(EquipmentSlot.MAINHAND, 0F);
        if (!level.isClientSide) { introTicks = 80; entityData.set(ATTACK, INTRO); }
        this.refreshDimensions();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 400)
                .add(Attributes.MOVEMENT_SPEED, 0.30)
                .add(Attributes.ATTACK_DAMAGE, 10)
                .add(Attributes.FOLLOW_RANGE, 48)
                .add(Attributes.ARMOR, 12)
                .add(Attributes.ARMOR_TOUGHNESS, 6)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.STEP_HEIGHT, 1.6)
                .add(Attributes.SCALE, 2.1);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(ATTACK, NONE); b.define(ATTACK_TICK, 0); b.define(ENRAGED, false);
    }

    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true) {
            @Override public boolean canUse() { return isIdle() && super.canUse(); }
            @Override public boolean canContinueToUse() { return isIdle() && super.canContinueToUse(); }
        });
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 24F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // ---------- accessors for the client model ----------
    public int getAttackType() { return entityData.get(ATTACK); }
    public int getAttackTick() { return entityData.get(ATTACK_TICK); }
    public boolean isEnraged() { return entityData.get(ENRAGED); }
    private boolean isIdle() { return introTicks <= 0 && getAttackType() == NONE; }

    // ---------- boss bar ----------
    @Override public void startSeenByPlayer(ServerPlayer p) { super.startSeenByPlayer(p); bossEvent.addPlayer(p); }
    @Override public void stopSeenByPlayer(ServerPlayer p) { super.stopSeenByPlayer(p); bossEvent.removePlayer(p); }
    @Override public void remove(RemovalReason r) { super.remove(r); bossEvent.removeAllPlayers(); }
    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override protected boolean shouldDespawnInPeaceful() { return false; }
    @Override public boolean isInvulnerableTo(DamageSource s) { return introTicks > 0 || super.isInvulnerableTo(s); }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Enraged", isEnraged());
        if (introTicks <= 0) tag.putBoolean("IntroDone", true);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(ENRAGED, tag.getBoolean("Enraged"));
        if (tag.getBoolean("IntroDone")) { introTicks = 0; entityData.set(ATTACK, NONE); }
        if (isEnraged()) bossEvent.setColor(BossEvent.BossBarColor.RED);
    }

    // ---------- sounds ----------
    @Override protected SoundEvent getAmbientSound() { return SoundEvents.WITHER_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource s) { return SoundEvents.WITHER_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.WITHER_DEATH; }

    // ---------- main server brain ----------
    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel sl)) return;
        bossEvent.setName(Component.literal("SHADOW SAMURAI").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        tickWaves(sl);
        tickStrikes(sl);

        if (introTicks > 0) { tickIntro(sl); return; }
        bossEvent.setProgress(getHealth() / getMaxHealth());

        if (!isEnraged() && getHealth() <= getMaxHealth() * 0.5F) enrage(sl);

        LivingEntity t = getTarget();
        if (getAttackType() != NONE) { tickAttack(sl, t); return; }
        if (cooldown > 0) cooldown--;
        else if (t != null && t.isAlive() && hasLineOfSight(t)) chooseAttack(t);
    }

    private void tickIntro(ServerLevel sl) {
        introTicks--;
        int t = 80 - introTicks;
        bossEvent.setProgress(t / 80F);
        getNavigation().stop();
        setDeltaMovement(0, getDeltaMovement().y, 0);
        entityData.set(ATTACK_TICK, t);
        sl.sendParticles(ParticleTypes.PORTAL, getX(), getY() + 0.2, getZ(), 14, 2.0, 0.2, 2.0, 0.7);
        sl.sendParticles(ParticleTypes.SQUID_INK, getX(), getY() + 0.1, getZ(), 4, 1.5, 0.1, 1.5, 0.05);
        if (t == 1) {
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 3F, 0.55F);
            for (ServerPlayer p : sl.players()) if (p.distanceToSqr(this) < 64 * 64) {
                p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 25));
                p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("The Eclipse Awakens").withStyle(ChatFormatting.RED, ChatFormatting.ITALIC)));
                p.connection.send(new ClientboundSetTitleTextPacket(Component.literal("SHADOW SAMURAI").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD)));
            }
        }
        if (t == 30) {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(sl);
            if (bolt != null) { bolt.moveTo(getX(), getY(), getZ()); bolt.setVisualOnly(true); sl.addFreshEntity(bolt); }
        }
        if (t == 50) sl.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 3F, 0.5F);
        if (t == 60) {
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 80, 3, 1, 3, 0.15);
            sl.sendParticles(ParticleTypes.DRAGON_BREATH, getX(), getY() + 0.5, getZ(), 60, 3, 0.5, 3, 0.1);
        }
        if (introTicks <= 0) { entityData.set(ATTACK, NONE); entityData.set(ATTACK_TICK, 0); cooldown = 30; }
    }

    private void enrage(ServerLevel sl) {
        entityData.set(ENRAGED, true);
        bossEvent.setColor(BossEvent.BossBarColor.RED);
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.38);
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 3F, 0.4F);
        sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 2, getZ(), 1, 0, 0, 0, 0);
        sl.sendParticles(ParticleTypes.CRIMSON_SPORE, getX(), getY() + 2, getZ(), 80, 2, 2, 2, 0.1);
        forcedNext = STORM; cooldown = 15;
    }

    private void chooseAttack(LivingEntity t) {
        if (forcedNext != NONE) { int f = forcedNext; forcedNext = NONE; startAttack(f); return; }
        double d = distanceTo(t);
        List<Integer> o = new ArrayList<>();
        if (d < 7) { o.add(SPIRAL); o.add(SPIRAL); o.add(MOON); o.add(STORM); }
        else if (d < 20) { o.add(THROW); o.add(THROW); o.add(HOOK); o.add(MOON); o.add(STORM); }
        else { o.add(HOOK); o.add(THROW); o.add(STORM); }
        final int last = lastAttack;
        List<Integer> f = o.stream().filter(x -> x != last).toList();
        if (f.isEmpty()) f = o;
        startAttack(f.get(random.nextInt(f.size())));
    }

    private void startAttack(int type) {
        attackTick = 0; lastAttack = type;
        entityData.set(ATTACK, type); entityData.set(ATTACK_TICK, 0);
        playSound(SoundEvents.WITHER_AMBIENT, 2F, 0.5F);
    }

    private void endAttack(int cd) {
        entityData.set(ATTACK, NONE); entityData.set(ATTACK_TICK, 0);
        attackTick = 0; cooldown = cd + random.nextInt(15);
    }

    private void tickAttack(ServerLevel sl, LivingEntity t) {
        attackTick++;
        entityData.set(ATTACK_TICK, attackTick);
        getNavigation().stop();
        boolean enr = isEnraged();
        float atk = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * (enr ? 1.25F : 1F);
        int type = getAttackType();
        if (t != null && type != SPIRAL) {
            getLookControl().setLookAt(t, 40, 40);
            setDeltaMovement(getDeltaMovement().multiply(0.2, 1, 0.2));
        }
        switch (type) {
            case THROW -> tickThrow(sl, t, atk, enr);
            case SPIRAL -> tickSpiral(sl, t, atk, enr);
            case HOOK -> tickHook(sl, t, atk);
            case MOON -> tickMoon(sl, t, atk, enr);
            case STORM -> tickStorm(sl, t, enr);
            default -> endAttack(20);
        }
    }

    // ===== 1. Chain Reaper Throw =====
    private void tickThrow(ServerLevel sl, LivingEntity t, float atk, boolean enr) {
        Vec3 hand = ChainBladeProjectile.handPos(this, 1F);
        if (attackTick < 14) {
            sl.sendParticles(ParticleTypes.WITCH, hand.x, hand.y + 0.3, hand.z, 4, 0.5, 0.5, 0.5, 0.05);
            if (attackTick == 1) playSound(SoundEvents.CHAIN_PLACE, 2F, 0.6F);
        }
        if ((attackTick == 14 || (enr && attackTick == 34)) && t != null) {
            Vec3 aim = t.position().add(0, t.getBbHeight() * 0.6, 0).subtract(hand);
            ChainBladeProjectile p = new ChainBladeProjectile(sl);
            p.setup(this, hand, aim, 2.0F, 26F, atk * 1.5F, false, false, 0);
            sl.addFreshEntity(p);
            playSound(SoundEvents.WITHER_SHOOT, 2F, 0.6F);
        }
        if (attackTick >= (enr ? 62 : 54)) endAttack(enr ? 25 : 45);
    }

    // ===== 2. Shadow Spiral =====
    private void tickSpiral(ServerLevel sl, LivingEntity t, float atk, boolean enr) {
        float yaw = getYRot() + 38F;
        setYRot(yaw); yBodyRot = yaw; yHeadRot = yaw;
        Vec3 c = position();
        double y = c.y + getBbHeight() * 0.35;
        for (int k = 0; k < 2; k++) {
            double base = attackTick * 0.7 + k * Math.PI;
            for (double r = 1.5; r <= 6.6; r += 0.8) {
                double a = base - r * 0.22;
                double x = c.x + Math.cos(a) * r, z = c.z + Math.sin(a) * r;
                sl.sendParticles(r > 5 ? ParticleTypes.WITCH : ParticleTypes.DRAGON_BREATH, x, y, z, 1, 0.05, 0.05, 0.05, 0);
                if (r > 6.2) sl.sendParticles(ParticleTypes.SWEEP_ATTACK, x, y, z, 1, 0, 0, 0, 0);
                if (k == 0 && ((int) (r * 10)) % 2 == 0) sl.sendParticles(ParticleTypes.SQUID_INK, x, y - 0.1, z, 1, 0.05, 0.05, 0.05, 0.01);
            }
        }
        if (t != null) {
            Vec3 d = t.position().subtract(position()).multiply(1, 0, 1);
            if (d.lengthSqr() > 4) { d = d.normalize().scale(0.13); setDeltaMovement(d.x, getDeltaMovement().y, d.z); }
        }
        if (attackTick % 5 == 0) {
            playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 2F, 0.5F);
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(6.8, 2.5, 6.8), e -> e != this && !(e instanceof ShadowSamuraiEntity))) {
                e.hurt(damageSources().mobAttack(this), atk * 0.9F);
                Vec3 push = e.position().subtract(position()).multiply(1, 0, 1).normalize().scale(0.9);
                e.setDeltaMovement(push.x, 0.35, push.z); e.hurtMarked = true;
            }
        }
        if (attackTick >= (enr ? 66 : 50)) endAttack(40);
    }

    // ===== 3. Eclipse Hook =====
    private void tickHook(ServerLevel sl, LivingEntity t, float atk) {
        Vec3 hand = ChainBladeProjectile.handPos(this, 1F);
        if (attackTick < 12) {
            for (int i = 0; i < 8; i++) {
                double a = attackTick * 0.8 + i * Math.PI / 4;
                sl.sendParticles(ParticleTypes.CRIMSON_SPORE, hand.x + Math.cos(a) * 1.0, hand.y, hand.z + Math.sin(a) * 1.0, 1, 0, 0, 0, 0);
            }
        }
        if (attackTick == 12 && t != null) {
            Vec3 aim = t.position().add(0, t.getBbHeight() * 0.5, 0).subtract(hand);
            ChainBladeProjectile p = new ChainBladeProjectile(sl);
            p.setup(this, hand, aim, 2.8F, 30F, atk * 0.4F, true, true, 0);
            sl.addFreshEntity(p);
            playSound(SoundEvents.CHAIN_HIT, 2F, 0.5F);
        }
        if (attackTick >= 50) { forcedNext = SPIRAL; endAttack(5); } // punish whoever got dragged in
    }

    // ===== 4. Crimson Moon Slash =====
    private void tickMoon(ServerLevel sl, LivingEntity t, float atk, boolean enr) {
        Vec3 hand = ChainBladeProjectile.handPos(this, 1F).add(0, 1.0, 0);
        if (attackTick < 32) {
            double shrink = 1.0 - attackTick / 32.0;
            for (int i = 0; i < 5; i++) {
                Vec3 off = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize().scale(4 * shrink + 0.3);
                Vec3 from = hand.add(off);
                sl.sendParticles(i % 2 == 0 ? ParticleTypes.CRIMSON_SPORE : ParticleTypes.DRAGON_BREATH, from.x, from.y, from.z, 0, -off.x, -off.y, -off.z, 0.12);
            }
            if (attackTick == 1) playSound(SoundEvents.BEACON_ACTIVATE, 3F, 0.5F);
        }
        if (attackTick == 32) {
            Vec3 f = t != null ? t.position().subtract(position()).multiply(1, 0, 1).normalize() : Vec3.directionFromRotation(0, getYRot());
            int[] angles = enr ? new int[]{-22, 0, 22} : new int[]{0};
            for (int deg : angles) {
                double r = Math.toRadians(deg);
                Vec3 d = new Vec3(f.x * Math.cos(r) - f.z * Math.sin(r), 0, f.x * Math.sin(r) + f.z * Math.cos(r));
                waves.add(new Wave(position().add(d.scale(2.5)).add(0, 0.2, 0), d));
            }
            playSound(SoundEvents.WARDEN_SONIC_BOOM, 3F, 0.7F);
            playSound(SoundEvents.LIGHTNING_BOLT_THUNDER, 2F, 0.8F);
            sl.sendParticles(ParticleTypes.SWEEP_ATTACK, getX() + f.x * 2, getY() + 2, getZ() + f.z * 2, 3, 1.5, 0.5, 1.5, 0);
        }
        if (attackTick >= (enr ? 48 : 54)) endAttack(enr ? 25 : 45);
    }

    private void tickWaves(ServerLevel sl) {
        float atk = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * (isEnraged() ? 1.25F : 1F);
        Iterator<Wave> it = waves.iterator();
        while (it.hasNext()) {
            Wave w = it.next();
            w.pos = w.pos.add(w.dir.scale(1.6));
            BlockPosHelper bp = new BlockPosHelper(w.pos);
            if (--w.life <= 0 || bp.solid(sl)) {
                sl.sendParticles(ParticleTypes.EXPLOSION, w.pos.x, w.pos.y + 1, w.pos.z, 2, 1, 1, 1, 0);
                sl.sendParticles(ParticleTypes.DRAGON_BREATH, w.pos.x, w.pos.y + 1, w.pos.z, 25, 1.5, 1, 1.5, 0.08);
                it.remove();
                continue;
            }
            Vec3 perp = new Vec3(-w.dir.z, 0, w.dir.x);
            double R = 4.5;
            for (int deg = -70; deg <= 70; deg += 10) {
                double th = Math.toRadians(deg);
                double side = R * Math.sin(th);
                double h = R * (Math.cos(th) - Math.cos(Math.toRadians(70))) * 0.75;
                Vec3 p = w.pos.add(perp.scale(side)).add(0, h, 0).subtract(w.dir.scale(Math.abs(side) * 0.25));
                sl.sendParticles(ParticleTypes.DRAGON_BREATH, p.x, p.y, p.z, 1, 0.08, 0.08, 0.08, 0);
                sl.sendParticles(deg % 20 == 0 ? ParticleTypes.CRIMSON_SPORE : ParticleTypes.ENCHANTED_HIT, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.01);
            }
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(w.pos, w.pos).inflate(3.8, 2.5, 3.8),
                    e -> e != this && !(e instanceof ShadowSamuraiEntity) && !w.hit.contains(e.getId()))) {
                w.hit.add(e.getId());
                e.hurt(damageSources().mobAttack(this), atk * 1.6F);
                e.setDeltaMovement(w.dir.x * 1.2, 0.5, w.dir.z * 1.2); e.hurtMarked = true;
            }
        }
    }

    /** Tiny helper so the wave code reads cleanly. */
    private record BlockPosHelper(Vec3 v) {
        boolean solid(ServerLevel l) {
            var bp = net.minecraft.core.BlockPos.containing(v.x, v.y + 0.5, v.z);
            return l.getBlockState(bp).isCollisionShapeFullBlock(l, bp);
        }
    }

    // ===== 5. Phantom Chain Storm =====
    private void tickStorm(ServerLevel sl, LivingEntity t, boolean enr) {
        if (attackTick == 1) {
            playSound(SoundEvents.ENDER_DRAGON_GROWL, 3F, 0.7F);
            int per = enr ? 7 : 5;
            for (Player p : sl.getEntitiesOfClass(Player.class, getBoundingBox().inflate(32), p -> !p.isSpectator() && p.isAlive())) {
                strikes.add(new Strike(p.position(), 26));
                for (int i = 0; i < per; i++) {
                    double a = random.nextDouble() * Math.PI * 2, r = 2 + random.nextDouble() * 5;
                    strikes.add(new Strike(p.position().add(Math.cos(a) * r, 0, Math.sin(a) * r), 20 + i * 4));
                }
            }
        }
        if (attackTick % 2 == 0) for (int i = 0; i < 12; i++) {
            double a = attackTick * 0.3 + i * Math.PI / 6;
            sl.sendParticles(ParticleTypes.WITCH, getX() + Math.cos(a) * 3.5, getY() + 0.2, getZ() + Math.sin(a) * 3.5, 1, 0, 0.1, 0, 0.02);
        }
        if (attackTick >= 36) endAttack(enr ? 30 : 50);
    }

    private void tickStrikes(ServerLevel sl) {
        float atk = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * (isEnraged() ? 1.25F : 1F);
        Iterator<Strike> it = strikes.iterator();
        while (it.hasNext()) {
            Strike s = it.next();
            if (--s.delay > 0) {
                if (s.delay % 2 == 0) {
                    double rr = 0.6 + s.delay * 0.06;
                    for (int i = 0; i < 10; i++) {
                        double a = i * Math.PI / 5;
                        sl.sendParticles(ParticleTypes.WITCH, s.pos.x + Math.cos(a) * rr, s.pos.y + 0.1, s.pos.z + Math.sin(a) * rr, 1, 0, 0, 0, 0);
                    }
                    sl.sendParticles(ParticleTypes.SQUID_INK, s.pos.x, s.pos.y + 0.1, s.pos.z, 2, 0.6, 0, 0.6, 0.01);
                }
                continue;
            }
            // chains erupt
            sl.playSound(null, s.pos.x, s.pos.y, s.pos.z, SoundEvents.CHAIN_BREAK, SoundSource.HOSTILE, 2F, 0.6F);
            sl.playSound(null, s.pos.x, s.pos.y, s.pos.z, SoundEvents.WITHER_BREAK_BLOCK, SoundSource.HOSTILE, 0.8F, 1.4F);
            for (int i = 0; i < 26; i++) {
                double h = i * 0.22, a = i * 0.9;
                for (int k = 0; k < 2; k++) {
                    double x = s.pos.x + Math.cos(a + k * Math.PI) * 0.7, z = s.pos.z + Math.sin(a + k * Math.PI) * 0.7;
                    sl.sendParticles(k == 0 ? ParticleTypes.ENCHANTED_HIT : ParticleTypes.SQUID_INK, x, s.pos.y + h, z, 1, 0, 0, 0, 0);
                }
            }
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, s.pos.x, s.pos.y + 0.5, s.pos.z, 18, 0.8, 1.2, 0.8, 0.04);
            sl.sendParticles(ParticleTypes.DRAGON_BREATH, s.pos.x, s.pos.y + 0.3, s.pos.z, 20, 0.8, 0.3, 0.8, 0.05);
            AABB box = new AABB(s.pos.x - 2.2, s.pos.y - 1, s.pos.z - 2.2, s.pos.x + 2.2, s.pos.y + 5, s.pos.z + 2.2);
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, box, e -> e != this && !(e instanceof ShadowSamuraiEntity))) {
                e.hurt(damageSources().mobAttack(this), atk * 1.2F);
                e.setDeltaMovement(e.getDeltaMovement().add(0, 0.9, 0)); e.hurtMarked = true;
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
            }
            it.remove();
        }
    }

    // ---------- death: long, dramatic collapse ----------
    @Override public void die(DamageSource src) {
        super.die(src);
        waves.clear(); strikes.clear();
        if (level() instanceof ServerLevel sl)
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDER_DRAGON_DEATH, SoundSource.HOSTILE, 3F, 0.6F);
    }

    @Override protected void tickDeath() {
        if (deathTime < 60) {
            deathTime++;
            if (level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 2, getZ(), 6, 1, 1.5, 1, 0.05);
                sl.sendParticles(ParticleTypes.WITCH, getX(), getY() + 2, getZ(), 10, 1, 1.5, 1, 0.2);
                if (deathTime % 10 == 0) sl.sendParticles(ParticleTypes.SOUL, getX(), getY() + 1, getZ(), 15, 0.8, 1, 0.8, 0.1);
                if (deathTime == 59) {
                    sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 2, getZ(), 1, 0, 0, 0, 0);
                    sl.sendParticles(ParticleTypes.DRAGON_BREATH, getX(), getY() + 2, getZ(), 120, 2, 2, 2, 0.2);
                }
            }
            return;
        }
        super.tickDeath();
    }

    // ---------- client-side aura: black smoke, purple particles, shadow ring ----------
    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide || isDeadOrDying()) return;
        RandomSource r = random;
        double w = getBbWidth() * 1.2, h = getBbHeight();
        for (int i = 0; i < 3; i++)
            level().addParticle(ParticleTypes.LARGE_SMOKE, getX() + (r.nextDouble() - .5) * w * 2, getY() + r.nextDouble() * h, getZ() + (r.nextDouble() - .5) * w * 2, 0, 0.03, 0);
        if (r.nextInt(2) == 0) level().addParticle(ParticleTypes.WITCH, getX() + (r.nextDouble() - .5) * w * 2.5, getY() + r.nextDouble() * h, getZ() + (r.nextDouble() - .5) * w * 2.5, 0, 0.02, 0);
        if (r.nextInt(3) == 0) level().addParticle(ParticleTypes.PORTAL, getX() + (r.nextDouble() - .5) * w * 3, getY() + r.nextDouble() * h, getZ() + (r.nextDouble() - .5) * w * 3, (r.nextDouble() - .5), -0.5, (r.nextDouble() - .5));
        if (tickCount % 2 == 0) {
            double a = tickCount * 0.25;
            level().addParticle(ParticleTypes.SQUID_INK, getX() + Math.cos(a) * 2.6, getY() + 0.1, getZ() + Math.sin(a) * 2.6, 0, 0.04, 0);
        }
        if (isEnraged()) {
            level().addParticle(ParticleTypes.CRIMSON_SPORE, getX() + (r.nextDouble() - .5) * w * 2, getY() + r.nextDouble() * h, getZ() + (r.nextDouble() - .5) * w * 2, 0, 0.05, 0);
            if (r.nextInt(4) == 0) level().addParticle(ParticleTypes.ELECTRIC_SPARK, getX() + (r.nextDouble() - .5) * w * 2, getY() + r.nextDouble() * h, getZ() + (r.nextDouble() - .5) * w * 2, 0, 0, 0);
        }
    }
}
