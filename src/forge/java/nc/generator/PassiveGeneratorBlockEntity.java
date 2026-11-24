package nc.generator;

import nc.energy.EnergyBankBlockEntity;
import nc.init.NCGenerators;
import nc.init.NCGenerators.PassiveGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class PassiveGeneratorBlockEntity extends EnergyBankBlockEntity {
    private final PassiveGenerator generator;
    private long lastGenerationTick = Long.MIN_VALUE;

    public PassiveGeneratorBlockEntity(BlockPos pos, BlockState state, PassiveGenerator generator) {
        super(generator.entity(), pos, state);
        this.generator = generator;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, PassiveGeneratorBlockEntity entity) {
        entity.generateTick(level.getGameTime());
        entity.pushPower();
    }

    @Override
    protected long localCapacity() {
        return 4L * generator.power();
    }

    @Override
    protected long minimumBankCapacity() {
        // Legacy energy storage had a minimum buffer of one default RF-to-EU unit.
        return 16L;
    }

    @Override
    protected int configRevision() {
        return NCGenerators.configRevision();
    }

    @Override
    protected boolean sharesBankWith(EnergyBankBlockEntity other) {
        return !generator.solar() && other instanceof PassiveGeneratorBlockEntity part && !part.generator.solar();
    }

    @Override
    protected boolean canReceive(Direction face) {
        return false;
    }

    @Override
    protected boolean canExtract(Direction face) {
        return true;
    }

    @Override
    protected long generated(long tick) {
        if (lastGenerationTick == tick) {
            return 0;
        }
        lastGenerationTick = tick;
        int power = generator.power();
        if (!generator.solar()) {
            return power;
        }
        if (!level.dimensionType().hasSkyLight() || !level.canSeeSkyFromBelowWater(worldPosition.above())) {
            return 0;
        }
        float brightness = Mth.clamp(2F * Mth.cos(level.getSunAngle(1F)) + 0.5F, 0F, 1F);
        brightness = (float) (brightness * (1D - level.getRainLevel(1F) * 5F / 16D));
        brightness = (float) (brightness * (1D - level.getThunderLevel(1F) * 5F / 16D));
        return (long) (brightness * power);
    }
}
