package nc.gametest;

import nc.NuclearCraft;
import nc.energy.BatteryBlockEntity;
import nc.generator.PassiveGeneratorBlockEntity;
import nc.init.NCBatteries.Tier;
import nc.init.NCGenerators.PassiveGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PassiveGeneratorGameTests {
    private static final BlockPos WEST = new BlockPos(1, 1, 2);
    private static final BlockPos CENTER = WEST.east();
    private static final BlockPos EAST = CENTER.east();

    @GameTest(template = "empty", timeoutTicks = 70)
    public static void rtgsCombineMixedIsotopesAndPreserveChargeAcrossASplit(GameTestHelper helper) {
        helper.setBlock(WEST, PassiveGenerator.RTG_URANIUM.block());
        var first = generator(helper, WEST);
        var oldOutlet = first.getCapability(ForgeCapabilities.ENERGY, Direction.EAST);
        var oldHandler = oldOutlet.orElseThrow(AssertionError::new);
        helper.setBlock(CENTER, PassiveGenerator.RTG_PLUTONIUM.block());
        helper.setBlock(EAST, PassiveGenerator.RTG_AMERICIUM.block());
        helper.setBlock(EAST.east(), PassiveGenerator.RTG_CALIFORNIUM.block());
        helper.assertTrue(!oldOutlet.isPresent() && !oldHandler.canExtract()
                        && !first.getCapability(ForgeCapabilities.ENERGY, Direction.EAST).isPresent(),
                "Joining an RTG invalidates the former outside face");
        var output = energy(first, Direction.UP);
        helper.assertTrue(!output.canReceive() && output.receiveEnergy(100, false) == 0
                        && output.getMaxEnergyStored() == 1_004,
                "The shared buffer is four times the sum of the legacy outputs");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(first.stored() == 1_004, "Wait for the RTG buffer to fill"))
                .thenExecute(() -> {
                    helper.assertTrue(output.extractEnergy(2_000, true) == 1_004 && first.stored() == 1_004,
                            "Inspecting the buffer does not extract its charge");
                    var western = first.saveWithoutMetadata();
                    var eastern = generator(helper, EAST).saveWithoutMetadata();
                    var fourth = generator(helper, EAST.east()).saveWithoutMetadata();
                    helper.assertTrue(western.getLong("energy") == 4 && eastern.getLong("energy") == 40
                                    && fourth.getLong("energy") == 800,
                            "Each generator saves its proportional share");
                    helper.setBlock(CENTER, Blocks.AIR);
                    helper.assertTrue(!output.canExtract() && first.capacity() == 16 && first.stored() == 4
                                    && generator(helper, EAST).stored() == 840,
                            "Splitting preserves the surviving shares and invalidates the old exterior handler");
                    helper.setBlock(EAST, Blocks.AIR);
                    helper.setBlock(EAST.east(), Blocks.AIR);
                    helper.setBlock(EAST, PassiveGenerator.RTG_AMERICIUM.block());
                    generator(helper, EAST).load(eastern);
                    helper.setBlock(EAST.east(), PassiveGenerator.RTG_CALIFORNIUM.block());
                    generator(helper, EAST.east()).load(fourth);
                    helper.assertTrue(generator(helper, EAST).stored() == 840, "The restored bank keeps its full charge");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 65)
    public static void anRtgBankGeneratesOncePerTickIntoABattery(GameTestHelper helper) {
        helper.setBlock(WEST, PassiveGenerator.RTG_AMERICIUM.block());
        helper.setBlock(CENTER, PassiveGenerator.RTG_PLUTONIUM.block());
        helper.setBlock(EAST, Tier.VOLTAIC_PILE_BASIC.block());
        var destination = (BatteryBlockEntity) helper.getBlockEntity(EAST);
        long[] start = new long[2];
        helper.startSequence()
                .thenIdle(8)
                .thenExecute(() -> {
                    start[0] = helper.getLevel().getGameTime();
                    start[1] = destination.stored();
                })
                .thenIdle(8)
                .thenExecute(() -> {
                    long ticks = helper.getLevel().getGameTime() - start[0];
                    helper.assertTrue(destination.stored() - start[1] == 50 * ticks,
                            "The two-block RTG bank delivers exactly 50 FE per tick");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", batch = "solar_output", timeoutTicks = 125)
    public static void solarPanelsRespectSunlightRainShadingAndSeparateBuffers(GameTestHelper helper) {
        var level = helper.getLevel();
        var daylightRule = level.getGameRules().getRule(GameRules.RULE_DAYLIGHT);
        var weatherRule = level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE);
        boolean daylightSetting = daylightRule.get();
        boolean weatherSetting = weatherRule.get();
        long savedTime = level.getDayTime();
        boolean wasRaining = level.isRaining();
        boolean wasThundering = level.isThundering();
        float initialRain = level.getRainLevel(1F);
        float initialThunder = level.getThunderLevel(1F);
        daylightRule.set(false, level.getServer());
        weatherRule.set(false, level.getServer());
        level.setDayTime(6_000);
        level.setWeatherParameters(6_000, 0, false, false);
        level.setRainLevel(0F);
        level.setThunderLevel(0F);

        // The GameTest world may contain terrain above its low-lying structures.
        int elevation = level.getMaxBuildHeight() - 8 - helper.absolutePos(WEST).getY();
        BlockPos basicPos = WEST.above(elevation);
        BlockPos elitePos = CENTER.above(elevation);
        BlockPos shadedPos = EAST.above(elevation);
        BlockPos batteryPos = elitePos.south();
        // At this elevation the fixture lies above the area GameTest resets on each run.
        for (BlockPos pos : new BlockPos[]{basicPos, elitePos, shadedPos, batteryPos}) {
            helper.setBlock(pos, Blocks.AIR);
        }
        helper.setBlock(basicPos, PassiveGenerator.SOLAR_BASIC.block());
        helper.setBlock(elitePos, PassiveGenerator.SOLAR_ELITE.block());
        helper.setBlock(shadedPos.above(2), Blocks.STONE);
        var first = generator(helper, basicPos);
        var elite = generator(helper, elitePos);
        BlockPos underRoof = helper.absolutePos(shadedPos.above());
        long[] rainSample = new long[2];
        helper.startSequence()
                .thenWaitUntil(() -> {
                    var sky = helper.absolutePos(basicPos.above());
                    helper.assertTrue(first.stored() == 20 && elite.stored() == 1_280,
                            "Solar buffers at noon: " + first.stored() + "/" + first.capacity() + ", "
                                    + elite.stored() + "/" + elite.capacity() + " at " + sky
                                    + ", sky visible " + level.canSeeSkyFromBelowWater(sky)
                                    + ", time " + level.getDayTime());
                })
                .thenWaitUntil(() -> helper.assertTrue(!level.canSeeSkyFromBelowWater(underRoof),
                        "Wait for the placed roof to block skylight"))
                .thenExecute(() -> helper.setBlock(shadedPos, PassiveGenerator.SOLAR_DU.block()))
                .thenIdle(6)
                .thenExecute(() -> {
                    long shadedEnergy = generator(helper, shadedPos).stored();
                    helper.assertTrue(shadedEnergy == 0,
                            "A panel under an opaque roof stored " + shadedEnergy + " FE");
                    helper.assertTrue(energy(first, Direction.EAST).getMaxEnergyStored() == 20,
                            "Adjacent solar panels maintain independent buffers");
                    level.setDayTime(18_000);
                    energy(first, Direction.UP).extractEnergy(Integer.MAX_VALUE, false);
                    energy(elite, Direction.UP).extractEnergy(Integer.MAX_VALUE, false);
                })
                .thenIdle(8)
                .thenExecute(() -> {
                    helper.assertTrue(first.stored() == 0 && elite.stored() == 0, "Midnight generates no power");
                    level.setDayTime(6_000);
                    level.setWeatherParameters(0, 6_000, true, false);
                    level.setRainLevel(1F);
                    helper.setBlock(batteryPos, Tier.VOLTAIC_PILE_BASIC.block());
                })
                .thenIdle(10)
                .thenExecute(() -> {
                    rainSample[0] = ((BatteryBlockEntity) helper.getBlockEntity(batteryPos)).stored();
                    rainSample[1] = level.getGameTime();
                })
                .thenIdle(6)
                .thenExecute(() -> {
                    long delta = ((BatteryBlockEntity) helper.getBlockEntity(batteryPos)).stored() - rainSample[0];
                    level.setDayTime(savedTime);
                    level.setWeatherParameters(0, 6_000, wasRaining, wasThundering);
                    level.setRainLevel(initialRain);
                    level.setThunderLevel(initialThunder);
                    daylightRule.set(daylightSetting, level.getServer());
                    weatherRule.set(weatherSetting, level.getServer());
                    helper.assertTrue(delta == 220 * (level.getGameTime() - rainSample[1]),
                            "Rain reduces elite solar generation from 320 to 220 FE per tick");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void passiveGeneratorsUseLegacyComponentsInTheirShapedRecipes(GameTestHelper helper) {
        var level = helper.getLevel();
        var recipes = level.getRecipeManager();
        for (String generator : new String[]{"rtg_uranium", "rtg_plutonium", "rtg_americium", "rtg_californium",
                "solar_panel_basic", "solar_panel_advanced", "solar_panel_du", "solar_panel_elite"}) {
            helper.assertTrue(recipes.byKey(NuclearCraft.id("crafting/" + generator)).isPresent(),
                    "Missing generator recipe " + generator);
        }
        var grid = MaterialGameTests.craftingGrid(3, 3);
        for (int slot : new int[]{0, 2, 6, 8}) {
            grid.setItem(slot, material("plate_advanced"));
        }
        for (int slot : new int[]{1, 3, 5, 7}) {
            grid.setItem(slot, material("graphite_ingot"));
        }
        grid.setItem(4, material("plutonium_238_oxide_ingot"));
        var result = recipes.getRecipeFor(RecipeType.CRAFTING, grid, level).orElseThrow(
                () -> new AssertionError("No recipe matches the legacy Plutonium RTG ingredients"));
        helper.assertTrue(result.assemble(grid, level.registryAccess()).is(PassiveGenerator.RTG_PLUTONIUM.block().asItem()),
                "The RTG accepts the original alternate isotope forms");

        grid.clearContent();
        grid.setItem(0, material("graphite_dust"));
        grid.setItem(1, material("quartz_dust"));
        grid.setItem(2, material("graphite_dust"));
        for (int slot : new int[]{3, 5, 7}) {
            grid.setItem(slot, new ItemStack(Items.HEAVY_WEIGHTED_PRESSURE_PLATE));
        }
        grid.setItem(4, new ItemStack(Items.LAPIS_LAZULI));
        grid.setItem(6, material("wire_copper"));
        grid.setItem(8, material("wire_copper"));
        result = recipes.getRecipeFor(RecipeType.CRAFTING, grid, level).orElseThrow(
                () -> new AssertionError("No recipe matches the legacy Basic Solar Panel ingredients"));
        helper.assertTrue(result.assemble(grid, level.registryAccess()).is(PassiveGenerator.SOLAR_BASIC.block().asItem()),
                "The basic panel uses the legacy graphite, quartz, and lapis pattern");
        helper.succeed();
    }

    private static PassiveGeneratorBlockEntity generator(GameTestHelper helper, BlockPos pos) {
        return (PassiveGeneratorBlockEntity) helper.getBlockEntity(pos);
    }

    private static IEnergyStorage energy(PassiveGeneratorBlockEntity generator, Direction face) {
        return generator.getCapability(ForgeCapabilities.ENERGY, face).orElseThrow(AssertionError::new);
    }

    private static ItemStack material(String name) {
        return new ItemStack(ForgeRegistries.ITEMS.getValue(NuclearCraft.id(name)));
    }
}
