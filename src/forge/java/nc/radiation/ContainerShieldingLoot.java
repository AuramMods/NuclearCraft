package nc.radiation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;

public final class ContainerShieldingLoot extends LootModifier {
    public static final Codec<ContainerShieldingLoot> CODEC =
            RecordCodecBuilder.create(instance -> codecStart(instance).apply(instance, ContainerShieldingLoot::new));

    private ContainerShieldingLoot(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generated, LootContext context) {
        var container = context.getParamOrNull(LootContextParams.BLOCK_ENTITY);
        if (container != null) {
            double resistance = RadiationShielding.containerResistance(container);
            if (resistance > 0 && generated.stream().noneMatch(RadiationShielding::carriedBy)) {
                ItemStack recovered = ShieldingItem.recover(resistance);
                if (!recovered.isEmpty()) {
                    generated.add(recovered);
                }
            }
        }
        return generated;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
