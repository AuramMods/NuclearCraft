package nc.multiblock.fission;

import nc.init.NCGasCoolants;
import nc.init.NCPebbleFission;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class PebbleCoolerBlock extends FissionPartBlock implements EntityBlock {
    private final NCGasCoolants gas;

    public PebbleCoolerBlock(Properties properties, NCGasCoolants gas) {
        super(properties, Role.PEBBLE_COOLER);
        this.gas = gas;
    }

    @Override
    public PebbleCooler newBlockEntity(BlockPos pos, BlockState state) {
        return new PebbleCooler(pos, state);
    }

    static @Nullable BlockEntity entity(ServerLevel level, BlockPos pos) {
        // Older Forge saves have a cooler block without the local-tank block entity.
        return level.getChunkAt(pos).getBlockEntity(pos, LevelChunk.EntityCreationType.IMMEDIATE);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server && entity(server, pos) instanceof PebbleCooler cooler) {
            var sample = FluidUtil.getFluidContained(player.getItemInHand(hand)).orElse(FluidStack.EMPTY);
            if (!sample.isEmpty() && cooler.selectFilter(sample)) {
                player.displayClientMessage(Component.translatable("gui.nuclearcraft.salt_part.filter", sample.getDisplayName()), true);
            } else if (!FluidUtil.interactWithFluidHandler(player, hand, cooler.tanks().tank(sample.isEmpty()))) {
                PebblePartMenu.open(player, cooler, PebblePartMenu.Kind.COOLER);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.nuclearcraft.fission.placement_rule",
                NCPebbleFission.placementRules().text(gas)).withStyle(ChatFormatting.GRAY));
    }

    public NCGasCoolants gas() {
        return gas;
    }
}
