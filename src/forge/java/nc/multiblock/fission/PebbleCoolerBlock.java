package nc.multiblock.fission;

import nc.init.NCGasCoolants;

public final class PebbleCoolerBlock extends FissionPartBlock {
    private final NCGasCoolants gas;

    public PebbleCoolerBlock(Properties properties, NCGasCoolants gas) {
        super(properties, Role.PEBBLE_COOLER);
        this.gas = gas;
    }

    public NCGasCoolants gas() {
        return gas;
    }
}
