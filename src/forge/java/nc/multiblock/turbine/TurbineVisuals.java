package nc.multiblock.turbine;

import nc.init.NCTurbine;
import nc.init.TurbineConfig;
import nc.multiblock.turbine.TurbinePartBlock.Part;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public record TurbineVisuals(BlockPos origin, Direction flow, int shaftWidth, int bladeLength, List<TurbineRotorBlock> stages,
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
        var stageBlocks = new ListTag();
        for (var block : stages) {
            stageBlocks.add(StringTag.valueOf(ForgeRegistries.BLOCKS.getKey(block).toString()));
        }
        tag.put("stage_blocks", stageBlocks);
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
        int max = TurbineConfig.MAX_INTERIOR_SIZE;
        if (flow == null || shaft < 1 || shaft > max || blades < 1 || blades > max
                || shaft + 2 * blades > max) {
            return null;
        }
        var stages = readStages(tag, max);
        if (stages.isEmpty()) {
            return null;
        }
        return new TurbineVisuals(BlockPos.of(tag.getLong("origin")), flow, shaft, blades, stages,
                tag.getBoolean("glass"), tag.getBoolean("processing"), tag.getDouble("rotation"),
                tag.getDouble("velocity"), tag.getLong("tick"), tag.getDouble("speed_ratio"),
                FluidStack.loadFluidStackFromNBT(tag.getCompound("exhaust")), tag.getInt("exhaust_capacity"),
                ResourceLocation.tryParse(tag.getString("particle")), tag.getDouble("particle_rate"), tag.getDouble("particle_speed"));
    }

    private static List<TurbineRotorBlock> readStages(CompoundTag tag, int max) {
        var stages = new ArrayList<TurbineRotorBlock>();
        if (tag.contains("stage_blocks")) {
            var ids = tag.getList("stage_blocks", Tag.TAG_STRING);
            if (ids.size() > max) {
                return List.of();
            }
            for (Tag entry : ids) {
                var id = ResourceLocation.tryParse(entry.getAsString());
                var block = id == null ? null : ForgeRegistries.BLOCKS.getValue(id);
                if (!(block instanceof TurbineRotorBlock rotor) || !rotor.stage()) {
                    return List.of();
                }
                stages.add(rotor);
            }
        } else {
            int[] ids = tag.getIntArray("stages");
            if (ids.length > max) {
                return List.of();
            }
            Part[] parts = Part.values();
            for (int id : ids) {
                if (id < 0 || id >= parts.length) {
                    return List.of();
                }
                var block = switch (parts[id]) {
                    case STEEL -> NCTurbine.STEEL.get();
                    case EXTREME -> NCTurbine.EXTREME.get();
                    case SIC_SIC_CMC -> NCTurbine.SIC_SIC_CMC.get();
                    case STATOR -> NCTurbine.STATOR.get();
                    default -> null;
                };
                if (block == null) {
                    return List.of();
                }
                stages.add(block);
            }
        }
        return List.copyOf(stages);
    }
}
