package nc.multiblock.fission;

import nc.init.NCSaltCoolants;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class SaltHeaterBlock extends FissionPartBlock {
    private final NCSaltCoolants coolant;

    public SaltHeaterBlock(Properties properties, NCSaltCoolants coolant) {
        super(properties, Role.SALT_HEATER);
        this.coolant = coolant;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server) {
            SaltFissionPartMenu.open(player, server, pos, SaltFissionPartMenu.Kind.HEATER);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.nuclearcraft.fission.placement_rule",
                NCSaltFission.placementRules().text(coolant)).withStyle(ChatFormatting.GRAY));
    }

    NCSaltCoolants coolant() {
        return coolant;
    }
}
