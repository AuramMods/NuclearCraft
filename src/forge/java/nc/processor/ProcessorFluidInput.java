package nc.processor;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.function.Predicate;

public final class ProcessorFluidInput {
    public static final int CAPACITY = 16_000;
    private static final Direction[] SIDES = Direction.values();
    private final BlockEntity owner;
    private final FluidTank tank;
    private final EnumMap<Direction, Face> sides = new EnumMap<>(Direction.class);
    private Face unsided = new Face(Mode.INPUT);
    private boolean transferring;

    ProcessorFluidInput(BlockEntity owner, Predicate<FluidStack> accepted) {
        this.owner = owner;
        tank = new FluidTank(CAPACITY, accepted) {
            @Override
            protected void onContentsChanged() {
                owner.setChanged();
            }
        };
        for (Direction side : SIDES) {
            sides.put(side, new Face(Mode.INPUT));
        }
    }

    FluidStack stored() {
        return tank.getFluid();
    }

    void consume(int amount) {
        tank.drain(amount, IFluidHandler.FluidAction.EXECUTE);
    }

    int sideModes() {
        int modes = 0;
        for (Direction side : SIDES) {
            modes |= sides.get(side).mode.ordinal() << (2 * side.ordinal());
        }
        return modes;
    }

    void cycle(Direction side) {
        sides.compute(side, (direction, previous) -> {
            previous.invalidate();
            return new Face(previous.mode.next());
        });
    }

    LazyOptional<IFluidHandler> capability(@Nullable Direction side) {
        return (side == null ? unsided : sides.get(side)).capability;
    }

    void invalidate() {
        unsided.invalidate();
        sides.values().forEach(Face::invalidate);
    }

    void revive() {
        unsided = new Face(Mode.INPUT);
        sides.replaceAll((direction, port) -> new Face(port.mode));
    }

    void save(CompoundTag saved) {
        saved.put("input_fluid", tank.writeToNBT(new CompoundTag()));
        saved.putInt("fluid_sides", sideModes());
    }

    void load(CompoundTag saved) {
        tank.readFromNBT(saved.getCompound("input_fluid"));
        int modes = saved.getInt("fluid_sides");
        sides.replaceAll((direction, port) -> {
            port.invalidate();
            return new Face(Mode.read(modes, direction));
        });
    }

    void push(Level level) {
        if (tank.isEmpty()) {
            return;
        }
        for (Direction side : SIDES) {
            if (sides.get(side).mode != Mode.AUTO_OUTPUT || tank.isEmpty()) {
                continue;
            }
            var adjacent = owner.getBlockPos().relative(side);
            BlockEntity target = level.hasChunkAt(adjacent) ? level.getBlockEntity(adjacent) : null;
            if (target != null) {
                target.getCapability(ForgeCapabilities.FLUID_HANDLER, side.getOpposite()).ifPresent(this::transferTo);
            }
        }
    }

    private void transferTo(IFluidHandler target) {
        if (transferring) {
            return;
        }
        transferring = true;
        try {
            FluidStack offered = tank.getFluid().copy();
            int fillable = Math.min(offered.getAmount(), target.fill(offered, IFluidHandler.FluidAction.SIMULATE));
            if (fillable <= 0) {
                return;
            }
            FluidStack withdrawn = tank.drain(fillable, IFluidHandler.FluidAction.EXECUTE);
            int accepted = 0;
            try {
                accepted = Math.max(0, Math.min(fillable, target.fill(withdrawn.copy(), IFluidHandler.FluidAction.EXECUTE)));
            } finally {
                if (accepted < fillable) {
                    withdrawn.setAmount(tank.getFluidAmount() + fillable - accepted);
                    tank.setFluid(withdrawn);
                    owner.setChanged();
                }
            }
        } finally {
            transferring = false;
        }
    }

    public enum Mode {
        INPUT("input"), OUTPUT("output"), AUTO_OUTPUT("auto_output"), OFF("off");

        private static final Mode[] MODES = values();
        private final String key;

        Mode(String key) {
            this.key = key;
        }

        private Mode next() {
            return MODES[(ordinal() + 1) % MODES.length];
        }

        public static Mode read(int modes, Direction side) {
            return MODES[(modes >>> (2 * side.ordinal())) & 3];
        }

        public Component label() {
            return Component.translatable("gui.nuclearcraft.fluid_side." + key);
        }
    }

    private final class Face implements IFluidHandler {
        private final Mode mode;
        private final LazyOptional<IFluidHandler> capability;
        private boolean valid = true;

        Face(Mode mode) {
            this.mode = mode;
            capability = mode == Mode.OFF ? LazyOptional.empty() : LazyOptional.of(() -> this);
        }

        void invalidate() {
            valid = false;
            capability.invalidate();
        }

        private boolean active() {
            return valid && !owner.isRemoved() && !transferring;
        }

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int index) {
            return index == 0 && active() ? tank.getFluid().copy() : FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int index) {
            return index == 0 && active() ? tank.getCapacity() : 0;
        }

        @Override
        public boolean isFluidValid(int index, FluidStack stack) {
            return index == 0 && active() && mode == Mode.INPUT && tank.isFluidValid(stack);
        }

        @Override
        public int fill(FluidStack stack, FluidAction action) {
            return active() && mode == Mode.INPUT ? tank.fill(stack, action) : 0;
        }

        @Override
        public FluidStack drain(FluidStack stack, FluidAction action) {
            return active() && (mode == Mode.OUTPUT || mode == Mode.AUTO_OUTPUT)
                    ? tank.drain(stack, action) : FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int max, FluidAction action) {
            return active() && (mode == Mode.OUTPUT || mode == Mode.AUTO_OUTPUT)
                    ? tank.drain(max, action) : FluidStack.EMPTY;
        }
    }
}
