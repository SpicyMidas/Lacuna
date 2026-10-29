package com.lacuna.mod.chart;

import com.lacuna.mod.Balance;
import com.lacuna.mod.registry.ModComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Wayfarer's Chart: held in either hand it is a live map of the Overworld centred on its holder, scrolling as they walk
 * (it is not cut to a grid like a vanilla map). From the Forgotten Land, the Nether or another dimension it shows the
 * Overworld around the holder's translated position ({@link ChartView}). Sneak + use cycles the scale, 1 to 16 blocks
 * per pixel. Server side: {@link ChartService}; drawing: {@code client/chart}.
 */
public class WayfarersChartItem extends Item {
    public WayfarersChartItem(Properties properties) {
        super(properties);
    }

    /** The chart's zoom level (0..4: 1, 2, 4, 8, 16 blocks per pixel). */
    public static int zoom(ItemStack stack) {
        return ChartGrid.clampZoom(stack.getOrDefault(ModComponents.CHART_ZOOM.get(), Balance.CHART_DEFAULT_ZOOM));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isSecondaryUseActive()) {
            return InteractionResultHolder.pass(stack);
        }
        if (!level.isClientSide()) {
            int next = (zoom(stack) + 1) % ChartGrid.ZOOM_LEVELS;
            stack.set(ModComponents.CHART_ZOOM.get(), next);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS,
                    1.0F, 1.2F - next * 0.1F);
            player.displayClientMessage(Component.translatable("lacuna.chart.scale", ChartGrid.blocksPerPixel(next)), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.lacuna.wayfarers_chart.lore").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("item.lacuna.wayfarers_chart.use").withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("item.lacuna.wayfarers_chart.zoom", ChartGrid.blocksPerPixel(zoom(stack)))
                .withStyle(ChatFormatting.DARK_AQUA));
    }
}
