package nc.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.forge.ForgeTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.handlers.IGuiClickableArea;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import nc.NuclearCraft;
import nc.client.FluidProcessorScreen;
import nc.client.FissionIrradiatorScreen;
import nc.client.SaltFissionPartScreen;
import nc.client.ItemProcessorScreen;
import nc.client.ScrubberScreen;
import nc.client.MelterScreen;
import nc.client.NuclearFurnaceScreen;
import nc.init.NCBatteries;
import nc.init.NCCollectors;
import nc.init.NCHeatExchangers;
import nc.init.NCFission;
import nc.init.NCSaltCoolants;
import nc.multiblock.fission.FissionMaterials;
import nc.init.NCSaltFission;
import nc.multiblock.fission.SaltHeaterBlock;
import nc.init.NCGenerators;
import nc.init.NCOres;
import nc.init.NCMachines;
import nc.init.NCItemProcessors;
import nc.init.NCItemProcessors.Machine;
import nc.init.NCItemProcessors.Rate;
import nc.init.NCProcessorAvailability;
import nc.init.NCPebbleFission;
import nc.init.NCRegistries;
import nc.init.NCRadiation;
import nc.init.NCTurbine;
import nc.multiblock.machine.MachinePartBlock.Part;
import nc.processor.NuclearFurnaceMenu;
import nc.processor.ProcessorLayout;
import nc.processor.ProcessorProfile;
import nc.processor.FluidProcessorBlockEntity;
import nc.radiation.ShieldingItem.Tier;
import nc.recipe.CentrifugeRecipe;
import nc.recipe.CondenserCoolants;
import nc.recipe.CondenserRecipe;
import nc.recipe.DecayGeneratorRecipe;
import nc.recipe.DecayPoolRecipe;
import nc.recipe.DistillerRecipe;
import nc.recipe.InfiltratorPressureRecipe;
import nc.recipe.InfiltratorRecipe;
import nc.recipe.LargeElectrolyzerRecipe;
import nc.recipe.ElectrolyzerElectrolyteRecipe;
import nc.recipe.MachineMaterialRecipe;
import nc.recipe.MachineMaterialRecipe.Kind;
import nc.recipe.ChemicalReactorRecipe;
import nc.recipe.ElectrolyzerRecipe;
import nc.recipe.EnricherRecipe;
import nc.recipe.FluidBatch;
import nc.recipe.FluidProduct;
import nc.recipe.HeatExchangerRecipe;
import nc.recipe.ItemProcessorRecipe;
import nc.recipe.MelterRecipe;
import nc.recipe.MelterMaterialRecipes;
import nc.recipe.SaltMixerRecipe;
import nc.recipe.ScrubberRecipe;
import nc.recipe.SaltFissionRecipe;
import nc.recipe.SolidFissionRecipe;
import nc.recipe.FissionIrradiatorRecipe;
import nc.recipe.FissionFuelStats;
import nc.recipe.FissionHeatRecipe;
import nc.recipe.FissionMaterialRecipe;
import nc.recipe.ProcessingRecipe;
import nc.radiation.RadiationDisplay;
import nc.recipe.SupercoolerRecipe;
import nc.recipe.TurbineRecipe;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Stream;

import static mezz.jei.api.recipe.RecipeIngredientRole.INPUT;
import static mezz.jei.api.recipe.RecipeIngredientRole.OUTPUT;
import static net.minecraft.world.item.crafting.RecipeType.SMELTING;

@JeiPlugin
public final class NuclearCraftJei implements IModPlugin {
    private static final RecipeType<MelterRecipe> MELTER = RecipeType.create("nuclearcraft", "melter", MelterRecipe.class);
    private static final RecipeType<ElectrolyzerRecipe> ELECTROLYZER =
            RecipeType.create("nuclearcraft", "electrolyzer", ElectrolyzerRecipe.class);
    private static final RecipeType<ChemicalReactorRecipe> CHEMICAL_REACTOR =
            RecipeType.create(NuclearCraft.MOD_ID, "chemical_reactor", ChemicalReactorRecipe.class);
    private static final RecipeType<ScrubberRecipe> SCRUBBER =
            RecipeType.create(NuclearCraft.MOD_ID, "radiation_scrubber", ScrubberRecipe.class);
    private static final RecipeType<EnricherRecipe> ENRICHER =
            RecipeType.create(NuclearCraft.MOD_ID, "enricher", EnricherRecipe.class);
    private static final RecipeType<CentrifugeRecipe> CENTRIFUGE =
            RecipeType.create(NuclearCraft.MOD_ID, "centrifuge", CentrifugeRecipe.class);
    private static final RecipeType<SupercoolerRecipe> SUPERCOOLER =
            RecipeType.create(NuclearCraft.MOD_ID, "supercooler", SupercoolerRecipe.class);
    private static final RecipeType<SaltMixerRecipe> SALT_MIXER =
            RecipeType.create(NuclearCraft.MOD_ID, "salt_mixer", SaltMixerRecipe.class);
    private static final Map<RecipeType<?>, Rate> STOCK_FLUID_CATEGORIES = Map.of(
            MELTER, Rate.MELTER,
            ELECTROLYZER, Rate.ELECTROLYZER,
            CHEMICAL_REACTOR, Rate.CHEMICAL_REACTOR,
            ENRICHER, Rate.ENRICHER,
            CENTRIFUGE, Rate.CENTRIFUGE,
            SUPERCOOLER, Rate.SUPERCOOLER,
            SALT_MIXER, Rate.SALT_MIXER);
    private static final RecipeType<FissionIrradiatorRecipe> IRRADIATION =
            RecipeType.create(NuclearCraft.MOD_ID, "fission_irradiator", FissionIrradiatorRecipe.class);
    private static final RecipeType<SolidFissionRecipe> FISSION =
            RecipeType.create("nuclearcraft", "solid_fission", SolidFissionRecipe.class);
    private static final RecipeType<SolidFissionRecipe> PEBBLE =
            RecipeType.create("nuclearcraft", "pebble_fission", SolidFissionRecipe.class);
    private static final RecipeType<SaltFissionRecipe> SALT_FISSION =
            RecipeType.create(NuclearCraft.MOD_ID, "salt_fission", SaltFissionRecipe.class);
    private static final RecipeType<FissionHeatRecipe> FISSION_HEATING =
            RecipeType.create(NuclearCraft.MOD_ID, "fission_heating", FissionHeatRecipe.class);
    private static final RecipeType<FissionHeatRecipe> FISSION_EMERGENCY_COOLING =
            RecipeType.create(NuclearCraft.MOD_ID, "fission_emergency_cooling", FissionHeatRecipe.class);
    private static final RecipeType<SaltHeaterBlock> SALT_HEATING =
            RecipeType.create(NuclearCraft.MOD_ID, "coolant_heater", SaltHeaterBlock.class);
    private static final RecipeType<HeatExchangerRecipe> HEAT_EXCHANGER =
            RecipeType.create(NuclearCraft.MOD_ID, "heat_exchanger", HeatExchangerRecipe.class);
    private static final RecipeType<CondenserRecipe> CONDENSER =
            RecipeType.create(NuclearCraft.MOD_ID, "condenser", CondenserRecipe.class);
    private static final RecipeType<TurbineRecipe> TURBINE =
            RecipeType.create("nuclearcraft", "turbine", TurbineRecipe.class);

    private static final RecipeType<DecayGeneratorRecipe> DECAY =
            RecipeType.create(NuclearCraft.MOD_ID, "decay_generator", DecayGeneratorRecipe.class);
    private static final RecipeType<DecayPoolRecipe> DECAY_POOL =
            RecipeType.create(NuclearCraft.MOD_ID, "multiblock_decay_pool", DecayPoolRecipe.class);
    private static final RecipeType<DecayPoolRecipe> DECAY_POOL_SOURCE =
            RecipeType.create(NuclearCraft.MOD_ID, "decay_pool_heat_source", DecayPoolRecipe.class);
    private static final RecipeType<DistillerRecipe> DISTILLER =
            RecipeType.create(NuclearCraft.MOD_ID, "multiblock_distiller", DistillerRecipe.class);
    private static final RecipeType<InfiltratorRecipe> INFILTRATOR =
            RecipeType.create(NuclearCraft.MOD_ID, "multiblock_infiltrator", InfiltratorRecipe.class);
    private static final RecipeType<InfiltratorPressureRecipe> PRESSURE =
            RecipeType.create(NuclearCraft.MOD_ID, "infiltrator_pressure_fluid", InfiltratorPressureRecipe.class);
    private static final RecipeType<LargeElectrolyzerRecipe> LARGE_ELECTROLYZER =
            RecipeType.create(NuclearCraft.MOD_ID, "multiblock_electrolyzer", LargeElectrolyzerRecipe.class);
    private static final RecipeType<ElectrolyzerElectrolyteRecipe> ELECTROLYTE =
            RecipeType.create(NuclearCraft.MOD_ID, "electrolyzer_electrolyte", ElectrolyzerElectrolyteRecipe.class);

    @Override
    public ResourceLocation getPluginUid() {
        return NuclearCraft.id("jei");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper gui = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new CollectorCategory(gui), new GasCoolerCategory(gui),
                new CondenserCoolantCategory(gui),
                new FissionMaterialCategory(FissionMaterialCategory.MODERATORS, NCFission.HEAVY_WATER_MODERATOR.get(), gui),
                new FissionMaterialCategory(FissionMaterialCategory.REFLECTORS, NCFission.BERYLLIUM_CARBON_REFLECTOR.get(), gui));
        for (ProcessorProfile machine : NCItemProcessors.profiles()) {
            if (machine.jeiEnabled()) {
                var presentation = machine.layout();
                var title = presentation != null && presentation.jei().title() != null
                        ? Component.translatable(presentation.jei().title()) : machine.block().getName();
                registration.addRecipeCategories(new Category<>(itemType(machine), machine.block(), gui,
                        NuclearCraftJei::itemRecipe, recipe -> energy(recipe.configuredTime(), recipe.configuredPower()),
                        title, presentation));
            }
        }
        for (Kind kind : Kind.values()) {
            Block catalyst = materialBlock(kind);
            registration.addRecipeCategories(new Category<>(materialType(kind), catalyst, gui,
                    (layout, material) -> layout.addSlot(INPUT, 24, 12).setStandardSlotBackground()
                            .addItemStacks(Arrays.stream(material.block().getItems()).filter(material::accepts).toList()),
                    material -> switch (kind) {
                        case DIAPHRAGM -> Component.translatable("gui.nuclearcraft.jei.diaphragm",
                                decimal(100 * material.efficiency()), decimal(material.contact()));
                        case SIEVE -> Component.translatable("gui.nuclearcraft.jei.sieve_efficiency",
                                decimal(100 * material.efficiency()));
                        default -> Component.translatable("gui.nuclearcraft.jei.electrode", decimal(100 * material.efficiency()));
                    }, kind == Kind.SIEVE ? Component.translatable("gui.nuclearcraft.jei.sieve_assembly") : catalyst.getName()));
        }
        Stream.of(
                new Category<>(DECAY, NCGenerators.DECAY_GENERATOR.get(), gui, (layout, recipe) -> {
                    layout.addSlot(INPUT, 20, 12).setStandardSlotBackground().addItemStacks(
                            Arrays.stream(recipe.input().getItems()).filter(recipe::accepts).toList());
                    layout.addSlot(OUTPUT, 104, 12).setOutputSlotBackground().addItemStack(new ItemStack(recipe.resultBlock()));
                }, recipe -> Component.translatable("gui.nuclearcraft.jei.decay_generator",
                        decimal(recipe.power()), decimal(recipe.lifetime()))),
                new Category<>(DECAY_POOL, NCMachines.block(Part.DECAY_POOL), gui, NuclearCraftJei::poolRecipe,
                        recipe -> Component.translatable("gui.nuclearcraft.jei.decay_pool", decimal(recipe.heat()))),
                new Category<>(DISTILLER, NCMachines.block(Part.DISTILLER), gui, NuclearCraftJei::distillerRecipe,
                        recipe -> Component.translatable("gui.nuclearcraft.jei.distiller",
                                decimal(NCMachines.distillerTime() * recipe.timeMultiplier() / recipe.batchFactor()),
                                decimal(NCMachines.distillerPower() * recipe.powerMultiplier()), recipe.sieveTrays())),
                new Category<>(INFILTRATOR, NCMachines.block(Part.INFILTRATOR), gui, NuclearCraftJei::infiltratorRecipe,
                        recipe -> Component.translatable("gui.nuclearcraft.jei.infiltrator",
                                decimal(NCMachines.infiltratorTime() * recipe.time()),
                                decimal(NCMachines.infiltratorPower() * recipe.power()),
                                decimal(recipe.heatingBonus() * 100))),
                new Category<>(PRESSURE, NCMachines.block(Part.PRESSURE_CHAMBER), gui,
                        (layout, recipe) -> fluidSlot(layout, INPUT, 42, recipe.fluid().alternatives()),
                        recipe -> Component.translatable("gui.nuclearcraft.jei.infiltrator_pressure", decimal(100 * recipe.efficiency()))),
                new Category<>(LARGE_ELECTROLYZER, NCMachines.block(Part.ELECTROLYZER), gui, NuclearCraftJei::largeElectrolyzerRecipe,
                        recipe -> Component.translatable("gui.nuclearcraft.jei.large_electrolyzer",
                                decimal(NCMachines.electrolyzerTime() * recipe.time()),
                                decimal(NCMachines.electrolyzerPower() * recipe.power()))),
                new Category<>(ELECTROLYTE, NCMachines.block(Part.RESERVOIR), gui,
                        (layout, recipe) -> fluidSlot(layout, INPUT, 42, recipe.fluid().alternatives()),
                        recipe -> Component.translatable("gui.nuclearcraft.jei.electrolyte",
                                decimal(100 * recipe.efficiency()), recipe.group().replace('_', ' ')),
                        Component.translatable("gui.nuclearcraft.jei.electrolytes")),
                new Category<>(DECAY_POOL_SOURCE, NCMachines.block(Part.CONTAINER), gui, NuclearCraftJei::poolRecipe,
                        recipe -> Component.translatable("gui.nuclearcraft.jei.decay_pool_source",
                                decimal(recipe.heat()), decimal(recipe.lifetime()))),
                new Category<>(MELTER, NCRegistries.MELTER.get(), gui, (layout, recipe) -> {
                    layout.addSlot(INPUT, 20, 12).setStandardSlotBackground().addIngredients(recipe.input());
                    fluidSlot(layout, OUTPUT, 104, List.of(recipe.product()));
                }, recipe -> energy(Rate.MELTER, recipe.ticks(), recipe.energyPerTick())),
                new Category<>(ELECTROLYZER, NCRegistries.ELECTROLYZER.get(), gui, (layout, recipe) -> {
                    int factor = recipe.batchFactor();
                    fluidSlot(layout, INPUT, 20, recipe.input().alternatives(factor));
                    List<FluidStack> outputs = recipe.results();
                    for (int index = 0; index < outputs.size(); index++) {
                        fluidSlot(layout, OUTPUT, 98 + (index % 2) * 20, 2 + (index / 2) * 20,
                                List.of(FluidBatch.divide(outputs.get(index), factor)));
                    }
                }, recipe -> energy(Rate.ELECTROLYZER, recipe.batchDuration(), recipe.energyPerTick())),
                new Category<>(CHEMICAL_REACTOR, NCRegistries.CHEMICAL_REACTOR.get(), gui, (layout, recipe) -> {
                    int factor = recipe.batchFactor();
                    fluidSlot(layout, INPUT, 18, recipe.inputs().get(0).alternatives(factor));
                    fluidSlot(layout, INPUT, 40, recipe.inputs().get(1).alternatives(factor));
                    List<FluidStack> results = recipe.results();
                    for (int index = 0; index < results.size(); index++) {
                        fluidSlot(layout, OUTPUT, 100 + index * 20, List.of(FluidBatch.divide(results.get(index), factor)));
                    }
                }, recipe -> energy(Rate.CHEMICAL_REACTOR, recipe.batchDuration(), recipe.energyPerTick())),
                new Category<>(SCRUBBER, NCRadiation.SCRUBBER.get(), gui, NuclearCraftJei::scrubberRecipe,
                        recipe -> Component.translatable("gui.nuclearcraft.jei.scrubber",
                                recipe.ticks(), recipe.power(), decimal(recipe.efficiency()))),
                new Category<>(ENRICHER, NCRegistries.ENRICHER.get(), gui, (layout, recipe) -> {
                    ItemProcessorRecipe.Input item = recipe.itemInput();
                    layout.addSlot(INPUT, 18, 12).setStandardSlotBackground().addItemStacks(
                            Arrays.stream(item.ingredient().getItems()).map(stack -> stack.copyWithCount(item.count())).toList());
                    fluidSlot(layout, INPUT, 40, recipe.fluidInput().alternatives());
                    fluidSlot(layout, OUTPUT, 104, List.of(recipe.product()));
                }, recipe -> energy(Rate.ENRICHER, recipe.duration(), recipe.energyPerTick())),
                new Category<>(SALT_MIXER, NCRegistries.SALT_MIXER.get(), gui, (layout, recipe) -> {
                    int factor = recipe.batchFactor();
                    fluidSlot(layout, INPUT, 18, recipe.inputs().get(0).alternatives(factor));
                    fluidSlot(layout, INPUT, 40, recipe.inputs().get(1).alternatives(factor));
                    fluidSlot(layout, OUTPUT, 104, List.of(FluidBatch.divide(recipe.product(), factor)));
                }, recipe -> energy(Rate.SALT_MIXER, recipe.batchDuration(), recipe.energyPerTick())),
                new Category<>(SUPERCOOLER, NCRegistries.SUPERCOOLER.get(), gui, (layout, recipe) -> {
                    int factor = recipe.batchFactor();
                    fluidSlot(layout, INPUT, 20, recipe.input().alternatives(factor));
                    fluidSlot(layout, OUTPUT, 104, List.of(FluidBatch.divide(recipe.product(), factor)));
                }, recipe -> energy(Rate.SUPERCOOLER, recipe.batchDuration(), recipe.energyPerTick())),
                new Category<>(CENTRIFUGE, NCRegistries.CENTRIFUGE.get(), gui, NuclearCraftJei::centrifugeRecipe,
                        recipe -> energy(Rate.CENTRIFUGE, recipe.batchDuration(), recipe.energyPerTick())),
                new Category<>(IRRADIATION, NCFission.IRRADIATOR.get(), gui, (layout, recipe) -> {
                    layout.addSlot(INPUT, 30, 12).setStandardSlotBackground().addItemStacks(
                            Arrays.stream(recipe.input().getItems())
                                    .filter(recipe::accepts).map(stack -> stack.copyWithCount(recipe.count())).toList());
                    layout.addSlot(OUTPUT, 108, 12).setStandardSlotBackground().addItemStack(recipe.result());
                }, recipe -> Component.translatable("gui.nuclearcraft.jei.irradiation", recipe.fluxRequired())),
                new Category<>(FISSION, NCFission.CONTROLLER.get(), gui, NuclearCraftJei::fissionFuel,
                        recipe -> Component.translatable("gui.nuclearcraft.jei.fission", (int) recipe.runtimeTicks(), recipe.runtimeHeat())),
                new Category<>(PEBBLE, NCPebbleFission.CONTROLLER.get(), gui, NuclearCraftJei::fissionFuel,
                        recipe -> Component.translatable("gui.nuclearcraft.jei.fission", (int) recipe.runtimeTicks(), recipe.runtimeHeat())),
                new Category<>(SALT_FISSION, NCSaltFission.CONTROLLER.get(), gui, (layout, recipe) -> {
                    fluidSlot(layout, INPUT, 20, recipe.fuel().alternatives());
                    fluidSlot(layout, OUTPUT, 104, List.of(recipe.depleted()));
                }, recipe -> Component.translatable("gui.nuclearcraft.jei.fission",
                        decimal(recipe.runtimeTicks()), recipe.runtimeHeat())),
                new Category<>(FISSION_HEATING, NCFission.VENT.get(), gui, NuclearCraftJei::fissionHeat,
                        NuclearCraftJei::fissionHeatCost, Component.translatable("gui.nuclearcraft.jei.fission_heating")),
                new Category<>(FISSION_EMERGENCY_COOLING, NCPebbleFission.CONTROLLER.get(), gui,
                        NuclearCraftJei::fissionHeat, NuclearCraftJei::fissionHeatCost,
                        Component.translatable("gui.nuclearcraft.jei.fission_emergency_cooling")),
                new Category<>(SALT_HEATING, NCSaltFission.heater(NCSaltCoolants.NAK), gui, (layout, heater) -> {
                    var coolant = heater.coolant();
                    layout.addSlot(INPUT, 20, 12).setStandardSlotBackground().addItemStack(new ItemStack(heater));
                    fluidSlot(layout, INPUT, 43, coolant.inputIngredient().alternatives());
                    fluidSlot(layout, OUTPUT, 104, List.of(new FluidStack(coolant.hot(), coolant.outputAmount())));
                }, heater -> Component.translatable("gui.nuclearcraft.jei.coolant_heater", heater.coolant().cooling())),
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
                    fluidSlot(layout, INPUT, 20, recipe.input().alternatives());
                    fluidSlot(layout, OUTPUT, 104, List.of(recipe.output()));
                }, recipe -> Component.translatable("gui.nuclearcraft.jei.turbine", recipe.powerPerMb(), recipe.expansion())))
                .filter(category -> stockCategoryEnabled(category.getRecipeType()))
                .forEach(registration::addRecipeCategories);
    }

    private static boolean stockCategoryEnabled(RecipeType<?> type) {
        Rate rate = STOCK_FLUID_CATEGORIES.get(type);
        return rate == null || rate.enabled();
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return;
        }
        RecipeManager recipes = connection.getRecipeManager();
        registration.addIngredientInfo(Arrays.stream(Tier.values()).map(NCRadiation::shielding)
                        .map(ItemStack::new).toList(),
                VanillaTypes.ITEM_STACK, Component.translatable("gui.nuclearcraft.jei.armor_shielding"));
        registration.addRecipes(CollectorCategory.TYPE, NCCollectors.enabledCollectors());
        var neutronMaterials = new FissionMaterials(recipes);
        registration.addRecipes(FissionMaterialCategory.MODERATORS,
                FissionMaterialCategory.materials(neutronMaterials, FissionMaterialRecipe.Kind.MODERATOR));
        registration.addRecipes(FissionMaterialCategory.REFLECTORS,
                FissionMaterialCategory.materials(neutronMaterials, FissionMaterialRecipe.Kind.REFLECTOR));
        for (ProcessorProfile machine : NCItemProcessors.profiles()) {
            if (!machine.jeiEnabled()) {
                continue;
            }
            var machineRecipes = new ArrayList<ItemProcessorRecipe>();
            recipes.getAllRecipesFor(machine.recipes()).stream()
                    .filter(recipe -> recipe.fluidInputs().stream().allMatch(fluid -> !fluid.alternatives().isEmpty()))
                    .forEach(machineRecipes::add);
            if (machine == Machine.ELECTRIC_FURNACE) {
                recipes.getAllRecipesFor(SMELTING).stream()
                        .map(recipe -> ItemProcessorRecipe.electricSmelting(recipe, connection.registryAccess()))
                        .forEach(machineRecipes::add);
            }
            registration.addRecipes(itemType(machine), machineRecipes);
        }
        registration.addRecipes(DECAY, recipes.getAllRecipesFor(NCGenerators.DECAY_RECIPES.get()));
        registration.addRecipes(DECAY_POOL, recipes.getAllRecipesFor(NCMachines.POOL_RECIPES.get()));
        registration.addRecipes(DISTILLER, recipes.getAllRecipesFor(NCMachines.DISTILLER_RECIPES.get()));
        registration.addRecipes(INFILTRATOR, recipes.getAllRecipesFor(NCMachines.INFILTRATOR_RECIPES.get()));
        registration.addRecipes(PRESSURE, recipes.getAllRecipesFor(NCMachines.PRESSURE_RECIPES.get()).stream()
                .filter(recipe -> !recipe.fluid().alternatives().isEmpty()).toList());
        registration.addRecipes(LARGE_ELECTROLYZER, recipes.getAllRecipesFor(NCMachines.ELECTROLYZER_RECIPES.get()));
        registration.addRecipes(ELECTROLYTE, recipes.getAllRecipesFor(NCMachines.ELECTROLYTES.get()).stream()
                .filter(recipe -> !recipe.fluid().alternatives().isEmpty()).toList());
        for (Kind kind : Kind.values()) {
            registration.addRecipes(materialType(kind), recipes.getAllRecipesFor(NCMachines.materialRecipes(kind)).stream()
                    .filter(recipe -> Arrays.stream(recipe.block().getItems()).anyMatch(recipe::accepts)).toList());
        }
        registration.addRecipes(DECAY_POOL_SOURCE, recipes.getAllRecipesFor(NCMachines.HEAT_SOURCES.get()));
        var melterRecipes = new ArrayList<MelterRecipe>();
        recipes.getAllRecipesFor(NCRegistries.MELTING.get()).stream().filter(recipe -> !recipe.input().isEmpty())
                .forEach(melterRecipes::add);
        melterRecipes.addAll(MelterMaterialRecipes.fromSyncedTags(recipes));
        addStockRecipes(registration, MELTER, melterRecipes);
        addStockRecipes(registration, ELECTROLYZER, recipes.getAllRecipesFor(NCRegistries.ELECTROLYSIS.get()));
        addStockRecipes(registration, CHEMICAL_REACTOR, recipes.getAllRecipesFor(NCRegistries.CHEMICAL_REACTIONS.get()));
        registration.addRecipes(SCRUBBER, recipes.getAllRecipesFor(NCRadiation.SCRUBBER_RECIPES.get()));
        addStockRecipes(registration, ENRICHER, recipes.getAllRecipesFor(NCRegistries.ENRICHING.get()));
        addStockRecipes(registration, SUPERCOOLER, recipes.getAllRecipesFor(NCRegistries.SUPERCOOLING.get()));
        addStockRecipes(registration, CENTRIFUGE, recipes.getAllRecipesFor(NCRegistries.CENTRIFUGING.get()));
        addStockRecipes(registration, SALT_MIXER, recipes.getAllRecipesFor(NCRegistries.MIXING.get()));
        registration.addRecipes(IRRADIATION, recipes.getAllRecipesFor(NCFission.IRRADIATION.get()));
        registration.addRecipes(FISSION, recipes.getAllRecipesFor(NCFission.FUEL_TYPE.get()).stream()
                .filter(recipe -> !ForgeHooks.hasNoElements(recipe.fuel())).toList());
        registration.addRecipes(PEBBLE, recipes.getAllRecipesFor(NCPebbleFission.FUEL_TYPE.get()));
        registration.addRecipes(SALT_FISSION, recipes.getAllRecipesFor(NCSaltFission.FUEL_TYPE.get()));
        registration.addRecipes(FISSION_HEATING, recipes.getAllRecipesFor(NCFission.HEATING_TYPE.get()));
        registration.addRecipes(FISSION_EMERGENCY_COOLING, recipes.getAllRecipesFor(NCFission.EMERGENCY_COOLING_TYPE.get()));
        registration.addRecipes(SALT_HEATING, NCSaltFission.heaters().stream()
                .filter(heater -> heater.coolant().cold() != Fluids.EMPTY && heater.coolant().hot() != Fluids.EMPTY).toList());
        registration.addRecipes(GasCoolerCategory.TYPE, NCPebbleFission.coolers().stream()
                .filter(cooler -> cooler.gas().cold() != Fluids.EMPTY && cooler.gas().hot() != Fluids.EMPTY).toList());
        registration.addRecipes(CONDENSER, recipes.getAllRecipesFor(NCHeatExchangers.RECIPES.get()));
        registration.addRecipes(CondenserCoolantCategory.TYPE, new CondenserCoolants(recipes).entries());
        registration.addRecipes(HEAT_EXCHANGER, recipes.getAllRecipesFor(NCHeatExchangers.HEAT_EXCHANGER_RECIPES.get()));
        registration.addRecipes(TURBINE, recipes.getAllRecipesFor(NCTurbine.RECIPES.get()));
    }

    private static <R> void addStockRecipes(IRecipeRegistration registration, RecipeType<R> type, List<R> recipes) {
        if (stockCategoryEnabled(type)) {
            registration.addRecipes(type, recipes);
        }
    }

    private static void addStockCatalyst(IRecipeCatalystRegistration registration, Block block, RecipeType<?> type) {
        if (stockCategoryEnabled(type)) {
            registration.addRecipeCatalyst(block, type);
        }
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        var disabled = new ArrayList<ItemStack>();
        if (!NCProcessorAvailability.nuclearFurnaceEnabled()) {
            disabled.add(new ItemStack(NCRegistries.NUCLEAR_FURNACE.get()));
        }
        if (!Rate.MELTER.enabled()) {
            disabled.add(new ItemStack(NCRegistries.MELTER.get()));
        }
        for (Machine machine : Machine.values()) {
            if (!machine.rate().enabled()) {
                disabled.add(new ItemStack(machine.block()));
            }
        }
        for (var machine : FluidProcessorBlockEntity.Machine.values()) {
            if (!machine.rate().enabled()) {
                disabled.add(new ItemStack(machine.block()));
            }
        }
        for (var tier : NCBatteries.Tier.values()) {
            if (!tier.enabled()) {
                disabled.add(new ItemStack(tier.block()));
            }
        }
        for (var ore : NCOres.Ore.values()) {
            if (NCOres.hiddenInJei(ore)) {
                String name = ore.getSerializedName();
                for (String suffix : List.of("_ore", "_block", "_ingot", "_dust")) {
                    var item = BuiltInRegistries.ITEM.get(NuclearCraft.id(name + suffix));
                    disabled.add(item.getDefaultInstance());
                }
            }
        }
        if (!disabled.isEmpty()) {
            runtime.getIngredientManager().removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, disabled);
        }
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        for (var collector : NCCollectors.enabledCollectors()) {
            registration.addRecipeCatalyst(collector.block(), CollectorCategory.TYPE);
        }
        for (ProcessorProfile machine : NCItemProcessors.profiles()) {
            if (machine.jeiEnabled()) {
                registration.addRecipeCatalyst(machine.block(), itemType(machine));
            }
        }
        registration.addRecipeCatalyst(NCGenerators.DECAY_GENERATOR.get(), DECAY);
        registration.addRecipeCatalyst(NCMachines.block(Part.DECAY_POOL), DECAY_POOL, DECAY_POOL_SOURCE);
        registration.addRecipeCatalyst(NCMachines.block(Part.DISTILLER), DISTILLER, materialType(Kind.SIEVE));
        registration.addRecipeCatalyst(NCMachines.block(Part.INFILTRATOR), INFILTRATOR, PRESSURE);
        registration.addRecipeCatalyst(NCMachines.block(Part.PRESSURE_CHAMBER), PRESSURE);
        registration.addRecipeCatalyst(NCMachines.block(Part.ELECTROLYZER), LARGE_ELECTROLYZER, ELECTROLYTE,
                materialType(Kind.CATHODE), materialType(Kind.ANODE), materialType(Kind.DIAPHRAGM));
        for (Kind kind : Kind.values()) registration.addRecipeCatalyst(materialBlock(kind), materialType(kind));
        addStockCatalyst(registration, NCRegistries.MELTER.get(), MELTER);
        if (NCProcessorAvailability.nuclearFurnaceEnabled()) {
            registration.addRecipeCatalyst(NCRegistries.NUCLEAR_FURNACE.get(), RecipeTypes.SMELTING);
        }
        addStockCatalyst(registration, NCRegistries.ELECTROLYZER.get(), ELECTROLYZER);
        addStockCatalyst(registration, NCRegistries.SALT_MIXER.get(), SALT_MIXER);
        addStockCatalyst(registration, NCRegistries.CHEMICAL_REACTOR.get(), CHEMICAL_REACTOR);
        registration.addRecipeCatalyst(NCRadiation.SCRUBBER.get(), SCRUBBER);
        addStockCatalyst(registration, NCRegistries.ENRICHER.get(), ENRICHER);
        addStockCatalyst(registration, NCRegistries.SUPERCOOLER.get(), SUPERCOOLER);
        addStockCatalyst(registration, NCRegistries.CENTRIFUGE.get(), CENTRIFUGE);
        registration.addRecipeCatalyst(NCFission.IRRADIATOR.get(), IRRADIATION);
        registration.addRecipeCatalyst(NCFission.IRRADIATOR_PORT.get(), IRRADIATION);
        registration.addRecipeCatalyst(NCFission.CONTROLLER.get(), FISSION);
        registration.addRecipeCatalyst(NCPebbleFission.CONTROLLER.get(), PEBBLE);
        registration.addRecipeCatalyst(NCSaltFission.CONTROLLER.get(), SALT_FISSION);
        registration.addRecipeCatalyst(NCSaltFission.VESSEL.get(), SALT_FISSION);
        registration.addRecipeCatalyst(NCFission.VENT.get(), FISSION_HEATING, FISSION_EMERGENCY_COOLING);
        registration.addRecipeCatalyst(NCFission.CONTROLLER.get(), FISSION_HEATING);
        registration.addRecipeCatalyst(NCPebbleFission.CONTROLLER.get(), FISSION_EMERGENCY_COOLING);
        registration.addRecipeCatalyst(NCSaltFission.CONTROLLER.get(), FISSION_EMERGENCY_COOLING);
        for (var heater : NCSaltFission.heaters()) {
            registration.addRecipeCatalyst(heater, SALT_HEATING);
        }
        for (var cooler : NCPebbleFission.coolers()) {
            registration.addRecipeCatalyst(cooler, GasCoolerCategory.TYPE);
        }
        for (var controller : List.of(NCFission.CONTROLLER, NCPebbleFission.CONTROLLER, NCSaltFission.CONTROLLER)) {
            registration.addRecipeCatalyst(controller.get(), FissionMaterialCategory.MODERATORS, FissionMaterialCategory.REFLECTORS);
        }
        registration.addRecipeCatalyst(NCHeatExchangers.CONDENSER.get(), CONDENSER, CondenserCoolantCategory.TYPE);
        registration.addRecipeCatalyst(NCHeatExchangers.INLET.get(), CondenserCoolantCategory.TYPE);
        registration.addRecipeCatalyst(NCHeatExchangers.CONTROLLER.get(), HEAT_EXCHANGER);
        registration.addRecipeCatalyst(NCTurbine.CONTROLLER.get(), TURBINE);
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        if (NCProcessorAvailability.nuclearFurnaceEnabled()) {
            registration.addRecipeTransferHandler(NuclearFurnaceMenu.class, NCRegistries.NUCLEAR_FURNACE_MENU.get(),
                    RecipeTypes.SMELTING, 0, 1, 3, 36);
        }
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler(FissionIrradiatorScreen.class, new IGuiContainerHandler<>() {
            @Override
            public Collection<IGuiClickableArea> getGuiClickableAreas(FissionIrradiatorScreen screen, double x, double y) {
                return screen.getMenu().port() ? List.of() : List.of(IGuiClickableArea.createBasic(74, 35, 37, 16, IRRADIATION));
            }
        });
        registration.addGuiContainerHandler(SaltFissionPartScreen.class, new IGuiContainerHandler<>() {
            @Override
            public Collection<IGuiClickableArea> getGuiClickableAreas(SaltFissionPartScreen screen, double x, double y) {
                var kind = screen.getMenu().kind();
                return kind.port() ? List.of() : List.of(IGuiClickableArea.createBasic(74, 35, 37, 16,
                        kind.fuel() ? SALT_FISSION : SALT_HEATING));
            }
        });
        if (Rate.MELTER.enabled()) {
            registration.addRecipeClickArea(MelterScreen.class, 74, 35, 37, 16, MELTER);
        }
        if (NCProcessorAvailability.nuclearFurnaceEnabled()) {
            registration.addRecipeClickArea(NuclearFurnaceScreen.class, 78, 32, 28, 23, RecipeTypes.SMELTING);
        }
        registration.addRecipeClickArea(ScrubberScreen.class, 70, 35, 37, 16, SCRUBBER);
        registration.addGuiContainerHandler(FluidProcessorScreen.class, new IGuiContainerHandler<>() {
            @Override
            public Collection<IGuiClickableArea> getGuiClickableAreas(FluidProcessorScreen screen, double x, double y) {
                if (!screen.getMenu().type().rate().enabled()) {
                    return List.of();
                }
                return switch (screen.getMenu().type()) {
                    case ELECTROLYZER -> List.of(IGuiClickableArea.createBasic(68, 30, 37, 38, ELECTROLYZER));
                    case SALT_MIXER -> List.of(IGuiClickableArea.createBasic(84, 34, 37, 18, SALT_MIXER));
                    case CHEMICAL_REACTOR -> List.of(IGuiClickableArea.createBasic(70, 34, 37, 18, CHEMICAL_REACTOR));
                    case ENRICHER -> List.of(IGuiClickableArea.createBasic(84, 35, 37, 16, ENRICHER));
                    case SUPERCOOLER -> List.of(IGuiClickableArea.createBasic(74, 35, 37, 16, SUPERCOOLER));
                    case CENTRIFUGE -> List.of(IGuiClickableArea.createBasic(58, 30, 37, 38, CENTRIFUGE));
                };
            }
        });
        registration.addGuiContainerHandler(ItemProcessorScreen.class, new IGuiContainerHandler<>() {
            @Override
            public Collection<IGuiClickableArea> getGuiClickableAreas(ItemProcessorScreen screen, double x, double y) {
                ProcessorProfile machine = screen.getMenu().machine();
                var presentation = machine.layout();
                var area = presentation == null ? new ProcessorLayout.Rect(machine.progressX(), machine.progressY(), 37,
                        machine.progressHeight()) : presentation.jei().clickArea();
                return machine.jeiEnabled() && area.visible() ? List.of(IGuiClickableArea.createBasic(area.x(), area.y(),
                        area.width(), area.height(), itemType(machine))) : List.of();
            }
        });
    }

    private static RecipeType<ItemProcessorRecipe> itemType(ProcessorProfile machine) {
        var layout = machine.layout();
        ResourceLocation id = layout == null ? NuclearCraft.id(machine.id()) : layout.jei().id();
        return RecipeType.create(id.getNamespace(), id.getPath(), ItemProcessorRecipe.class);
    }

    private static String decimal(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static Component energy(Rate rate, double ticks, int power) {
        return energy(rate.time(ticks), rate.power(power));
    }

    private static Component energy(double ticks, double power) {
        String time = BigDecimal.valueOf(ticks).round(MathContext.DECIMAL32).stripTrailingZeros().toPlainString();
        return Component.translatable("gui.nuclearcraft.jei.energy", time, (int) Math.ceil(power));
    }

    private static void fissionHeat(IRecipeLayoutBuilder layout, FissionHeatRecipe recipe) {
        fluidSlot(layout, INPUT, 20, recipe.input().alternatives());
        fluidSlot(layout, OUTPUT, 104, List.of(recipe.output()));
    }

    private static Component fissionHeatCost(FissionHeatRecipe recipe) {
        return Component.translatable("gui.nuclearcraft.jei.fission_heat", decimal(recipe.heatPerMb()));
    }

    private static void fissionFuel(IRecipeLayoutBuilder layout, SolidFissionRecipe recipe) {
        layout.addSlot(INPUT, 20, 12).setStandardSlotBackground().addIngredients(recipe.fuel());
        layout.addSlot(OUTPUT, 104, 12).setOutputSlotBackground().addItemStack(recipe.depleted());
    }

    private static void centrifugeRecipe(IRecipeLayoutBuilder layout, CentrifugeRecipe recipe) {
        int factor = recipe.batchFactor();
        fluidSlot(layout, INPUT, 20, 12, recipe.input().alternatives(factor));
        for (int index = 0; index < recipe.products().size(); index++) {
            var product = recipe.products().get(index);
            FluidStack maximum = FluidBatch.divide(product.stack(), factor);
            if (!maximum.isEmpty()) {
                int x = 98 + (index % 3) * 20;
                int y = 2 + (index / 3) * 20;
                var slot = layout.addSlot(OUTPUT, x, y).setOutputSlotBackground()
                        .setFluidRenderer(maximum.getAmount(), true, 16, 16)
                        .addIngredient(ForgeTypes.FLUID_STACK, maximum);
                addFluidChance(slot, product, factor, maximum.getAmount());
            }
        }
    }

    private static void addFluidChance(IRecipeSlotBuilder slot, FluidProduct product, int factor, int maximum) {
        if (product.chancePercent() < 100) {
            slot.addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable(
                    "gui.nuclearcraft.jei.fluid_product_chance", product.chancePercent(), product.step() / factor,
                    product.minAmount() / factor, maximum)));
        }
    }

    private static void scrubberRecipe(IRecipeLayoutBuilder layout, ScrubberRecipe recipe) {
        var item = recipe.itemInput();
        if (item != null) {
            layout.addSlot(INPUT, 18, 12).setStandardSlotBackground().addItemStacks(
                    Arrays.stream(item.ingredient().getItems()).map(stack -> stack.copyWithCount(item.count())).toList());
        }
        if (recipe.fluidInput() != null) {
            fluidSlot(layout, INPUT, 40, recipe.fluidInput().alternatives());
        }
        ItemStack product = recipe.itemOutput();
        if (!product.isEmpty()) {
            layout.addSlot(OUTPUT, 104, 12).setOutputSlotBackground().addItemStack(product);
        }
        FluidStack fluid = recipe.fluidOutput();
        if (!fluid.isEmpty()) {
            fluidSlot(layout, OUTPUT, 128, List.of(fluid));
        }
    }

    private static void itemRecipe(IRecipeLayoutBuilder layout, ItemProcessorRecipe recipe) {
        ProcessorProfile machine = recipe.machine();
        var presentation = machine.layout();
        if (presentation != null) {
            scriptedItemRecipe(layout, recipe, presentation);
            return;
        }
        boolean extended = machine.menuExtension() > 0 && !machine.hasFluidOutput();
        for (int index = 0; index < recipe.inputs().size(); index++) {
            ItemProcessorRecipe.Input input = recipe.inputs().get(index);
            int x = extended ? 20 + machine.inputX(index) - machine.inputX(0) : 20 + index * 23;
            int y = extended ? machine.inputY(index) - 19 : 12;
            layout.addSlot(INPUT, x, y).setStandardSlotBackground().addItemStacks(
                    Arrays.stream(input.ingredient().getItems()).map(stack -> stack.copyWithCount(input.count())).toList());
        }
        var fluid = recipe.fluidInput();
        if (fluid != null) {
            fluidSlot(layout, INPUT, recipe.inputs().isEmpty() ? 20 : 43, fluid.alternatives());
        }
        if (machine.hasFluidOutput()) {
            fluidSlot(layout, OUTPUT, 132, List.of(recipe.fluidOutput()));
        }
        var products = recipe.products();
        for (int index = 0; index < machine.outputs(); index++) {
            var product = products.get(index);
            int x = machine == Machine.FUEL_REPROCESSOR ? machine.outputX(index) + 12 : 99 + index * 23;
            int y = extended ? machine.outputY(index) - 19 : 12;
            itemProduct(layout.addSlot(OUTPUT, x, y).setOutputSlotBackground(), product);
        }
    }

    private static void scriptedItemRecipe(IRecipeLayoutBuilder layout, ItemProcessorRecipe recipe, ProcessorLayout presentation) {
        var crop = presentation.jei().background();
        boolean drawSlots = presentation.jei().texture() == null;
        for (int index = 0; index < recipe.inputs().size(); index++) {
            var area = presentation.itemInputs().get(index);
            var input = recipe.inputs().get(index);
            var slot = layout.addSlot(INPUT, area.itemX() - crop.x(), area.itemY() - crop.y());
            if (drawSlots) {
                slot.setStandardSlotBackground();
            }
            slot.addItemStacks(Arrays.stream(input.ingredient().getItems()).map(stack -> stack.copyWithCount(input.count())).toList());
        }
        var products = recipe.products();
        for (int index = 0; index < products.size(); index++) {
            var area = presentation.itemOutputs().get(index);
            var slot = layout.addSlot(OUTPUT, area.itemX() - crop.x(), area.itemY() - crop.y());
            if (drawSlots) {
                slot.setOutputSlotBackground();
            }
            itemProduct(slot, products.get(index));
        }
        for (int index = 0; index < recipe.fluidInputs().size(); index++) {
            var area = presentation.fluidInputs().get(index);
            fluidSlot(layout, INPUT, area.x() - crop.x(), area.y() - crop.y(), area.width(), area.height(),
                    recipe.fluidInputs().get(index).alternatives(), drawSlots);
        }
        var outputs = recipe.fluidOutputs();
        for (int index = 0; index < outputs.size(); index++) {
            var area = presentation.fluidOutputs().get(index);
            fluidSlot(layout, OUTPUT, area.x() - crop.x(), area.y() - crop.y(), area.width(), area.height(),
                    List.of(outputs.get(index)), drawSlots);
        }
    }

    private static void itemProduct(IRecipeSlotBuilder slot, ItemProcessorRecipe.Product product) {
        var stack = product.stack();
        if (!stack.isEmpty()) {
            slot.addItemStack(stack);
        }
        if (product.chancePercent() < 100 && product.minCount() < stack.getCount()) {
            slot.addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable(
                    "gui.nuclearcraft.jei.product_chance", product.minCount(), stack.getCount(), product.chancePercent())));
        }
    }

    private static void fluidSlot(IRecipeLayoutBuilder layout, RecipeIngredientRole role, int x, List<FluidStack> fluids) {
        fluidSlot(layout, role, x, 12, fluids);
    }

    private static IRecipeSlotBuilder fluidSlot(IRecipeLayoutBuilder layout, RecipeIngredientRole role, int x, int y,
                                              List<FluidStack> fluids) {
        return fluidSlot(layout, role, x, y, 16, 16, fluids, true);
    }

    private static IRecipeSlotBuilder fluidSlot(IRecipeLayoutBuilder layout, RecipeIngredientRole role, int x, int y,
                                              int width, int height, List<FluidStack> fluids, boolean drawSlot) {
        long capacity = fluids.stream().mapToInt(FluidStack::getAmount).max().orElse(1);
        var slot = layout.addSlot(role, x, y);
        if (drawSlot) {
            slot.setStandardSlotBackground();
        }
        slot.setFluidRenderer(capacity, true, width, height).addIngredients(ForgeTypes.FLUID_STACK, fluids);
        return slot;
    }

    private static void poolRecipe(IRecipeLayoutBuilder layout, DecayPoolRecipe recipe) {
        if (recipe.itemCount() > 0) {
            layout.addSlot(INPUT, 20, 12).setStandardSlotBackground().addItemStacks(
                    Arrays.stream(recipe.itemInput().getItems()).map(stack -> stack.copyWithCount(recipe.itemCount())).toList());
        }
        if (recipe.fluidInput() != null) fluidSlot(layout, INPUT, recipe.itemCount() > 0 ? 42 : 20, recipe.fluidInput().alternatives());
        if (!recipe.itemResult().isEmpty()) {
            layout.addSlot(OUTPUT, 104, 12).setOutputSlotBackground().addItemStack(recipe.itemResult());
        }
        if (!recipe.fluidResult().isEmpty()) fluidSlot(layout, OUTPUT, recipe.itemResult().isEmpty() ? 104 : 126, List.of(recipe.fluidResult()));
    }

    private static void distillerRecipe(IRecipeLayoutBuilder layout, DistillerRecipe recipe) {
        int factor = recipe.batchFactor();
        for (int slot = 0; slot < DistillerRecipe.INPUT_COUNT; slot++) {
            fluidSlot(layout, INPUT, 20 + 20 * slot, 22,
                    slot < recipe.inputs().size() ? recipe.inputs().get(slot).alternatives(factor) : List.of());
        }
        for (int slot = 0; slot < DistillerRecipe.OUTPUT_COUNT; slot++) {
            var product = recipe.products().get(slot);
            var maximum = FluidBatch.divide(product.stack(), factor);
            var layoutSlot = fluidSlot(layout, OUTPUT, 99 + (slot % 4) * 18, 12 + (slot / 4) * 20,
                    maximum.isEmpty() ? List.of() : List.of(maximum));
            addFluidChance(layoutSlot, product, factor, maximum.getAmount());
        }
    }

    private static void infiltratorRecipe(IRecipeLayoutBuilder layout, InfiltratorRecipe recipe) {
        for (int slot = 0; slot < recipe.items().size(); slot++) {
            var input = recipe.items().get(slot);
            layout.addSlot(INPUT, 20 + slot * 20, 12).setStandardSlotBackground().addItemStacks(
                    Arrays.stream(input.ingredient().getItems()).map(stack -> stack.copyWithCount(input.count())).toList());
        }
        for (int slot = 0; slot < recipe.fluids().size(); slot++) {
            fluidSlot(layout, INPUT, 20 + slot * 20, 33, recipe.fluids().get(slot).alternatives());
        }
        itemProduct(layout.addSlot(OUTPUT, 112, 22).setOutputSlotBackground(), recipe.result());
    }

    private static RecipeType<MachineMaterialRecipe> materialType(Kind kind) {
        return RecipeType.create(NuclearCraft.MOD_ID, kind.id(), MachineMaterialRecipe.class);
    }

    private static Block materialBlock(Kind kind) {
        return NCMachines.block(switch (kind) {
            case CATHODE -> Part.CATHODE_TERMINAL;
            case ANODE -> Part.ANODE_TERMINAL;
            case DIAPHRAGM -> Part.STEEL_DIAPHRAGM;
            case SIEVE -> Part.SIEVE_TRAY;
        });
    }

    private static void largeElectrolyzerRecipe(IRecipeLayoutBuilder layout, LargeElectrolyzerRecipe recipe) {
        for (int slot = 0; slot < recipe.items().size(); slot++) {
            var item = recipe.items().get(slot);
            layout.addSlot(INPUT, 15 + slot * 20, 12).setStandardSlotBackground().addItemStacks(
                    Arrays.stream(item.ingredient().getItems()).map(stack -> stack.copyWithCount(item.count())).toList());
        }
        for (int slot = 0; slot < recipe.fluids().size(); slot++) {
            fluidSlot(layout, INPUT, 15 + slot * 20, 33, recipe.fluids().get(slot).alternatives());
        }
        for (int slot = 0; slot < LargeElectrolyzerRecipe.ITEM_OUTPUTS; slot++) {
            var item = recipe.itemProducts().get(slot);
            if (!item.isEmpty()) itemProduct(layout.addSlot(OUTPUT, 99 + slot * 18, 12).setOutputSlotBackground(), item);
        }
        for (int slot = 0; slot < LargeElectrolyzerRecipe.FLUID_OUTPUTS; slot++) {
            var fluid = recipe.fluidProducts().get(slot);
            if (fluid.isEmpty()) continue;
            var maximum = fluid.stack();
            addFluidChance(fluidSlot(layout, OUTPUT, 99 + slot * 18, 33, List.of(maximum)), fluid, 1, maximum.getAmount());
        }
    }

    private static final class Category<R> implements IRecipeCategory<R> {
        private final RecipeType<R> type;
        private final Component title;
        private final IDrawable icon;
        private final IDrawable arrow;
        private final BiConsumer<IRecipeLayoutBuilder, R> layout;
        private final Function<R, Component> stats;
        private final @Nullable ProcessorLayout processor;

        Category(RecipeType<R> type, Block block, IGuiHelper gui, BiConsumer<IRecipeLayoutBuilder, R> layout,
                 Function<R, Component> stats) {
            this(type, block, gui, layout, stats, block.getName());
        }

        Category(RecipeType<R> type, Block block, IGuiHelper gui, BiConsumer<IRecipeLayoutBuilder, R> layout,
                 Function<R, Component> stats, Component title) {
            this(type, block, gui, layout, stats, title, null);
        }

        Category(RecipeType<R> type, Block block, IGuiHelper gui, BiConsumer<IRecipeLayoutBuilder, R> layout,
                 Function<R, Component> stats, Component title, @Nullable ProcessorLayout processor) {
            this.type = type;
            this.title = title;
            this.layout = layout;
            this.stats = stats;
            this.processor = processor;
            icon = gui.createDrawableItemLike(block);
            arrow = gui.getRecipeArrow();
        }

        @Override public RecipeType<R> getRecipeType() { return type; }
        @Override public Component getTitle() { return title; }
        @Override public IDrawable getIcon() { return icon; }
        @Override
        public int getWidth() {
            if (processor != null) {
                return processor.jei().background().width();
            }
            return switch (type.getUid().getPath()) {
                case "fuel_reprocessor", "multiblock_distiller", "multiblock_infiltrator", "multiblock_electrolyzer", "electrolyzer_electrolyte" -> 178;
                case "rock_crusher", "extractor", "centrifuge", "radiation_scrubber" -> 166;
                default -> 140;
            };
        }

        @Override
        public int getHeight() {
            if (processor != null) {
                return processor.jei().background().height();
            }
            return switch (type.getUid().getPath()) {
                case "multiblock_electrolyzer" -> 82;
                case "fuel_reprocessor", "assembler", "multiblock_distiller", "multiblock_infiltrator" -> 70;
                case "electrolyzer", "centrifuge" -> 58;
                default -> 48;
            };
        }
        @Override
        public ResourceLocation getRegistryName(R recipe) {
            return recipe instanceof Recipe<?> registered ? registered.getId() : null;
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, R recipe, IFocusGroup focuses) {
            layout.accept(builder, recipe);
        }

        @Override
        public void getTooltip(ITooltipBuilder tooltip, R recipe, IRecipeSlotsView slots, double mouseX, double mouseY) {
            if (processor != null) {
                var crop = processor.jei().background();
                if (!processor.jei().tooltip().contains(mouseX + crop.x(), mouseY + crop.y())) {
                    return;
                }
                if (processor.jei().cropped()) {
                    tooltip.add(stats.apply(recipe));
                }
            } else {
                int arrowY = getHeight() >= 70 ? 22 : 12;
                if (mouseY < arrowY - 3 || mouseY >= arrowY + 18 || mouseX < 69 || mouseX >= 95) {
                    return;
                }
            }
            if (recipe instanceof ProcessingRecipe processing && processing.radiation() > 0D) {
                double rate = processing.radiation();
                tooltip.add(Component.translatable("gui.nuclearcraft.jei.process_radiation",
                        Component.literal(RadiationDisplay.formatRate(rate)).withStyle(RadiationDisplay.rateColor(rate))));
            }
            if (recipe instanceof FissionFuelStats fuel) {
                tooltip.add(Component.translatable("gui.nuclearcraft.jei.fission_criticality", fuel.criticality(), fuel.intrinsicFlux()));
                String decayPercent = BigDecimal.valueOf(fuel.decayFactor()).movePointRight(2).stripTrailingZeros().toPlainString();
                tooltip.add(Component.translatable("gui.nuclearcraft.jei.fission_decay", decayPercent));
                return;
            }
            if (!(recipe instanceof FissionIrradiatorRecipe irradiation)) {
                return;
            }
            Object maximum = irradiation.maxFlux() < 0
                    ? Component.translatable("gui.nuclearcraft.jei.irradiation_unlimited") : irradiation.maxFlux();
            tooltip.add(Component.translatable("gui.nuclearcraft.jei.irradiation_range", irradiation.minFlux(), maximum));
            tooltip.add(Component.translatable("gui.nuclearcraft.jei.irradiation_stats",
                    decimal(irradiation.heatPerFlux()), decimal(100D * irradiation.efficiency())));
        }

        @Override
        public void draw(R recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
            if (processor == null) {
                arrow.draw(graphics, 69, getHeight() >= 70 ? 22 : 12);
            } else if (recipe instanceof ItemProcessorRecipe item) {
                drawProcessor(graphics, item);
            }
            if (processor == null || !processor.jei().cropped()) {
                graphics.drawString(Minecraft.getInstance().font, stats.apply(recipe), 4, getHeight() - 13, 0x404040, false);
            }
            if (recipe instanceof LargeElectrolyzerRecipe electrolyzer) {
                graphics.drawString(Minecraft.getInstance().font,
                        Component.translatable("gui.nuclearcraft.jei.electrolyte_group", electrolyzer.electrolyte().replace('_', ' ')),
                        4, getHeight() - 24, 0x404040, false);
            }
        }

        private void drawProcessor(GuiGraphics graphics, ItemProcessorRecipe recipe) {
            var settings = processor.jei();
            var crop = settings.background();
            var texture = settings.texture();
            var textureSize = settings.textureSize();
            if (texture != null) {
                graphics.blit(texture, 0, 0, crop.x(), crop.y(), crop.width(), crop.height(), textureSize.x(), textureSize.y());
            }
            var progress = processor.progress();
            var area = progress.area();
            if (!area.visible()) {
                return;
            }
            int x = area.x() - crop.x();
            int y = area.y() - crop.y();
            if (texture != null && progress.sprite().visible()) {
                double duration = Math.max(1D, recipe.configuredTime());
                int width = (int) ((Util.getMillis() / 50D % duration) * area.width() / duration);
                graphics.blit(texture, x, y, progress.sprite().x(), progress.sprite().y(), width, area.height(),
                        textureSize.x(), textureSize.y());
            } else {
                arrow.draw(graphics, x, y);
            }
        }
    }
}
