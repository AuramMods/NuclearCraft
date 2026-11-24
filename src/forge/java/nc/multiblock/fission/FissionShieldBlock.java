package nc.multiblock.fission;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.DoubleSupplier;

import static nc.multiblock.fission.FissionWallBlock.ACTIVE;

public final class FissionShieldBlock extends FissionControlBlock {
    private final DoubleSupplier heat;
    private final DoubleSupplier efficiency;

    public FissionShieldBlock(Properties properties, DoubleSupplier heat, DoubleSupplier efficiency) {
        super(properties, Role.SHIELD);
        this.heat = heat;
        this.efficiency = efficiency;
        registerDefaultState(stateDefinition.any().setValue(ACTIVE, false));
    }

    double heatPerFlux() {
        return heat.getAsDouble();
    }

    double efficiency() {
        return efficiency.getAsDouble();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.nuclearcraft.fission_shield.stats", heatPerFlux(), efficiency())
                .withStyle(ChatFormatting.GRAY));
    }
}
