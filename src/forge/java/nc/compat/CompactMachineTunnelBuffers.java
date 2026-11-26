package nc.compat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

public final class CompactMachineTunnelBuffers {
    private static final ClassValue<Method> GET_TUNNEL = new ClassValue<>() {
        @Override
        protected Method computeValue(Class<?> type) {
            try {
                return type.getMethod("getTunnel");
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException("Compact Machines tunnel wall has no getTunnel method", e);
            }
        }
    };

    private CompactMachineTunnelBuffers() {}

    public interface Owned {
        void nuclearcraft$setTunnelOwner(@Nullable BlockEntity owner);
    }

    public static @Nullable Owned ownerBuffer(BlockEntity wall) {
        try {
            Object tunnel = GET_TUNNEL.get(wall.getClass()).invoke(wall);
            return tunnel instanceof Owned owned ? owned : null;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to bind Compact Machines tunnel buffer", e);
        }
    }

    public static void markChanged(@Nullable BlockEntity owner) {
        if (owner != null && owner.getLevel() instanceof ServerLevel && !owner.isRemoved()) {
            owner.setChanged();
        }
    }

    public static ItemStackHandler items(int slots, Runnable changed) {
        return new ItemStackHandler(slots) {
            @Override
            protected void onContentsChanged(int slot) {
                changed.run();
            }
        };
    }

    public static FluidTank fluid(int capacity, Runnable changed) {
        return new FluidTank(capacity) {
            @Override
            protected void onContentsChanged() {
                changed.run();
            }
        };
    }

    public static EnergyStorage energy(int capacity, Runnable changed) {
        return new EnergyStorage(capacity) {
            @Override
            public int receiveEnergy(int amount, boolean simulate) {
                int received = super.receiveEnergy(amount, simulate);
                if (!simulate && received > 0) {
                    changed.run();
                }
                return received;
            }

            @Override
            public int extractEnergy(int amount, boolean simulate) {
                int extracted = super.extractEnergy(amount, simulate);
                if (!simulate && extracted > 0) {
                    changed.run();
                }
                return extracted;
            }
        };
    }
}
