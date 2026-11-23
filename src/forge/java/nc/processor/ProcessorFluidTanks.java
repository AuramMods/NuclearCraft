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

import java.util.Arrays;
import java.util.EnumMap;
import java.util.function.Predicate;

import static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
import static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;

public final class ProcessorFluidTanks {
    public static final int CAPACITY = 16_000;
    private static final Direction[] SIDES = Direction.values();

    private final BlockEntity owner;
    private final FluidTank[] tanks;
    private final int inputs;
    private final int[] sideModes;
    private final EnumMap<Direction, Face> faces = new EnumMap<>(Direction.class);
    private Face unsided;
    private boolean transferring;

    ProcessorFluidTanks(BlockEntity owner, Predicate<FluidStack> accepted, int outputs) {
        this(owner, accepted, 1, outputs);
    }

    ProcessorFluidTanks(BlockEntity owner, Predicate<FluidStack> accepted, int inputs, int outputs) {
        this.owner = owner;
        this.inputs = inputs;
        tanks = new FluidTank[inputs + outputs];
        sideModes = new int[tanks.length];
        for (int index = 0; index < tanks.length; index++) {
            tanks[index] = new FluidTank(CAPACITY, index < inputs ? accepted : stack -> true) {
                @Override
                protected void onContentsChanged() {
                    owner.setChanged();
                }
            };
            sideModes[index] = defaultModes(index);
        }
        revive();
    }

    FluidStack stored(int index) {
        return tanks[index].getFluid();
    }

    void consume(int amount) {
        consume(0, amount);
    }

    void consume(int index, int amount) {
        tanks[index].drain(amount, EXECUTE);
    }

    boolean canProduce(int index, FluidStack stack) {
        return tanks[index].fill(stack, SIMULATE) == stack.getAmount();
    }

    void produce(int index, FluidStack stack) {
        tanks[index].fill(stack, EXECUTE);
    }

    void clear(int index) {
        if (!tanks[index].isEmpty()) {
            tanks[index].setFluid(FluidStack.EMPTY);
            owner.setChanged();
        }
    }

    int sideModes(int index) {
        return sideModes[index];
    }

    void cycle(int index, Direction side) {
        int shift = 2 * side.ordinal();
        Mode next = Mode.read(sideModes[index], side).next(index < inputs);
        sideModes[index] = (sideModes[index] & ~(3 << shift)) | (next.ordinal() << shift);
        faces.put(side, new Face(side)).invalidate();
    }

    LazyOptional<IFluidHandler> capability(@Nullable Direction side) {
        return (side == null ? unsided : faces.get(side)).capability;
    }

    void invalidate() {
        unsided.invalidate();
        faces.values().forEach(Face::invalidate);
    }

    void revive() {
        unsided = new Face(null);
        for (Direction side : SIDES) {
            faces.put(side, new Face(side));
        }
    }

    void refreshFaces() {
        for (Direction side : SIDES) {
            faces.put(side, new Face(side)).invalidate();
        }
    }

    void saveTank(int index, CompoundTag saved, String contentKey, String modesKey) {
        saved.put(contentKey, tanks[index].writeToNBT(new CompoundTag()));
        saved.putInt(modesKey, sideModes[index]);
    }

    void loadTank(int index, CompoundTag saved, String contentKey, String modesKey) {
        tanks[index].readFromNBT(saved.getCompound(contentKey));
        sideModes[index] = saved.contains(modesKey) ? saved.getInt(modesKey) : defaultModes(index);
    }

    private int defaultModes(int index) {
        // One two-bit mode per side. The six output modes begin in OUTPUT.
        return index < inputs ? 0 : 0b01_01_01_01_01_01;
    }

    void push(Level level) {
        for (Direction side : SIDES) {
            for (int index = 0; index < tanks.length; index++) {
                FluidTank tank = tanks[index];
                if (tank.isEmpty() || Mode.read(sideModes[index], side) != Mode.AUTO_OUTPUT) {
                    continue;
                }
                var adjacent = owner.getBlockPos().relative(side);
                BlockEntity target = level.hasChunkAt(adjacent) ? level.getBlockEntity(adjacent) : null;
                if (target != null) {
                    target.getCapability(ForgeCapabilities.FLUID_HANDLER, side.getOpposite())
                            .ifPresent(handler -> transferTo(tank, handler));
                }
            }
        }
    }

    private void transferTo(FluidTank tank, IFluidHandler target) {
        if (transferring) {
            return;
        }
        transferring = true;
        try {
            FluidStack offered = tank.getFluid().copy();
            int fillable = Math.min(offered.getAmount(), target.fill(offered, SIMULATE));
            if (fillable <= 0) {
                return;
            }
            FluidStack withdrawn = tank.drain(fillable, EXECUTE);
            int accepted = 0;
            try {
                accepted = Math.max(0, Math.min(fillable, target.fill(withdrawn.copy(), EXECUTE)));
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

        private Mode next(boolean input) {
            return this == OFF ? (input ? INPUT : OUTPUT) : MODES[ordinal() + 1];
        }

        public static Mode read(int modes, Direction side) {
            return MODES[(modes >>> (2 * side.ordinal())) & 3];
        }

        private boolean canDrain() {
            return this == OUTPUT || this == AUTO_OUTPUT;
        }

        public Component label() {
            return Component.translatable("gui.nuclearcraft.fluid_side." + key);
        }
    }

    private final class Face implements IFluidHandler {
        private final Mode[] modes;
        private final LazyOptional<IFluidHandler> capability;
        private boolean valid = true;

        Face(@Nullable Direction side) {
            modes = new Mode[tanks.length];
            for (int index = 0; index < modes.length; index++) {
                Mode fallback = index < inputs ? Mode.INPUT : Mode.OUTPUT;
                modes[index] = side == null ? fallback : Mode.read(sideModes[index], side);
            }
            capability = Arrays.stream(modes).allMatch(mode -> mode == Mode.OFF)
                    ? LazyOptional.empty() : LazyOptional.of(() -> this);
        }

        void invalidate() {
            valid = false;
            capability.invalidate();
        }

        private boolean active() {
            return valid && !owner.isRemoved() && !transferring;
        }

        private boolean visible(int index) {
            return index >= 0 && index < tanks.length && modes[index] != Mode.OFF && active();
        }

        @Override
        public int getTanks() {
            return tanks.length;
        }

        @Override
        public FluidStack getFluidInTank(int index) {
            return visible(index) ? tanks[index].getFluid().copy() : FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int index) {
            return visible(index) ? tanks[index].getCapacity() : 0;
        }

        @Override
        public boolean isFluidValid(int index, FluidStack stack) {
            return index >= 0 && index < inputs && active() && modes[index] == Mode.INPUT && tanks[index].isFluidValid(stack);
        }

        @Override
        public int fill(FluidStack stack, FluidAction action) {
            if (!active() || stack.isEmpty()) {
                return 0;
            }
            int empty = -1;
            for (int index = 0; index < inputs; index++) {
                if (modes[index] != Mode.INPUT || !tanks[index].isFluidValid(stack)) {
                    continue;
                }
                if (tanks[index].getFluid().isFluidEqual(stack)) {
                    return tanks[index].fill(stack, action);
                }
                if (empty == -1 && tanks[index].isEmpty()) {
                    empty = index;
                }
            }
            return empty == -1 ? 0 : tanks[empty].fill(stack, action);
        }

        @Override
        public FluidStack drain(FluidStack stack, FluidAction action) {
            if (active() && !stack.isEmpty()) {
                for (int index = 0; index < tanks.length; index++) {
                    if (modes[index].canDrain() && stack.isFluidEqual(tanks[index].getFluid())) {
                        return tanks[index].drain(stack, action);
                    }
                }
            }
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int max, FluidAction action) {
            if (active() && max > 0) {
                for (int index = 0; index < tanks.length; index++) {
                    if (modes[index].canDrain() && !tanks[index].isEmpty()) {
                        return tanks[index].drain(max, action);
                    }
                }
            }
            return FluidStack.EMPTY;
        }
    }
}
