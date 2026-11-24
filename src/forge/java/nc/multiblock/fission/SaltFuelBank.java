package nc.multiblock.fission;

import nc.init.NCSaltFission;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

final class SaltFuelBank {
    private static final int PORT_VOLUME = 36_000;
    private final SolidFissionController owner;
    // A null filter is the shared default circuit.
    private final Map<ResourceLocation, Circuit> circuits = new HashMap<>();

    SaltFuelBank(SolidFissionController owner) {
        this.owner = owner;
    }

    static boolean acceptsFuel(@Nullable Level level, FluidStack fluid) {
        return level != null && !fluid.isEmpty() && level.getRecipeManager().getAllRecipesFor(NCSaltFission.FUEL_TYPE.get())
                .stream().anyMatch(recipe -> recipe.fuel().accepts(fluid));
    }

    FluidTank tank(@Nullable ResourceLocation filter, boolean output) {
        Circuit circuit = circuits.computeIfAbsent(filter, Circuit::new);
        return output ? circuit.output : circuit.input;
    }

    void clearMaterial() {
        for (var circuit : circuits.values()) {
            circuit.input.setFluid(FluidStack.EMPTY);
            circuit.output.setFluid(FluidStack.EMPTY);
        }
        owner.setChanged();
    }

    @Nullable FluidTank existingTank(@Nullable ResourceLocation filter, boolean output) {
        Circuit circuit = circuits.get(filter);
        return circuit == null ? null : output ? circuit.output : circuit.input;
    }

    void resize(SolidFissionLayout layout) {
        var ports = new HashMap<ResourceLocation, Integer>();
        for (var port : layout.ports()) {
            if (port.role() == FissionPartBlock.Role.VESSEL_PORT) {
                ports.merge(port.fuelFilter(), 1, Integer::sum);
            }
        }
        ports.forEach((filter, count) -> {
            long capacity = (long) PORT_VOLUME * count;
            resize(tank(filter, false), capacity);
            resize(tank(filter, true), capacity);
        });
    }

    static void resize(FluidTank tank, long minimum) {
        tank.setCapacity((int) Math.min(Integer.MAX_VALUE, Math.max(minimum, tank.getFluidAmount())));
    }

    ListTag save() {
        ListTag list = new ListTag();
        circuits.forEach((filter, circuit) -> {
            if (!circuit.input.isEmpty() || !circuit.output.isEmpty()) {
                CompoundTag tag = new CompoundTag();
                if (filter != null) {
                    tag.putString("fuel_filter", filter.toString());
                }
                tag.put("input", circuit.input.writeToNBT(new CompoundTag()));
                tag.put("output", circuit.output.writeToNBT(new CompoundTag()));
                list.add(tag);
            }
        });
        return list;
    }

    void load(ListTag list) {
        for (Circuit circuit : circuits.values()) {
            circuit.input.setFluid(FluidStack.EMPTY);
            circuit.output.setFluid(FluidStack.EMPTY);
        }
        for (Tag entry : list) {
            CompoundTag tag = (CompoundTag) entry;
            ResourceLocation filter = tag.contains("fuel_filter") ? ResourceLocation.tryParse(tag.getString("fuel_filter")) : null;
            Circuit circuit = circuits.computeIfAbsent(filter, Circuit::new);
            circuit.input.readFromNBT(tag.getCompound("input"));
            circuit.output.readFromNBT(tag.getCompound("output"));
            resize(circuit.input, PORT_VOLUME);
            resize(circuit.output, PORT_VOLUME);
        }
    }

    private FluidTank makeTank(Predicate<FluidStack> accepts) {
        return new FluidTank(PORT_VOLUME, accepts) {
            @Override
            protected void onContentsChanged() {
                owner.setChanged();
            }
        };
    }

    private final class Circuit {
        final FluidTank input;
        final FluidTank output = makeTank(stack -> true);

        Circuit(@Nullable ResourceLocation filter) {
            input = makeTank(stack -> (filter == null || filter.equals(ForgeRegistries.FLUIDS.getKey(stack.getFluid())))
                    && acceptsFuel(owner.getLevel(), stack));
        }
    }
}
