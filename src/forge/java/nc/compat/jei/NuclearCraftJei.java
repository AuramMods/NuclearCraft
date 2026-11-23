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
import nc.client.FluidProcessorScreen;
import nc.client.ItemProcessorScreen;
import nc.client.MelterScreen;
import nc.init.NCCollectors.Collector;
import nc.init.NCHeatExchangers;
import nc.init.NCFission;
import nc.init.NCGenerators;
import nc.init.NCGasCoolants;
import nc.init.NCItemProcessors.Machine;
import nc.init.NCPebbleFission;
import nc.init.NCRegistries;
import nc.init.NCTurbine;
import nc.recipe.CondenserRecipe;
import nc.recipe.DecayGeneratorRecipe;
import nc.recipe.ChemicalReactorRecipe;
import nc.recipe.ElectrolyzerRecipe;
import nc.recipe.HeatExchangerRecipe;
import nc.recipe.ItemProcessorRecipe;
import nc.recipe.MelterRecipe;
import nc.recipe.SaltMixerRecipe;
import nc.recipe.SolidFissionRecipe;
import nc.recipe.TurbineRecipe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.math.BigDecimal;
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
    private static final RecipeType<ChemicalReactorRecipe> CHEMICAL_REACTOR =
            RecipeType.create(NuclearCraft.MOD_ID, "chemical_reactor", ChemicalReactorRecipe.class);
    private static final RecipeType<SaltMixerRecipe> SALT_MIXER =
            RecipeType.create(NuclearCraft.MOD_ID, "salt_mixer", SaltMixerRecipe.class);
    private static final RecipeType<SolidFissionRecipe> FISSION =
            RecipeType.create("nuclearcraft", "solid_fission", SolidFissionRecipe.class);
    private static final RecipeType<SolidFissionRecipe> PEBBLE =
            RecipeType.create("nuclearcraft", "pebble_fission", SolidFissionRecipe.class);
    private static final RecipeType<HeatExchangerRecipe> HEAT_EXCHANGER =
            RecipeType.create(NuclearCraft.MOD_ID, "heat_exchanger", HeatExchangerRecipe.class);
    private static final RecipeType<CondenserRecipe> CONDENSER =
            RecipeType.create(NuclearCraft.MOD_ID, "condenser", CondenserRecipe.class);
    private static final RecipeType<TurbineRecipe> TURBINE =
            RecipeType.create("nuclearcraft", "turbine", TurbineRecipe.class);

    private static final RecipeType<DecayGeneratorRecipe> DECAY =
            RecipeType.create(NuclearCraft.MOD_ID, "decay_generator", DecayGeneratorRecipe.class);

    @Override
    public ResourceLocation getPluginUid() {
        return NuclearCraft.id("jei");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper gui = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new CollectorCategory(gui), new GasCoolerCategory(gui),
                new FissionMaterialCategory(FissionMaterialCategory.MODERATORS, NCFission.HEAVY_WATER_MODERATOR.get(), gui),
                new FissionMaterialCategory(FissionMaterialCategory.REFLECTORS, NCFission.BERYLLIUM_CARBON_REFLECTOR.get(), gui));
        for (Machine machine : Machine.values()) {
            registration.addRecipeCategories(new Category<>(itemType(machine), machine.block(), gui,
                    NuclearCraftJei::itemRecipe, recipe -> energy(recipe.ticks(), recipe.energyPerTick())));
        }
        registration.addRecipeCategories(
                new Category<>(DECAY, NCGenerators.DECAY_GENERATOR.get(), gui, (layout, recipe) -> {
                    layout.addSlot(INPUT, 20, 12).setStandardSlotBackground().addItemStacks(
                            Arrays.stream(recipe.input().getItems()).filter(recipe::accepts).toList());
                    layout.addSlot(OUTPUT, 104, 12).setOutputSlotBackground().addItemStack(new ItemStack(recipe.resultBlock()));
                }, recipe -> Component.translatable("gui.nuclearcraft.jei.decay_generator",
                        decimal(recipe.power()), decimal(recipe.lifetime()))),
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
                new Category<>(CHEMICAL_REACTOR, NCRegistries.CHEMICAL_REACTOR.get(), gui, (layout, recipe) -> {
                    fluidSlot(layout, INPUT, 18, recipe.inputs().get(0).alternatives());
                    fluidSlot(layout, INPUT, 40, recipe.inputs().get(1).alternatives());
                    List<FluidStack> results = recipe.results();
                    for (int index = 0; index < results.size(); index++) {
                        fluidSlot(layout, OUTPUT, 100 + index * 20, List.of(results.get(index)));
                    }
                }, recipe -> energy(recipe.ticks(), recipe.energyPerTick())),
                new Category<>(SALT_MIXER, NCRegistries.SALT_MIXER.get(), gui, (layout, recipe) -> {
                    fluidSlot(layout, INPUT, 18, recipe.inputs().get(0).alternatives());
                    fluidSlot(layout, INPUT, 40, recipe.inputs().get(1).alternatives());
                    fluidSlot(layout, OUTPUT, 104, List.of(recipe.product()));
                }, recipe -> energy(recipe.ticks(), recipe.energyPerTick())),
                new Category<>(FISSION, NCFission.CONTROLLER.get(), gui, NuclearCraftJei::fissionFuel,
                        recipe -> Component.translatable("gui.nuclearcraft.jei.fission", recipe.ticks(), recipe.heat())),
                new Category<>(PEBBLE, NCPebbleFission.CONTROLLER.get(), gui, NuclearCraftJei::fissionFuel,
                        recipe -> Component.translatable("gui.nuclearcraft.jei.fission", recipe.ticks(), recipe.heat())),
                new Category<>(HEAT_EXCHANGER, NCHeatExchangers.CONTROLLER.get(), gui, (layout, recipe) -> {
                    fluidSlot(layout, INPUT, 20, recipe.input().alternatives());
                    fluidSlot(layout, OUTPUT, 104, List.of(recipe.output()));
                }, recipe -> Component.translatable("gui.nuclearcraft.jei.heat_exchanger",
                        decimal(recipe.heatRequired()), recipe.inputTemperature(), recipe.outputTemperature())),
                new Category<>(CONDENSER, NCHeatExchangers.CONDENSER.get(), gui, (layout, recipe) -> {
                    fluidSlot(layout, INPUT, 20, recipe.input().alternatives());
                    fluidSlot(layout, OUTPUT, 104, List.of(recipe.output()));
                }, recipe -> Component.translatable("gui.nuclearcraft.jei.condenser",
                        decimal(recipe.cooling()), recipe.inputTemperature(), recipe.outputTemperature())),
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
        registration.addRecipes(FissionMaterialCategory.MODERATORS, FissionMaterialCategory.moderators());
        registration.addRecipes(FissionMaterialCategory.REFLECTORS, FissionMaterialCategory.reflectors());
        for (Machine machine : Machine.values()) {
            registration.addRecipes(itemType(machine), recipes.getAllRecipesFor(machine.recipes()));
        }
        registration.addRecipes(DECAY, recipes.getAllRecipesFor(NCGenerators.DECAY_RECIPES.get()));
        registration.addRecipes(MELTER, recipes.getAllRecipesFor(NCRegistries.MELTING.get()));
        registration.addRecipes(ELECTROLYZER, recipes.getAllRecipesFor(NCRegistries.ELECTROLYSIS.get()));
        registration.addRecipes(CHEMICAL_REACTOR, recipes.getAllRecipesFor(NCRegistries.CHEMICAL_REACTIONS.get()));
        registration.addRecipes(SALT_MIXER, recipes.getAllRecipesFor(NCRegistries.MIXING.get()));
        registration.addRecipes(FISSION, recipes.getAllRecipesFor(NCFission.FUEL_TYPE.get()));
        registration.addRecipes(PEBBLE, recipes.getAllRecipesFor(NCPebbleFission.FUEL_TYPE.get()));
        registration.addRecipes(GasCoolerCategory.TYPE, List.of(NCGasCoolants.values()));
        registration.addRecipes(CONDENSER, recipes.getAllRecipesFor(NCHeatExchangers.RECIPES.get()));
        registration.addRecipes(HEAT_EXCHANGER, recipes.getAllRecipesFor(NCHeatExchangers.HEAT_EXCHANGER_RECIPES.get()));
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
        registration.addRecipeCatalyst(NCGenerators.DECAY_GENERATOR.get(), DECAY);
        registration.addRecipeCatalyst(NCRegistries.MELTER.get(), MELTER);
        registration.addRecipeCatalyst(NCRegistries.ELECTROLYZER.get(), ELECTROLYZER);
        registration.addRecipeCatalyst(NCRegistries.SALT_MIXER.get(), SALT_MIXER);
        registration.addRecipeCatalyst(NCRegistries.CHEMICAL_REACTOR.get(), CHEMICAL_REACTOR);
        registration.addRecipeCatalyst(NCFission.CONTROLLER.get(), FISSION);
        registration.addRecipeCatalyst(NCPebbleFission.CONTROLLER.get(), PEBBLE);
        for (NCGasCoolants gas : NCGasCoolants.values()) {
            registration.addRecipeCatalyst(NCPebbleFission.cooler(gas), GasCoolerCategory.TYPE);
        }
        for (var moderator : FissionMaterialCategory.moderators()) {
            for (var item : moderator.items()) {
                registration.addRecipeCatalyst(item.getItem(), FissionMaterialCategory.MODERATORS);
            }
        }
        registration.addRecipeCatalyst(NCFission.BERYLLIUM_CARBON_REFLECTOR.get(), FissionMaterialCategory.REFLECTORS);
        registration.addRecipeCatalyst(NCFission.LEAD_STEEL_REFLECTOR.get(), FissionMaterialCategory.REFLECTORS);
        registration.addRecipeCatalyst(NCHeatExchangers.CONDENSER.get(), CONDENSER);
        registration.addRecipeCatalyst(NCHeatExchangers.CONTROLLER.get(), HEAT_EXCHANGER);
        registration.addRecipeCatalyst(NCTurbine.CONTROLLER.get(), TURBINE);
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addRecipeClickArea(MelterScreen.class, 74, 35, 37, 16, MELTER);
        registration.addGuiContainerHandler(FluidProcessorScreen.class, new IGuiContainerHandler<>() {
            @Override
            public Collection<IGuiClickableArea> getGuiClickableAreas(FluidProcessorScreen screen, double x, double y) {
                return switch (screen.getMenu().type()) {
                    case ELECTROLYZER -> List.of(IGuiClickableArea.createBasic(68, 30, 37, 38, ELECTROLYZER));
                    case SALT_MIXER -> List.of(IGuiClickableArea.createBasic(84, 34, 37, 18, SALT_MIXER));
                    case CHEMICAL_REACTOR -> List.of(IGuiClickableArea.createBasic(70, 34, 37, 18, CHEMICAL_REACTOR));
                };
            }
        });
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

    private static String decimal(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static Component energy(int ticks, int power) {
        return Component.translatable("gui.nuclearcraft.jei.energy", ticks, power);
    }

    private static void fissionFuel(IRecipeLayoutBuilder layout, SolidFissionRecipe recipe) {
        layout.addSlot(INPUT, 20, 12).setStandardSlotBackground().addIngredients(recipe.fuel());
        layout.addSlot(OUTPUT, 104, 12).setOutputSlotBackground().addItemStack(recipe.depleted());
    }

    private static void itemRecipe(IRecipeLayoutBuilder layout, ItemProcessorRecipe recipe) {
        Machine machine = recipe.machine();
        boolean extended = machine.menuExtension() > 0;
        for (int index = 0; index < recipe.inputs().size(); index++) {
            ItemProcessorRecipe.Input input = recipe.inputs().get(index);
            int x = extended ? 20 + machine.inputX(index) - machine.inputX(0) : 20 + index * 23;
            int y = extended ? machine.inputY(index) - 19 : 12;
            layout.addSlot(INPUT, x, y).setStandardSlotBackground().addItemStacks(
                    Arrays.stream(input.ingredient().getItems()).map(stack -> stack.copyWithCount(input.count())).toList());
        }
        var fluid = recipe.fluidInput();
        if (fluid != null) {
            fluidSlot(layout, INPUT, 43, fluid.alternatives());
        }
        var products = recipe.products();
        for (int index = 0; index < machine.outputs(); index++) {
            var product = products.get(index);
            var stack = product.stack();
            int x = machine == Machine.FUEL_REPROCESSOR ? machine.outputX(index) + 12 : 99 + index * 23;
            int y = extended ? machine.outputY(index) - 19 : 12;
            var slot = layout.addSlot(OUTPUT, x, y).setOutputSlotBackground();
            if (!stack.isEmpty()) {
                slot.addItemStack(stack);
            }
            if (product.chancePercent() < 100 && product.minCount() < stack.getCount()) {
                slot.addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable(
                        "gui.nuclearcraft.jei.product_chance", product.minCount(), stack.getCount(), product.chancePercent())));
            }
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
        @Override
        public int getWidth() {
            return switch (type.getUid().getPath()) {
                case "fuel_reprocessor" -> 178;
                case "rock_crusher" -> 166;
                default -> 140;
            };
        }

        @Override
        public int getHeight() {
            return switch (type.getUid().getPath()) {
                case "fuel_reprocessor", "assembler" -> 70;
                case "electrolyzer" -> 58;
                default -> 48;
            };
        }
        @Override public ResourceLocation getRegistryName(R recipe) { return recipe.getId(); }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, R recipe, IFocusGroup focuses) {
            layout.accept(builder, recipe);
        }

        @Override
        public void draw(R recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
            arrow.draw(graphics, 69, getHeight() == 70 ? 22 : 12);
            graphics.drawString(Minecraft.getInstance().font, stats.apply(recipe), 4, getHeight() - 13, 0x404040, false);
        }
    }
}
