package com.lacuna.mod.mummy;

import com.lacuna.mod.Lacuna;
import com.lacuna.mod.LacunaConfig;
import com.lacuna.mod.registry.ModAttachments;
import com.lacuna.mod.registry.ModItems;
import com.lacuna.mod.registry.ModParticles;
import com.lacuna.mod.util.LacunaAdvancements;
import com.lacuna.mod.util.SafeRun;
import com.lacuna.mod.util.Sfx;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.living.LivingConversionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobDespawnEvent;
import net.neoforged.neoforge.event.entity.living.MobSplitEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Starlace Bandage mummification.
 * <ul>
 *   <li><b>Wrapping</b>: use a bandage on a creature. A peaceful one takes 1, a hostile or large one 2, a large hostile
 *       one 3 ({@link #wrapsNeeded}), applied one by one: each wrap shows as a ring of glints (bright for the wraps on,
 *       ash for those still missing) and slows the creature; unfinished wraps loosen after {@link #PARTIAL_TICKS}
 *       and fall off (the bandages drop back). Players, bosses, {@code #lacuna:bandage_refuses} (the Comically Large
 *       Bag's refusals among them), someone else's pets and ridden creatures are refused.</li>
 *   <li><b>A mummy</b>: its AI is off (NoAI) and it is silent, so it neither moves nor attacks, and deals no damage at
 *       all; it still falls, slides and can be pushed (the wraps keep their own simple physics, since NoAI alone would
 *       leave it hanging in the air). It takes {@link #DAMAGE_TAKEN} of all damage, does not catch fire (nor burn in
 *       the sun), never despawns, never converts (zombie to drowned...), does not teleport (endermen) and accepts no
 *       interaction but shears and name tags. Saved with the creature.</li>
 *   <li><b>Unwrapping</b>: shears cut the wraps off and the creature is restored exactly as it was (its own NoAI and
 *       Silent flags); each bandage comes back with {@link #RETURN_CHANCE}, as it does when a mummy dies.</li>
 * </ul>
 * Client: {@code client/mummy/MummyLayer} draws the wraps on every living-entity model.
 */
public final class Mummification {
    public static final String FEATURE = "starlace_bandage";
    public static final String ADVANCEMENT = "lacuna/its_a_wrap";
    public static final TagKey<EntityType<?>> REFUSED = TagKey.create(Registries.ENTITY_TYPE, Lacuna.id("bandage_refuses"));
    public static final ResourceLocation SLOW_ID = Lacuna.id("starlace_wraps");

    /** Time to finish wrapping before the wraps loosen (15 s). */
    public static final int PARTIAL_TICKS = 300;
    /** Movement slowdown per wrap on a partly wrapped creature (fraction of its speed). */
    public static final double SLOW_PER_WRAP = 0.3;
    /** A mummy takes this much of any damage. */
    public static final float DAMAGE_TAKEN = 0.5F;
    /** Chance for each bandage to come back when a mummy is unwrapped or dies. */
    public static final float RETURN_CHANCE = 0.5F;
    /** Bounding-box volume (width x width x height) above which a creature counts as large. */
    public static final double LARGE_VOLUME = 2.0;
    public static final int MAX_WRAPS = 3;

    private Mummification() {}

    public static void register(IEventBus bus) {
        bus.addListener(Mummification::onInteractAt);
        bus.addListener(Mummification::onInteract);
        bus.addListener(Mummification::onTick);
        bus.addListener(Mummification::onIncomingDamage);
        bus.addListener(Mummification::onDespawn);
        bus.addListener(Mummification::onTeleport);
        bus.addListener(Mummification::onConvert);
        bus.addListener(Mummification::onSplit);
        bus.addListener(Mummification::onDrops);
    }

    // ------------------------------------------------------------------ state

    @Nullable
    public static Mummy of(@Nullable Entity entity) {
        return entity instanceof Mob mob ? mob.getExistingDataOrNull(ModAttachments.MUMMY) : null;
    }

    /** A finished mummy. */
    public static boolean sealed(@Nullable Entity entity) {
        Mummy m = of(entity);
        return m != null && m.done();
    }

    /** 1 for a peaceful creature, +1 if hostile, +1 if large; at most {@link #MAX_WRAPS}. */
    public static int wrapsNeeded(Mob mob) {
        int n = mob instanceof Enemy ? 2 : 1;
        double volume = mob.getBbWidth() * mob.getBbWidth() * mob.getBbHeight();
        if (volume > LARGE_VOLUME) {
            n++;
        }
        return Math.min(n, MAX_WRAPS);
    }

    // ------------------------------------------------------------------ interaction

    private static void onInteractAt(PlayerInteractEvent.EntityInteractSpecific event) {
        InteractionResult result = handle(event.getEntity(), event.getHand(), event.getItemStack(), event.getTarget());
        if (result != null) {
            event.setCanceled(true);
            event.setCancellationResult(result);
        }
    }

    private static void onInteract(PlayerInteractEvent.EntityInteract event) {
        InteractionResult result = handle(event.getEntity(), event.getHand(), event.getItemStack(), event.getTarget());
        if (result != null) {
            event.setCanceled(true);
            event.setCancellationResult(result);
        }
    }

    /**
     * Bandage on a creature: wrap it. Shears on a wrapped one: unwrap it. Anything else on a mummy: nothing happens (no
     * trading, riding, leashing, milking or shearing it), except a name tag. Null leaves the interaction to vanilla.
     */
    @Nullable
    private static InteractionResult handle(Player player, InteractionHand hand, ItemStack stack, Entity target) {
        if (player.isSpectator() || SafeRun.isDisabled(FEATURE)) {
            return null;
        }
        Mummy m = of(target);
        if (m != null && isShears(stack)) {
            if (player instanceof ServerPlayer sp && target instanceof Mob mob) {
                return SafeRun.call(FEATURE, () -> unwrap(sp, hand, stack, mob, m), InteractionResult.FAIL);
            }
            return InteractionResult.SUCCESS;
        }
        if (stack.is(ModItems.STARLACE_BANDAGE.get()) && (target instanceof Mob || target instanceof Player)) {
            if (!LacunaConfig.get(LacunaConfig.STARLACE_BANDAGE_ENABLED)) {
                return null;
            }
            if (player instanceof ServerPlayer sp) {
                return SafeRun.call(FEATURE, () -> wrap(sp, stack, target), InteractionResult.FAIL);
            }
            return InteractionResult.SUCCESS;
        }
        if (m != null && m.done() && !stack.is(Items.NAME_TAG)) {
            return InteractionResult.FAIL; // wrapped tight
        }
        return null;
    }

    private static boolean isShears(ItemStack stack) {
        return stack.getItem() instanceof ShearsItem || stack.is(Tags.Items.TOOLS_SHEAR);
    }

    /** Why this creature cannot be wrapped (a lang key), or null when it can. */
    @Nullable
    private static String refusal(ServerPlayer player, Entity target) {
        if (target instanceof Player) {
            return "lacuna.mummy.no_players";
        }
        if (!(target instanceof Mob mob) || !mob.isAlive()) {
            return "lacuna.mummy.wont_hold";
        }
        if (!player.getAbilities().mayBuild) {
            return "lacuna.mummy.protected";
        }
        if (mob.isMultipartEntity() || mob.getType().is(Tags.EntityTypes.BOSSES) || mob.getType().is(REFUSED)) {
            return "lacuna.mummy.too_mighty";
        }
        if (mob.getControllingPassenger() instanceof Player || mob.getPassengers().stream().anyMatch(p -> p instanceof Player)) {
            return "lacuna.mummy.ridden";
        }
        if (mob instanceof OwnableEntity ownable && !player.isCreative()) {
            UUID owner = ownable.getOwnerUUID();
            if (owner != null && !owner.equals(player.getUUID())) {
                return "lacuna.mummy.not_yours";
            }
        }
        return null;
    }

    private static InteractionResult wrap(ServerPlayer player, ItemStack stack, Entity target) {
        String refusal = refusal(player, target);
        if (refusal != null) {
            player.displayClientMessage(Component.translatable(refusal), true);
            return InteractionResult.FAIL;
        }
        Mob mob = (Mob) target;
        ServerLevel level = player.serverLevel();
        Mummy m = of(mob);
        if (m != null && m.done()) {
            player.displayClientMessage(Component.translatable("lacuna.mummy.already"), true);
            return InteractionResult.FAIL;
        }
        long now = level.getGameTime();
        if (m == null) {
            m = new Mummy(0, wrapsNeeded(mob), mob.isNoAi(), mob.isSilent(), now);
        }
        m = m.wrapped(now);
        stack.consume(1, player);
        player.awardStat(Stats.ITEM_USED.get(ModItems.STARLACE_BANDAGE.get()));
        mob.setData(ModAttachments.MUMMY, m);
        if (m.done()) {
            seal(level, mob);
            player.displayClientMessage(Component.translatable("lacuna.mummy.sealed", mob.getDisplayName()), true);
            LacunaAdvancements.award(player, ADVANCEMENT);
        } else {
            slow(mob, m.wraps());
            rings(level, mob, m);
            Sfx.at(mob, SoundEvents.WOOL_PLACE, 1.0F, 1.0F + 0.12F * m.wraps());
            Sfx.at(mob, SoundEvents.ARMOR_EQUIP_LEATHER.value(), 0.8F, 1.2F);
            Sfx.at(mob, SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F, 1.1F + 0.2F * m.wraps());
            player.displayClientMessage(Component.translatable("lacuna.mummy.progress", m.wraps(), m.needed()), true);
        }
        mob.syncData(ModAttachments.MUMMY);
        return InteractionResult.SUCCESS;
    }

    /** The last wrap: the creature is frozen in its starlace. */
    private static void seal(ServerLevel level, Mob mob) {
        mob.setNoAi(true);
        mob.setSilent(true);
        mob.getNavigation().stop();
        mob.setTarget(null);
        mob.setAggressive(false);
        mob.setJumping(false);
        mob.setXxa(0.0F);
        mob.setYya(0.0F);
        mob.setZza(0.0F);
        mob.setSprinting(false);
        if (mob instanceof Creeper creeper && !creeper.isIgnited()) {
            creeper.setSwellDir(-1);
        }
        unslow(mob);
        mob.clearFire();
        Vec3 v = mob.getDeltaMovement();
        mob.setDeltaMovement(0.0, Math.min(0.0, v.y), 0.0);
        Vec3 c = mob.position().add(0.0, mob.getBbHeight() * 0.5, 0.0);
        double rx = mob.getBbWidth() * 0.6;
        double ry = mob.getBbHeight() * 0.45;
        level.sendParticles(ModParticles.LIGHT_MOTE.get(), c.x, c.y, c.z, 24, rx, ry, rx, 0.02);
        level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 6, rx, ry, rx, 0.03);
        level.sendParticles(bandageBits(), c.x, c.y, c.z, 10, rx, ry, rx, 0.05);
        Sfx.at(mob, SoundEvents.WOOL_PLACE, 1.2F, 0.8F);
        Sfx.at(mob, SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1.0F, 0.9F);
        Sfx.at(mob, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.4F, 1.5F);
        Sfx.at(mob, SoundEvents.AMETHYST_CLUSTER_PLACE, 1.0F, 1.3F);
    }

    /**
     * Shears: the wraps come off and the creature is as it was. A finished mummy gives each bandage back with
     * {@link #RETURN_CHANCE}; a partly wrapped creature gives them all back.
     */
    private static InteractionResult unwrap(ServerPlayer player, InteractionHand hand, ItemStack shears, Mob mob, Mummy m) {
        if (!player.getAbilities().mayBuild) {
            player.displayClientMessage(Component.translatable("lacuna.mummy.protected"), true);
            return InteractionResult.FAIL;
        }
        ServerLevel level = player.serverLevel();
        release(mob, m);
        int back = m.done() ? returned(mob, m.wraps()) : m.wraps();
        drop(mob, back);
        shears.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
        Vec3 c = mob.position().add(0.0, mob.getBbHeight() * 0.5, 0.0);
        double rx = mob.getBbWidth() * 0.6;
        double ry = mob.getBbHeight() * 0.45;
        level.sendParticles(bandageBits(), c.x, c.y, c.z, 16, rx, ry, rx, 0.08);
        level.sendParticles(ModParticles.LIGHT_MOTE.get(), c.x, c.y, c.z, 10, rx, ry, rx, 0.03);
        Sfx.at(mob, SoundEvents.SHEEP_SHEAR, 1.0F, 1.0F);
        Sfx.at(mob, SoundEvents.WOOL_BREAK, 1.0F, 1.0F);
        Sfx.at(mob, SoundEvents.AMETHYST_BLOCK_BREAK, 0.8F, 1.6F);
        return InteractionResult.SUCCESS;
    }

    /** Takes the wraps off: the creature's own NoAI/Silent flags back, no slowdown, no attachment. */
    private static void release(Mob mob, Mummy m) {
        if (m.done()) {
            mob.setNoAi(m.wasNoAi());
            mob.setSilent(m.wasSilent());
        }
        unslow(mob);
        mob.removeData(ModAttachments.MUMMY);
        mob.syncData(ModAttachments.MUMMY);
    }

    private static int returned(Mob mob, int wraps) {
        int n = 0;
        for (int i = 0; i < wraps; i++) {
            if (mob.getRandom().nextFloat() < RETURN_CHANCE) {
                n++;
            }
        }
        return n;
    }

    private static void drop(Mob mob, int count) {
        if (count > 0) {
            mob.spawnAtLocation(new ItemStack(ModItems.STARLACE_BANDAGE.get(), count), mob.getBbHeight() * 0.5F);
        }
    }

    private static ParticleOptions bandageBits() {
        return new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(ModItems.STARLACE_BANDAGE.get()));
    }

    /** The wrap counter: a bright ring of glints per wrap on (bottom up), a faint ring of ash per wrap still missing. */
    private static void rings(ServerLevel level, Mob mob, Mummy m) {
        double r = mob.getBbWidth() * 0.5 + 0.25;
        for (int i = 0; i < m.needed(); i++) {
            double y = mob.getY() + mob.getBbHeight() * (i + 0.5) / m.needed();
            boolean on = i < m.wraps();
            int n = on ? 12 : 6;
            for (int k = 0; k < n; k++) {
                double a = Math.PI * 2.0 * k / n + i * 0.4;
                double x = mob.getX() + Math.cos(a) * r;
                double z = mob.getZ() + Math.sin(a) * r;
                level.sendParticles(on ? ModParticles.LIGHT_MOTE.get() : ParticleTypes.WHITE_ASH, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
        level.sendParticles(bandageBits(), mob.getX(), mob.getY() + mob.getBbHeight() * 0.5, mob.getZ(), 4,
                mob.getBbWidth() * 0.4, mob.getBbHeight() * 0.3, mob.getBbWidth() * 0.4, 0.04);
    }

    private static void slow(Mob mob, int wraps) {
        AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SLOW_ID);
            // transient: never saved (a reloaded creature's partial wraps loosen instead of slowing it again)
            speed.addTransientModifier(new AttributeModifier(SLOW_ID, -Math.min(0.9, SLOW_PER_WRAP * wraps),
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    private static void unslow(Mob mob) {
        AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SLOW_ID);
        }
    }

    // ------------------------------------------------------------------ ticking

    private static void onTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Mob mob) || !mob.hasData(ModAttachments.MUMMY)) {
            return;
        }
        Mummy m = mob.getData(ModAttachments.MUMMY);
        if (mob.level() instanceof ServerLevel level) {
            SafeRun.run(FEATURE, () -> serverTick(level, mob, m));
        } else if (m.done() && !mob.isInvisible() && mob.getRandom().nextInt(12) == 0) {
            // a starlit glint now and then on the wraps
            double w = mob.getBbWidth() * 0.6;
            mob.level().addParticle(ModParticles.LIGHT_MOTE.get(), mob.getX() + (mob.getRandom().nextDouble() - 0.5) * 2.0 * w,
                    mob.getY() + mob.getRandom().nextDouble() * mob.getBbHeight(), mob.getZ() + (mob.getRandom().nextDouble() - 0.5) * 2.0 * w,
                    0.0, 0.005, 0.0);
        }
    }

    private static void serverTick(ServerLevel level, Mob mob, Mummy m) {
        if (!mob.isAlive()) {
            return;
        }
        if (!m.done()) {
            if (level.getGameTime() - m.lastWrap() > PARTIAL_TICKS) {
                loosen(level, mob, m);
            }
            return;
        }
        // the wraps hold: whatever cleared these (a command), the mummy stays frozen and silent until unwrapped
        if (!mob.isNoAi()) {
            mob.setNoAi(true);
        }
        if (!mob.isSilent()) {
            mob.setSilent(true);
        }
        if (mob.getRemainingFireTicks() > 0) {
            mob.clearFire(); // starlace does not burn (nor do the undead under it in the sun)
        }
        if (mob instanceof Creeper creeper && !creeper.isIgnited() && creeper.getSwellDir() > 0) {
            creeper.setSwellDir(-1);
        }
        settle(mob);
    }

    /**
     * NoAI leaves a creature hanging in the air: a mummy gets simple physics of its own instead. It falls (slowly in
     * fluids), slides to a stop with the ground's friction, and moves when pushed or knocked back.
     */
    private static void settle(Mob mob) {
        if (mob.isPassenger()) {
            return;
        }
        Vec3 v = mob.getDeltaMovement();
        double g = mob.getGravity();
        boolean fluid = mob.isInWater() || mob.isInLava();
        v = fluid ? new Vec3(v.x * 0.8, v.y * 0.8 - g * 0.25, v.z * 0.8) : v.add(0.0, -g, 0.0);
        if (v.lengthSqr() < 1.0E-7 && mob.onGround()) {
            return;
        }
        mob.setDeltaMovement(v);
        mob.move(MoverType.SELF, v);
        Vec3 after = mob.getDeltaMovement(); // blocked directions were zeroed by the move
        float friction = 0.91F;
        if (mob.onGround()) {
            BlockPos under = BlockPos.containing(mob.getX(), mob.getY() - 0.5, mob.getZ());
            friction = mob.level().getBlockState(under).getFriction(mob.level(), under, mob) * 0.91F;
        }
        mob.setDeltaMovement(after.x * friction, after.y * 0.98, after.z * friction);
    }

    /** Unfinished wraps come loose and fall off: the bandages drop back. */
    private static void loosen(ServerLevel level, Mob mob, Mummy m) {
        release(mob, m);
        drop(mob, m.wraps());
        level.sendParticles(bandageBits(), mob.getX(), mob.getY() + mob.getBbHeight() * 0.5, mob.getZ(), 8,
                mob.getBbWidth() * 0.4, mob.getBbHeight() * 0.3, mob.getBbWidth() * 0.4, 0.05);
        Sfx.at(mob, SoundEvents.WOOL_BREAK, 0.9F, 1.3F);
        Sfx.at(mob, SoundEvents.AMETHYST_BLOCK_HIT, 0.7F, 1.6F);
    }

    // ------------------------------------------------------------------ what a mummy does not do

    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        DamageSource source = event.getSource();
        if (sealed(source.getEntity()) || sealed(source.getDirectEntity())) {
            event.setCanceled(true); // a mummy harms nobody
            return;
        }
        if (sealed(event.getEntity()) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            event.setAmount(event.getAmount() * DAMAGE_TAKEN);
        }
    }

    private static void onDespawn(MobDespawnEvent event) {
        if (event.getEntity().hasData(ModAttachments.MUMMY)) {
            event.setResult(MobDespawnEvent.Result.DENY);
        }
    }

    private static void onTeleport(EntityTeleportEvent.EnderEntity event) {
        if (sealed(event.getEntityLiving())) {
            event.setCanceled(true);
        }
    }

    private static void onConvert(LivingConversionEvent.Pre event) {
        if (sealed(event.getEntity())) {
            event.setConversionTimer(200);
            event.setCanceled(true);
        }
    }

    /** A mummified slime that dies splits into free little slimes (they copied its NoAI). */
    private static void onSplit(MobSplitEvent event) {
        Mummy m = of(event.getParent());
        if (m != null && m.done()) {
            for (Mob child : event.getChildren()) {
                child.setNoAi(m.wasNoAi());
                child.setSilent(m.wasSilent());
            }
        }
    }

    private static void onDrops(LivingDropsEvent event) {
        LivingEntity e = event.getEntity();
        Mummy m = of(e);
        if (m == null || !(e instanceof Mob mob)) {
            return;
        }
        int back = m.done() ? returned(mob, m.wraps()) : m.wraps();
        if (back > 0) {
            ItemEntity drop = new ItemEntity(e.level(), e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(),
                    new ItemStack(ModItems.STARLACE_BANDAGE.get(), back));
            drop.setDefaultPickUpDelay();
            event.getDrops().add(drop);
        }
    }
}
