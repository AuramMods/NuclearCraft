package nc.compat;

import net.minecraftforge.energy.IEnergyStorage;

import java.util.IdentityHashMap;
import java.util.function.LongSupplier;

public final class EslTransferProbe {
    private static final ThreadLocal<IdentityHashMap<IEnergyStorage, Integer>> ACTIVE = new ThreadLocal<>();

    private EslTransferProbe() {}

    public static boolean active() {
        return ACTIVE.get() != null;
    }

    public static long simulate(LongSupplier transfer) {
        if (active()) {
            return transfer.getAsLong();
        }
        ACTIVE.set(new IdentityHashMap<>());
        try {
            return transfer.getAsLong();
        } finally {
            ACTIVE.remove();
        }
    }

    public static int receive(IEnergyStorage storage, int maximum) {
        var reservations = ACTIVE.get();
        int reserved = reservations.getOrDefault(storage, 0);
        int total = storage.receiveEnergy((int) Math.min((long) reserved + maximum, Integer.MAX_VALUE), true);
        int accepted = Math.max(0, Math.min(maximum, total - reserved));
        if (accepted > 0) {
            reservations.put(storage, reserved + accepted);
        }
        return accepted;
    }
}
