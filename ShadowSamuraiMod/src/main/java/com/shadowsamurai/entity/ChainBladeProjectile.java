package com.shadowsamurai.entity;

import com.shadowsamurai.ModEntities;
import com.shadowsamurai.ModItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The flying end of the chain. Used for the boss' Chain Reaper Throw / Eclipse Hook and the
 * player's Reaper Throw / Eclipse Pull / Nightmare Storm. The chain itself is drawn by the renderer
 * from the owner's hand to this entity.
 */
public class ChainBladeProjectile extends Entity {
    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(ChainBladeProjectile.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> HOOK = SynchedEntityData.defineId(ChainBladeProjectile.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> SOULS = SynchedEntityData.defineId(ChainBladeProjectile.class, EntityDataSerializers.INT);

    private Vec3 dir = Vec3.ZERO;
    private float speed = 1.8F, range = 22F, damage = 10F;
    private boolean pull, returning;
    private int outTicks;
    private final Set<Integer> hitIds = new HashSet<>();
    private final List<LivingEntity> grabbed = new ArrayList<>();

    public ChainBladeProjectile(EntityType<? extends ChainBladeProjectile> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public ChainBladeProjectile(Level level) { this(ModEntities.CHAIN_BLADE, level); }

    public void setup(Entity owner, Vec3 handPos, Vec3 direction, float speed, float range, float damage, boolean pull, boolean hook, int souls) {
        this.entityData.set(OWNER_ID, owner.getId());
        this.entityData.set(HOOK, hook);
        this.entityData.set(SOULS, souls);
        this.dir = direction.normalize();
        this.speed = speed; this.range = range; this.damage = damage; this.pull = pull;
        this.setPos(handPos.x, handPos.y - getBbHeight() / 2, handPos.z);
        this.setYRot((float) (Math.toDegrees(Math.atan2(-dir.x, dir.z))));
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(OWNER_ID, -1); b.define(HOOK, false); b.define(SOULS, 0);
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}

    public boolean isHook() { return entityData.get(HOOK); }
    public Entity getOwnerEntity() { int id = entityData.get(OWNER_ID); return id < 0 ? null : level().getEntity(id); }
    public Vec3 center() { return position().add(0, getBbHeight() / 2, 0); }
    public ItemStack getRenderStack() { return new ItemStack(isHook() ? ModItems.ECLIPSE_RING : ModItems.ECLIPSE_BLADE); }

    /** Where the chain leaves the wielder's hand. Used by logic AND renderer so they always agree. */
    public static Vec3 handPos(Entity o, float pt) {
        double s = o.getBbHeight() / 1.8;
        Vec3 f = Vec3.directionFromRotation(0, o.getViewYRot(pt));
        Vec3 right = new Vec3(-f.z, 0, f.x);
        return o.getPosition(pt).add(0, o.getBbHeight() * 0.62, 0).add(f.scale(0.45 * s)).add(right.scale(0.35 * s));
    }

    @Override
    public void tick() {
        super.tick();
        Entity owner = getOwnerEntity();
        if (level().isClientSide) {
            int souls = entityData.get(SOULS);
            for (int i = 0; i < 2 + souls / 10; i++)
                level().addParticle(i % 2 == 0 ? ParticleTypes.WITCH : ParticleTypes.SQUID_INK,
                        getX() + random.nextGaussian() * 0.3, getY() + 0.4 + random.nextGaussian() * 0.3, getZ() + random.nextGaussian() * 0.3, 0, 0.02, 0);
            return;
        }
        if (owner == null || !owner.isAlive() || tickCount > 140) { discard(); return; }
        ServerLevel sl = (ServerLevel) level();

        if (!returning) {
            Vec3 c = center(), next = c.add(dir.scale(speed));
            var hit = level().clip(new ClipContext(c, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (hit.getType() == HitResult.Type.MISS) setCenter(next);
            else {
                setCenter(hit.getLocation());
                sl.sendParticles(ParticleTypes.CRIT, getX(), getY() + .4, getZ(), 12, .2, .2, .2, .3);
                sl.playSound(null, getX(), getY(), getZ(), SoundEvents.CHAIN_HIT, SoundSource.HOSTILE, 1F, .7F);
                returning = true;
            }
            if (++outTicks * speed >= range) returning = true;
            hitEntities(owner, sl);
        } else {
            Vec3 hand = handPos(owner, 1F);
            Vec3 to = hand.subtract(center());
            double d = to.length();
            if (d < speed + 0.6) {
                sl.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.HOSTILE, 1F, 1.3F);
                discard();
                return;
            }
            setCenter(center().add(to.normalize().scale(speed * 1.2)));
            hitEntities(owner, sl);
        }
        // Eclipse Pull: drag everything the chain has grabbed back toward the wielder
        if (returning && !grabbed.isEmpty()) {
            Vec3 tgt = handPos(owner, 1F);
            for (LivingEntity e : grabbed) {
                if (!e.isAlive()) continue;
                Vec3 d = tgt.subtract(e.position().add(0, e.getBbHeight() / 2, 0));
                if (d.length() < 2.0) continue;
                e.setDeltaMovement(d.normalize().scale(Math.min(1.5, 0.4 + d.length() * 0.15)).add(0, 0.04, 0));
                e.hurtMarked = true; e.fallDistance = 0;
            }
        }
    }

    private void setCenter(Vec3 c) { setPos(c.x, c.y - getBbHeight() / 2, c.z); }

    private void hitEntities(Entity owner, ServerLevel sl) {
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.5),
                x -> x != owner && x.isAlive() && !hitIds.contains(x.getId()) && canHit(owner, x))) {
            hitIds.add(e.getId());
            DamageSource src = owner instanceof Player p ? damageSources().playerAttack(p)
                    : owner instanceof LivingEntity le ? damageSources().mobAttack(le) : damageSources().generic();
            e.hurt(src, damage);
            sl.sendParticles(ParticleTypes.ENCHANTED_HIT, e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(), 14, .3, .4, .3, .2);
            sl.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.CHAIN_BREAK, SoundSource.HOSTILE, 1F, .8F);
            if (pull || isHook()) {
                grabbed.add(e);
                if (!returning && isHook()) returning = true; // hook snags the first target and reels back
            }
        }
    }

    private boolean canHit(Entity owner, LivingEntity t) {
        if (t instanceof ShadowSamuraiEntity) return false;
        if (owner instanceof Player p) {
            if (t instanceof Player tp && !p.canHarmPlayer(tp)) return false;
            return p.canAttack(t) && !t.isAlliedTo(p);
        }
        return true;
    }
}
