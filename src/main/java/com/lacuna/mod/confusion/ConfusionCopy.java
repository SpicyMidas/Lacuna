package com.lacuna.mod.confusion;

import com.lacuna.mod.util.SafeRun;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import org.jetbrains.annotations.Nullable;

/**
 * One copy conjured by the Totem of Confusion. A plain living entity with the player's shape (sizes, eye heights and
 * poses of a player), drawn with the owner's skin, name tag, armour and held items (client/confusion).
 * <p>
 * Movement is server-authoritative and player-like: every tick the copy takes the owner's movement keys
 * ({@link MirrorInput}), rotation, sprinting, sneaking, swings, speed and jump strength, and walks through the normal
 * living-entity physics with its own heading offset added to the owner's yaw. A wall stops it like it would stop the
 * player; sneaking keeps it from walking off ledges like a player; a cliff without sneaking is simply fallen down.
 * Clients interpolate it from the regular entity tracking (update interval 2, like players).
 * <p>
 * Any damage instance is one hit (10 tick invulnerability between hits like players); {@link Confusion#HITS} hits pop
 * it in a puff, without drops or experience. It fades after {@link Confusion#LIFETIME} ticks, or at once when the owner
 * logs out, dies, changes dimension or wanders off. Never saved to disk (the entity type is {@code noSave}).
 */
public class ConfusionCopy extends LivingEntity {
    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER = SynchedEntityData.defineId(ConfusionCopy.class,
            EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<String> DATA_OWNER_NAME = SynchedEntityData.defineId(ConfusionCopy.class,
            EntityDataSerializers.STRING);
    /** The owner's skin customisation (hat, jacket, sleeves, trousers, cape), as {@link PlayerModelPart} masks. */
    private static final EntityDataAccessor<Byte> DATA_PARTS = SynchedEntityData.defineId(ConfusionCopy.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> DATA_LEFT_HANDED = SynchedEntityData.defineId(ConfusionCopy.class,
            EntityDataSerializers.BOOLEAN);
    /** The owner is burning: the copy shows the flames too, without taking fire damage. */
    private static final EntityDataAccessor<Boolean> DATA_BURNING = SynchedEntityData.defineId(ConfusionCopy.class,
            EntityDataSerializers.BOOLEAN);

    private static final Map<Pose, EntityDimensions> POSES = Map.of(
            Pose.STANDING, Player.STANDING_DIMENSIONS,
            Pose.CROUCHING, EntityDimensions.scalable(0.6F, 1.5F).withEyeHeight(1.27F),
            Pose.SWIMMING, EntityDimensions.scalable(0.6F, 0.6F).withEyeHeight(0.4F));

    /** Copies present on this client (client thread only): while the local player owns one, it streams its input. */
    private static final Set<ConfusionCopy> CLIENT_COPIES = Collections.newSetFromMap(new WeakHashMap<>());
    /** Client hook (set by client/confusion): true for the local player's own copies, which their crosshair passes through. */
    private static Predicate<ConfusionCopy> ownedLocally = copy -> false;

    private final NonNullList<ItemStack> hands = NonNullList.withSize(2, ItemStack.EMPTY);
    private final NonNullList<ItemStack> armor = NonNullList.withSize(4, ItemStack.EMPTY);

    // server: mirroring state
    private float heading;
    private int life;
    private int hits;
    private boolean seenSwinging;
    private int seenSwingTime;
    private float mirroredSpeed = 0.1F;
    private double mirroredJump = 0.42;

    // client: cape physics, as on players
    public double xCloakO;
    public double yCloakO;
    public double zCloakO;
    public double xCloak;
    public double yCloak;
    public double zCloak;
    public float oBob;
    public float bob;
    public float capeWalkO;
    public float capeWalk;

    public ConfusionCopy(EntityType<? extends ConfusionCopy> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.MOVEMENT_SPEED, 0.1);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_OWNER, Optional.empty());
        builder.define(DATA_OWNER_NAME, "");
        builder.define(DATA_PARTS, (byte) 0x7F);
        builder.define(DATA_LEFT_HANDED, false);
        builder.define(DATA_BURNING, false);
    }

    /** Server: binds the copy to {@code owner}, places it at {@code pos} turned by {@code heading} degrees. */
    void setup(ServerPlayer owner, float heading, Vec3 pos) {
        this.heading = heading;
        entityData.set(DATA_OWNER, Optional.of(owner.getUUID()));
        entityData.set(DATA_OWNER_NAME, owner.getGameProfile().getName());
        float yaw = owner.getYRot() + heading;
        moveTo(pos.x, pos.y, pos.z, yaw, owner.getXRot());
        setYHeadRot(yaw);
        setYBodyRot(yaw);
        seenSwinging = owner.swinging;
        seenSwingTime = owner.swingTime;
        mirror(owner);
        mirrorEffects(owner);
    }

    @Nullable
    public UUID ownerId() {
        return entityData.get(DATA_OWNER).orElse(null);
    }

    public String ownerName() {
        return entityData.get(DATA_OWNER_NAME);
    }

    public boolean isPartShown(PlayerModelPart part) {
        return (entityData.get(DATA_PARTS) & part.getMask()) == part.getMask();
    }

    /** Client: true while a live copy owned by {@code player} is in the client level. */
    public static boolean clientHasCopyOf(UUID player) {
        for (ConfusionCopy copy : CLIENT_COPIES) {
            if (!copy.isRemoved() && player.equals(copy.ownerId())) {
                return true;
            }
        }
        return false;
    }

    public static void setOwnedLocally(Predicate<ConfusionCopy> test) {
        ownedLocally = test;
    }

    /** The owner aims and swings through their own copies (at the mob behind them); everyone else hits them. */
    @Override
    public boolean isPickable() {
        return super.isPickable() && !(level().isClientSide && ownedLocally.test(this));
    }

    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        if (level().isClientSide) {
            CLIENT_COPIES.add(this);
        }
    }

    @Override
    public void onRemovedFromLevel() {
        super.onRemovedFromLevel();
        if (level().isClientSide) {
            CLIENT_COPIES.remove(this);
        }
    }

    @Nullable
    private ServerPlayer owner() {
        UUID id = ownerId();
        if (id == null || level().getServer() == null) {
            return null;
        }
        return level().getServer().getPlayerList().getPlayer(id);
    }

    // ------------------------------------------------------------------ ticking

    @Override
    public void tick() {
        if (level() instanceof ServerLevel server) {
            ServerPlayer owner = owner();
            if (owner == null || !owner.isAlive() || owner.isSpectator() || owner.level() != server
                    || distanceToSqr(owner) > Confusion.LEASH * Confusion.LEASH || ++life > Confusion.LIFETIME
                    || SafeRun.isDisabled(Confusion.FEATURE)) {
                vanish(false);
                return;
            }
            SafeRun.run(Confusion.FEATURE, () -> {
                mirror(owner);
                if (tickCount % 10 == 0) {
                    mirrorEffects(owner);
                }
                if ((life + getId()) % Confusion.LURE_INTERVAL == 0) {
                    Confusion.lureNear(server, owner, this, Confusion.LURE_CHANCE, getRandom());
                }
            });
            if (isRemoved()) {
                return;
            }
        }
        super.tick();
        if (level().isClientSide) {
            moveCape();
        }
    }

    /** Copies the owner's look, gait and gear (server, every tick before physics). */
    private void mirror(ServerPlayer owner) {
        float yaw = owner.getYRot() + heading;
        setYRot(yaw);
        setXRot(owner.getXRot());
        setYHeadRot(yaw);
        setSprinting(owner.isSprinting());
        setShiftKeyDown(owner.isShiftKeyDown());
        mirroredSpeed = owner.getSpeed();
        mirroredJump = owner.getAttributeValue(Attributes.JUMP_STRENGTH);
        setGlowingTag(owner.hasGlowingTag());
        entityData.set(DATA_BURNING, owner.isOnFire());
        entityData.set(DATA_PARTS, partsOf(owner));
        entityData.set(DATA_LEFT_HANDED, owner.getMainArm() == HumanoidArm.LEFT);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.ANIMAL_ARMOR) {
                continue;
            }
            ItemStack want = owner.getItemBySlot(slot);
            if (!ItemStack.matches(want, getItemBySlot(slot))) {
                setItemSlot(slot, want.copy());
            }
        }
        // a fresh swing starts over at swingTime -1, so a drop in swingTime (or a new swing) is a new swing
        if (owner.swinging && (!seenSwinging || owner.swingTime < seenSwingTime)) {
            swing(owner.swingingArm == null ? InteractionHand.MAIN_HAND : owner.swingingArm);
        }
        seenSwinging = owner.swinging;
        seenSwingTime = owner.swingTime;
        updatePose();
    }

    private static byte partsOf(Player player) {
        int mask = 0;
        for (PlayerModelPart part : PlayerModelPart.values()) {
            if (player.isModelPartShown(part)) {
                mask |= part.getMask();
            }
        }
        return (byte) mask;
    }

    /**
     * Wears the owner's status effects too (swirls, invisibility, glowing, jump boost, slow falling...), except the
     * ones that would hurt a copy on their own (poison, wither) and instant ones.
     */
    private void mirrorEffects(ServerPlayer owner) {
        for (MobEffectInstance e : owner.getActiveEffects()) {
            Holder<MobEffect> effect = e.getEffect();
            if (!mirrored(effect)) {
                continue;
            }
            MobEffectInstance mine = getEffect(effect);
            if (mine == null || mine.getAmplifier() != e.getAmplifier() || mine.isInfiniteDuration() != e.isInfiniteDuration()
                    || Math.abs(mine.getDuration() - e.getDuration()) > 40) {
                if (mine != null) {
                    removeEffect(effect);
                }
                addEffect(new MobEffectInstance(effect, e.getDuration(), e.getAmplifier(), e.isAmbient(), e.isVisible(), e.showIcon()));
            }
        }
        for (Holder<MobEffect> effect : List.copyOf(getActiveEffectsMap().keySet())) {
            if (!owner.hasEffect(effect)) {
                removeEffect(effect);
            }
        }
    }

    private static boolean mirrored(Holder<MobEffect> effect) {
        MobEffect value = effect.value();
        return !value.isInstantenous() && value != MobEffects.POISON.value() && value != MobEffects.WITHER.value();
    }

    /** Player pose rules: swimming, sneaking or standing, crouching or crawling where the space is too low. */
    private void updatePose() {
        Pose pose = isSwimming() ? Pose.SWIMMING : isShiftKeyDown() ? Pose.CROUCHING : Pose.STANDING;
        if (!fits(pose)) {
            pose = fits(Pose.CROUCHING) ? Pose.CROUCHING : Pose.SWIMMING;
        }
        setPose(pose);
    }

    private boolean fits(Pose pose) {
        return level().noCollision(this, getDimensions(pose).makeBoundingBox(position()).deflate(1.0E-7));
    }

    @Override
    protected void serverAiStep() {
        super.serverAiStep();
        ServerPlayer owner = owner();
        MirrorInput in = owner == null || owner.isPassenger() || owner.isSleeping() || owner.isFallFlying()
                || owner.getAbilities().flying ? MirrorInput.NONE : MirrorInput.current(owner);
        xxa = in.strafe();
        zza = in.forward();
        setJumping(in.jump());
    }

    @Override
    public void aiStep() {
        updateSwingTime();
        super.aiStep();
    }

    @Override
    public float getSpeed() {
        return mirroredSpeed;
    }

    @Override
    protected float getFlyingSpeed() {
        return isSprinting() ? 0.026F : 0.02F;
    }

    @Override
    protected float getJumpPower(float multiplier) {
        return (float) mirroredJump * multiplier * getBlockJumpFactor() + getJumpBoostPower();
    }

    /** As players: sneaking on the ground keeps the copy from stepping off a ledge. */
    @Override
    protected Vec3 maybeBackOffFromEdge(Vec3 move, MoverType type) {
        float step = maxUpStep();
        if (move.y > 0.0 || type != MoverType.SELF || !isShiftKeyDown() || !isAboveGround(step)) {
            return move;
        }
        double dx = move.x;
        double dz = move.z;
        double sx = Math.signum(dx) * 0.05;
        double sz = Math.signum(dz) * 0.05;
        while (dx != 0.0 && canFallAtLeast(dx, 0.0, step)) {
            if (Math.abs(dx) <= 0.05) {
                dx = 0.0;
                break;
            }
            dx -= sx;
        }
        while (dz != 0.0 && canFallAtLeast(0.0, dz, step)) {
            if (Math.abs(dz) <= 0.05) {
                dz = 0.0;
                break;
            }
            dz -= sz;
        }
        while (dx != 0.0 && dz != 0.0 && canFallAtLeast(dx, dz, step)) {
            dx = Math.abs(dx) <= 0.05 ? 0.0 : dx - sx;
            dz = Math.abs(dz) <= 0.05 ? 0.0 : dz - sz;
        }
        return new Vec3(dx, move.y, dz);
    }

    private boolean isAboveGround(float step) {
        return onGround() || fallDistance < step && !canFallAtLeast(0.0, 0.0, step - fallDistance);
    }

    private boolean canFallAtLeast(double dx, double dz, float depth) {
        AABB box = getBoundingBox();
        return level().noCollision(this, new AABB(box.minX + dx, box.minY - depth - 1.0E-5F, box.minZ + dz, box.maxX + dx, box.minY,
                box.maxZ + dz));
    }

    private void moveCape() {
        xCloakO = xCloak;
        yCloakO = yCloak;
        zCloakO = zCloak;
        double dx = getX() - xCloak;
        double dy = getY() - yCloak;
        double dz = getZ() - zCloak;
        if (Math.abs(dx) > 10.0 || Math.abs(dy) > 10.0 || Math.abs(dz) > 10.0) {
            xCloak = xCloakO = getX();
            yCloak = yCloakO = getY();
            zCloak = zCloakO = getZ();
        } else {
            xCloak += dx * 0.25;
            yCloak += dy * 0.25;
            zCloak += dz * 0.25;
        }
        double moved = Math.sqrt((getX() - xo) * (getX() - xo) + (getZ() - zo) * (getZ() - zo));
        oBob = bob;
        bob += ((onGround() && !isSwimming() ? (float) Math.min(0.1, moved) : 0.0F) - bob) * 0.4F;
        capeWalkO = capeWalk;
        capeWalk += (float) moved * 0.6F;
    }

    // ------------------------------------------------------------------ hits and fading

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isRemoved()) {
            return false;
        }
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            vanish(true); // /kill, the void
            return true;
        }
        if (isInvulnerableTo(source) || isOwner(source.getEntity())) {
            return false; // the owner never breaks their own copies
        }
        if (invulnerableTime > 10 && !source.is(DamageTypeTags.BYPASSES_COOLDOWN)) {
            return false; // one hit per invulnerability window, like players
        }
        boolean hurt = super.hurt(source, amount);
        if (hits >= Confusion.HITS) {
            vanish(true);
        }
        return hurt;
    }

    /** Every damage instance is one hit whatever its amount; health never changes. */
    @Override
    protected void actuallyHurt(DamageSource source, float amount) {
        if (!isInvulnerableTo(source)) {
            hits++;
            gameEvent(GameEvent.ENTITY_DAMAGE);
        }
    }

    private boolean isOwner(@Nullable Entity entity) {
        return entity != null && entity.getUUID().equals(ownerId());
    }

    /** Removes the copy in a puff ({@code popped}: broken by hits, else faded). */
    void vanish(boolean popped) {
        if (isRemoved()) {
            return;
        }
        if (level() instanceof ServerLevel server) {
            Confusion.poof(server, position(), popped);
        }
        discard();
    }

    @Override
    public void die(DamageSource source) {
        vanish(true);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide) {
            // mobs that were chasing this copy look for a new target (usually the player)
            for (Mob mob : level().getEntitiesOfClass(Mob.class, getBoundingBox().inflate(48.0), m -> m.getTarget() == this)) {
                mob.setTarget(null);
            }
        }
        super.remove(reason);
    }

    @Override
    protected void dropAllDeathLoot(ServerLevel level, DamageSource source) {
        // a copy leaves nothing behind
    }

    @Override
    public boolean shouldDropExperience() {
        return false;
    }

    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return false;
    }

    // ------------------------------------------------------------------ looking like the owner

    @Override
    public Component getName() {
        String name = ownerName();
        return name.isEmpty() ? super.getName() : Component.literal(name);
    }

    /** The owner's team: same name tag colour, name tag visibility and friendly-fire rules as the real player. */
    @Override
    @Nullable
    public PlayerTeam getTeam() {
        String name = ownerName();
        return name.isEmpty() ? super.getTeam() : level().getScoreboard().getPlayersTeam(name);
    }

    @Override
    public boolean displayFireAnimation() {
        return super.displayFireAnimation() || entityData.get(DATA_BURNING);
    }

    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) {
        return POSES.getOrDefault(pose, Player.STANDING_DIMENSIONS);
    }

    @Override
    public HumanoidArm getMainArm() {
        return entityData.get(DATA_LEFT_HANDED) ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
    }

    @Override
    public Iterable<ItemStack> getHandSlots() {
        return hands;
    }

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return armor;
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        return switch (slot.getType()) {
            case HAND -> hands.get(slot.getIndex());
            case HUMANOID_ARMOR -> armor.get(slot.getIndex());
            default -> ItemStack.EMPTY;
        };
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
        switch (slot.getType()) {
            case HAND -> hands.set(slot.getIndex(), stack);
            case HUMANOID_ARMOR -> armor.set(slot.getIndex(), stack);
            default -> {
            }
        }
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.PLAYERS;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return source.type().effects().sound();
    }

    @Override
    @Nullable
    protected SoundEvent getDeathSound() {
        return null;
    }

    @Override
    public Fallsounds getFallSounds() {
        return new Fallsounds(SoundEvents.PLAYER_SMALL_FALL, SoundEvents.PLAYER_BIG_FALL);
    }

    @Override
    protected SoundEvent getSwimSound() {
        return SoundEvents.PLAYER_SWIM;
    }

    @Override
    protected SoundEvent getSwimSplashSound() {
        return SoundEvents.PLAYER_SPLASH;
    }

    @Override
    protected SoundEvent getSwimHighSpeedSplashSound() {
        return SoundEvents.PLAYER_SPLASH_HIGH_SPEED;
    }
}
