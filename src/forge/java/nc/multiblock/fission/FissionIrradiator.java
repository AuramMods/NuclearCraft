package nc.multiblock.fission;

import nc.init.NCFission;
import nc.multiblock.computer.ComputerValues;
import nc.radiation.ChunkRadiationSource;
import nc.recipe.FissionIrradiatorRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

public final class FissionIrradiator extends BlockEntity implements ChunkRadiationSource {
    private final IrradiatorItems items = new IrradiatorItems(this);
    private final SimpleContainer recipeInput = new SimpleContainer(1);
    private final RecipeManager.CachedCheck<Container, FissionIrradiatorRecipe> recipes =
            RecipeManager.createCheck(NCFission.IRRADIATION.get());
    private @Nullable IrradiatorItems linked;
    private @Nullable SolidFissionController controller;
    private long progress;
    private ItemStack workingInput = ItemStack.EMPTY;
    private @Nullable ResourceLocation workingRecipe;
    private long flux;
    private double emission;
    private long lastTick = Long.MIN_VALUE;

    public FissionIrradiator(BlockPos pos, BlockState state) {
        super(NCFission.IRRADIATOR_ENTITY.get(), pos, state);
    }

    IrradiatorItems localItems() {
        return items;
    }

    IrradiatorItems inventory() {
        return controller() != null && linked != null ? linked : items;
    }

    @Nullable SolidFissionController controller() {
        return controller != null && controller.assembled() ? controller : null;
    }

    void bind(SolidFissionController controller, @Nullable IrradiatorItems linked) {
        this.controller = controller;
        this.linked = linked;
    }

    void unbind() {
        controller = null;
        linked = null;
        lastTick = Long.MIN_VALUE;
    }

    @Nullable FissionIrradiatorRecipe prepare(ServerLevel level) {
        var inventory = inventory();
        ItemStack input = inventory.getStackInSlot(0);
        var recipe = input.isEmpty() || !inventory.matchesFilter(input) ? null : findRecipe(level, input);
        if (controller() != null && workingRecipe != null && (input.isEmpty()
                || !ItemStack.isSameItemSameTags(input, workingInput) || recipe != null && !recipe.id().equals(workingRecipe))) {
            progress = 0;
            workingRecipe = null;
            workingInput = ItemStack.EMPTY;
            setChanged();
        }
        return recipe != null && inventory.insertItem(1, recipe.result(), true).isEmpty() ? recipe : null;
    }

    private @Nullable FissionIrradiatorRecipe findRecipe(ServerLevel level, ItemStack input) {
        recipeInput.setItem(0, input);
        return recipes.getRecipeFor(recipeInput, level).orElse(null);
    }

    void irradiate(ServerLevel level, FissionIrradiatorRecipe recipe, long received) {
        flux = received;
        emission = 0;
        lastTick = level.getGameTime();
        if (received <= 0 || !recipe.inFluxRange(received)) {
            return;
        }
        var inventory = inventory();
        ItemStack input = inventory.getStackInSlot(0);
        ItemStack result = recipe.result();
        if (!recipe.matchesInput(input) || !inventory.insertItem(1, result, true).isEmpty()) {
            return;
        }
        if (workingRecipe != null && (!workingRecipe.equals(recipe.id()) || !ItemStack.isSameItemSameTags(input, workingInput))) {
            progress = 0;
        }
        workingRecipe = recipe.id();
        workingInput = input.copyWithCount(1);
        long remaining = received;
        while (remaining > 0 && inventory.insertItem(1, result, true).isEmpty()) {
            int count = recipe.input().matchedCount(inventory.getStackInSlot(0));
            if (count < 0) {
                break;
            }
            long used = Math.min(remaining, Math.max(0, recipe.fluxRequired() - progress));
            remaining -= used;
            progress += used;
            if (progress >= recipe.fluxRequired()) {
                inventory.extractItem(0, count, false);
                inventory.insertItem(1, result.copy(), false);
                progress = 0;
            }
        }
        if (remaining < received) {
            emission = recipe.radiation() * received / FissionMaterials.get(level).maxModeratorLineFlux();
        }
        setChanged();
    }

    long flux() {
        return level != null && lastTick != Long.MIN_VALUE && level.getGameTime() - lastTick <= 1
                && controller() != null ? flux : 0;
    }

    void clearMaterial() {
        items.clearMaterial();
        if (linked != null && linked != items) {
            linked.clearMaterial();
        }
        workingInput = ItemStack.EMPTY;
        workingRecipe = null;
        progress = 0L;
        flux = 0L;
        setChanged();
    }

    Map<String, Object> computerStats(ServerLevel level) {
        var recipe = computerRecipe(level);
        var info = new LinkedHashMap<String, Object>();
        long received = flux();
        info.put("input", ComputerValues.item(inventory().getStackInSlot(0)));
        info.put("output", ComputerValues.item(inventory().getStackInSlot(1)));
        info.put("current_time", progress);
        info.put("base_process_time", recipe == null ? 1L : recipe.fluxRequired());
        info.put("base_process_heat_per_flux", recipe == null ? 0D : recipe.heatPerFlux());
        info.put("base_process_efficiency", recipe == null ? 0D : recipe.efficiency());
        info.put("is_processing", received > 0);
        info.put("flux", received);
        info.put("effective_heating", recipe == null ? 0D : received * recipe.heatPerFlux() * recipe.efficiency());
        return info;
    }

    float progress(ServerLevel level) {
        var recipe = computerRecipe(level);
        return recipe == null ? 0F : (float) Math.min(1D, (double) progress / recipe.fluxRequired());
    }

    private @Nullable FissionIrradiatorRecipe computerRecipe(ServerLevel level) {
        return workingRecipe == null ? findRecipe(level, inventory().getStackInSlot(0))
                : level.getRecipeManager().byKey(workingRecipe)
                        .filter(FissionIrradiatorRecipe.class::isInstance).map(FissionIrradiatorRecipe.class::cast).orElse(null);
    }

    @Override
    public double radiationEmission() {
        return level != null && level.getGameTime() == lastTick ? emission : 0;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        items.save(tag);
        tag.putLong("progress", progress);
        if (workingRecipe != null) {
            tag.putString("progress_recipe", workingRecipe.toString());
            tag.put("progress_input", workingInput.save(new CompoundTag()));
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        items.load(tag);
        progress = Math.max(0, tag.contains("progress") ? tag.getLong("progress") : (long) tag.getDouble("time"));
        workingRecipe = tag.contains("progress_recipe") ? ResourceLocation.tryParse(tag.getString("progress_recipe")) : null;
        workingInput = ItemStack.of(tag.getCompound("progress_input"));
    }
}
