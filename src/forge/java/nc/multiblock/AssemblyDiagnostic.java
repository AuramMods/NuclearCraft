package nc.multiblock;

import nc.multiblock.fission.FissionHighlights;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

public record AssemblyDiagnostic(Component message, @Nullable BlockPos position) {
    public static final String NO_CONTROLLER = "message.nuclearcraft.multiblock.no_controller";

    public AssemblyDiagnostic {
        if (position != null) {
            position = position.immutable();
        }
    }

    public static AssemblyDiagnostic at(String key, BlockPos position) {
        return new AssemblyDiagnostic(Component.translatable(key, position.getX(), position.getY(), position.getZ()),
                position);
    }

    public void show(Player player, boolean actionBar) {
        player.displayClientMessage(message, actionBar);
        if (position != null && player instanceof ServerPlayer serverPlayer) {
            FissionHighlights.send(serverPlayer, List.of(position));
        }
    }

    public interface Controller {
        @Nullable AssemblyDiagnostic diagnostic(ServerLevel level);
    }

    public static <T extends BlockEntity & Controller> InteractionResult inspect(Level level, BlockPos pos,
            Player player, InteractionHand hand, int maxLength, Predicate<BlockState> isPart, Class<T> type) {
        if (!(level instanceof ServerLevel server) || hand != InteractionHand.MAIN_HAND || player.isShiftKeyDown()
                || !player.getItemInHand(hand).isEmpty() || !player.getOffhandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        T controller = Cuboid.findConnectedController(server, pos, maxLength, isPart, type);
        if (controller == null) {
            player.displayClientMessage(Component.translatable(NO_CONTROLLER), false);
            return InteractionResult.CONSUME;
        }
        AssemblyDiagnostic diagnostic = controller.diagnostic(server);
        if (diagnostic == null) {
            return InteractionResult.PASS;
        }
        diagnostic.show(player, false);
        return InteractionResult.CONSUME;
    }
}
