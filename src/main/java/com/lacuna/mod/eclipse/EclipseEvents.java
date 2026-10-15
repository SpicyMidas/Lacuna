package com.lacuna.mod.eclipse;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/** Game-bus listeners for the Eclipser and Shadow Walk. */
public final class EclipseEvents {
    private EclipseEvents() {}

    public static void register(IEventBus bus) {
        bus.addListener((LivingDamageEvent.Post e) -> {
            if (!e.getEntity().level().isClientSide() && e.getNewDamage() > 0) {
                EclipserItem.resetOnDamage(e.getEntity());
            }
        });
        bus.addListener(EclipserItem::onAttack);
        bus.addListener(EclipseDash::onPlayerTick);
        bus.addListener(EclipseDash::onDeath);
        bus.addListener(EclipseDash::onLogout);
        bus.addListener(EclipseDash::onChangedDimension);
        bus.addListener(EclipseDash::onServerStopping);
        bus.addListener(ShadowWalk::onPlayerTick);
        bus.addListener(ShadowWalk::onEffectAdded);
        bus.addListener(ShadowWalk::onEffectExpired);
        bus.addListener(ShadowWalk::onEffectRemoved);
        bus.addListener(ShadowWalk::onDeath);
        bus.addListener(ShadowWalk::onLogout);
        bus.addListener(ShadowWalk::onLogin);
        bus.addListener(ShadowWalk::onChangedDimension);
        bus.addListener(ShadowWalk::onTravel);
        bus.addListener(ShadowWalk::onChangeTarget);
        bus.addListener(ShadowWalk::onServerStopping);
    }
}
