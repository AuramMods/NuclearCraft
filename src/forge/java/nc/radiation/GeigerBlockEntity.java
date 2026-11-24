package nc.radiation;

import nc.init.NCRadiation;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class GeigerBlockEntity extends BlockEntity {
    private int signal;

    public GeigerBlockEntity(BlockPos pos, BlockState state) {
        super(NCRadiation.GEIGER_ENTITY.get(), pos, state);
    }

    public int signal() {
        return signal;
    }

    static void tick(Level level, BlockPos pos, BlockState state, GeigerBlockEntity geiger) {
        if (level instanceof ServerLevel server) {
            double rate = WorldRadiation.rate(server, pos);
            int next = rate <= 0 ? 0 : Mth.clamp(Mth.floor(15 + Math.log10(rate) - RadiationConfig.GEIGER_SCALE.get()), 0, 15);
            if (next != geiger.signal) {
                geiger.signal = next;
                geiger.setChanged();
                level.updateNeighbourForOutputSignal(pos, state.getBlock());
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("comparatorStrength", signal);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        signal = Mth.clamp(tag.getInt("comparatorStrength"), 0, 15);
    }
}
