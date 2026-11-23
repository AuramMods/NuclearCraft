package nc.multiblock.fission;

import nc.init.NCSaltCoolants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

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

    NCSaltCoolants coolant() {
        return coolant;
    }
}
