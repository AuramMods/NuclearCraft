package nc.fluid;

import nc.fluid.FluidHazard.Kind;
import nc.init.NCFluidProducts;
import nc.radiation.RadiationSources;
import nc.radiation.WorldRadiation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.fluids.ForgeFlowingFluid;

public abstract class NCFlowingFluid extends ForgeFlowingFluid {
    private final Kind kind;
    private NCFlowingFluid(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
    }

    public static final class Source extends NCFlowingFluid {
        public Source(Properties properties, Kind kind) {
            super(properties, kind);
        }

        @Override
        public int getAmount(FluidState state) {
            return 8;
        }

        @Override
        public boolean isSource(FluidState state) {
            return true;
        }
    }

    public static final class Flowing extends NCFlowingFluid {
        public Flowing(Properties properties, Kind kind) {
            super(properties, kind);
            registerDefaultState(defaultFluidState().setValue(LEVEL, 7));
        }

        @Override
        protected void createFluidStateDefinition(StateDefinition.Builder<Fluid, FluidState> builder) {
            super.createFluidStateDefinition(builder);
            builder.add(LEVEL);
        }

        @Override
        public int getAmount(FluidState state) {
            return state.getValue(LEVEL);
        }

        @Override
        public boolean isSource(FluidState state) {
            return false;
        }
    }

    @Override
    public void tick(Level level, BlockPos pos, FluidState state) {
        if (rises()) {
            rise(level, pos, state);
            return;
        }
        if (!state.isSource() && NCLiquidBlock.isWater(level, pos.below())) {
            Block product = switch (kind) {
                case MOLTEN, GLOWSTONE, HOT_COOLANT -> Blocks.STONE;
                case FISSION, CORIUM -> NCFluidProducts.WASTELAND_EARTH.get();
                case CRYOTHEUM -> Blocks.ICE;
                case SUPERFLUID -> NCFluidProducts.SUPERCOLD_ICE.get();
                default -> null;
            };
            if (product != null) {
                NCLiquidBlock.replaceFluid(level, pos.below(), pos, product.defaultBlockState());
            }
        }
        if (kind == Kind.CORIUM) {
            emitCoriumRadiation(level, pos, state);
        }
        super.tick(level, pos, state);
        if (kind == Kind.CORIUM && state.isSource() && isSame(level.getFluidState(pos).getType())) {
            coolCorium(level, pos, level.random);
        }
    }

    @Override
    protected void spreadTo(LevelAccessor level, BlockPos pos, BlockState before, Direction direction, FluidState state) {
        if (!(before.getBlock() instanceof LiquidBlock) || isSame(before.getFluidState().getType())) {
            super.spreadTo(level, pos, before, direction, state);
        }
    }

    private boolean rises() {
        return kind == Kind.GAS || kind == Kind.HOT_GAS || kind == Kind.STEAM;
    }

    private void rise(Level level, BlockPos pos, FluidState state) {
        int amount = state.getAmount();
        if (state.isSource() && kind == Kind.HOT_GAS) {
            ignite(level, pos, level.random);
        }
        if (amount > 1) {
            BlockPos above = pos.above();
            if (!moveGas(level, above, amount - 1) && !isSame(level.getFluidState(above).getType())) {
                for (Direction direction : Direction.Plane.HORIZONTAL) {
                    moveGas(level, pos.relative(direction), amount - 2);
                }
            }
        }
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
    }

    private boolean moveGas(Level level, BlockPos pos, int amount) {
        if (amount < 1 || !level.isInWorldBounds(pos) || !level.hasChunkAt(pos)) {
            return false;
        }
        BlockState before = level.getBlockState(pos);
        return before.getFluidState().isEmpty() && before.canBeReplaced(this)
                && level.setBlockAndUpdate(pos, getFlowing(amount, false).createLegacyBlock());
    }

    @Override
    protected boolean isRandomlyTicking() {
        return isSource(defaultFluidState()) && switch (kind) {
            case CORIUM, MOLTEN, FISSION, HOT_COOLANT, PLASMA -> true;
            default -> false;
        };
    }

    @Override
    protected void randomTick(Level level, BlockPos pos, FluidState state, RandomSource random) {
        ignite(level, pos, random);
        if (kind == Kind.CORIUM) {
            emitCoriumRadiation(level, pos, state);
            coolCorium(level, pos, random);
        } else if (kind == Kind.PLASMA && FluidConfig.plasmaFire() && random.nextInt(4) == 0) {
            placeFire(level, pos.relative(Direction.getRandom(random)), pos);
        }
    }

    private static void emitCoriumRadiation(Level level, BlockPos pos, FluidState state) {
        if (level instanceof ServerLevel server) {
            WorldRadiation.addLeak(server, pos, RadiationSources.CORIUM * state.getAmount());
        }
    }

    private void coolCorium(Level level, BlockPos pos, RandomSource random) {
        if (FluidConfig.coriumCools(level.dimension().location())) {
            int neighbors = 0;
            for (Direction direction : Direction.values()) {
                FluidState adjacent = level.getFluidState(pos.relative(direction));
                if (adjacent.isSource() && isSame(adjacent.getType())) {
                    neighbors++;
                }
            }
            if (neighbors <= 3 && random.nextInt(2 + 4 * neighbors) == 0) {
                level.setBlockAndUpdate(pos, NCFluidProducts.SOLIDIFIED_CORIUM.get().defaultBlockState());
            }
        }
    }

    private static void ignite(Level level, BlockPos pos, RandomSource random) {
        if (!level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK)) {
            return;
        }
        int tries = random.nextInt(3);
        if (tries == 0) {
            for (int i = 0; i < 3; i++) {
                BlockPos fuel = pos.offset(random.nextInt(3) - 1, 0, random.nextInt(3) - 1);
                if (level.hasChunkAt(fuel) && level.getBlockState(fuel).isFlammable(level, fuel, Direction.UP)) {
                    placeFire(level, fuel.above(), pos);
                }
            }
        } else {
            BlockPos target = pos;
            for (int i = 0; i < tries; i++) {
                target = target.offset(random.nextInt(3) - 1, 1, random.nextInt(3) - 1);
                if (!level.hasChunkAt(target) || level.getBlockState(target).blocksMotion()
                        || placeFire(level, target, pos)) {
                    return;
                }
            }
        }
    }

    private static boolean placeFire(Level level, BlockPos target, BlockPos source) {
        if (!level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK) || !level.isInWorldBounds(target)
                || !level.hasChunkAt(target) || !level.isEmptyBlock(target)) {
            return false;
        }
        BlockState fire = BaseFireBlock.getState(level, target);
        if (!fire.canSurvive(level, target)) {
            return false;
        }
        return level.setBlockAndUpdate(target, ForgeEventFactory.fireFluidPlaceBlockEvent(level, target, source, fire));
    }
}
