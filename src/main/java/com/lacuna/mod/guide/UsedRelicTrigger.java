package com.lacuna.mod.guide;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Advancement trigger {@code lacuna:used_relic}: a player used the power of a Lacuna relic or gadget (the item that did
 * it is matched by the optional {@code item} predicate, like vanilla's {@code using_item}). It fires where the power
 * actually took effect, not on every click: an item the Wand of Quickening woke up, a creature or player stuffed into
 * the Comically Large Bag ({@link Guide#usedRelic}). Advancement data, datapacks included, can use it like this:
 * <pre>{"trigger": "lacuna:used_relic", "conditions": {"item": {"items": "lacuna:quickening_wand"}}}</pre>
 */
public class UsedRelicTrigger extends SimpleCriterionTrigger<UsedRelicTrigger.TriggerInstance> {
    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, ItemStack stack) {
        this.trigger(player, instance -> instance.matches(stack));
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player, Optional<ItemPredicate> item)
            implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(i -> i.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                ItemPredicate.CODEC.optionalFieldOf("item").forGetter(TriggerInstance::item)
        ).apply(i, TriggerInstance::new));

        public boolean matches(ItemStack stack) {
            return item.isEmpty() || item.get().test(stack);
        }
    }
}
