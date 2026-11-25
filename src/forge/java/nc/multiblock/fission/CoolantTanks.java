package nc.multiblock.fission;

import nc.recipe.FissionCoolantRecipe;
import nc.recipe.FissionCoolantRecipes;
import nc.recipe.FluidBatch;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

final class CoolantTanks {
    static final int PORT_VOLUME = 1296;

    final FluidTank cold;
    final FluidTank hot;
    private final FissionCoolant material;
    private final BlockEntity owner;
    private final Supplier<@Nullable ResourceLocation> filter;
    private long minimum = PORT_VOLUME;

    CoolantTanks(FissionCoolant material, BlockEntity owner) {
        this(material, owner, () -> null);
    }

    CoolantTanks(FissionCoolant material, BlockEntity owner, Supplier<@Nullable ResourceLocation> filter) {
        this.material = material;
        this.owner = owner;
        this.filter = filter;
        cold = new Tank(false);
        hot = new Tank(true);
    }

    @Nullable ResourceLocation filter() {
        return filter.get();
    }

    FluidTank tank(boolean output) {
        return output ? hot : cold;
    }

    void resize(long capacity) {
        minimum = Math.max(PORT_VOLUME, capacity);
    }

    private List<FissionCoolantRecipe> recipes() {
        return FissionCoolantRecipes.forMaterial(owner.getLevel(), material);
    }

    boolean accepts(FluidStack stack) {
        for (var recipe : recipes()) {
            if (recipe.input().accepts(stack)) return true;
        }
        return false;
    }

    private static boolean matches(FluidStack stack, @Nullable ResourceLocation filter) {
        return filter == null || filter.equals(ForgeRegistries.FLUIDS.getKey(stack.getFluid()));
    }

    @Nullable FissionCoolantRecipe ready(@Nullable ResourceLocation partFilter) {
        FluidStack input = cold.getFluid();
        if (input.isEmpty() || !matches(input, filter()) || !matches(input, partFilter)) {
            return null;
        }
        for (var recipe : recipes()) {
            if (recipe.input().matches(input)) {
                return recipe.cooling() > 0 && recipe.product().batchesIn(hot.getFluid(), hot.getCapacity()) > 0
                        ? recipe : null;
            }
        }
        return null;
    }

    void process(@Nullable ResourceLocation partFilter, FissionCoolantRecipe recipe, int batches, RandomSource random) {
        while (batches > 0 && recipe != null) {
            int limit = Math.min(batches, recipe.product().batchesIn(hot.getFluid(), hot.getCapacity()));
            var input = FluidBatch.fit(recipe.input(), cold.getFluid(), cold.getFluidAmount(), limit);
            if (input.batches() == 0) {
                return;
            }
            int produced = recipe.product().totalAmount(input.batches(), 1, random);
            if (produced > 0) {
                hot.fill(new FluidStack(recipe.product().stack(), produced), FluidAction.EXECUTE);
            }
            cold.drain(input.consumed(), FluidAction.EXECUTE);
            batches -= input.batches();
            if (batches > 0) {
                recipe = ready(partFilter);
            }
        }
    }

    void clear() {
        cold.drain(cold.getFluidAmount(), FluidAction.EXECUTE);
        hot.drain(hot.getFluidAmount(), FluidAction.EXECUTE);
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("input", cold.writeToNBT(new CompoundTag()));
        tag.put("output", hot.writeToNBT(new CompoundTag()));
        return tag;
    }

    void load(CompoundTag tag) {
        cold.readFromNBT(tag.getCompound("input"));
        hot.readFromNBT(tag.getCompound("output"));
    }

    private final class Tank extends FluidTank {
        private final boolean output;

        private Tank(boolean output) {
            super(PORT_VOLUME);
            this.output = output;
        }

        @Override
        public boolean isFluidValid(FluidStack stack) {
            if (stack.isEmpty()) return false;
            if (!output) return matches(stack, filter()) && accepts(stack);
            for (var recipe : recipes()) {
                if (recipe.product().stack().isFluidEqual(stack)) return true;
            }
            return false;
        }

        @Override
        public int getCapacity() {
            long limit = Math.max(minimum, getFluidAmount());
            for (var recipe : recipes()) {
                int batch = output ? recipe.product().stack().getAmount() : recipe.input().maxAmount();
                limit = Math.max(limit, batch);
            }
            return (int) Math.min(Integer.MAX_VALUE, limit);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            setCapacity(getCapacity());
            return super.fill(resource, action);
        }

        @Override
        protected void onContentsChanged() {
            owner.setChanged();
        }
    }
}
