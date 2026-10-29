package com.lacuna.mod.gear;

import com.lacuna.mod.Balance;
import com.lacuna.mod.Lacuna;
import com.lacuna.mod.LacunaConfig;
import com.lacuna.mod.fx.LacunaFx;
import com.lacuna.mod.network.FxPayload;
import com.lacuna.mod.network.StarStepPayload;
import com.lacuna.mod.util.Sfx;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerFlyableFallEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The Starlace set abilities, all decided on the server (the client only reports the mid-air jump press):
 * <ul>
 *   <li><b>Constellation Step</b> (full set): jump again in mid-air, once per airtime, with a tiny cooldown. The
 *       client sends {@link StarStepPayload} on the rising edge of the jump key; {@link #onStepRequest} re-checks the
 *       set, the airtime and the cooldown, then sets the motion itself and leaves a constellation behind.</li>
 *   <li><b>Starfall</b>: with 2+ pieces fall damage is halved; with the full set a landing from more than 5 blocks
 *       does no harm and releases a shockwave of starlight that strikes and repels nearby hostile mobs.</li>
 *   <li><b>Starlit</b> (each piece): +5 % movement speed per piece at night under the open sky, or anywhere in the
 *       End, as one transient attribute modifier refreshed every second (and on armor changes).</li>
 * </ul>
 * Toggle: {@code gear.starlaceAbilities}. The Starlace Boots glide ({@link GearEvents}) is separate.
 */
public final class StarlaceSet {
    public static final ResourceLocation STARLIT_ID = Lacuna.id("starlit");
    public static final String FX_STEP = "starlace_step";
    public static final String FX_STARFALL = "starlace_starfall";
    public static final int FULL = 4;
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    /** Players who already used their Constellation Step during the current airtime (cleared on landing). */
    private static final Set<UUID> STEPPED = new HashSet<>();
    /** Game time of each player's last Constellation Step. */
    private static final Map<UUID, Long> LAST_STEP = new HashMap<>();
    /** The local player on a physical client (for tooltips); installed by the client entry point. */
    private static Supplier<Player> localPlayer = () -> null;

    private StarlaceSet() {}

    public static void register(IEventBus bus) {
        bus.addListener(StarlaceSet::onPlayerTick);
        bus.addListener(StarlaceSet::onFall);
        bus.addListener(StarlaceSet::onFlyableFall);
        bus.addListener(StarlaceSet::onEquipmentChange);
        bus.addListener(StarlaceSet::onLogout);
        bus.addListener(StarlaceSet::onServerStopped);
    }

    public static void setLocalPlayer(Supplier<Player> supplier) {
        localPlayer = supplier;
    }

    public static boolean enabled() {
        return LacunaConfig.get(LacunaConfig.STARLACE_ABILITIES_ENABLED);
    }

    /** Starlace pieces worn in their own slots (0-4). */
    public static int pieces(LivingEntity entity) {
        int n = 0;
        for (EquipmentSlot slot : SLOTS) {
            if (entity.getItemBySlot(slot).getItem() instanceof StarlaceArmorItem armor && armor.getEquipmentSlot() == slot) {
                n++;
            }
        }
        return n;
    }

    public static boolean fullSet(LivingEntity entity) {
        return pieces(entity) == FULL;
    }

    /** Night under the open sky (stars overhead), or anywhere in the End. */
    public static boolean starlit(Player player) {
        Level level = player.level();
        if (level.dimension() == Level.END) {
            return true;
        }
        if (level.dimensionType().hasFixedTime() || !level.dimensionType().hasSkyLight()) {
            return false;
        }
        long time = level.getDayTime() % 24000L;
        return time > 13000L && time < 23000L && level.canSeeSky(BlockPos.containing(player.getX(), player.getEyeY(), player.getZ()));
    }

    /** Touching ground, liquid or a ladder (or riding) ends the airtime and gives the Constellation Step back. */
    public static boolean grounded(Player player) {
        return player.onGround() || player.isInWater() || player.isInLava() || player.onClimbable() || player.isPassenger();
    }

    // ------------------------------------------------------------------ Starlit + airtime bookkeeping

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (grounded(player) || player.getAbilities().flying) {
            STEPPED.remove(player.getUUID());
        }
        if (player.tickCount % Balance.STARLACE_STARLIT_INTERVAL == 0) {
            updateStarlit(player);
        }
    }

    private static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getSlot().getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
            updateStarlit(player);
        }
    }

    /** Keeps exactly one Starlit speed modifier (or none) on the player, matching the pieces worn right now. */
    public static void updateStarlit(Player player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        int n = enabled() && player.isAlive() && !player.isSpectator() && starlit(player) ? pieces(player) : 0;
        double amount = n * Balance.STARLACE_STARLIT_SPEED;
        AttributeModifier current = speed.getModifier(STARLIT_ID);
        if (current == null ? amount <= 0.0 : current.amount() == amount) {
            return;
        }
        speed.removeModifier(STARLIT_ID);
        if (amount > 0.0) {
            speed.addTransientModifier(new AttributeModifier(STARLIT_ID, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }

    // ------------------------------------------------------------------ Constellation Step

    /** Server side of the double jump: every condition is re-checked here, the client's word is only "I pressed jump". */
    public static void onStepRequest(ServerPlayer player, StarStepPayload payload) {
        if (!enabled() || !player.isAlive() || player.isSpectator() || player.isSleeping() || !fullSet(player)
                || grounded(player) || player.getAbilities().flying || player.isFallFlying()) {
            return;
        }
        UUID id = player.getUUID();
        long now = player.level().getGameTime();
        Long last = LAST_STEP.get(id);
        if (STEPPED.contains(id) || (last != null && now - last >= 0 && now - last < Balance.STARLACE_STEP_COOLDOWN)) {
            return;
        }
        STEPPED.add(id);
        LAST_STEP.put(id, now);

        // horizontal: what the player is already carrying (from its own movement packets), plus a push along the keys
        Vec3 known = player.getKnownMovement();
        Vec3 carry = new Vec3(known.x, 0.0, known.z);
        if (carry.length() > Balance.STARLACE_STEP_MAX_CARRY) {
            carry = carry.normalize().scale(Balance.STARLACE_STEP_MAX_CARRY);
        }
        Vec3 dir = direction(payload);
        if (dir.lengthSqr() < 1.0E-4 && carry.lengthSqr() > 1.0E-4) {
            dir = carry.normalize();
        }
        Vec3 horizontal = carry.add(dir.scale(Balance.STARLACE_STEP_FORWARD));
        if (horizontal.length() > Balance.STARLACE_STEP_MAX_HORIZONTAL) {
            horizontal = horizontal.normalize().scale(Balance.STARLACE_STEP_MAX_HORIZONTAL);
        }
        double up = player.getAttributeValue(Attributes.JUMP_STRENGTH) * Balance.STARLACE_STEP_JUMP_FACTOR + player.getJumpBoostPower();
        player.setDeltaMovement(horizontal.x, up, horizontal.z);
        player.hurtMarked = true;
        player.resetFallDistance();
        player.causeFoodExhaustion(Balance.STARLACE_STEP_EXHAUSTION);

        ServerLevel level = player.serverLevel();
        Vec3 feet = player.position();
        float yaw = (float) Math.atan2(horizontal.z, horizontal.x);
        LacunaFx.send(level, feet, FxPayload.at(FX_STEP, feet).withEntity(player.getId())
                .withParams(player.getRandom().nextInt(1 << 20), yaw, (float) horizontal.length()));
        float pitch = 1.3F + player.getRandom().nextFloat() * 0.3F;
        Sfx.at(level, feet, SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F, pitch);
        Sfx.at(level, feet, SoundEvents.AMETHYST_CLUSTER_BREAK, 0.25F, 1.8F);
        Sfx.at(level, feet, SoundEvents.WIND_CHARGE_THROW, 0.35F, 1.5F);
    }

    /** The reported key direction: finite, horizontal and at most one block long. */
    private static Vec3 direction(StarStepPayload payload) {
        if (!Float.isFinite(payload.dirX()) || !Float.isFinite(payload.dirZ())) {
            return Vec3.ZERO;
        }
        Vec3 dir = new Vec3(payload.dirX(), 0.0, payload.dirZ());
        return dir.lengthSqr() > 1.0 ? dir.normalize() : dir;
    }

    // ------------------------------------------------------------------ Starfall

    private static void onFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        STEPPED.remove(player.getUUID());
        if (!enabled() || event.getDistance() <= 0.0F) {
            return;
        }
        int n = pieces(player);
        if (n == FULL && event.getDistance() > Balance.STARLACE_STARFALL_FALL && event.getDamageMultiplier() > 0.0F) {
            starfall(player, event.getDistance());
            event.setDistance(0.0F);
            event.setDamageMultiplier(0.0F);
            event.setCanceled(true);
        } else if (n >= Balance.STARLACE_HALF_FALL_PIECES) {
            event.setDamageMultiplier(event.getDamageMultiplier() * Balance.STARLACE_FALL_DAMAGE_FACTOR);
        }
    }

    /** Players who may fly (creative) never take fall damage, but a long fall still lands as a Starfall. */
    private static void onFlyableFall(PlayerFlyableFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        STEPPED.remove(player.getUUID());
        if (enabled() && !player.getAbilities().flying && event.getDistance() > Balance.STARLACE_STARFALL_FALL && fullSet(player)) {
            starfall(player, event.getDistance());
        }
    }

    /** The landing shockwave: magic damage and a push away for hostile mobs in range, light and a deep chime for everyone. */
    private static void starfall(ServerPlayer player, float distance) {
        if (player.isSpectator()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        Vec3 at = player.position();
        double r = Balance.STARLACE_STARFALL_RADIUS;
        AABB box = new AABB(at.x - r, at.y - 1.5, at.z - r, at.x + r, at.y + 2.5, at.z + r);
        DamageSource source = level.damageSources().indirectMagic(player, player);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e instanceof Enemy && e != player && e.isAlive() && !e.isAlliedTo(player))) {
            double dx = target.getX() - at.x;
            double dz = target.getZ() - at.z;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > r) {
                continue;
            }
            target.hurt(source, Balance.STARLACE_STARFALL_DAMAGE);
            double strength = Balance.STARLACE_STARFALL_KNOCKBACK * (1.0 - 0.5 * dist / r);
            if (dist < 1.0E-3) {
                double a = player.getRandom().nextDouble() * Mth.TWO_PI;
                dx = Math.cos(a);
                dz = Math.sin(a);
            }
            // knockback pushes away from the given point: pass the direction from the target back to the player
            target.knockback(strength, -dx, -dz);
            target.setDeltaMovement(target.getDeltaMovement().add(0.0, 0.2, 0.0));
            target.hurtMarked = true;
        }
        LacunaFx.send(level, at, FxPayload.at(FX_STARFALL, at).withEntity(player.getId())
                .withParams((float) r, Math.min(distance, 64.0F), player.getRandom().nextInt(1 << 20)));
        Sfx.at(level, at, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.4F, 0.5F);
        Sfx.at(level, at, SoundEvents.AMETHYST_BLOCK_CHIME, 1.6F, 0.55F);
        Sfx.at(level, at, SoundEvents.BELL_RESONATE, 0.35F, 1.9F);
        Sfx.at(level, at, SoundEvents.PLAYER_ATTACK_SWEEP, 0.7F, 0.5F);
        Sfx.at(level, at, SoundEvents.WIND_CHARGE_BURST, 0.45F, 0.7F);
    }

    // ------------------------------------------------------------------ cleanup

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        STEPPED.remove(id);
        LAST_STEP.remove(id);
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        STEPPED.clear();
        LAST_STEP.clear();
    }

    // ------------------------------------------------------------------ tooltip

    /** Lore, the piece's own line and the set bonus, lit up for the thresholds the local player's armor reaches. */
    public static void appendTooltip(StarlaceArmorItem item, List<Component> tooltip) {
        tooltip.add(Component.translatable("item.lacuna.starlace.lore").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        if (item.getType() == ArmorItem.Type.BOOTS) {
            tooltip.add(Component.translatable("item.lacuna.starlace_boots.glide").withStyle(ChatFormatting.AQUA));
        }
        Player player = localPlayer.get();
        boolean on = enabled();
        int n = player != null ? pieces(player) : 0;
        tooltip.add(Component.translatable("item.lacuna.starlace.set", n, FULL)
                .withStyle(on && n > 0 ? ChatFormatting.GOLD : ChatFormatting.GRAY));
        bonus(tooltip, "item.lacuna.starlace.starlit", on && n >= 1, percent(Balance.STARLACE_STARLIT_SPEED));
        bonus(tooltip, "item.lacuna.starlace.cushion", on && n >= Balance.STARLACE_HALF_FALL_PIECES,
                Balance.STARLACE_HALF_FALL_PIECES, percent(1.0 - Balance.STARLACE_FALL_DAMAGE_FACTOR));
        bonus(tooltip, "item.lacuna.starlace.starfall", on && n >= FULL, FULL, number(Balance.STARLACE_STARFALL_FALL));
        bonus(tooltip, "item.lacuna.starlace.step", on && n >= FULL, FULL);
    }

    private static void bonus(List<Component> tooltip, String key, boolean active, Object... args) {
        tooltip.add(Component.literal(active ? " ✦ " : " ✧ ").append(Component.translatable(key, args))
                .withStyle(active ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.DARK_GRAY));
    }

    private static String percent(double fraction) {
        return number(fraction * 100.0);
    }

    private static String number(double v) {
        double rounded = Math.round(v * 10.0) / 10.0;
        return rounded == Math.floor(rounded) ? String.valueOf((long) rounded) : String.valueOf(rounded);
    }
}
