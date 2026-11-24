package nc.worldgen;

import nc.init.NCWasteland;
import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.material.MapColor;

import java.util.List;
import java.util.Locale;

public final class HugeGlowingMushroomBlock extends Block {
    public static final EnumProperty<Part> VARIANT = EnumProperty.create("variant", Part.class);

    public HugeGlowingMushroomBlock(Properties properties) {
        super(properties.mapColor(state -> state.getValue(VARIANT).mapColor()));
        registerDefaultState(defaultBlockState().setValue(VARIANT, Part.ALL_OUTSIDE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(VARIANT);
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        return NCWasteland.GLOWING_MUSHROOM_ITEM.get().getDefaultInstance();
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(VARIANT, state.getValue(VARIANT).transform(Mirror.NONE, rotation));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(VARIANT, state.getValue(VARIANT).transform(mirror, Rotation.NONE));
    }

    public enum Part implements StringRepresentable {
        ALL_INSIDE, NORTH_WEST(-1, -1), NORTH(0, -1), NORTH_EAST(1, -1), WEST(-1, 0), CENTER(0, 0),
        EAST(1, 0), SOUTH_WEST(-1, 1), SOUTH(0, 1), SOUTH_EAST(1, 1),
        STEM, ALL_OUTSIDE, ALL_STEM;

        private static final List<Part> CAP_PARTS = List.of(NORTH_WEST, NORTH, NORTH_EAST,
                WEST, CENTER, EAST, SOUTH_WEST, SOUTH, SOUTH_EAST);
        private final String name = name().toLowerCase(Locale.ROOT);
        private final BlockPos facing;

        Part() {
            this.facing = null;
        }

        Part(int x, int z) {
            this.facing = new BlockPos(x, 0, z);
        }

        static Part cap(int xFacing, int zFacing) {
            return CAP_PARTS.get((zFacing + 1) * 3 + (xFacing + 1));
        }

        private Part transform(Mirror mirror, Rotation rotation) {
            if (facing == null) {
                return this;
            }
            var offset = StructureTemplate.transform(facing, mirror, rotation, BlockPos.ZERO);
            return cap(offset.getX(), offset.getZ());
        }

        private MapColor mapColor() {
            return switch (this) {
                case ALL_STEM -> MapColor.WOOL;
                case STEM, ALL_INSIDE -> MapColor.SAND;
                default -> MapColor.GOLD;
            };
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }
}
