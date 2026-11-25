package nc.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import nc.NuclearCraft;
import nc.init.NCLoot;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

public final class OreBonusLoot extends LootModifier {
    public static final Codec<OreBonusLoot> CODEC =
            RecordCodecBuilder.create(instance -> codecStart(instance).apply(instance, OreBonusLoot::new));

    private OreBonusLoot(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generated, LootContext context) {
        var state = context.getParamOrNull(LootContextParams.BLOCK_STATE);
        if (state == null || !state.getBlock().getLootTable().equals(context.getQueriedLootTableId())
                || !context.getLevel().getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)) {
            return generated;
        }
        var tool = context.getParamOrNull(LootContextParams.TOOL);
        if (tool != null && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SILK_TOUCH, tool) > 0) {
            return generated;
        }

        var random = context.getRandom();
        for (Drop drop : Drop.values()) {
            if (drop.blocks.contains(state.getBlock()) && NCLoot.oreDropEnabled(drop.ordinal())
                    && random.nextInt(100) < drop.chance) {
                int count = drop.minimum == drop.maximum
                        ? drop.minimum : drop.minimum + random.nextInt(drop.maximum - drop.minimum + 1);
                if (count > 0) {
                    generated.add(new ItemStack(ForgeRegistries.ITEMS.getValue(drop.item), count));
                }
            }
        }
        return generated;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }

    // Keep the legacy ore_drops config order.
    private enum Drop {
        RHODOCHROSITE("rhodochrosite", 25, 1, 1, Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE),
        BERYLLIUM("beryllium_dust", 18, 1, 1, Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE),
        ZIRCONIUM("zirconium_dust", 18, 1, 1, Blocks.NETHER_QUARTZ_ORE),
        FLUORITE("fluorite", 95, 2, 2, Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE),
        SULFUR("sulfur_dust", 18, 1, 1, Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE),
        VILLIAUMITE("villiaumite", 100, 0, 1, Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE),
        CAROBBIITE("carobbiite", 100, 1, 3, Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE);

        private final ResourceLocation item;
        private final int chance;
        private final int minimum;
        private final int maximum;
        private final List<Block> blocks;

        Drop(String item, int chance, int minimum, int maximum, Block... blocks) {
            this.item = NuclearCraft.id(item);
            this.chance = chance;
            this.minimum = minimum;
            this.maximum = maximum;
            this.blocks = List.of(blocks);
        }
    }
}
