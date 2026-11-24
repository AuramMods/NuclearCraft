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
    private final OutputMode[] outputModes;
    private int voidUnusable;
    private boolean separateInputs;
    private final EnumMap<Direction, Face> faces = new EnumMap<>(Direction.class);
    private Face unsided;
    private boolean transferring;

    ProcessorFluidTanks(BlockEntity owner, Predicate<FluidStack> accepted, int outputs) {
        this(owner, accepted, 1, outputs);
    }

    ProcessorFluidTanks(BlockEntity owner, Predicate<FluidStack> accepted, int inputs, int outputs) {
        this.owner = owner;
        this.inputs = inputs;
        separateInputs = inputs > 1;
        tanks = new FluidTank[inputs + outputs];
        sideModes = new int[tanks.length];
        outputModes = new OutputMode[outputs];
        Arrays.fill(outputModes, OutputMode.HOLD);
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

    int size() {
        return tanks.length;
    }

    void consume(int amount) {
        consume(0, amount);
    }

    void consume(int index, int amount) {
        tanks[index].drain(amount, EXECUTE);
    }

    boolean canRestoreInput(FluidStack stack) {
        FluidStack stored = tanks[0].getFluid();
        return (stored.isEmpty() || stack.isFluidEqual(stored))
                && stack.getAmount() <= tanks[0].getCapacity() - stored.getAmount();
    }

    void restoreInput(FluidStack stack) {
        FluidStack restored = stack.copy();
        restored.grow(tanks[0].getFluidAmount());
        tanks[0].setFluid(restored);
        owner.setChanged();
    }

    boolean canProduce(int index, FluidStack product) {
        OutputMode mode = outputModes[index - inputs];
        FluidStack stored = stored(index);
        return mode == OutputMode.VOID || ((stored.isEmpty() || stored.isFluidEqual(product))
                && (mode == OutputMode.VOID_EXCESS || tanks[index].fill(product, SIMULATE) == product.getAmount()));
    }

    void produce(int index, FluidStack stack) {
        if (outputModes[index - inputs] != OutputMode.VOID) {
            tanks[index].fill(stack, EXECUTE);
        }
    }

    int setting(int index) {
        return index < inputs ? (voidUnusable >>> index) & 1 : outputModes[index - inputs].ordinal();
    }

    void cycleSetting(int index) {
        if (index < inputs) {
            voidUnusable ^= 1 << index;
        } else {
            OutputMode next = outputModes[index - inputs].next();
            outputModes[index - inputs] = next;
            if (next == OutputMode.VOID) {
                clear(index);
            }
        }
        owner.setChanged();
    }

    boolean discardsUnusableInputs() {
        return voidUnusable != 0;
    }

    void discardUnusableInputs() {
        for (int index = 0; index < inputs; index++) {
            if ((voidUnusable & (1 << index)) != 0) {
                clear(index);
            }
        }
    }

    boolean separatesInputs() {
        return separateInputs;
    }

    void toggleSeparation() {
        separateInputs = !separateInputs;
        owner.setChanged();
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
        Mode next = Mode.read(sideModes[index], side).next(index < inputs);
        Mode[] modes = new Mode[SIDES.length];
        for (Direction face : SIDES) {
            modes[face.ordinal()] = face == side ? next : Mode.read(sideModes[index], face);
        }
        sideModes[index] = Mode.pack(modes);
        faces.put(side, new Face(side)).invalidate();
        connectionsChanged();
    }

    void reset(int index, boolean defaults) {
        sideModes[index] = defaults ? defaultModes(index) : 4095;
        refreshFaces();
        connectionsChanged();
    }

    private void connectionsChanged() {
        owner.setChanged();
        Level level = owner.getLevel();
        if (level != null) {
            level.updateNeighborsAt(owner.getBlockPos(), owner.getBlockState().getBlock());
        }
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

    void saveTank(int index, CompoundTag saved, String contentKey, String modesKey, String settingKey) {
        saved.put(contentKey, tanks[index].writeToNBT(new CompoundTag()));
        saved.putInt(modesKey, sideModes[index]);
        saved.putInt(settingKey, setting(index));
    }

    void loadTank(int index, CompoundTag saved, String contentKey, String modesKey, String settingKey) {
        tanks[index].readFromNBT(saved.getCompound(contentKey));
        sideModes[index] = saved.contains(modesKey) ? saved.getInt(modesKey) : legacyModes(index, saved);
        if (index < inputs) {
            boolean discard = saved.contains(settingKey) ? saved.getInt(settingKey) == 1
                    : saved.getBoolean("voidUnusableFluidInput" + index);
            int bit = 1 << index;
            voidUnusable = discard ? voidUnusable | bit : voidUnusable & ~bit;
        } else {
            int policy = saved.contains(settingKey) ? saved.getInt(settingKey)
                    : saved.contains("voidExcessFluidOutput" + index) ? (saved.getBoolean("voidExcessFluidOutput" + index) ? 1 : 0)
                    : saved.getInt("tankOutputSetting" + index);
            OutputMode mode = OutputMode.byId(policy);
            outputModes[index - inputs] = mode;
            if (mode == OutputMode.VOID) {
                clear(index);
            }
        }
    }

    void saveSeparation(CompoundTag tag) {
        if (inputs > 1) {
            tag.putBoolean("input_tanks_separated", separateInputs);
        }
    }

    void loadSeparation(CompoundTag tag) {
        if (inputs > 1) {
            separateInputs = tag.contains("input_tanks_separated") ? tag.getBoolean("input_tanks_separated")
                    : !tag.contains("inputTanksSeparated") || tag.getBoolean("inputTanksSeparated");
        }
    }

    private int legacyModes(int index, CompoundTag tag) {
        Mode[] modes = new Mode[SIDES.length];
        for (Direction side : SIDES) {
            int face = side.ordinal();
            CompoundTag connection = tag.getCompound("fluidConnection" + face);
            String key = "sorption" + index;
            modes[face] = connection.contains(key) ? Mode.fromLegacy(connection.getInt(key))
                    : Mode.read(defaultModes(index), side);
        }
        return Mode.pack(modes);
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

    public enum OutputMode {
        HOLD("hold"), VOID_EXCESS("void_excess"), VOID("void");

        private static final OutputMode[] MODES = values();
        private final String key;

        OutputMode(String key) {
            this.key = key;
        }

        OutputMode next() {
            return MODES[(ordinal() + 1) % MODES.length];
        }

        static OutputMode byId(int index) {
            return index >= 0 && index < MODES.length ? MODES[index] : HOLD;
        }

        public Component label() {
            return Component.translatable("gui.nuclearcraft.fluid_output." + key);
        }
    }

    public enum Mode {
        INPUT("input"), OUTPUT("output"), AUTO_OUTPUT("auto_output"), OFF("off"),
        BOTH("both"), AUTO_INPUT("input");

        private static final int EXTENDED_MODES = 4096;
        private static final int[] PLACE_VALUES = {1, 6, 36, 216, 1296, 7776};
        private static final Mode[] MODES = values();
        private final String key;

        Mode(String key) {
            this.key = key;
        }

        private Mode next(boolean input) {
            return switch (this) {
                case INPUT -> OUTPUT;
                case OUTPUT -> AUTO_OUTPUT;
                case AUTO_OUTPUT -> OFF;
                case OFF -> input ? INPUT : OUTPUT;
                case BOTH, AUTO_INPUT -> input ? INPUT : OUTPUT;
            };
        }

        public static Mode read(int modes, Direction side) {
            // ContainerData transports a signed short. Retain the old two-bit encoding for ordinary modes.
            int unsigned = modes & 0xFFFF;
            if (unsigned >= EXTENDED_MODES + PLACE_VALUES[SIDES.length - 1] * MODES.length) {
                return OFF;
            }
            int face = side.ordinal();
            return unsigned < EXTENDED_MODES ? MODES[(unsigned >>> (2 * face)) & 3]
                    : MODES[(unsigned - EXTENDED_MODES) / PLACE_VALUES[face] % MODES.length];
        }

        private static int pack(Mode[] modes) {
            int compact = 0;
            int extended = 0;
            boolean needsExtended = false;
            for (int face = 0; face < modes.length; face++) {
                int id = modes[face].ordinal();
                compact |= id << (2 * face);
                extended += id * PLACE_VALUES[face];
                needsExtended |= id > 3;
            }
            return needsExtended ? EXTENDED_MODES + extended : compact;
        }

        private static Mode fromLegacy(int id) {
            return switch (id) {
                case 0 -> INPUT;
                case 1 -> OUTPUT;
                case 2 -> BOTH;
                case 4 -> AUTO_INPUT;
                case 5 -> AUTO_OUTPUT;
                default -> OFF;
            };
        }

        private boolean canFill() {
            return this == INPUT || this == BOTH || this == AUTO_INPUT;
        }

        private boolean canDrain() {
            return this == OUTPUT || this == AUTO_OUTPUT || this == BOTH;
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
            return index >= 0 && index < inputs && active() && modes[index].canFill() && tanks[index].isFluidValid(stack);
        }

        @Override
        public int fill(FluidStack stack, FluidAction action) {
            if (!active() || stack.isEmpty()) {
                return 0;
            }
            int empty = -1;
            for (int index = 0; index < inputs; index++) {
                if (!modes[index].canFill() || !tanks[index].isFluidValid(stack)) {
                    continue;
                }
                FluidStack contents = tanks[index].getFluid();
                if (!separateInputs && (contents.isEmpty() || contents.isFluidEqual(stack))) {
                    int filled = tanks[index].fill(stack, action);
                    if (filled > 0) {
                        return filled;
                    }
                } else if (separateInputs && contents.isFluidEqual(stack)) {
                    return tanks[index].fill(stack, action);
                }
                if (empty == -1 && contents.isEmpty()) {
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
