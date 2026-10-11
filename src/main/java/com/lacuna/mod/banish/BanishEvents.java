package com.lacuna.mod.banish;

import net.neoforged.bus.api.IEventBus;

/** Registers every Forgotten Land / banishment listener on the game event bus. */
public final class BanishEvents {
    private BanishEvents() {}

    public static void register(IEventBus gameBus) {
        gameBus.addListener(BanishmentManager::onServerTick);
        gameBus.addListener(BanishmentManager::onLogin);
        gameBus.addListener(BanishmentManager::onDeath);
        gameBus.addListener(BanishmentManager::onIncomingDamage);
        gameBus.addListener(BanishmentManager::onServerStopped);
        gameBus.addListener(ForgottenLandRules::onCommand);
        gameBus.addListener(ForgottenLandRules::onTravel);
        gameBus.addListener(ForgottenLandRules::onEnderPearl);
        gameBus.addListener(ForgottenLandRules::onChorusFruit);
        gameBus.addListener(ForgottenLandRules::onRightClickBlock);
        gameBus.addListener(ForgottenLandRules::onAnvil);
        gameBus.addListener(RelicHandler::onDrops);
        gameBus.addListener(RelicHandler::onPickup);
    }
}
