package nc.processor;

import nc.NuclearCraft;
import nc.recipe.FluidInput;
import nc.recipe.FluidProcessorRecipe;
import nc.recipe.FluidProduct;
import nc.recipe.ItemInput;
import nc.recipe.ItemProcessorRecipe;
import nc.recipe.MelterRecipe;
import nc.recipe.ProcessingRecipe;
import nc.util.LoadedBlockEntities;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fluids.FluidStack;
import org.cyclops.commoncapabilities.api.capability.recipehandler.IPrototypedIngredientAlternatives;
import org.cyclops.commoncapabilities.api.capability.recipehandler.IRecipeDefinition;
import org.cyclops.commoncapabilities.api.capability.recipehandler.IRecipeHandler;
import org.cyclops.commoncapabilities.api.capability.recipehandler.PrototypedIngredientAlternativesList;
import org.cyclops.commoncapabilities.api.capability.recipehandler.RecipeDefinition;
import org.cyclops.commoncapabilities.api.ingredient.IMixedIngredients;
import org.cyclops.commoncapabilities.api.ingredient.IPrototypedIngredient;
import org.cyclops.commoncapabilities.api.ingredient.IngredientComponent;
import org.cyclops.commoncapabilities.api.ingredient.MixedIngredients;
import org.cyclops.commoncapabilities.api.ingredient.PrototypedIngredient;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;

import static org.cyclops.commoncapabilities.api.ingredient.IngredientComponent.FLUIDSTACK;
import static org.cyclops.commoncapabilities.api.ingredient.IngredientComponent.ITEMSTACK;

public final class CommonCapabilitiesRecipes {
    private static final Capability<IRecipeHandler> RECIPES = CapabilityManager.get(new CapabilityToken<>() {});
    private static final IMixedIngredients EMPTY = new MixedIngredients(Map.of());
    private static final Set<UpgradableProcessorBlockEntity> OBSERVED_PROCESSORS =
            Collections.newSetFromMap(new WeakHashMap<>());

    private CommonCapabilitiesRecipes() {}

    public static void register() {
        MinecraftForge.EVENT_BUS.addGenericListener(BlockEntity.class, CommonCapabilitiesRecipes::attach);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, CommonCapabilitiesRecipes::recipesReloaded);
    }

    private static void attach(AttachCapabilitiesEvent<BlockEntity> event) {
        if (event.getObject() instanceof UpgradableProcessorBlockEntity processor) {
            LazyOptional<IRecipeHandler> recipes = LazyOptional.of(() -> new Handler(processor));
            event.addCapability(NuclearCraft.id("processor_recipes"), new ICapabilityProvider() {
                @Override
                public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
                    if (capability == RECIPES && processor.getLevel() instanceof ServerLevel) {
                        synchronized (OBSERVED_PROCESSORS) {
                            OBSERVED_PROCESSORS.add(processor);
                        }
                    }
                    return RECIPES.orEmpty(capability, recipes);
                }
            });
            event.addListener(recipes::invalidate);
        }
    }

    private static void recipesReloaded(OnDatapackSyncEvent event) {
        if (event.getPlayer() != null) {
            return;
        }
        List<UpgradableProcessorBlockEntity> observed;
        synchronized (OBSERVED_PROCESSORS) {
            observed = List.copyOf(OBSERVED_PROCESSORS);
        }
        var server = event.getPlayerList().getServer();
        for (var processor : observed) {
            if (!(processor.getLevel() instanceof ServerLevel level) || level.getServer() != server
                    || processor.isRemoved()) {
                continue;
            }
            var source = processor.getBlockPos();
            if (LoadedBlockEntities.get(level, source) != processor) {
                continue;
            }
            for (Direction direction : Direction.values()) {
                var pos = source.relative(direction);
                var neighbor = LoadedBlockEntities.get(level, pos);
                if (neighbor != null && BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(neighbor.getType())
                        .getNamespace().equals("integrateddynamics")) {
                    // Integrated Crafting refreshes an attuned interface on target changes.
                    neighbor.getBlockState().onNeighborChange(level, pos, source);
                }
            }
        }
    }

    private record Handler(UpgradableProcessorBlockEntity processor) implements IRecipeHandler {
        @Override
        public Set<IngredientComponent<?, ?>> getRecipeInputComponents() {
            var shape = processor.recipeInput();
            if (shape.getContainerSize() == 0) return Set.of(FLUIDSTACK);
            if (shape.fluidCount() == 0) return Set.of(ITEMSTACK);
            return Set.of(ITEMSTACK, FLUIDSTACK);
        }

        @Override
        public Set<IngredientComponent<?, ?>> getRecipeOutputComponents() {
            var outputs = new HashSet<IngredientComponent<?, ?>>();
            for (var recipe : getRecipes()) {
                outputs.addAll(recipe.getOutput().getComponents());
            }
            return outputs;
        }

        @Override
        public boolean isValidSizeInput(IngredientComponent<?, ?> component, int size) {
            var shape = processor.recipeInput();
            return size >= 0 && (component == ITEMSTACK && size <= shape.getContainerSize()
                    || component == FLUIDSTACK && size <= shape.fluidCount());
        }

        @Override
        public Collection<IRecipeDefinition> getRecipes() {
            var level = processor.getLevel();
            return level == null || !processor.rate.enabled() ? List.of()
                    : processor.availableRecipes(level).stream().map(CommonCapabilitiesRecipes::definition)
                            .filter(Objects::nonNull).toList();
        }

        @Override
        public IMixedIngredients simulate(IMixedIngredients ingredients) {
            var level = processor.getLevel();
            if (level == null || !processor.rate.enabled() || !getRecipeInputComponents().containsAll(ingredients.getComponents())) {
                return EMPTY;
            }
            var input = processor.recipeInput();
            var items = ingredients.getInstances(ITEMSTACK);
            var fluids = ingredients.getInstances(FLUIDSTACK);
            if (items.size() > input.getContainerSize() || fluids.size() > input.fluidCount()) {
                return EMPTY;
            }
            for (int slot = 0; slot < items.size(); slot++) {
                input.setItem(slot, items.get(slot).copy());
            }
            for (int tank = 0; tank < fluids.size(); tank++) {
                input.setFluid(tank, fluids.get(tank));
            }
            for (var recipe : processor.availableRecipes(level)) {
                if (recipe.matches(input, level)) {
                    var definition = definition(recipe);
                    return definition == null ? EMPTY : definition.getOutput();
                }
            }
            return EMPTY;
        }
    }

    private static @Nullable IRecipeDefinition definition(ProcessingRecipe recipe) {
        if (recipe instanceof ItemProcessorRecipe item) {
            return definition(item.inputs(), item.fluidInputs(), item.products(), item.fluidProducts(), 1);
        }
        if (recipe instanceof FluidProcessorRecipe fluid) {
            var input = fluid.itemInput();
            return definition(input == null ? List.of() : List.of(input), fluid.inputs(), List.of(),
                    fluid.products(), fluid.batchFactor());
        }
        if (recipe instanceof MelterRecipe melter) {
            return definition(List.of(melter.input()), List.of(), List.of(), List.of(melter.fluidProduct()), 1);
        }
        return null;
    }

    private static @Nullable IRecipeDefinition definition(List<ItemInput> itemInputs, List<FluidInput> fluidInputs,
                                                           List<ItemProcessorRecipe.Product> itemProducts,
                                                           List<FluidProduct> fluidProducts, int factor) {
        var inputs = new HashMap<IngredientComponent<?, ?>, List<IPrototypedIngredientAlternatives<?, ?>>>();
        var items = itemInputs.stream().filter(input -> !input.isEmpty()).map(input -> input.alternatives().stream()
                .filter(stack -> !stack.isEmpty()).toList()).toList();
        var fluids = fluidInputs.stream().filter(input -> !input.isEmpty()).map(input -> input.alternatives(factor).stream()
                .filter(stack -> !stack.isEmpty()).toList()).toList();
        if (items.stream().anyMatch(List::isEmpty) || fluids.stream().anyMatch(List::isEmpty)) {
            return null;
        }
        addInputs(inputs, ITEMSTACK, items);
        addInputs(inputs, FLUIDSTACK, fluids);
        if (inputs.isEmpty()) {
            return null;
        }

        var outputItems = new ArrayList<ItemStack>();
        for (var product : itemProducts) {
            var stack = product.stack();
            int maximum = stack.getCount();
            if (product.chancePercent() > 0 && product.chancePercent() < 100 && product.minCount() != maximum) {
                return null;
            }
            int count = product.chancePercent() == 100 ? maximum : product.minCount();
            if (count > 0) {
                outputItems.add(stack.copyWithCount(count));
            }
        }
        var outputFluids = new ArrayList<FluidStack>();
        for (var product : fluidProducts) {
            if (product.varies()) {
                return null;
            }
            var stack = product.stack();
            int amount = (product.chancePercent() == 100 ? stack.getAmount() : product.minAmount()) / factor;
            if (amount > 0) {
                outputFluids.add(new FluidStack(stack, amount));
            }
        }

        var outputs = new HashMap<IngredientComponent<?, ?>, List<?>>();
        if (!outputItems.isEmpty()) outputs.put(ITEMSTACK, outputItems);
        if (!outputFluids.isEmpty()) outputs.put(FLUIDSTACK, outputFluids);
        return outputs.isEmpty() ? null : new RecipeDefinition(inputs, new MixedIngredients(outputs));
    }

    private static <T, M> void addInputs(
            Map<IngredientComponent<?, ?>, List<IPrototypedIngredientAlternatives<?, ?>>> inputs,
            IngredientComponent<T, M> component, List<List<T>> groups) {
        if (!groups.isEmpty()) {
            M exact = component.getMatcher().getExactMatchCondition();
            List<IPrototypedIngredientAlternatives<?, ?>> ingredients = new ArrayList<>(groups.size());
            for (var group : groups) {
                ingredients.add(new PrototypedIngredientAlternativesList<>(group.stream()
                        .<IPrototypedIngredient<T, M>>map(stack -> new PrototypedIngredient<>(component, stack, exact)).toList()));
            }
            inputs.put(component, ingredients);
        }
    }
}
