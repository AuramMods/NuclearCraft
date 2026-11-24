package nc.init;

import nc.fluid.FluidHazard;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.MagmaBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

public final class NCFluidProducts {
    private static final VoxelShape ICE_COLLISION = Block.box(0.032D, 0.032D, 0.032D, 15.968D, 16D, 15.968D);

    public static final RegistryObject<Block> SOLIDIFIED_CORIUM = register("solidified_corium",
            () -> new MagmaBlock(BlockBehaviour.Properties.copy(Blocks.MAGMA_BLOCK)
                    .isValidSpawn((state, level, pos, entity) -> false)) {
                @Override
                public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
                    if (!level.isClientSide && entity instanceof LivingEntity living && !living.fireImmune()
                            && !EnchantmentHelper.hasFrostWalker(living)) {
                        FluidHazard.hurt(entity, "corium_burn", 1F);
                    }
                    super.stepOn(level, pos, state, entity);
                }
            });
    public static final RegistryObject<Block> WASTELAND_EARTH = register("wasteland_earth",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.STONE)));
    public static final RegistryObject<Block> SUPERCOLD_ICE = register("supercold_ice",
            () -> new IceBlock(BlockBehaviour.Properties.copy(Blocks.ICE).friction(Math.nextDown(1F))
                    .isValidSpawn((state, level, pos, entity) -> false)) {
                @Override
                public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
                    if (!level.isClientSide) {
                        FluidHazard.hurt(entity, "hypothermia", 2F);
                    }
                }

                @Override
                public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                                    CollisionContext context) {
                    return ICE_COLLISION;
                }
            });

    private NCFluidProducts() {}

    static void register() {}

    private static RegistryObject<Block> register(String name, Supplier<Block> factory) {
        var block = NCRegistries.BLOCKS.register(name, factory);
        NCRegistries.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
        return block;
    }
}
