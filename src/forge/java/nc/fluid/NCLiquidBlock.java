package nc.fluid;

import nc.fluid.FluidHazard.Kind;
import nc.init.NCFluidProducts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraftforge.event.ForgeEventFactory;

import java.util.function.Supplier;

public final class NCLiquidBlock extends LiquidBlock {
    private final Kind kind;

    public NCLiquidBlock(Supplier<? extends FlowingFluid> fluid, Properties properties, Kind kind) {
        super(fluid, properties);
        this.kind = kind;
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!level.isClientSide) {
            var fluid = getFluidState(state);
            FluidHazard.contact(kind, entity, fluid.getFluidType().getTemperature());
        }
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState previous, boolean moving) {
        if (!mix(state, level, pos)) {
            super.onPlace(state, level, pos, previous, moving);
        }
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos,
                                boolean moving) {
        if (!mix(state, level, pos)) {
            super.neighborChanged(state, level, pos, neighbor, neighborPos, moving);
        }
    }

    private boolean mix(BlockState state, Level level, BlockPos pos) {
        if (level.isClientSide) {
            return false;
        }
        boolean source = getFluidState(state).isSource();
        Block product = switch (kind) {
            case MOLTEN, FISSION, HOT_COOLANT -> source ? Blocks.STONE : Blocks.COBBLESTONE;
            case GLOWSTONE -> source ? Blocks.GLOWSTONE : Blocks.COBBLESTONE;
            case CORIUM -> source ? NCFluidProducts.SOLIDIFIED_CORIUM.get() : Blocks.STONE;
            default -> null;
        };
        if (product == null) {
            return false;
        }
        for (Direction direction : Direction.values()) {
            if (direction != Direction.DOWN && isWater(level, pos.relative(direction))) {
                return replaceFluid(level, pos, pos, product.defaultBlockState());
            }
        }
        return false;
    }

    static boolean isWater(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof LiquidBlock && state.getFluidState().is(FluidTags.WATER);
    }

    static boolean replaceFluid(Level level, BlockPos target, BlockPos source, BlockState product) {
        BlockState replacement = ForgeEventFactory.fireFluidPlaceBlockEvent(level, target, source, product);
        if (level.setBlockAndUpdate(target, replacement)) {
            level.levelEvent(1501, target, 0);
            return true;
        }
        return false;
    }

    @Override
    public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction face) {
        return kind == Kind.FLAMMABLE ? 200 : super.getFlammability(state, level, pos, face);
    }

    @Override
    public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction face) {
        return kind == Kind.FLAMMABLE ? 500 : super.getFireSpreadSpeed(state, level, pos, face);
    }
}
