package nc.multiblock.fission;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.registries.ForgeRegistries;

public final class SaltVesselBlock extends FissionPartBlock implements EntityBlock {
    public SaltVesselBlock(Properties properties) {
        super(properties, Role.VESSEL);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level instanceof ServerLevel server) {
            return (world, pos, block, entity) -> {
                if (entity instanceof SaltVessel fuel) {
                    fuel.decay().tickUnmanaged(server);
                }
            };
        }
        return null;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState block) {
        return new SaltVessel(pos, block);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof SaltVessel vessel) {
            if (player.isShiftKeyDown()) {
                return selectFilter(player, hand, server, pos, vessel::filter);
            }
            SaltFissionPartMenu.open(player, server, pos, SaltFissionPartMenu.Kind.VESSEL);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    static InteractionResult selectFilter(Player player, InteractionHand hand, ServerLevel level, BlockPos pos,
                                          java.util.function.Consumer<ResourceLocation> update) {
        var held = player.getItemInHand(hand);
        if (held.is(Items.BUCKET)) {
            update.accept(null);
            geometryChanged(level, pos);
            player.displayClientMessage(Component.translatable("message.nuclearcraft.fission.fuel_filter_cleared"), true);
            return InteractionResult.CONSUME;
        }
        var candidate = FluidUtil.getFluidContained(held).orElse(null);
        if (candidate == null || !SaltFuelBank.acceptsFuel(level, candidate)) {
            return InteractionResult.PASS;
        }
        update.accept(ForgeRegistries.FLUIDS.getKey(candidate.getFluid()));
        geometryChanged(level, pos);
        player.displayClientMessage(Component.translatable("message.nuclearcraft.fission.fuel_filter", candidate.getDisplayName()), true);
        return InteractionResult.CONSUME;
    }
}
