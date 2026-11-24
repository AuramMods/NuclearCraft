package nc.multiblock.turbine;

import nc.multiblock.turbine.TurbinePartBlock.Part;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public record TurbineVisuals(BlockPos origin, Direction flow, int shaftWidth, int bladeLength, List<Part> stages,
                             boolean glass, boolean processing, double angle, double velocity, long tick,
                             double speedRatio, FluidStack exhaust, int capacity,
                             @Nullable ResourceLocation particle, double particleRate, double particleSpeed) {
    public int size(Direction.Axis axis) {
        return axis == flow.getAxis() ? stages.size() : diameter();
    }

    public int diameter() {
        return shaftWidth + 2 * bladeLength;
    }

    public double angleAt(double time) {
        return (angle + velocity * Mth.clamp(time - tick, -1D, 10D)) % 360D;
    }

    CompoundTag write() {
        var tag = new CompoundTag();
        tag.putLong("origin", origin.asLong());
        tag.putString("flow", flow.getSerializedName());
        tag.putInt("shaft", shaftWidth);
        tag.putInt("blades", bladeLength);
        tag.putIntArray("stages", stages.stream().mapToInt(Part::ordinal).toArray());
        tag.putBoolean("glass", glass);
        tag.putBoolean("processing", processing);
        tag.putDouble("rotation", angle);
        tag.putDouble("velocity", velocity);
        tag.putLong("tick", tick);
        tag.putDouble("speed_ratio", speedRatio);
        if (glass) {
            tag.put("exhaust", exhaust.writeToNBT(new CompoundTag()));
            tag.putInt("exhaust_capacity", capacity);
            if (particle != null) {
                tag.putString("particle", particle.toString());
                tag.putDouble("particle_rate", particleRate);
                tag.putDouble("particle_speed", particleSpeed);
            }
        }
        return tag;
    }

    static @Nullable TurbineVisuals read(CompoundTag tag) {
        Direction flow = Direction.byName(tag.getString("flow"));
        int shaft = tag.getInt("shaft");
        int blades = tag.getInt("blades");
        int max = TurbinePartBlock.MAX_INTERIOR_LENGTH;
        int[] ids = tag.getIntArray("stages");
        if (flow == null || shaft < 1 || shaft > max || blades < 1 || blades > max
                || shaft + 2 * blades > max || ids.length == 0 || ids.length > max) {
            return null;
        }
        Part[] all = Part.values();
        var stages = new ArrayList<Part>(ids.length);
        for (int id : ids) {
            if (id < 0 || id >= all.length || all[id].expansion <= 0) {
                return null;
            }
            stages.add(all[id]);
        }
        return new TurbineVisuals(BlockPos.of(tag.getLong("origin")), flow, shaft, blades, List.copyOf(stages),
                tag.getBoolean("glass"), tag.getBoolean("processing"), tag.getDouble("rotation"),
                tag.getDouble("velocity"), tag.getLong("tick"), tag.getDouble("speed_ratio"),
                FluidStack.loadFluidStackFromNBT(tag.getCompound("exhaust")), tag.getInt("exhaust_capacity"),
                ResourceLocation.tryParse(tag.getString("particle")), tag.getDouble("particle_rate"), tag.getDouble("particle_speed"));
    }
}
