package nc.processor;

import nc.init.NCRadiation;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Locale;

public final class ScrubberBlock extends ProcessorBlock<RadiationScrubber> {
    public ScrubberBlock(Properties properties) {
        super(properties, NCRadiation.SCRUBBER_ENTITY::get, RadiationScrubber::tick, RadiationScrubber::inventory);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (hand == InteractionHand.MAIN_HAND && player.isShiftKeyDown() && player.getItemInHand(hand).isEmpty()) {
            if (level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof RadiationScrubber scrubber) {
                scrubber.refreshEnvironment(server);
                player.sendSystemMessage(Component.translatable("message.nuclearcraft.scrubber_removal",
                        String.format(Locale.ROOT, "%.4g", -scrubber.measuredRemoval()),
                        String.format(Locale.ROOT, "%.1f", 100D * scrubber.effectiveEfficiency())));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.use(state, level, pos, player, hand, hit);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moved) {
        if (!level.isClientSide && !state.is(replacement.getBlock())
                && level.getBlockEntity(pos) instanceof RadiationScrubber scrubber) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), scrubber.reservedItem());
        }
        super.onRemove(state, level, pos, replacement, moved);
    }
}
