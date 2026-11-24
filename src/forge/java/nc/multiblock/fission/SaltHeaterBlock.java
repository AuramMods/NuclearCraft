package nc.multiblock.fission;

import nc.init.NCSaltFission;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.fluids.FluidUtil;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class SaltHeaterBlock extends FissionPartBlock implements EntityBlock {
    private final FissionCoolant coolant;

    public SaltHeaterBlock(Properties properties, FissionCoolant coolant) {
        super(properties, Role.SALT_HEATER);
        this.coolant = coolant;
    }

    @Override
    public SaltHeater newBlockEntity(BlockPos pos, BlockState state) {
        return new SaltHeater(pos, state);
    }

    static @Nullable BlockEntity entity(ServerLevel level, BlockPos pos) {
        // Existing Forge worlds can have a heater block without its local tanks.
        return level.getChunkAt(pos).getBlockEntity(pos, LevelChunk.EntityCreationType.IMMEDIATE);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server && entity(server, pos) instanceof SaltHeater heater) {
            boolean output = FluidUtil.getFluidContained(player.getItemInHand(hand)).isEmpty();
            if (!FluidUtil.interactWithFluidHandler(player, hand, heater.tanks().tank(output))) {
                SaltFissionPartMenu.open(player, server, pos, SaltFissionPartMenu.Kind.HEATER);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.nuclearcraft.fission.placement_rule",
                NCSaltFission.placementRule(coolant)).withStyle(ChatFormatting.GRAY));
    }

    public FissionCoolant coolant() {
        return coolant;
    }
}
