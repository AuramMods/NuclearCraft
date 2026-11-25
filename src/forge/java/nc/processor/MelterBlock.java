package nc.processor;

import nc.init.NCItemProcessors.Rate;
import nc.init.NCRegistries;

public final class MelterBlock extends ProcessorBlock<MelterBlockEntity> {
    public MelterBlock(Properties properties) {
        super(properties, NCRegistries.MELTER_ENTITY, MelterBlockEntity::tick, MelterBlockEntity::inventory, Rate.MELTER);
    }
}
