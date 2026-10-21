package com.lacuna.mod.gear;

import com.lacuna.mod.Balance;
import com.lacuna.mod.Lacuna;
import com.lacuna.mod.registry.ModEntities;
import com.lacuna.mod.registry.ModParticles;
import com.lacuna.mod.util.SafeRun;
import com.lacuna.mod.util.Sfx;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.entity.projectile.ThrownEgg;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.EggItem;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.FireChargeItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SnowballItem;
import net.minecraft.world.item.ThrowablePotionItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * Animated Item (Wand of Quickening): a dropped item that grew legs and fights for its owner for 40 s, keeping its
 * own gifts. The stack is its main-hand equipment (drop chance 0), so vanilla attributes and enchantments apply, and
 * when its life runs out - or it dies, is killed or discarded - exactly that stack falls back into the world as a
 * normal dropped item (TNT is the exception: every blast spends one). Chunk unloads and dimension changes carry it
 * along unchanged.
 */
public class AnimatedItemEntity extends PathfinderMob implements OwnableEntity {
    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER =
            SynchedEntityData.defineId(AnimatedItemEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> DATA_LIFE = SynchedEntityData.defineId(AnimatedItemEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_FUSING = SynchedEntityData.defineId(AnimatedItemEntity.class, EntityDataSerializers.BOOLEAN);
    /** Its special power is gathering or under way (the client rears it up and makes it glow). */
    private static final EntityDataAccessor<Boolean> DATA_POWER = SynchedEntityData.defineId(AnimatedItemEntity.class, EntityDataSerializers.BOOLEAN);
    /** Entity event: it crouches before a leap (client squash). */
    private static final byte EVENT_CROUCH = 91;
    public static final DustParticleOptions PINK_DUST = new DustParticleOptions(new Vector3f(1.0F, 0.45F, 0.8F), 0.7F);
    public static final DustParticleOptions GREEN_DUST = new DustParticleOptions(new Vector3f(0.45F, 1.0F, 0.6F), 0.7F);
    private static final ResourceLocation ARMOR_ID = Lacuna.id("animated_item_armor");
    private static final ResourceLocation TOUGHNESS_ID = Lacuna.id("animated_item_toughness");
    private static final ResourceLocation STEADINESS_ID = Lacuna.id("animated_item_steadiness");

    /** What the item does with its new life, chosen from the stack. */
    public enum Kind {
        MELEE, ARCHER, TRIDENT, THROWER, HEALER, TNT, IGNITER, SHIELD, FOOD, PEARL, SLAM;

        public boolean ranged() {
            return this == ARCHER || this == TRIDENT || this == THROWER;
        }

        public boolean brawler() {
            return !ranged() && this != TNT && this != HEALER;
        }
    }

    private int life = Balance.ANIMATED_LIFETIME;
    /** The stack went back into the world (or was used up): it must never be dropped twice. */
    private boolean released;
    @Nullable
    private Kind kind;
    private boolean attributesDirty = true;
    private int attackCooldown;
    private int rangedCooldown;
    private int leapCooldown;
    private int abilityCooldown;
    private int tauntCooldown;
    private int feedCooldown;
    private int pendingLeap;
    @Nullable
    private LivingEntity leapTarget;
    private int fuse;
    private int ownerHurtByStamp = Integer.MIN_VALUE;
    private int ownerHurtStamp = Integer.MIN_VALUE;
    /** Magical copies it shot or threw: they fade away after a few seconds, they are never items. */
    private final List<Projectile> conjured = new ArrayList<>();
    /** The item's own special power ({@link AnimatedAbilities}): cooldown, the cue before it, a power under way. */
    private int specialCooldown;
    private int specialCue;
    @Nullable
    private LivingEntity specialTarget;
    @Nullable
    private AnimatedAbilities.Channel channel;

    // ------------------------------------------------------------------ client animation state (never saved)
    public int clientLife = Balance.ANIMATED_LIFETIME;
    public float clientAir;
    public float clientAirO;
    public float clientVy;
    public float clientVyO;
    public int clientLandTicks;
    public int clientCrouchTicks;
    public int clientFuseTicks;
    /** 0..1: how far it has reared up, glowing, for its special power. */
    public float clientPower;
    public float clientPowerO;
    private boolean clientWasOnGround = true;
    private int clientAirTicks;
    /** Renderer-owned: sprite facing (Paper-style flip toward the walking direction). */
    public float renderFlip;
    public boolean renderFacingRight = true;
    public long renderFlipNanos;

    public AnimatedItemEntity(EntityType<? extends AnimatedItemEntity> type, Level level) {
        super(type, level);
        this.xpReward = 0;
        setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        setCanPickUpLoot(false);
        setPersistenceRequired();
        this.specialCooldown = Mth.nextInt(random, AnimatedAbilities.FIRST_DELAY_MIN, AnimatedAbilities.FIRST_DELAY_MAX);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, Balance.ANIMATED_MAX_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, Balance.ANIMATED_SPEED)
                .add(Attributes.ATTACK_DAMAGE, 1.0)
                .add(Attributes.ATTACK_SPEED, 4.0)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_OWNER, Optional.empty());
        builder.define(DATA_LIFE, Balance.ANIMATED_LIFETIME);
        builder.define(DATA_FUSING, false);
        builder.define(DATA_POWER, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new FeedOwnerGoal());
        goalSelector.addGoal(2, new TntGoal());
        goalSelector.addGoal(2, new RangedGoal());
        goalSelector.addGoal(2, new MeleeGoal());
        goalSelector.addGoal(5, new FollowOwnerGoal());
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
    }

    // ------------------------------------------------------------------ waking up
    /** Creates, places and adds an animated item; null (and nothing changed) if the level refused it. */
    @Nullable
    public static AnimatedItemEntity spawn(ServerLevel level, ItemStack stack, Player owner, Vec3 at, Vec3 motion, float yaw) {
        AnimatedItemEntity animated = ModEntities.ANIMATED_ITEM.get().create(level);
        if (animated == null || stack.isEmpty()) {
            return null;
        }
        Vec3 pos = freeSpot(level, animated, at, owner.position());
        animated.moveTo(pos.x, pos.y, pos.z, yaw, 0.0F);
        animated.setYHeadRot(yaw);
        animated.setYBodyRot(yaw);
        animated.entityData.set(DATA_OWNER, Optional.of(owner.getUUID()));
        animated.setItemSlot(EquipmentSlot.MAINHAND, stack);
        animated.setHealth(animated.getMaxHealth());
        animated.setDeltaMovement(motion);
        if (!level.addFreshEntity(animated)) {
            animated.released = true;
            return null;
        }
        animated.wakeEffects(level);
        return animated;
    }

    private static Vec3 freeSpot(Level level, Entity entity, Vec3... candidates) {
        for (Vec3 c : candidates) {
            for (double dy = 0.0; dy <= 1.0; dy += 0.5) {
                Vec3 p = c.add(0.0, dy, 0.0);
                if (level.noCollision(entity, entity.getType().getDimensions().makeBoundingBox(p))) {
                    return p;
                }
            }
        }
        return candidates[candidates.length - 1];
    }

    /** A player keeps at most {@link Balance#QUICKENING_MAX_ALIVE} awake: the oldest fall asleep early. */
    public static void enforceCap(ServerLevel level, Player owner, AnimatedItemEntity fresh) {
        List<AnimatedItemEntity> mine = new ArrayList<>(level.getEntities(ModEntities.ANIMATED_ITEM.get(),
                e -> e != fresh && e.isAlive() && owner.getUUID().equals(e.getOwnerUUID()) && e.life > Balance.ANIMATED_SLEEP_TICKS));
        int excess = mine.size() + 1 - Balance.QUICKENING_MAX_ALIVE;
        mine.sort(Comparator.comparingInt(e -> e.life));
        for (int i = 0; i < excess && i < mine.size(); i++) {
            mine.get(i).sleepSoon();
        }
    }

    /** Ends its life early: the legs shrink away and it falls asleep in half a second. */
    public void sleepSoon() {
        if (life > Balance.ANIMATED_SLEEP_TICKS) {
            life = Balance.ANIMATED_SLEEP_TICKS;
            entityData.set(DATA_LIFE, life);
        }
        setFuse(0);
    }

    private void wakeEffects(ServerLevel level) {
        Vec3 c = position().add(0.0, 0.35, 0.0);
        level.sendParticles(ParticleTypes.HEART, c.x, c.y + 0.4, c.z, 3, 0.3, 0.15, 0.3, 0.0);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, c.x, c.y, c.z, 12, 0.35, 0.3, 0.35, 0.0);
        level.sendParticles(PINK_DUST, c.x, c.y, c.z, 16, 0.3, 0.3, 0.3, 0.0);
        level.sendParticles(ModParticles.LIGHT_MOTE.get(), c.x, c.y, c.z, 8, 0.25, 0.25, 0.25, 0.02);
        level.sendParticles(ParticleTypes.POOF, c.x, c.y - 0.2, c.z, 6, 0.2, 0.05, 0.2, 0.02);
        Sfx.at(this, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.3F);
        Sfx.at(this, SoundEvents.ALLAY_ITEM_GIVEN, 0.9F, 1.1F);
        Sfx.at(this, SoundEvents.CHICKEN_EGG, 0.6F, 1.6F);
    }

    // ------------------------------------------------------------------ identity
    @Nullable
    @Override
    public UUID getOwnerUUID() {
        return entityData.get(DATA_OWNER).orElse(null);
    }

    public Kind kind() {
        if (kind == null) {
            kind = kindOf(getMainHandItem());
        }
        return kind;
    }

    public static Kind kindOf(ItemStack stack) {
        Item item = stack.getItem();
        if (stack.is(Items.TNT)) {
            return Kind.TNT;
        }
        if (item instanceof ShieldItem || stack.canPerformAction(ItemAbilities.SHIELD_BLOCK)) {
            return Kind.SHIELD;
        }
        if (item instanceof BowItem || item instanceof CrossbowItem) {
            return Kind.ARCHER;
        }
        if (item instanceof TridentItem) {
            return Kind.TRIDENT;
        }
        if (item instanceof ThrowablePotionItem) {
            return beneficialPotion(stack) ? Kind.HEALER : Kind.THROWER;
        }
        if (item instanceof SnowballItem || item instanceof EggItem || item instanceof FireChargeItem) {
            return Kind.THROWER;
        }
        if (item instanceof FlintAndSteelItem) {
            return Kind.IGNITER;
        }
        if (item instanceof EnderpearlItem) {
            return Kind.PEARL;
        }
        if (hasAttackDamage(stack)) {
            return Kind.MELEE;
        }
        if (stack.get(DataComponents.FOOD) != null) {
            return Kind.FOOD;
        }
        return Kind.SLAM;
    }

    private static boolean hasAttackDamage(ItemStack stack) {
        boolean[] found = {false};
        stack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.value() == Attributes.ATTACK_DAMAGE.value() && modifier.amount() > 0.0) {
                found[0] = true;
            }
        });
        return found[0];
    }

    private static boolean beneficialPotion(ItemStack stack) {
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        if (contents == null || !contents.hasEffects()) {
            return false;
        }
        for (MobEffectInstance effect : contents.getAllEffects()) {
            if (!effect.getEffect().value().isBeneficial()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
        super.setItemSlot(slot, stack);
        if (slot == EquipmentSlot.MAINHAND) {
            kind = null;
            attributesDirty = true;
        }
    }

    /** Armour lends its protection; a shield makes a heavy, steady guard. */
    private void applyItemAttributes() {
        ItemStack stack = getMainHandItem();
        double armor = 0.0;
        double toughness = 0.0;
        double steadiness = 0.0;
        if (stack.getItem() instanceof ArmorItem armorItem) {
            armor = armorItem.getDefense();
            toughness = armorItem.getToughness();
            steadiness = armorItem.getMaterial().value().knockbackResistance();
        }
        if (kind() == Kind.SHIELD) {
            armor += Balance.ANIMATED_SHIELD_ARMOR;
            steadiness = Math.max(steadiness, Balance.ANIMATED_SHIELD_KNOCKBACK_RESISTANCE);
        }
        setModifier(Attributes.ARMOR, ARMOR_ID, armor);
        setModifier(Attributes.ARMOR_TOUGHNESS, TOUGHNESS_ID, toughness);
        setModifier(Attributes.KNOCKBACK_RESISTANCE, STEADINESS_ID, steadiness);
    }

    private void setModifier(Holder<Attribute> attribute, ResourceLocation id, double amount) {
        AttributeInstance instance = getAttribute(attribute);
        if (instance == null) {
            return;
        }
        instance.removeModifier(id);
        if (amount != 0.0) {
            instance.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    @Override
    protected Component getTypeName() {
        ItemStack stack = getMainHandItem();
        return stack.isEmpty() ? super.getTypeName() : Component.translatable("entity.lacuna.animated_item.of", stack.getHoverName());
    }

    // ------------------------------------------------------------------ friends and foes
    private boolean pvpAllowed() {
        MinecraftServer server = level().getServer();
        return server != null && server.isPvpAllowed();
    }

    /** Its owner, the owner's pets and other animated items, villagers, and players when PvP is off. */
    public boolean isAlly(@Nullable Entity entity) {
        if (entity == null) {
            return false;
        }
        if (entity == this) {
            return true;
        }
        UUID owner = getOwnerUUID();
        if (entity instanceof AnimatedItemEntity other) {
            return owner == null ? other.getOwnerUUID() == null : owner.equals(other.getOwnerUUID());
        }
        if (owner != null && owner.equals(entity.getUUID())) {
            return true;
        }
        if (owner != null && entity instanceof OwnableEntity pet && owner.equals(pet.getOwnerUUID())) {
            return true;
        }
        if (entity instanceof AbstractVillager) {
            return true;
        }
        return entity instanceof Player && !pvpAllowed();
    }

    public boolean canTarget(@Nullable LivingEntity target) {
        return target != null && target != this && target.isAlive() && target.level() == level() && !isAlly(target)
                && target.canBeSeenAsEnemy() && !(target instanceof ArmorStand);
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return canTarget(target) && super.canAttack(target);
    }

    @Override
    public boolean isAlliedTo(Entity entity) {
        return isAlly(entity) || super.isAlliedTo(entity);
    }

    /** Defend the owner (whoever hurt them, then whoever they hurt), retaliate, else hunt the nearest hostile it can see. */
    private void updateTarget(@Nullable LivingEntity owner) {
        if (kind() == Kind.HEALER) {
            // a kind potion never fights: it stays by its owner
            if (getTarget() != null) {
                setTarget(null);
            }
            return;
        }
        LivingEntity current = getTarget();
        boolean leashed = owner != null && owner.level() == level() && distanceToSqr(owner) > 28.0 * 28.0;
        if (current != null && (!canTarget(current) || distanceToSqr(current) > 32.0 * 32.0 || leashed)) {
            setTarget(null);
            current = null;
        }
        if (owner != null && owner.level() == level()) {
            LivingEntity attacker = owner.getLastHurtByMob();
            int stamp = owner.getLastHurtByMobTimestamp();
            if (attacker != null && stamp != ownerHurtByStamp && canTarget(attacker) && attacker.distanceToSqr(this) < 24.0 * 24.0) {
                ownerHurtByStamp = stamp;
                setTarget(attacker);
                return;
            }
            LivingEntity victim = owner.getLastHurtMob();
            int stamp2 = owner.getLastHurtMobTimestamp();
            if (victim != null && stamp2 != ownerHurtStamp && owner.tickCount - stamp2 < 200 && canTarget(victim)
                    && victim.distanceToSqr(this) < 24.0 * 24.0) {
                ownerHurtStamp = stamp2;
                setTarget(victim);
                return;
            }
        }
        if (current != null || leashed) {
            return;
        }
        LivingEntity hurtBy = getLastHurtByMob();
        if (hurtBy != null && canTarget(hurtBy)) {
            setTarget(hurtBy);
            return;
        }
        double r = Balance.ANIMATED_HUNT_RANGE;
        Mob best = null;
        double bestDistance = r * r;
        for (Mob mob : level().getEntitiesOfClass(Mob.class, getBoundingBox().inflate(r, 6.0, r), m -> m instanceof Enemy
                && !(m instanceof Creeper) && !(m instanceof NeutralMob neutral && !neutral.isAngry()) && canTarget(m))) {
            double d = distanceToSqr(mob);
            if (d < bestDistance && getSensing().hasLineOfSight(mob)) {
                bestDistance = d;
                best = mob;
            }
        }
        if (best != null) {
            setTarget(best);
        }
    }

    // ------------------------------------------------------------------ ticking
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            clientTick();
            return;
        }
        if (isRemoved()) {
            return;
        }
        if (getMainHandItem().isEmpty()) {
            // used up (or taken away by a command): nothing is left to wake
            released = true;
            discard();
            return;
        }
        if (attributesDirty) {
            attributesDirty = false;
            applyItemAttributes();
        }
        if (tickCount % 10 == 0) {
            tickConjured();
        }
        life--;
        if (life % 20 == 0 || life <= Balance.ANIMATED_SLEEP_TICKS + 1) {
            entityData.set(DATA_LIFE, Math.max(0, life));
        }
        if (life <= 0) {
            releaseStack(true);
            discard();
        }
    }

    @Override
    public void aiStep() {
        updateSwingTime();
        super.aiStep();
    }

    @Override
    public int getCurrentSwingDuration() {
        return 9;
    }

    @Override
    protected void updateWalkAnimation(float distance) {
        // short legs: full stride at a gentler pace
        walkAnimation.update(Math.min(distance * 6.0F, 1.0F), 0.4F);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (attackCooldown > 0) {
            attackCooldown--;
        }
        if (rangedCooldown > 0) {
            rangedCooldown--;
        }
        if (leapCooldown > 0) {
            leapCooldown--;
        }
        if (abilityCooldown > 0) {
            abilityCooldown--;
        }
        if (tauntCooldown > 0) {
            tauntCooldown--;
        }
        if (feedCooldown > 0) {
            feedCooldown--;
        }
        LivingEntity owner = getOwner();
        if (tickCount % 5 == 0) {
            updateTarget(owner);
        }
        tickLeap();
        if (owner != null && tickCount % 20 == 0) {
            followByTeleport(owner);
        }
        Kind k = kind();
        if (k == Kind.SHIELD) {
            taunt();
        } else if (k == Kind.HEALER && owner != null) {
            supportOwner(owner);
        }
        tickSpecial();
    }

    // ------------------------------------------------------------------ special powers (AnimatedAbilities)
    /** True while its special power gathers or is under way: the fighting goals hold still. */
    boolean busy() {
        return specialCue > 0 || channel != null;
    }

    void startChannel(AnimatedAbilities.Channel power) {
        channel = power;
    }

    /** Ticks of life it has left before it falls asleep. */
    int lifeLeft() {
        return life;
    }

    private void tickSpecial() {
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        if (channel != null) {
            AnimatedAbilities.Channel power = channel;
            getNavigation().stop();
            if (!SafeRun.call(AnimatedAbilities.FEATURE, () -> power.tick(server, this), false, () -> channel = null)) {
                channel = null;
            }
        } else if (specialCue > 0) {
            // the cue: it rears up and glows, facing its target, then the power goes off
            getNavigation().stop();
            if (specialTarget != null) {
                getLookControl().setLookAt(specialTarget, 30.0F, 30.0F);
            }
            if (--specialCue <= 0) {
                LivingEntity target = specialTarget;
                specialTarget = null;
                specialCooldown = AnimatedAbilities.perform(server, this, target);
            }
        } else if (specialCooldown > 0) {
            specialCooldown--;
        } else if ((tickCount + getId()) % AnimatedAbilities.OPPORTUNITY_INTERVAL == 0 && pendingLeap <= 0 && fuse <= 0) {
            LivingEntity target = getTarget();
            if (target != null && SafeRun.call(AnimatedAbilities.FEATURE, () -> AnimatedAbilities.roll(server, this, target), false)) {
                specialTarget = target;
                specialCue = AnimatedAbilities.CUE_TICKS;
            }
        }
        boolean powered = busy();
        if (entityData.get(DATA_POWER) != powered) {
            entityData.set(DATA_POWER, powered);
        }
    }

    private void clientTick() {
        clientLife = Math.max(0, clientLife - 1);
        boolean ground = onGround();
        clientAirO = clientAir;
        clientAir += ((ground ? 0.0F : 1.0F) - clientAir) * 0.35F;
        if (ground && !clientWasOnGround && clientAirTicks > 2) {
            clientLandTicks = 6;
        } else if (clientLandTicks > 0) {
            clientLandTicks--;
        }
        clientAirTicks = ground ? 0 : clientAirTicks + 1;
        clientWasOnGround = ground;
        if (clientCrouchTicks > 0) {
            clientCrouchTicks--;
        }
        clientVyO = clientVy;
        clientVy += ((float) (getY() - yo) - clientVy) * 0.5F;
        clientFuseTicks = entityData.get(DATA_FUSING) ? clientFuseTicks + 1 : 0;
        clientPowerO = clientPower;
        clientPower = Mth.approach(clientPower, entityData.get(DATA_POWER) ? 1.0F : 0.0F, entityData.get(DATA_POWER) ? 0.12F : 0.2F);
        if (clientPower > 0.3F && !isInvisible() && random.nextInt(3) == 0) {
            // gathering its power: motes of light rise around it
            level().addParticle(random.nextBoolean() ? ParticleTypes.END_ROD : ModParticles.LIGHT_MOTE.get(), getRandomX(0.8),
                    getY() + 0.2 + random.nextDouble() * 0.9, getRandomZ(0.8), 0.0, 0.03 + random.nextDouble() * 0.03, 0.0);
        }
        if (clientLife > 0 && !isInvisible()) {
            // a faint green-and-pink shimmer of borrowed life
            if (random.nextInt(5) == 0) {
                level().addParticle(random.nextBoolean() ? PINK_DUST : GREEN_DUST, getRandomX(0.6), getY() + 0.25 + random.nextDouble() * 0.8,
                        getRandomZ(0.6), 0.0, 0.0, 0.0);
            }
            if (random.nextInt(45) == 0) {
                level().addParticle(ParticleTypes.HEART, getRandomX(0.4), getY() + 0.95 + random.nextDouble() * 0.25, getRandomZ(0.4), 0.0, 0.02, 0.0);
            }
            if (random.nextInt(30) == 0) {
                level().addParticle(ModParticles.LIGHT_MOTE.get(), getRandomX(0.6), getY() + 0.3 + random.nextDouble() * 0.6, getRandomZ(0.6),
                        0.0, 0.01, 0.0);
            }
        }
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_LIFE.equals(key)) {
            clientLife = entityData.get(DATA_LIFE);
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EVENT_CROUCH) {
            clientCrouchTicks = Balance.ANIMATED_LEAP_WINDUP + 1;
        } else {
            super.handleEntityEvent(id);
        }
    }

    private void tickConjured() {
        Iterator<Projectile> it = conjured.iterator();
        while (it.hasNext()) {
            Projectile p = it.next();
            if (p.isRemoved()) {
                it.remove();
            } else if (p.tickCount > 80) {
                vanish(p);
                it.remove();
            }
        }
    }

    private void vanish(Projectile p) {
        if (level() instanceof ServerLevel server) {
            server.sendParticles(GREEN_DUST, p.getX(), p.getY(), p.getZ(), 4, 0.1, 0.1, 0.1, 0.0);
        }
        p.discard();
    }

    // ------------------------------------------------------------------ following
    private void followByTeleport(LivingEntity owner) {
        if (owner.level() != level() || owner.isSpectator() || isPassenger() || getTarget() != null
                || distanceToSqr(owner) < Balance.ANIMATED_TELEPORT_DISTANCE * Balance.ANIMATED_TELEPORT_DISTANCE) {
            return;
        }
        BlockPos base = owner.blockPosition();
        for (int i = 0; i < 10; i++) {
            int dx = random.nextIntBetweenInclusive(-3, 3);
            int dz = random.nextIntBetweenInclusive(-3, 3);
            if (Math.abs(dx) < 2 && Math.abs(dz) < 2) {
                continue;
            }
            BlockPos p = base.offset(dx, random.nextIntBetweenInclusive(-1, 1), dz);
            if (canTeleportTo(p)) {
                if (level() instanceof ServerLevel server) {
                    server.sendParticles(GREEN_DUST, getX(), getY() + 0.4, getZ(), 10, 0.25, 0.3, 0.25, 0.0);
                }
                moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, getYRot(), getXRot());
                getNavigation().stop();
                if (level() instanceof ServerLevel server) {
                    server.sendParticles(PINK_DUST, getX(), getY() + 0.4, getZ(), 10, 0.25, 0.3, 0.25, 0.0);
                }
                return;
            }
        }
    }

    private boolean canTeleportTo(BlockPos pos) {
        if (WalkNodeEvaluator.getPathTypeStatic(this, pos) != PathType.WALKABLE) {
            return false;
        }
        if (level().getBlockState(pos.below()).getBlock() instanceof LeavesBlock) {
            return false;
        }
        return level().noCollision(this, getBoundingBox().move(pos.subtract(blockPosition())));
    }

    // ------------------------------------------------------------------ leaping
    private void beginLeap(LivingEntity target) {
        pendingLeap = Balance.ANIMATED_LEAP_WINDUP;
        leapTarget = target;
        leapCooldown = 30 + random.nextInt(20);
        getNavigation().stop();
        level().broadcastEntityEvent(this, EVENT_CROUCH);
    }

    private void tickLeap() {
        if (pendingLeap <= 0) {
            return;
        }
        getNavigation().stop();
        if (--pendingLeap > 0) {
            return;
        }
        LivingEntity target = leapTarget;
        leapTarget = null;
        if (target == null || !target.isAlive() || !onGround()) {
            return;
        }
        Vec3 flat = new Vec3(target.getX() - getX(), 0.0, target.getZ() - getZ());
        double length = flat.length();
        if (length < 1.0E-4) {
            return;
        }
        // about 7 ticks of air drift cover the gap
        Vec3 push = flat.scale(Math.min(length, 4.5) / length / 7.0);
        setDeltaMovement(push.x, 0.42 + Mth.clamp((target.getY() - getY()) * 0.08, 0.0, 0.12), push.z);
        hasImpulse = true;
        lookAt(target, 360.0F, 360.0F);
        setYBodyRot(getYRot());
        Sfx.varied(this, SoundEvents.SLIME_JUMP_SMALL, 0.35F, 1.7F, 0.15F);
    }

    // ------------------------------------------------------------------ fighting
    private int meleeInterval() {
        if (kind() != Kind.MELEE) {
            return 20;
        }
        double speed = Math.max(0.5, getAttributeValue(Attributes.ATTACK_SPEED));
        return Mth.clamp((int) Math.ceil(20.0 / speed), 10, 30);
    }

    private float strikeDamage(ItemStack stack, Kind k) {
        if (k == Kind.MELEE) {
            return (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * Balance.ANIMATED_WEAPON_MULTIPLIER;
        }
        float damage = Balance.ANIMATED_SLAM_DAMAGE;
        if (stack.getItem() instanceof BlockItem blockItem) {
            float hardness = blockItem.getBlock().defaultDestroyTime();
            damage += hardness < 0.0F ? Balance.ANIMATED_SLAM_HARDNESS_BONUS : Math.min(Balance.ANIMATED_SLAM_HARDNESS_BONUS, hardness * 0.8F);
        }
        return damage;
    }

    /** A melee hit: weapons slash with their own damage x1.25 and enchantments, everything else body-slams. */
    private void strike(LivingEntity target) {
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        ItemStack stack = getMainHandItem();
        Kind k = kind();
        swing(InteractionHand.MAIN_HAND);
        DamageSource source = damageSources().mobAttack(this);
        float damage = EnchantmentHelper.modifyDamage(server, stack, target, source, strikeDamage(stack, k));
        if (k == Kind.MELEE) {
            damage += stack.getItem().getAttackDamageBonus(target, damage, source);
        }
        if (!target.hurt(source, damage)) {
            Sfx.varied(this, SoundEvents.PLAYER_ATTACK_NODAMAGE, 0.6F, 1.4F, 0.1F);
            return;
        }
        float yawRad = getYRot() * Mth.DEG_TO_RAD;
        float knockback = getKnockback(target, source) + (k == Kind.SHIELD ? 1.0F : k == Kind.MELEE ? 0.0F : 0.4F);
        if (knockback > 0.0F) {
            target.knockback(knockback * 0.5F, Mth.sin(yawRad), -Mth.cos(yawRad));
        }
        EnchantmentHelper.doPostAttackEffects(server, target, source);
        if (k == Kind.MELEE) {
            // the blade keeps its own gifts (rends, drenches, charges...): hurtEnemy, never the durability cost
            SafeRun.run("animated_item_blade", () -> stack.getItem().hurtEnemy(stack, target, this));
            float dealt = damage;
            SafeRun.run(AnimatedAbilities.FEATURE, () -> AnimatedAbilities.afterStrike(this, stack, target, dealt));
            if (stack.canPerformAction(ItemAbilities.SWORD_SWEEP)) {
                sweep(target, damage);
            }
        }
        if (k == Kind.IGNITER || stack.getItem() instanceof FireChargeItem) {
            target.igniteForSeconds(Balance.ANIMATED_IGNITE_SECONDS);
        }
        setLastHurtMob(target);
        strikeEffects(server, target, stack, k);
    }

    private void sweep(LivingEntity target, float damage) {
        float yawRad = getYRot() * Mth.DEG_TO_RAD;
        float sweepDamage = 1.0F + damage * Balance.ANIMATED_SWEEP_FRACTION;
        for (LivingEntity other : level().getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(1.0, 0.25, 1.0),
                e -> e != target && e != this && e instanceof Enemy && canTarget(e) && distanceToSqr(e) < 9.0)) {
            other.knockback(0.4, Mth.sin(yawRad), -Mth.cos(yawRad));
            other.hurt(damageSources().mobAttack(this), sweepDamage);
        }
    }

    private void strikeEffects(ServerLevel server, LivingEntity target, ItemStack stack, Kind k) {
        double fx = -Mth.sin(getYRot() * Mth.DEG_TO_RAD);
        double fz = Mth.cos(getYRot() * Mth.DEG_TO_RAD);
        Vec3 at = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
        switch (k) {
            case MELEE -> {
                server.sendParticles(ParticleTypes.SWEEP_ATTACK, getX() + fx * 0.8, getY(0.6), getZ() + fz * 0.8, 0, fx, 0.0, fz, 0.0);
                boolean sweeper = stack.canPerformAction(ItemAbilities.SWORD_SWEEP);
                Sfx.varied(this, sweeper ? SoundEvents.PLAYER_ATTACK_SWEEP : SoundEvents.PLAYER_ATTACK_STRONG, 0.9F, 1.15F, 0.1F);
            }
            case IGNITER -> {
                server.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 10, 0.25, 0.3, 0.25, 0.02);
                Sfx.varied(this, SoundEvents.FLINTANDSTEEL_USE, 0.9F, 1.1F, 0.1F);
            }
            case SHIELD -> {
                server.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 10, 0.25, 0.3, 0.25, 0.2);
                Sfx.varied(this, SoundEvents.SHIELD_BLOCK, 0.9F, 0.7F, 0.1F);
            }
            default -> {
                if (stack.getItem() instanceof BlockItem blockItem) {
                    BlockState state = blockItem.getBlock().defaultBlockState();
                    server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y, at.z, 14, 0.25, 0.25, 0.25, 0.12);
                    SoundType sound = state.getSoundType(level(), target.blockPosition(), this);
                    server.playSound(null, at.x, at.y, at.z, sound.getBreakSound(), SoundSource.NEUTRAL, sound.getVolume() * 0.8F,
                            sound.getPitch() * 1.2F);
                } else {
                    server.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, stack.copyWithCount(1)), at.x, at.y, at.z, 8,
                            0.2, 0.2, 0.2, 0.08);
                }
                Sfx.varied(this, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.8F, 1.3F, 0.1F);
            }
        }
    }

    private double preferredRange() {
        return kind() == Kind.ARCHER ? Balance.ANIMATED_ARCHER_RANGE : Balance.ANIMATED_THROWER_RANGE;
    }

    private int rangedInterval() {
        return switch (kind()) {
            case ARCHER -> getMainHandItem().getItem() instanceof CrossbowItem ? Balance.ANIMATED_SHOT_INTERVAL + 10 : Balance.ANIMATED_SHOT_INTERVAL;
            case TRIDENT -> Balance.ANIMATED_TRIDENT_INTERVAL;
            default -> Mth.nextInt(random, Balance.ANIMATED_THROW_MIN_INTERVAL, Balance.ANIMATED_THROW_MAX_INTERVAL);
        };
    }

    /** Shoots or throws what it is: arrows from a bow, trident copies, snowballs, eggs, fireballs, potions. */
    private void fire(LivingEntity target) {
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        ItemStack stack = getMainHandItem();
        swing(InteractionHand.MAIN_HAND);
        switch (kind()) {
            case ARCHER -> shootArrows(server, stack, target);
            case TRIDENT -> throwTrident(server, stack, target);
            case THROWER -> throwItem(server, stack, target);
            default -> {
            }
        }
    }

    private void shootArrows(ServerLevel server, ItemStack weapon, LivingEntity target) {
        ItemStack ammo = new ItemStack(Items.ARROW);
        boolean crossbow = weapon.getItem() instanceof CrossbowItem;
        int count = Math.max(1, EnchantmentHelper.processProjectileCount(server, weapon, this, 1));
        float spread = count > 1 ? Math.max(10.0F, EnchantmentHelper.processProjectileSpread(server, weapon, this, 0.0F)) : 0.0F;
        for (int i = 0; i < count; i++) {
            AbstractArrow arrow = ProjectileUtil.getMobArrow(this, ammo, 1.0F, weapon);
            if (weapon.getItem() instanceof ProjectileWeaponItem projectileWeapon) {
                // keeps the bow's own arrow magic, e.g. the Heartwood Bow's grasping roots
                arrow = projectileWeapon.customArrow(arrow, ammo, weapon);
            }
            arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
            if (crossbow) {
                arrow.setCritArrow(true);
                arrow.setSoundEvent(SoundEvents.CROSSBOW_HIT);
            }
            double dx = target.getX() - getX();
            double dy = target.getY(0.3333333333333333) - arrow.getY();
            double dz = target.getZ() - getZ();
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            Vec3 aim = new Vec3(dx, dy + horizontal * 0.2, dz);
            if (count > 1) {
                aim = aim.yRot((-spread + i * (2.0F * spread / (count - 1))) * Mth.DEG_TO_RAD);
            }
            arrow.shoot(aim.x, aim.y, aim.z, 1.6F, 14 - level().getDifficulty().getId() * 4);
            server.addFreshEntity(arrow);
            conjured.add(arrow);
        }
        playSound(crossbow ? SoundEvents.CROSSBOW_SHOOT : SoundEvents.ARROW_SHOOT, 1.0F, 1.0F / (random.nextFloat() * 0.4F + 0.8F));
    }

    private void throwTrident(ServerLevel server, ItemStack stack, LivingEntity target) {
        ItemStack copy = stack.copyWithCount(1);
        // a loyal copy would try to fly home to its thrower forever
        EnchantmentHelper.updateEnchantments(copy, enchantments -> enchantments.removeIf(e -> e.is(Enchantments.LOYALTY)));
        ThrownTrident trident = new ThrownTrident(server, this, copy);
        trident.pickup = AbstractArrow.Pickup.DISALLOWED;
        double dx = target.getX() - getX();
        double dy = target.getY(0.3333333333333333) - trident.getY();
        double dz = target.getZ() - getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        trident.shoot(dx, dy + horizontal * 0.2, dz, 1.6F, 14 - level().getDifficulty().getId() * 4);
        Sfx.at(this, SoundEvents.TRIDENT_THROW, 1.0F, 1.0F);
        server.addFreshEntity(trident);
        conjured.add(trident);
    }

    private void throwItem(ServerLevel server, ItemStack stack, LivingEntity target) {
        Item item = stack.getItem();
        if (item instanceof FireChargeItem) {
            Vec3 dir = new Vec3(target.getX() - getX(), target.getY(0.5) - getY(0.5), target.getZ() - getZ());
            SmallFireball fireball = new SmallFireball(server, this, dir.normalize());
            fireball.setPos(fireball.getX(), getY(0.5) + 0.3, fireball.getZ());
            server.addFreshEntity(fireball);
            conjured.add(fireball);
            playSound(SoundEvents.FIRECHARGE_USE, 0.8F, 1.0F + (random.nextFloat() - 0.5F) * 0.2F);
            return;
        }
        if (item instanceof ThrowablePotionItem) {
            throwPotion(server, stack, target, false);
            return;
        }
        ThrowableItemProjectile projectile = item instanceof EggItem ? new ThrownEgg(server, this) : new Snowball(server, this);
        projectile.setItem(stack.copyWithCount(1));
        double dx = target.getX() - getX();
        double dy = target.getEyeY() - 1.1 - projectile.getY();
        double dz = target.getZ() - getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz) * 0.2;
        projectile.shoot(dx, dy + horizontal, dz, 1.6F, 12.0F);
        server.addFreshEntity(projectile);
        conjured.add(projectile);
        playSound(item instanceof EggItem ? SoundEvents.EGG_THROW : SoundEvents.SNOWBALL_THROW, 0.6F, 0.4F / (random.nextFloat() * 0.4F + 0.8F));
    }

    private void throwPotion(ServerLevel server, ItemStack stack, LivingEntity target, boolean atFeet) {
        ThrownPotion potion = new ThrownPotion(server, this);
        potion.setItem(stack.copyWithCount(1));
        potion.setXRot(potion.getXRot() + 20.0F);
        Vec3 motion = target.getDeltaMovement();
        double dx = target.getX() + motion.x - getX();
        double dy = (atFeet ? target.getY() : target.getEyeY() - 1.1) - getY();
        double dz = target.getZ() + motion.z - getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        potion.shoot(dx, dy + horizontal * 0.2, dz, 0.75F, atFeet ? 2.0F : 8.0F);
        server.addFreshEntity(potion);
        conjured.add(potion);
        playSound(stack.is(Items.LINGERING_POTION) ? SoundEvents.LINGERING_POTION_THROW : SoundEvents.SPLASH_POTION_THROW, 0.8F,
                0.8F + random.nextFloat() * 0.4F);
    }

    /** A kind potion never hits enemies: it is splashed at the owner's feet when they need it. */
    private void supportOwner(LivingEntity owner) {
        if (abilityCooldown > 0 || owner.level() != level() || distanceToSqr(owner) > 10.0 * 10.0 || !getSensing().hasLineOfSight(owner)
                || !(level() instanceof ServerLevel server)) {
            return;
        }
        ItemStack stack = getMainHandItem();
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        if (contents == null) {
            return;
        }
        boolean needed = owner.getHealth() < owner.getMaxHealth() - 1.0F;
        for (MobEffectInstance effect : contents.getAllEffects()) {
            if (!effect.getEffect().value().isInstantenous() && !owner.hasEffect(effect.getEffect())) {
                needed = true;
            }
        }
        if (needed) {
            abilityCooldown = Balance.ANIMATED_POTION_OWNER_INTERVAL;
            lookAt(owner, 360.0F, 360.0F);
            swing(InteractionHand.MAIN_HAND);
            throwPotion(server, stack, owner, true);
        }
    }

    /** A shield taunts nearby hostiles into attacking it instead. */
    private void taunt() {
        if (tauntCooldown > 0 || !(level() instanceof ServerLevel server)) {
            return;
        }
        tauntCooldown = Balance.ANIMATED_SHIELD_TAUNT_INTERVAL;
        double r = Balance.ANIMATED_SHIELD_TAUNT_RANGE;
        List<Mob> mobs = level().getEntitiesOfClass(Mob.class, getBoundingBox().inflate(r, 4.0, r),
                m -> m instanceof Enemy && m.isAlive() && m.getTarget() != this && !isAlly(m) && m.distanceToSqr(this) < r * r);
        if (mobs.isEmpty()) {
            return;
        }
        for (Mob mob : mobs) {
            mob.setTarget(this);
            server.sendParticles(ParticleTypes.ANGRY_VILLAGER, mob.getX(), mob.getEyeY() + 0.5, mob.getZ(), 1, 0.1, 0.1, 0.1, 0.0);
        }
        swing(InteractionHand.MAIN_HAND);
        server.sendParticles(ParticleTypes.CRIT, getX(), getY(0.6), getZ(), 14, 0.4, 0.3, 0.4, 0.15);
        Sfx.at(this, SoundEvents.SHIELD_BLOCK, 0.9F, 0.8F);
        Sfx.at(this, SoundEvents.RAVAGER_ROAR, 0.25F, 2.0F);
    }

    /** An ender pearl blinks next to its target. */
    private void blinkTo(LivingEntity target) {
        abilityCooldown = Balance.ANIMATED_BLINK_INTERVAL;
        Vec3 from = position();
        Vec3 away = new Vec3(from.x - target.getX(), 0.0, from.z - target.getZ());
        away = away.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
        for (int i = 0; i < 12; i++) {
            double angle = (random.nextDouble() - 0.5) * Math.PI * (i < 6 ? 0.8 : 2.0);
            Vec3 dir = away.yRot((float) angle);
            double distance = 1.4 + random.nextDouble();
            if (randomTeleport(target.getX() + dir.x * distance, target.getY() + 1.0, target.getZ() + dir.z * distance, false)) {
                if (level() instanceof ServerLevel server) {
                    server.sendParticles(ParticleTypes.PORTAL, from.x, from.y + 0.4, from.z, 24, 0.25, 0.4, 0.25, 0.3);
                    server.sendParticles(ParticleTypes.PORTAL, getX(), getY() + 0.4, getZ(), 24, 0.25, 0.4, 0.25, 0.3);
                    server.playSound(null, from.x, from.y, from.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 0.6F, 1.6F);
                }
                Sfx.at(this, SoundEvents.ENDERMAN_TELEPORT, 0.6F, 1.6F);
                getNavigation().stop();
                lookAt(target, 360.0F, 360.0F);
                return;
            }
        }
    }

    private void setFuse(int ticks) {
        fuse = ticks;
        entityData.set(DATA_FUSING, ticks > 0);
    }

    /** TNT goes off without breaking blocks, sparing friends and dropped items, and spends one stick. */
    private void detonate() {
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        setFuse(0);
        abilityCooldown = Balance.ANIMATED_TNT_REARM;
        Vec3 c = position().add(0.0, 0.35, 0.0);
        server.explode(this, null, new ExplosionDamageCalculator() {
            @Override
            public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
                return spares(entity);
            }

            @Override
            public float getKnockbackMultiplier(Entity entity) {
                return spares(entity) ? 1.0F : 0.0F;
            }
        }, c.x, c.y, c.z, Balance.ANIMATED_TNT_RADIUS, false, Level.ExplosionInteraction.NONE);
        ItemStack rest = getMainHandItem().copy();
        rest.shrink(1);
        if (rest.isEmpty()) {
            // the last stick: nothing is left to fall asleep
            released = true;
            setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            discard();
        } else {
            setItemSlot(EquipmentSlot.MAINHAND, rest);
        }
    }

    /** True for what a blast may hurt: living foes only. */
    private boolean spares(Entity entity) {
        return entity instanceof LivingEntity living && living != this && !(living instanceof ArmorStand) && !isAlly(living);
    }

    private void feed(LivingEntity owner) {
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        ItemStack stack = getMainHandItem();
        FoodProperties food = stack.getFoodProperties(owner);
        if (food == null) {
            return;
        }
        feedCooldown = Balance.ANIMATED_FEED_INTERVAL;
        owner.heal(Math.max(1, food.nutrition()));
        swing(InteractionHand.MAIN_HAND);
        Vec3 mouth = owner.getEyePosition().add(owner.getViewVector(1.0F).scale(0.35)).subtract(0.0, 0.15, 0.0);
        server.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, stack.copyWithCount(1)), mouth.x, mouth.y, mouth.z, 10,
                0.12, 0.08, 0.12, 0.05);
        server.sendParticles(ParticleTypes.HEART, owner.getX(), owner.getY() + owner.getBbHeight() + 0.3, owner.getZ(), 2, 0.3, 0.1, 0.3, 0.0);
        server.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.GENERIC_EAT, SoundSource.PLAYERS, 0.8F,
                1.0F + random.nextFloat() * 0.2F);
        Sfx.at(owner, SoundEvents.PLAYER_BURP, 0.5F, 0.9F + random.nextFloat() * 0.1F);
    }

    private boolean ownerNeedsFood(@Nullable LivingEntity owner) {
        return kind() == Kind.FOOD && feedCooldown <= 0 && owner != null && owner.isAlive() && owner.level() == level()
                && owner.getHealth() < owner.getMaxHealth() && distanceToSqr(owner) < 24.0 * 24.0;
    }

    // ------------------------------------------------------------------ taking damage, dying, falling asleep
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide()) {
            return false;
        }
        Entity attacker = source.getEntity();
        if (attacker != null && attacker != this && isAlly(attacker) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        if (kind() == Kind.SHIELD && !source.is(DamageTypeTags.BYPASSES_SHIELD) && facing(source)) {
            // a raised shield: blocked, and whoever swung is thrown back
            level().broadcastEntityEvent(this, (byte) 29);
            if (source.getDirectEntity() instanceof LivingEntity melee) {
                melee.knockback(0.8, getX() - melee.getX(), getZ() - melee.getZ());
            }
            return false;
        }
        boolean hurt = super.hurt(source, amount);
        if (hurt && kind() == Kind.SHIELD && source.getDirectEntity() instanceof LivingEntity melee && melee != this) {
            melee.knockback(0.6, getX() - melee.getX(), getZ() - melee.getZ());
        }
        return hurt;
    }

    private boolean facing(DamageSource source) {
        Vec3 from = source.getSourcePosition();
        if (from == null) {
            return false;
        }
        Vec3 look = calculateViewVector(0.0F, getYHeadRot());
        Vec3 toward = from.vectorTo(position());
        toward = new Vec3(toward.x, 0.0, toward.z).normalize();
        return toward.dot(look) < 0.0;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (dead && !level().isClientSide()) {
            // no corpse: it simply falls asleep again where it fell
            releaseStack(true);
            discard();
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (reason.shouldDestroy() && !level().isClientSide()) {
            // killed or discarded by anything else (commands, other mods): the item is never lost
            releaseStack(false);
        }
        super.remove(reason);
    }

    /** Puts exactly its stack back into the world as a normal dropped item (once). */
    private void releaseStack(boolean effects) {
        if (released || !(level() instanceof ServerLevel server)) {
            return;
        }
        released = true;
        for (Projectile p : conjured) {
            if (!p.isRemoved() && p instanceof AbstractArrow) {
                vanish(p);
            }
        }
        conjured.clear();
        ItemStack stack = getMainHandItem();
        setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        if (!stack.isEmpty()) {
            ItemEntity drop = new ItemEntity(server, getX(), getY() + 0.2, getZ(), stack,
                    (random.nextDouble() - 0.5) * 0.08, 0.2, (random.nextDouble() - 0.5) * 0.08);
            drop.setDefaultPickUpDelay();
            server.addFreshEntity(drop);
        }
        if (effects) {
            Vec3 c = position().add(0.0, 0.3, 0.0);
            server.sendParticles(ParticleTypes.POOF, c.x, c.y, c.z, 8, 0.2, 0.15, 0.2, 0.02);
            server.sendParticles(GREEN_DUST, c.x, c.y + 0.2, c.z, 10, 0.3, 0.25, 0.3, 0.0);
            server.sendParticles(ModParticles.LIGHT_MOTE.get(), c.x, c.y + 0.2, c.z, 5, 0.2, 0.2, 0.2, 0.01);
            Sfx.at(this, SoundEvents.ALLAY_ITEM_TAKEN, 0.8F, 1.0F);
            Sfx.at(this, SoundEvents.CHICKEN_EGG, 0.6F, 1.3F);
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player.isShiftKeyDown() && player.getUUID().equals(getOwnerUUID())) {
            if (!level().isClientSide()) {
                sleepSoon();
            }
            return InteractionResult.sidedSuccess(level().isClientSide());
        }
        return super.mobInteract(player, hand);
    }

    // ------------------------------------------------------------------ nature of an animated object
    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public int getMaxFallDistance() {
        return 12;
    }

    @Override
    public boolean fireImmune() {
        return super.fireImmune() || getMainHandItem().has(DataComponents.FIRE_RESISTANT);
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ALLAY_HURT;
    }

    @Nullable
    @Override
    protected SoundEvent getDeathSound() {
        return null;
    }

    @Override
    public float getVoicePitch() {
        return 1.45F + (random.nextFloat() - 0.5F) * 0.2F;
    }

    @Override
    protected float getSoundVolume() {
        return 0.6F;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        // tiny wooden taps
        playSound(SoundEvents.BAMBOO_WOOD_STEP, 0.2F, 1.8F + random.nextFloat() * 0.4F);
    }

    @Override
    protected float nextStep() {
        return moveDist + 0.45F;
    }

    // ------------------------------------------------------------------ saving
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        UUID owner = getOwnerUUID();
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putInt("Life", life);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(DATA_OWNER, tag.hasUUID("Owner") ? Optional.of(tag.getUUID("Owner")) : Optional.empty());
        life = tag.contains("Life") ? Math.max(1, tag.getInt("Life")) : Balance.ANIMATED_LIFETIME;
        entityData.set(DATA_LIFE, life);
        kind = null;
        attributesDirty = true;
    }

    // ------------------------------------------------------------------ game-bus hooks (registered in GearEvents)
    /** Animated items never hurt their friends, and what they defeat counts as their owner's (XP and player-only drops). */
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof AnimatedItemEntity animated) || event.getEntity().level().isClientSide()) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (victim == animated) {
            return;
        }
        if (animated.isAlly(victim)) {
            event.setCanceled(true);
            return;
        }
        if (animated.getOwner() instanceof Player player) {
            victim.setLastHurtByPlayer(player);
        }
    }

    /** Their fire charges never set the world alight. */
    public static void onMobGriefing(EntityMobGriefingEvent event) {
        if (event.getEntity() instanceof AnimatedItemEntity) {
            event.setCanGrief(false);
        }
    }

    // ------------------------------------------------------------------ goals
    /** Food: runs to a hurt owner and feeds them. */
    private class FeedOwnerGoal extends Goal {
        private int repath;

        FeedOwnerGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return ownerNeedsFood(getOwner());
        }

        @Override
        public void start() {
            repath = 0;
        }

        @Override
        public void stop() {
            getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity owner = getOwner();
            if (owner == null) {
                return;
            }
            getLookControl().setLookAt(owner, 30.0F, 30.0F);
            if (distanceToSqr(owner) <= 2.4 * 2.4) {
                getNavigation().stop();
                feed(owner);
            } else if (--repath <= 0) {
                repath = 8;
                getNavigation().moveTo(owner, 1.3);
            }
        }
    }

    /** TNT: rushes in, hisses and goes off. */
    private class TntGoal extends Goal {
        private int repath;

        TntGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = getTarget();
            return kind() == Kind.TNT && target != null && target.isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse() || fuse > 0;
        }

        @Override
        public void start() {
            repath = 0;
            setAggressive(true);
        }

        @Override
        public void stop() {
            setAggressive(false);
            getNavigation().stop();
            setFuse(0);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = getTarget();
            if (fuse > 0) {
                getNavigation().stop();
                if (target == null || !target.isAlive() || distanceToSqr(target) > 7.0 * 7.0) {
                    setFuse(0);
                    return;
                }
                getLookControl().setLookAt(target, 30.0F, 30.0F);
                if (--fuse <= 0) {
                    detonate();
                }
                return;
            }
            if (target == null || pendingLeap > 0) {
                return;
            }
            getLookControl().setLookAt(target, 30.0F, 30.0F);
            double d = distanceToSqr(target);
            if (d < 2.5 * 2.5 && abilityCooldown <= 0) {
                setFuse(Balance.ANIMATED_TNT_FUSE);
                getNavigation().stop();
                Sfx.at(AnimatedItemEntity.this, SoundEvents.TNT_PRIMED, 1.0F, 1.2F);
                Sfx.at(AnimatedItemEntity.this, SoundEvents.CREEPER_PRIMED, 0.6F, 1.8F);
                return;
            }
            if (--repath <= 0) {
                repath = 5;
                getNavigation().moveTo(target, 1.35);
            }
            if (onGround() && leapCooldown <= 0 && d > 2.5 * 2.5 && d < 4.5 * 4.5 && getSensing().hasLineOfSight(target)) {
                beginLeap(target);
            }
        }
    }

    /** Bows, tridents and throwables keep their distance, strafe and shoot; they bonk whatever comes too close. */
    private class RangedGoal extends Goal {
        private int repath;
        private int seeTime;
        private int strafeTime;
        private boolean strafeLeft;
        private boolean strafeBack;

        RangedGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = getTarget();
            return target != null && target.isAlive() && kind().ranged();
        }

        @Override
        public void start() {
            setAggressive(true);
            seeTime = 0;
            repath = 0;
        }

        @Override
        public void stop() {
            setAggressive(false);
            getNavigation().stop();
            seeTime = 0;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = getTarget();
            if (target == null || busy()) {
                return;
            }
            double range = preferredRange();
            double d = distanceToSqr(target);
            boolean see = getSensing().hasLineOfSight(target);
            seeTime = see ? Math.max(1, seeTime + 1) : Math.min(-1, seeTime - 1);
            if (see && d < (range + 4.0) * (range + 4.0)) {
                getNavigation().stop();
                if (++strafeTime >= 20) {
                    strafeTime = 0;
                    if (random.nextFloat() < 0.3F) {
                        strafeLeft = !strafeLeft;
                    }
                    if (random.nextFloat() < 0.3F) {
                        strafeBack = !strafeBack;
                    }
                }
                if (d < range * range * 0.36) {
                    strafeBack = true;
                } else if (d > range * range) {
                    strafeBack = false;
                }
                getMoveControl().strafe(strafeBack ? -0.5F : 0.5F, strafeLeft ? 0.5F : -0.5F);
                lookAt(target, 30.0F, 30.0F);
            } else if (--repath <= 0) {
                repath = 10;
                getNavigation().moveTo(target, 1.15);
            }
            getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (attackCooldown <= 0 && isWithinMeleeAttackRange(target)) {
                strike(target);
                attackCooldown = 20;
            } else if (rangedCooldown <= 0 && see && seeTime >= 5 && d <= (range + 8.0) * (range + 8.0)) {
                fire(target);
                rangedCooldown = rangedInterval();
            }
        }
    }

    /** Blades, blocks and everything else: run in, crouch, leap and strike. */
    private class MeleeGoal extends Goal {
        private int repath;

        MeleeGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = getTarget();
            return target != null && target.isAlive() && kind().brawler() && !ownerNeedsFood(getOwner());
        }

        @Override
        public void start() {
            setAggressive(true);
            repath = 0;
        }

        @Override
        public void stop() {
            setAggressive(false);
            getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = getTarget();
            if (target == null) {
                return;
            }
            getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (pendingLeap > 0 || busy()) {
                return;
            }
            double d = distanceToSqr(target);
            if (kind() == Kind.PEARL && abilityCooldown <= 0 && d > 9.0 && d < 24.0 * 24.0) {
                blinkTo(target);
                d = distanceToSqr(target);
            }
            if (--repath <= 0) {
                repath = 4 + random.nextInt(7);
                if (!getNavigation().moveTo(target, 1.25)) {
                    repath += 15;
                }
            }
            boolean see = getSensing().hasLineOfSight(target);
            if (see && onGround() && leapCooldown <= 0 && d > 2.0 * 2.0 && d < 4.5 * 4.5) {
                beginLeap(target);
                return;
            }
            if (attackCooldown <= 0 && see && isWithinMeleeAttackRange(target)) {
                strike(target);
                attackCooldown = meleeInterval();
            }
        }
    }

    /** Idle: trots after its owner. */
    private class FollowOwnerGoal extends Goal {
        private int repath;

        FollowOwnerGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity owner = getOwner();
            return owner != null && !owner.isSpectator() && owner.level() == level() && getTarget() == null
                    && distanceToSqr(owner) > Balance.ANIMATED_FOLLOW_START * Balance.ANIMATED_FOLLOW_START;
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity owner = getOwner();
            return owner != null && owner.level() == level() && getTarget() == null && !getNavigation().isDone()
                    && distanceToSqr(owner) > Balance.ANIMATED_FOLLOW_STOP * Balance.ANIMATED_FOLLOW_STOP;
        }

        @Override
        public void start() {
            repath = 0;
        }

        @Override
        public void stop() {
            getNavigation().stop();
        }

        @Override
        public void tick() {
            LivingEntity owner = getOwner();
            if (owner == null) {
                return;
            }
            getLookControl().setLookAt(owner, 10.0F, getMaxHeadXRot());
            if (--repath <= 0) {
                repath = adjustedTickDelay(10);
                getNavigation().moveTo(owner, 1.15);
            }
        }
    }
}
