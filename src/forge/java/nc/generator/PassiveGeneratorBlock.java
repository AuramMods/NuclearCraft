package nc.generator;

import nc.init.NCGenerators;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class PassiveGeneratorBlock extends BaseEntityBlock {
    private final Definition generator;

    public PassiveGeneratorBlock(Properties properties, Definition generator) {
        super(properties);
        this.generator = generator;
    }

    public static PassiveGeneratorBlock createScriptedRtg(String power, double radiation) {
        long output = Long.parseLong(power);
        if (output < 0 || radiation < 0 || !Double.isFinite(radiation)) {
            throw new IllegalArgumentException("An RTG requires nonnegative power and finite, nonnegative radiation");
        }
        return new PassiveGeneratorBlock(Properties.copy(Blocks.IRON_BLOCK), new ScriptedRtg(output, radiation));
    }

    public static BlockItem createScriptedRtgItem(String blockId) {
        var block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(blockId));
        if (!(block instanceof PassiveGeneratorBlock generator) || !generator.isScriptedRtg()) {
            throw new IllegalArgumentException("No scripted NuclearCraft RTG block with ID " + blockId);
        }
        return new BlockItem(block, new Item.Properties());
    }

    public boolean isScriptedRtg() {
        return generator instanceof ScriptedRtg;
    }

    Definition definition() {
        return generator;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public PassiveGeneratorBlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PassiveGeneratorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                            BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, generator.entity(), PassiveGeneratorBlockEntity::tick);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flags) {
        tooltip.add(Component.translatable(generator.solar() ? "block.nuclearcraft.solar_panel.maximum"
                : "block.nuclearcraft.rtg.output", generator.power()));
    }

    public interface Definition {
        BlockEntityType<PassiveGeneratorBlockEntity> entity();

        long power();

        boolean solar();

        double radiation();
    }

    private record ScriptedRtg(long power, double radiation) implements Definition {
        @Override
        public BlockEntityType<PassiveGeneratorBlockEntity> entity() {
            return NCGenerators.SCRIPTED_RTG_ENTITY.get();
        }

        @Override
        public boolean solar() {
            return false;
        }
    }
}
