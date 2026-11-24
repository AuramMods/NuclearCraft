package nc.multiblock.computer;

import nc.init.NCComputerPorts;
import nc.multiblock.Cuboid;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class ComputerPort extends BlockEntity {
    public enum Kind {
        FISSION("fission_computer_port"), EXCHANGER("heat_exchanger_computer_port"),
        TURBINE("turbine_computer_port"), MACHINE("machine_computer_port");

        private final String id;

        Kind(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }

    public interface Controller {
        @Nullable Cuboid computerBounds();
    }

    private BlockEntity controller;
    private LazyOptional<?> peripheral = LazyOptional.empty();

    public ComputerPort(BlockPos pos, BlockState state) {
        super(NCComputerPorts.BLOCK_ENTITY.get(), pos, state);
    }

    public Kind kind() {
        return NCComputerPorts.kind(getBlockState());
    }

    public <T extends BlockEntity & Controller> @Nullable T controller(Class<T> type) {
        if (isRemoved() || !type.isInstance(controller) || controller.isRemoved()
                || controller.getLevel() != level) {
            return null;
        }
        T target = type.cast(controller);
        return target.computerBounds() == null ? null : target;
    }

    <T> LazyOptional<T> peripheral(Supplier<T> create) {
        if (!peripheral.isPresent()) {
            peripheral = LazyOptional.of(create::get);
        }
        return peripheral.cast();
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        controller = null;
        peripheral.invalidate();
        peripheral = LazyOptional.empty();
    }

    public static InteractionResult inspect(Level level, BlockPos pos, Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel && level.getBlockEntity(pos) instanceof ComputerPort port) {
            boolean attached = port.controller instanceof Controller owner && owner.computerBounds() != null;
            player.displayClientMessage(Component.translatable("message.nuclearcraft.computer_port."
                    + (attached ? "attached" : "detached")), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public static final class Links {
        private final BlockEntity owner;
        private List<ComputerPort> mounted = List.of();

        public Links(BlockEntity owner) {
            this.owner = owner;
        }

        public void bind(ServerLevel level, List<BlockPos> positions) {
            unbind();
            var found = new ArrayList<ComputerPort>(positions.size());
            for (BlockPos pos : positions) {
                if (level.getBlockEntity(pos) instanceof ComputerPort port) {
                    port.controller = owner;
                    found.add(port);
                }
            }
            mounted = List.copyOf(found);
        }

        public boolean hasPorts() {
            return !mounted.isEmpty();
        }

        public void unbind() {
            for (ComputerPort port : mounted) {
                if (port.controller == owner) {
                    port.controller = null;
                }
            }
            mounted = List.of();
        }
    }
}
