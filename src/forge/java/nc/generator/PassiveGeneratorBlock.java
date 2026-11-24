package nc.generator;

import nc.init.NCGenerators.PassiveGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class PassiveGeneratorBlock extends BaseEntityBlock {
    private final PassiveGenerator generator;

    public PassiveGeneratorBlock(Properties properties, PassiveGenerator generator) {
        super(properties);
        this.generator = generator;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public PassiveGeneratorBlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PassiveGeneratorBlockEntity(pos, state, generator);
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
}
