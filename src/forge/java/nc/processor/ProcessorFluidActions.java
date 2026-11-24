package nc.processor;

import org.jetbrains.annotations.Nullable;

public final class ProcessorFluidActions {
    private static final int CLEAR = 96;
    private static final int RESTORE = 104;
    private static final int DISABLE = 112;
    private static final int END = 120;

    private ProcessorFluidActions() {}

    public static int clearButton(int tank) {
        return CLEAR + tank;
    }

    public static int restoreButton(int tank) {
        return RESTORE + tank;
    }

    public static int disableButton(int tank) {
        return DISABLE + tank;
    }

    static boolean click(@Nullable ProcessorFluidTanks tanks, int button) {
        if (tanks == null || button < CLEAR || button >= END) {
            return false;
        }
        int tank = (button - CLEAR) % 8;
        if (tank >= tanks.size()) {
            return false;
        }
        if (button < RESTORE) {
            tanks.clear(tank);
        } else {
            tanks.reset(tank, button < DISABLE);
        }
        return true;
    }
}
