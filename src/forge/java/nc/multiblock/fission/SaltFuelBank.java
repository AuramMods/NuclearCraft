package nc.multiblock.fission;

import nc.init.NCSaltFission;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

final class SaltFuelBank {
    private static final int BASE_VOLUME = 1_296;
    private static final int VOLUME_PER_VESSEL = 36;

    private final SolidFissionController owner;
    // A null filter is the unfiltered shared circuit.
    private final Map<ResourceLocation, Tanks> circuits = new HashMap<>();
    private final Set<ResourceLocation> connected = new HashSet<>();

    SaltFuelBank(SolidFissionController owner) {
        this.owner = owner;
    }

    static boolean acceptsFuel(@Nullable Level level, FluidStack fluid) {
        return level != null && !fluid.isEmpty() && level.getRecipeManager().getAllRecipesFor(NCSaltFission.FUEL_TYPE.get())
                .stream().anyMatch(recipe -> recipe.fuel().accepts(fluid));
    }

    Tanks circuit(@Nullable ResourceLocation filter) {
        return circuits.computeIfAbsent(filter, key -> new Tanks(owner, () -> key));
    }

    @Nullable Tanks connected(@Nullable ResourceLocation filter) {
        return connected.contains(filter) ? circuits.get(filter) : null;
    }

    void clearMaterial() {
        circuits.values().forEach(Tanks::clear);
    }

    void resize(Level level, SolidFissionLayout layout) {
        connected.clear();
        for (var port : layout.ports()) {
            if (port.role() == FissionPartBlock.Role.VESSEL_PORT) {
                connected.add(port.filter());
            }
        }
        var vessels = new HashMap<ResourceLocation, Integer>();
        for (var pos : layout.cells()) {
            if (level.getBlockEntity(pos) instanceof SaltVessel vessel && connected.contains(vessel.filter())) {
                vessels.merge(vessel.filter(), 1, Integer::sum);
            }
        }
        connected.forEach(this::circuit);
        circuits.forEach((filter, tanks) -> tanks.resize(vessels.getOrDefault(filter, 0)));
    }

    static void resize(FluidTank tank, long minimum) {
        tank.setCapacity((int) Math.min(Integer.MAX_VALUE, Math.max(minimum, tank.getFluidAmount())));
    }

    ListTag save() {
        ListTag list = new ListTag();
        circuits.forEach((filter, tanks) -> {
            if (!tanks.input.isEmpty() || !tanks.output.isEmpty()) {
                CompoundTag tag = tanks.save();
                if (filter != null) {
                    tag.putString("fuel_filter", filter.toString());
                }
                list.add(tag);
            }
        });
        return list;
    }

    void load(ListTag list) {
        for (Tanks tanks : circuits.values()) {
            tanks.clear();
        }
        for (Tag entry : list) {
            CompoundTag tag = (CompoundTag) entry;
            ResourceLocation filter = tag.contains("fuel_filter") ? ResourceLocation.tryParse(tag.getString("fuel_filter")) : null;
            circuit(filter).load(tag);
        }
    }

    static final class Tanks {
        private final FluidTank input;
        private final FluidTank output;
        private int capacity = BASE_VOLUME;

        Tanks(BlockEntity owner, Supplier<@Nullable ResourceLocation> filter) {
            input = makeTank(owner, stack -> {
                var selected = filter.get();
                return (selected == null || selected.equals(ForgeRegistries.FLUIDS.getKey(stack.getFluid())))
                        && acceptsFuel(owner.getLevel(), stack);
            });
            output = makeTank(owner, stack -> true);
        }

        FluidTank tank(boolean drained) {
            return drained ? output : input;
        }

        private FluidTank makeTank(BlockEntity owner, Predicate<FluidStack> accepts) {
            return new FluidTank(BASE_VOLUME, accepts) {
                @Override
                protected void onContentsChanged() {
                    SaltFuelBank.resize(this, capacity);
                    owner.setChanged();
                }
            };
        }

        void resize(int vessels) {
            capacity = (int) Math.min(Integer.MAX_VALUE, Math.max(BASE_VOLUME, (long) VOLUME_PER_VESSEL * vessels));
            SaltFuelBank.resize(input, capacity);
            SaltFuelBank.resize(output, capacity);
        }

        void clear() {
            input.setFluid(FluidStack.EMPTY);
            output.setFluid(FluidStack.EMPTY);
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.put("input", input.writeToNBT(new CompoundTag()));
            tag.put("output", output.writeToNBT(new CompoundTag()));
            return tag;
        }

        void load(CompoundTag tag) {
            input.readFromNBT(tag.getCompound("input"));
            output.readFromNBT(tag.getCompound("output"));
            SaltFuelBank.resize(input, capacity);
            SaltFuelBank.resize(output, capacity);
        }
    }
}
