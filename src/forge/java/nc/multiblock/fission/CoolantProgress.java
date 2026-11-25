package nc.multiblock.fission;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;

final class CoolantProgress {
    private final BlockEntity part;
    private double amount;
    private boolean needsMigration;

    CoolantProgress(BlockEntity part) {
        this.part = part;
    }

    double amount() {
        return amount;
    }

    void set(double next) {
        if (amount != next || needsMigration) {
            amount = next;
            needsMigration = false;
            part.setChanged();
        }
    }

    void restoreFromController(double saved) {
        if (needsMigration) {
            set(saved);
        }
    }

    void save(CompoundTag tag) {
        // An explicit zero prevents an older controller entry from attaching to a replacement part.
        if (!needsMigration) {
            tag.putDouble("progress", amount);
        }
    }

    void load(CompoundTag tag) {
        boolean partProgress = tag.contains("progress", Tag.TAG_ANY_NUMERIC);
        needsMigration = !partProgress && !tag.contains("time", Tag.TAG_ANY_NUMERIC);
        double saved = tag.getDouble(partProgress ? "progress" : "time");
        amount = saved > 0D && saved < 1D ? saved : 0D;
    }
}
