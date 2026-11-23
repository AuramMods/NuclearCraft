package nc.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.forge.ForgeTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.handlers.IGuiClickableArea;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import nc.NuclearCraft;
import nc.client.ElectrolyzerScreen;
import nc.client.ItemProcessorScreen;
import nc.client.MelterScreen;
import nc.init.NCCollectors.Collector;
import nc.init.NCFission;
import nc.init.NCItemProcessors.Machine;
import nc.init.NCRegistries;
import nc.init.NCTurbine;
import nc.recipe.ElectrolyzerRecipe;
import nc.recipe.ItemProcessorRecipe;
import nc.recipe.MelterRecipe;
import nc.recipe.SolidFissionRecipe;
import nc.recipe.TurbineRecipe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static mezz.jei.api.recipe.RecipeIngredientRole.INPUT;
import static mezz.jei.api.recipe.RecipeIngredientRole.OUTPUT;

@JeiPlugin
public final class NuclearCraftJei implements IModPlugin {
    private static final RecipeType<MelterRecipe> MELTER = RecipeType.create("nuclearcraft", "melter", MelterRecipe.class);
    private static final RecipeType<ElectrolyzerRecipe> ELECTROLYZER =
            RecipeType.create("nuclearcraft", "electrolyzer", ElectrolyzerRecipe.class);
    private static final RecipeType<SolidFissionRecipe> FISSION =
            RecipeType.create("nuclearcraft", "solid_fission", SolidFissionRecipe.class);
    private static final RecipeType<TurbineRecipe> TURBINE =
            RecipeType.create("nuclearcraft", "turbine", TurbineRecipe.class);

    @Override
    public ResourceLocation getPluginUid() {
        return NuclearCraft.id("jei");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper gui = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new CollectorCategory(gui));
        for (Machine machine : Machine.values()) {
            registration.addRecipeCategories(new Category<>(itemType(machine), machine.block(), gui,
                    NuclearCraftJei::itemRecipe, recipe -> energy(recipe.ticks(), recipe.energyPerTick())));
        }
        registration.addRecipeCategories(
                new Category<>(MELTER, NCRegistries.MELTER.get(), gui, (layout, recipe) -> {
                    layout.addSlot(INPUT, 20, 12).setStandardSlotBackground().addIngredients(recipe.input());
                    fluidSlot(layout, OUTPUT, 104, List.of(recipe.product()));
                }, recipe -> energy(recipe.ticks(), recipe.energyPerTick())),
                new Category<>(ELECTROLYZER, NCRegistries.ELECTROLYZER.get(), gui, (layout, recipe) -> {
                    fluidSlot(layout, INPUT, 20, recipe.input().alternatives());
                    List<FluidStack> outputs = recipe.results();
                    for (int index = 0; index < outputs.size(); index++) {
                        fluidSlot(layout, OUTPUT, 98 + (index % 2) * 20, 2 + (index / 2) * 20,
                                List.of(outputs.get(index)));
                    }
                }, recipe -> energy(recipe.ticks(), recipe.energyPerTick())),
                new Category<>(FISSION, NCFission.CONTROLLER.get(), gui, (layout, recipe) -> {
                    layout.addSlot(INPUT, 20, 12).setStandardSlotBackground().addIngredients(recipe.fuel());
                    layout.addSlot(OUTPUT, 104, 12).setOutputSlotBackground().addItemStack(recipe.depleted());
                }, recipe -> Component.translatable("gui.nuclearcraft.jei.fission", recipe.ticks(), recipe.heat())),
                new Category<>(TURBINE, NCTurbine.CONTROLLER.get(), gui, (layout, recipe) -> {
                    var fluids = ForgeRegistries.FLUIDS.getValues().stream()
                            .filter(fluid -> fluid.isSource(fluid.defaultFluidState()))
                            .map(fluid -> new FluidStack(fluid, recipe.inputAmount())).filter(recipe::accepts).toList();
                    fluidSlot(layout, INPUT, 20, fluids);
                    fluidSlot(layout, OUTPUT, 104, List.of(recipe.output()));
                }, recipe -> Component.translatable("gui.nuclearcraft.jei.turbine", recipe.powerPerMb(), recipe.expansion())));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return;
        }
        RecipeManager recipes = connection.getRecipeManager();
        registration.addRecipes(CollectorCategory.TYPE, List.of(Collector.values()));
        for (Machine machine : Machine.values()) {
            registration.addRecipes(itemType(machine), recipes.getAllRecipesFor(machine.recipes()));
        }
        registration.addRecipes(MELTER, recipes.getAllRecipesFor(NCRegistries.MELTING.get()));
        registration.addRecipes(ELECTROLYZER, recipes.getAllRecipesFor(NCRegistries.ELECTROLYSIS.get()));
        registration.addRecipes(FISSION, recipes.getAllRecipesFor(NCFission.FUEL_TYPE.get()));
        registration.addRecipes(TURBINE, recipes.getAllRecipesFor(NCTurbine.RECIPES.get()));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        for (Collector collector : Collector.values()) {
            registration.addRecipeCatalyst(collector.block(), CollectorCategory.TYPE);
        }
        for (Machine machine : Machine.values()) {
            registration.addRecipeCatalyst(machine.block(), itemType(machine));
        }
        registration.addRecipeCatalyst(NCRegistries.MELTER.get(), MELTER);
        registration.addRecipeCatalyst(NCRegistries.ELECTROLYZER.get(), ELECTROLYZER);
        registration.addRecipeCatalyst(NCFission.CONTROLLER.get(), FISSION);
        registration.addRecipeCatalyst(NCTurbine.CONTROLLER.get(), TURBINE);
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addRecipeClickArea(MelterScreen.class, 74, 35, 37, 16, MELTER);
        registration.addRecipeClickArea(ElectrolyzerScreen.class, 68, 30, 37, 38, ELECTROLYZER);
        registration.addGuiContainerHandler(ItemProcessorScreen.class, new IGuiContainerHandler<>() {
            @Override
            public Collection<IGuiClickableArea> getGuiClickableAreas(ItemProcessorScreen screen, double x, double y) {
                Machine machine = screen.getMenu().machine();
                return List.of(IGuiClickableArea.createBasic(machine.progressX(), machine.progressY(),
                        37, machine.progressHeight(), itemType(machine)));
            }
        });
    }

    private static RecipeType<ItemProcessorRecipe> itemType(Machine machine) {
        return RecipeType.create(NuclearCraft.MOD_ID, machine.id(), ItemProcessorRecipe.class);
    }

    private static Component energy(int ticks, int power) {
        return Component.translatable("gui.nuclearcraft.jei.energy", ticks, power);
    }

    private static void itemRecipe(IRecipeLayoutBuilder layout, ItemProcessorRecipe recipe) {
        for (int index = 0; index < recipe.inputs().size(); index++) {
            ItemProcessorRecipe.Input input = recipe.inputs().get(index);
            layout.addSlot(INPUT, 20 + index * 23, 12).setStandardSlotBackground().addItemStacks(
                    Arrays.stream(input.ingredient().getItems()).map(stack -> stack.copyWithCount(input.count())).toList());
        }
        var fluid = recipe.fluidInput();
        if (fluid != null) {
            fluidSlot(layout, INPUT, 43, fluid.alternatives());
        }
        for (int index = 0; index < recipe.machine().outputs(); index++) {
            layout.addSlot(OUTPUT, 99 + index * 23, 12).setOutputSlotBackground().addItemStack(recipe.result(index));
        }
    }

    private static void fluidSlot(IRecipeLayoutBuilder layout, RecipeIngredientRole role, int x, List<FluidStack> fluids) {
        fluidSlot(layout, role, x, 12, fluids);
    }

    private static void fluidSlot(IRecipeLayoutBuilder layout, RecipeIngredientRole role, int x, int y, List<FluidStack> fluids) {
        long capacity = fluids.stream().mapToInt(FluidStack::getAmount).max().orElse(1);
        layout.addSlot(role, x, y).setStandardSlotBackground()
                .setFluidRenderer(capacity, true, 16, 16).addIngredients(ForgeTypes.FLUID_STACK, fluids);
    }

    private static final class Category<R extends Recipe<?>> implements IRecipeCategory<R> {
        private final RecipeType<R> type;
        private final Block block;
        private final IDrawable icon;
        private final IDrawable arrow;
        private final BiConsumer<IRecipeLayoutBuilder, R> layout;
        private final Function<R, Component> stats;

        Category(RecipeType<R> type, Block block, IGuiHelper gui, BiConsumer<IRecipeLayoutBuilder, R> layout,
                 Function<R, Component> stats) {
            this.type = type;
            this.block = block;
            this.layout = layout;
            this.stats = stats;
            icon = gui.createDrawableItemLike(block);
            arrow = gui.getRecipeArrow();
        }

        @Override public RecipeType<R> getRecipeType() { return type; }
        @Override public Component getTitle() { return block.getName(); }
        @Override public IDrawable getIcon() { return icon; }
        @Override public int getWidth() { return 140; }
        @Override public int getHeight() { return type.equals(ELECTROLYZER) ? 58 : 48; }
        @Override public ResourceLocation getRegistryName(R recipe) { return recipe.getId(); }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, R recipe, IFocusGroup focuses) {
            layout.accept(builder, recipe);
        }

        @Override
        public void draw(R recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
            arrow.draw(graphics, 69, 12);
            graphics.drawString(Minecraft.getInstance().font, stats.apply(recipe), 4, getHeight() - 13, 0x404040, false);
        }
    }
}
