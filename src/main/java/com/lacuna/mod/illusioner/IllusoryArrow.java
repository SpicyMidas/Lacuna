package com.lacuna.mod.illusioner;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The arrow an illusioner's duplicate looses: an ordinary arrow to every client (it is sent as a plain
 * {@code minecraft:arrow}), but on the server it is an illusion. Whatever it touches, a creature or a block, it
 * dissolves in a wisp of the illusioner's particles: no damage, no knockback, no effects, nothing to pick up, never
 * stuck in a wall and never saved with the chunk.
 */
public class IllusoryArrow extends net.minecraft.world.entity.projectile.Arrow {
    /** An illusion that somehow flies on this long (3 s) fades all the same. */
    private static final int MAX_FLIGHT = 60;

    public IllusoryArrow(Level level, LivingEntity shooter) {
        super(level, shooter, new ItemStack(Items.ARROW), null);
        this.pickup = Pickup.DISALLOWED;
        setBaseDamage(0.0);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && (tickCount > MAX_FLIGHT || inGround)) {
            dissolve(position());
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        dissolve(result.getLocation());
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        dissolve(result.getLocation());
    }

    private void dissolve(Vec3 at) {
        if (isRemoved()) {
            return;
        }
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 4, 0.08, 0.08, 0.08, 0.01);
            server.sendParticles(Illusioners.spellParticle(), at.x, at.y, at.z, 6, 0.15, 0.15, 0.15, 0.0);
            server.playSound(null, at.x, at.y, at.z, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.HOSTILE, 0.25F, 1.9F);
        }
        discard();
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
