package com.lacuna.mod.client.confusion;

import com.lacuna.mod.Lacuna;
import com.lacuna.mod.client.fx.FxHandlers;
import com.lacuna.mod.confusion.Confusion;
import com.lacuna.mod.confusion.ConfusionCopy;
import com.lacuna.mod.confusion.MirrorInput;
import com.lacuna.mod.network.FxPayload;
import com.lacuna.mod.registry.ModEntities;
import com.lacuna.mod.registry.ModItems;
import com.lacuna.mod.util.SafeRun;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client wiring of the Totem of Confusion: the copy renderer (the owner's skin on a player model), the raised-totem
 * model switch, the owner's movement input streamed to the server while they have copies, and the vanilla totem
 * "item activation" animation (with our totem) on the owner's screen when the copies appear.
 */
public final class ConfusionClient {
    private ConfusionClient() {}

    public static void register(IEventBus modBus, IEventBus forgeBus) {
        modBus.addListener(ConfusionClient::onRegisterRenderers);
        modBus.addListener(ConfusionClient::onClientSetup);
        forgeBus.addListener(ConfusionClient::onClientTick);
        FxHandlers.register(Confusion.FX_RAISE, ConfusionClient::onRaise);
        ConfusionCopy.setOwnedLocally(copy -> {
            LocalPlayer player = Minecraft.getInstance().player;
            return player != null && player.getUUID().equals(copy.ownerId());
        });
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.CONFUSION_COPY.get(), ConfusionCopyRenderer::new);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> SafeRun.run("item_properties", () -> ItemProperties.register(ModItems.TOTEM_OF_CONFUSION.get(),
                Lacuna.id("raising"), (stack, level, entity, seed) ->
                        entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1.0F : 0.0F)));
    }

    /** After the local player moved this tick: their keys, for their copies on the server to walk the same way. */
    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || player.input == null || mc.getConnection() == null || SafeRun.isDisabled(Confusion.FEATURE)
                || !ConfusionCopy.clientHasCopyOf(player.getUUID())) {
            return;
        }
        PacketDistributor.sendToServer(new MirrorInput(player.input.leftImpulse, player.input.forwardImpulse, player.input.jumping));
    }

    private static void onRaise(FxPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && payload.entityId() == mc.player.getId()) {
            mc.gameRenderer.displayItemActivation(new ItemStack(ModItems.TOTEM_OF_CONFUSION.get()));
        }
    }

    /** The owner's skin (texture, arm model, cape) from the tab list; the default skin for their UUID until it loads. */
    public static PlayerSkin skin(ConfusionCopy copy) {
        UUID owner = copy.ownerId();
        if (owner == null) {
            return DefaultPlayerSkin.get(copy.getUUID());
        }
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        PlayerInfo info = connection == null ? null : connection.getPlayerInfo(owner);
        return info != null ? info.getSkin() : DefaultPlayerSkin.get(owner);
    }
}
