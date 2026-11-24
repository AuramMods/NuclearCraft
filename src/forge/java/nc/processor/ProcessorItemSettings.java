package nc.processor;

import nc.init.NCItemProcessors;
import nc.processor.ProcessorFluidTanks.OutputMode;
import nc.processor.ProcessorItemPorts.GroupMode;
import nc.processor.ProcessorItemPorts.SlotMode;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import org.jetbrains.annotations.Nullable;

public final class ProcessorItemSettings {
    private static final int FACE_COUNT = ProcessorItemPorts.SIDES.length;
    private static final int FACES_PER_VALUE = 3;
    private final int inputs;
    private final int outputs;
    private final int slots;
    private final int firstButton;
    private final @Nullable ProcessorItemPorts ports;
    private final ContainerData data;

    ProcessorItemSettings(int inputs, int outputs, int upgrades, int firstButton, @Nullable ProcessorItemPorts ports) {
        this.inputs = inputs;
        this.outputs = outputs;
        this.ports = ports;
        // Vanilla sends the button number as a signed byte. Each menu uses its own available range.
        this.firstButton = firstButton;
        slots = inputs + outputs + upgrades;
        data = ports == null ? new SimpleContainerData(FACE_COUNT + slots * 2) : new ContainerData() {
            @Override
            public int get(int index) {
                var sides = ProcessorItemPorts.SIDES;
                if (index < FACE_COUNT) {
                    return ports.group(sides[index]).ordinal();
                }
                int slot = (index - FACE_COUNT) / 2;
                int half = (index - FACE_COUNT) % 2;
                int modes = 0;
                for (int face = 0; face < FACES_PER_VALUE; face++) {
                    modes |= ports.mode(slot, sides[half * FACES_PER_VALUE + face]).ordinal() << (3 * face);
                }
                if (half == 1 && isOutput(slot)) {
                    modes |= ports.overflow(slot).ordinal() << 9;
                }
                return modes;
            }

            @Override public void set(int index, int value) {}
            @Override public int getCount() { return FACE_COUNT + slots * 2; }
        };
    }

    ContainerData data() {
        return data;
    }

    public int slots() {
        return slots;
    }

    public boolean isOutput(int slot) {
        return slot >= inputs && slot < inputs + outputs;
    }

    public GroupMode group(Direction side) {
        return GroupMode.byId(data.get(side.ordinal()));
    }

    public SlotMode mode(int slot, Direction side) {
        int faces = data.get(FACE_COUNT + slot * 2 + side.ordinal() / FACES_PER_VALUE);
        return SlotMode.byId((faces >>> (3 * (side.ordinal() % FACES_PER_VALUE))) & 7);
    }

    public OutputMode overflow(int slot) {
        return OutputMode.byId(data.get(FACE_COUNT + slot * 2 + 1) >>> 9);
    }

    public int slotButton(int slot, Direction side) {
        return firstButton + slot * FACE_COUNT + side.ordinal();
    }

    public int overflowButton(int slot) {
        return firstButton + slots * FACE_COUNT + slot - inputs;
    }

    public Component slotName(int slot) {
        if (slot < inputs) {
            return Component.translatable("gui.nuclearcraft.processor_items.input", slot + 1);
        }
        if (isOutput(slot)) {
            return Component.translatable("gui.nuclearcraft.processor_items.output", slot - inputs + 1);
        }
        return (slot == inputs + outputs ? NCItemProcessors.SPEED_UPGRADE : NCItemProcessors.ENERGY_UPGRADE)
                .get().getDescription();
    }

    boolean click(int button) {
        int offset = button - firstButton;
        if (ports == null || offset < 0 || offset >= slots * FACE_COUNT + outputs) {
            return false;
        }
        if (offset < slots * FACE_COUNT) {
            ports.cycleSlot(offset / FACE_COUNT, ProcessorItemPorts.SIDES[offset % FACE_COUNT]);
        } else {
            ports.cycleOverflow(inputs + offset - slots * FACE_COUNT);
        }
        return true;
    }
}
