package nc.generator;

import nc.init.NCGenerators;
import nc.recipe.DecayGeneratorRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class DecayGeneratorBlockEntity extends BlockEntity {
    private static final Direction[] SIDES = Direction.values();

    private LazyOptional<IEnergyStorage> output = LazyOptional.of(Output::new);
    private int energy;
    private int capacity;

    public DecayGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(NCGenerators.DECAY_ENTITY.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, DecayGeneratorBlockEntity generator) {
        int interval = NCGenerators.decayInterval();
        if (Math.floorMod(level.getGameTime() + pos.hashCode(), interval) == 0) {
            generator.generate(level, pos, interval);
        }
        if (generator.energy > 0) {
            generator.push(level, pos);
        }
    }

    private void generate(Level level, BlockPos pos, int interval) {
        var recipes = level.getRecipeManager().getAllRecipesFor(NCGenerators.DECAY_RECIPES.get());
        double strongest = recipes.stream().mapToDouble(DecayGeneratorRecipe::power).max().orElse(0);
        capacity = 2 * (int) (interval * strongest);
        double power = 0;
        for (Direction side : SIDES) {
            BlockPos adjacent = pos.relative(side);
            if (!level.hasChunkAt(adjacent)) {
                continue;
            }
            var recipe = recipeAt(recipes, level.getBlockState(adjacent));
            if (recipe == null) {
                continue;
            }
            if (level.random.nextDouble() * recipe.lifetime() < interval) {
                level.setBlock(adjacent, recipe.resultBlock().defaultBlockState(), Block.UPDATE_ALL);
                recipe = recipeAt(recipes, level.getBlockState(adjacent));
            }
            if (recipe != null) {
                power += recipe.power();
            }
        }
        int next = energy + Math.min(Math.max(0, capacity - energy), (int) (interval * power));
        if (next != energy) {
            energy = next;
            setChanged();
        }
    }

    private static @Nullable DecayGeneratorRecipe recipeAt(List<DecayGeneratorRecipe> recipes, BlockState state) {
        ItemStack stack = new ItemStack(state.getBlock());
        if (stack.isEmpty()) {
            return null;
        }
        for (DecayGeneratorRecipe recipe : recipes) {
            if (recipe.accepts(stack)) {
                return recipe;
            }
        }
        return null;
    }

    private void push(Level level, BlockPos pos) {
        for (Direction side : SIDES) {
            if (energy == 0) {
                return;
            }
            BlockPos adjacent = pos.relative(side);
            BlockEntity target = level.hasChunkAt(adjacent) ? level.getBlockEntity(adjacent) : null;
            if (target != null) {
                target.getCapability(ForgeCapabilities.ENERGY, side.getOpposite()).ifPresent(this::pushEnergy);
            }
        }
    }

    private void pushEnergy(IEnergyStorage target) {
        int accepted = target.receiveEnergy(energy, true);
        int reserved = Math.max(0, Math.min(energy, accepted));
        if (reserved == 0) {
            return;
        }
        energy -= reserved;
        int received = 0;
        try {
            received = Math.max(0, Math.min(reserved, target.receiveEnergy(reserved, false)));
        } finally {
            energy += reserved - received;
            if (received != 0) {
                setChanged();
            }
        }
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
        return capability == ForgeCapabilities.ENERGY && !isRemoved() ? output.cast() : super.getCapability(capability, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        output.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        output = LazyOptional.of(Output::new);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("energy", energy);
        tag.putInt("capacity", capacity);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        energy = Math.max(0, tag.getInt("energy"));
        capacity = Math.max(0, tag.getInt("capacity"));
    }

    private final class Output implements IEnergyStorage {
        private final LazyOptional<IEnergyStorage> lifetime = output;

        @Override
        public int receiveEnergy(int amount, boolean simulate) { return 0; }

        @Override
        public int extractEnergy(int amount, boolean simulate) {
            int extracted = Math.max(0, Math.min(amount, getEnergyStored()));
            if (!simulate && extracted != 0) {
                energy -= extracted;
                setChanged();
            }
            return extracted;
        }

        @Override
        public int getEnergyStored() { return canExtract() ? energy : 0; }

        @Override
        public int getMaxEnergyStored() { return canExtract() ? Math.max(energy, capacity) : 0; }

        @Override
        public boolean canExtract() { return lifetime.isPresent() && !isRemoved(); }

        @Override
        public boolean canReceive() { return false; }
    }
}
