package nc.radiation;

import nc.NuclearCraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RadiationSources {
    private static final Map<ResourceLocation, Double> ITEMS = new HashMap<>();
    private static final Map<ResourceLocation, Double> ITEM_TAGS = new HashMap<>();
    private static final Map<ResourceLocation, Double> FLUIDS = new HashMap<>();
    private static final Map<ResourceLocation, Double> FLUID_TAGS = new HashMap<>();
    private static final List<String> SALT_FORMS = List.of("", "_za", "_fluoride", "_fluoride_flibe");
    private static final List<String> ISOTOPE_FORMS = List.of("", "_carbide", "_oxide", "_nitride", "_za");
    private static final List<String> FUEL_FORMS = List.of("_oxide", "_triso", "_nitride", "_za");

    // Stock NuclearCraft 2o.9.7 rates are in Rad/t per item.
    private static final double FLUID_MULTIPLIER = 125D / 18D;
    private static final double TRITIUM = 0.0115D;
    private static final double CORIUM = 0.0000165D;
    private static final double THORIUM = 0.0000000000715D;
    private static final double URANIUM = 0.000000000385D;
    private static final double PLUTONIUM = 0.000042D;
    private static final double BISMUTH = 4.975E-20D;
    private static final double RADIUM = 0.000625;
    private static final double POLONIUM = 2.64D / 9D;
    private static final double PROTACTINIUM_233 = 13.54 / 9D;
    private static final double TBP = getFuelRadiation(THORIUM, 8.5D, PROTACTINIUM_233, 0.5D);
    private static final double STRONTIUM_90 = 0.0345D;
    private static final double RUTHENIUM_106 = 0.98D;
    private static final double CAESIUM_137 = 0.033D;
    private static final double PROMETHIUM_147 = 0.38D;
    private static final double EUROPIUM_155 = 0.21D;
    private static final double URANIUM_233 = 0.0000063D;
    private static final double URANIUM_235 = 0.00000000145D;
    private static final double URANIUM_238 = 0.000000000225D;
    private static final double NEPTUNIUM_236 = 0.0000065D;
    private static final double NEPTUNIUM_237 = 0.00000047D;
    private static final double PLUTONIUM_238 = 0.0115D;
    private static final double PLUTONIUM_239 = 0.0000415D;
    private static final double PLUTONIUM_241 = 0.0715D;
    private static final double PLUTONIUM_242 = 0.0000027D;
    private static final double AMERICIUM_241 = 0.00235D;
    private static final double AMERICIUM_242 = 0.0071D;
    private static final double AMERICIUM_243 = 0.00014D;
    private static final double CURIUM_243 = 0.0345D;
    private static final double CURIUM_245 = 0.00012D;
    private static final double CURIUM_246 = 0.000215D;
    private static final double CURIUM_247 = 0.0000000645D;
    private static final double BERKELIUM_247 = 0.000725D;
    private static final double BERKELIUM_248 = 0.00325D;
    private static final double CALIFORNIUM_249 = 0.00285D;
    private static final double CALIFORNIUM_250 = 0.0765D;
    private static final double CALIFORNIUM_251 = 0.00115D;
    private static final double CALIFORNIUM_252 = 0.38D;
    private static final double TBU = getFuelRadiation(THORIUM, 8.5D, URANIUM_233, 0.5D);
    private static final double LEU_233 = getFuelRadiation(URANIUM_238, 8, URANIUM_233, 1);
    private static final double HEU_233 = getFuelRadiation(URANIUM_238, 6, URANIUM_233, 3);
    private static final double LEU_235 = getFuelRadiation(URANIUM_238, 8, URANIUM_235, 1);
    private static final double HEU_235 = getFuelRadiation(URANIUM_238, 6, URANIUM_235, 3);
    private static final double LEN_236 = getFuelRadiation(NEPTUNIUM_237, 8, NEPTUNIUM_236, 1);
    private static final double HEN_236 = getFuelRadiation(NEPTUNIUM_237, 6, NEPTUNIUM_236, 3);
    private static final double LEP_239 = getFuelRadiation(PLUTONIUM_242, 8, PLUTONIUM_239, 1);
    private static final double HEP_239 = getFuelRadiation(PLUTONIUM_242, 6, PLUTONIUM_239, 3);
    private static final double LEP_241 = getFuelRadiation(PLUTONIUM_242, 8, PLUTONIUM_241, 1);
    private static final double HEP_241 = getFuelRadiation(PLUTONIUM_242, 6, PLUTONIUM_241, 3);
    private static final double MIX_239 = getFuelRadiation(URANIUM_238, 8, PLUTONIUM_239, 1);
    private static final double MIX_241 = getFuelRadiation(URANIUM_238, 8, PLUTONIUM_241, 1);
    private static final double LEA_242 = getFuelRadiation(AMERICIUM_243, 8, AMERICIUM_242, 1);
    private static final double HEA_242 = getFuelRadiation(AMERICIUM_243, 6, AMERICIUM_242, 3);
    private static final double LECM_243 = getFuelRadiation(CURIUM_246, 8, CURIUM_243, 1);
    private static final double HECM_243 = getFuelRadiation(CURIUM_246, 6, CURIUM_243, 3);
    private static final double LECM_245 = getFuelRadiation(CURIUM_246, 8, CURIUM_245, 1);
    private static final double HECM_245 = getFuelRadiation(CURIUM_246, 6, CURIUM_245, 3);
    private static final double LECM_247 = getFuelRadiation(CURIUM_246, 8, CURIUM_247, 1);
    private static final double HECM_247 = getFuelRadiation(CURIUM_246, 6, CURIUM_247, 3);
    private static final double LEB_248 = getFuelRadiation(BERKELIUM_247, 8, BERKELIUM_248, 1);
    private static final double HEB_248 = getFuelRadiation(BERKELIUM_247, 6, BERKELIUM_248, 3);
    private static final double LECF_249 = getFuelRadiation(CALIFORNIUM_252, 8, CALIFORNIUM_249, 1);
    private static final double HECF_249 = getFuelRadiation(CALIFORNIUM_252, 6, CALIFORNIUM_249, 3);
    private static final double LECF_251 = getFuelRadiation(CALIFORNIUM_252, 8, CALIFORNIUM_251, 1);
    private static final double HECF_251 = getFuelRadiation(CALIFORNIUM_252, 6, CALIFORNIUM_251, 3);
    private static final double DEPLETED_TBU = getDepletedFuelRadiation(URANIUM_233, 1, URANIUM_238, 5, NEPTUNIUM_236, 1, NEPTUNIUM_237, 1, STRONTIUM_90, CAESIUM_137, 0.5D, 0.5D);
    private static final double DEPLETED_LEU_233 = getDepletedFuelRadiation(URANIUM_238, 5, PLUTONIUM_241, 1, PLUTONIUM_242, 1, AMERICIUM_243, 1, STRONTIUM_90, CAESIUM_137, 0.5D, 0.5D);
    private static final double DEPLETED_HEU_233 = getDepletedFuelRadiation(URANIUM_235, 1, URANIUM_238, 2, PLUTONIUM_242, 3, AMERICIUM_243, 1, STRONTIUM_90, CAESIUM_137, 1.5D, 0.5D);
    private static final double DEPLETED_LEU_235 = getDepletedFuelRadiation(URANIUM_238, 4, PLUTONIUM_239, 1, PLUTONIUM_242, 2, AMERICIUM_243, 1, 0D, CAESIUM_137, 0.5D, 0.5D);
    private static final double DEPLETED_HEU_235 = getDepletedFuelRadiation(URANIUM_238, 3, NEPTUNIUM_236, 1, PLUTONIUM_242, 2, AMERICIUM_243, 1, 0D, CAESIUM_137, 1.5D, 0.5D);
    private static final double DEPLETED_LEN_236 = getDepletedFuelRadiation(URANIUM_238, 4, NEPTUNIUM_237, 1, PLUTONIUM_241, 1, PLUTONIUM_242, 2, 0D, CAESIUM_137, 0.5D, 0.5D);
    private static final double DEPLETED_HEN_236 = getDepletedFuelRadiation(URANIUM_238, 4, PLUTONIUM_238, 1, PLUTONIUM_241, 1, PLUTONIUM_242, 1, 0D, CAESIUM_137, 1.5D, 0.5D);
    private static final double DEPLETED_LEP_239 = getDepletedFuelRadiation(PLUTONIUM_242, 5, AMERICIUM_242, 1, AMERICIUM_243, 1, CURIUM_246, 1, STRONTIUM_90, PROMETHIUM_147, 0.5D, 0.5D);
    private static final double DEPLETED_HEP_239 = getDepletedFuelRadiation(PLUTONIUM_241, 1, AMERICIUM_242, 1, AMERICIUM_243, 4, CURIUM_243, 1, STRONTIUM_90, PROMETHIUM_147, 1.5D, 0.5D);
    private static final double DEPLETED_LEP_241 = getDepletedFuelRadiation(PLUTONIUM_242, 5, AMERICIUM_243, 1, CURIUM_246, 1, BERKELIUM_247, 1, STRONTIUM_90, PROMETHIUM_147, 0.5D, 0.5D);
    private static final double DEPLETED_HEP_241 = getDepletedFuelRadiation(PLUTONIUM_241, 1, AMERICIUM_242, 1, AMERICIUM_243, 3, CURIUM_246, 2, STRONTIUM_90, PROMETHIUM_147, 1.5D, 0.5D);
    private static final double DEPLETED_MIX_239 = getDepletedFuelRadiation(URANIUM_238, 4, PLUTONIUM_241, 1, PLUTONIUM_242, 2, AMERICIUM_243, 1, STRONTIUM_90, PROMETHIUM_147, 0.5D, 0.5D);
    private static final double DEPLETED_MIX_241 = getDepletedFuelRadiation(URANIUM_238, 3, PLUTONIUM_241, 1, PLUTONIUM_242, 3, AMERICIUM_243, 1, STRONTIUM_90, PROMETHIUM_147, 0.5D, 0.5D);
    private static final double DEPLETED_LEA_242 = getDepletedFuelRadiation(AMERICIUM_243, 3, CURIUM_245, 1, CURIUM_246, 3, BERKELIUM_248, 1, 0D, PROMETHIUM_147, 0.5D, 0.5D);
    private static final double DEPLETED_HEA_242 = getDepletedFuelRadiation(AMERICIUM_243, 3, CURIUM_243, 1, CURIUM_246, 2, BERKELIUM_247, 1, 0D, PROMETHIUM_147, 1.5D, 0.5D);
    private static final double DEPLETED_LECM_243 = getDepletedFuelRadiation(CURIUM_246, 4, CURIUM_247, 1, BERKELIUM_247, 2, BERKELIUM_248, 1, 0D, PROMETHIUM_147, 0.5D, 0.5D);
    private static final double DEPLETED_HECM_243 = getDepletedFuelRadiation(CURIUM_245, 1, CURIUM_246, 3, BERKELIUM_247, 2, BERKELIUM_248, 1, 0D, PROMETHIUM_147, 1.5D, 0.5D);
    private static final double DEPLETED_LECM_245 = getDepletedFuelRadiation(CURIUM_246, 4, CURIUM_247, 1, BERKELIUM_247, 2, CALIFORNIUM_249, 1, 0D, EUROPIUM_155, 0.5D, 0.6D);
    private static final double DEPLETED_HECM_245 = getDepletedFuelRadiation(CURIUM_246, 3, CURIUM_247, 1, BERKELIUM_247, 2, CALIFORNIUM_249, 1, 0D, EUROPIUM_155, 1.5D, 0.6D);
    private static final double DEPLETED_LECM_247 = getDepletedFuelRadiation(CURIUM_246, 5, BERKELIUM_247, 1, BERKELIUM_248, 1, CALIFORNIUM_249, 1, 0D, EUROPIUM_155, 0.5D, 0.6D);
    private static final double DEPLETED_HECM_247 = getDepletedFuelRadiation(BERKELIUM_247, 4, BERKELIUM_248, 1, CALIFORNIUM_249, 1, CALIFORNIUM_251, 1, 0D, EUROPIUM_155, 1.5D, 0.6D);
    private static final double DEPLETED_LEB_248 = getDepletedFuelRadiation(BERKELIUM_247, 5, BERKELIUM_248, 1, CALIFORNIUM_249, 1, CALIFORNIUM_251, 1, RUTHENIUM_106, PROMETHIUM_147, 0.5D, 0.6D);
    private static final double DEPLETED_HEB_248 = getDepletedFuelRadiation(BERKELIUM_248, 1, CALIFORNIUM_249, 1, CALIFORNIUM_251, 2, CALIFORNIUM_252, 3, RUTHENIUM_106, PROMETHIUM_147, 1.5D, 0.6D);
    private static final double DEPLETED_LECF_249 = getDepletedFuelRadiation(CALIFORNIUM_252, 2, CALIFORNIUM_252, 2, CALIFORNIUM_252, 2, CALIFORNIUM_252, 2, RUTHENIUM_106, PROMETHIUM_147, 0.5D, 0.6D);
    private static final double DEPLETED_HECF_249 = getDepletedFuelRadiation(CALIFORNIUM_250, 1, CALIFORNIUM_252, 2, CALIFORNIUM_252, 2, CALIFORNIUM_252, 2, RUTHENIUM_106, PROMETHIUM_147, 1.5D, 0.6D);
    private static final double DEPLETED_LECF_251 = getDepletedFuelRadiation(CALIFORNIUM_252, 2, CALIFORNIUM_252, 2, CALIFORNIUM_252, 2, CALIFORNIUM_252, 2, RUTHENIUM_106, EUROPIUM_155, 0.5D, 0.6D);
    private static final double DEPLETED_HECF_251 = getDepletedFuelRadiation(CALIFORNIUM_252, 2, CALIFORNIUM_252, 2, CALIFORNIUM_252, 2, CALIFORNIUM_252, 1, RUTHENIUM_106, EUROPIUM_155, 1.5D, 0.6D);

    static {
        material("bismuth", BISMUTH);
        material("radium", RADIUM);
        material("polonium", POLONIUM);
        material("tbp", TBP);
        material("protactinium_233", PROTACTINIUM_233);
        material("strontium_90", STRONTIUM_90);
        material("ruthenium_106", RUTHENIUM_106);
        material("cesium_137", CAESIUM_137);
        material("caesium_137", CAESIUM_137);
        material("promethium_147", PROMETHIUM_147);
        material("europium_155", EUROPIUM_155);
        material("thorium", THORIUM);
        material("uranium", URANIUM);
        material("yellorium", URANIUM);
        material("plutonium", PLUTONIUM);
        material("blutonium", PLUTONIUM);
        material("cyanite", URANIUM_238);
        material("monazite", 17 * THORIUM);
        fluid("plasma", 0.000000315D);
        fluid("tritium", TRITIUM);
        fluid("corium", CORIUM);
        fluid("irradiated_borax_solution", CAESIUM_137 / 4);
        item("plate_du", 4 * URANIUM_238);
        item("solar_panel_du", 16 * URANIUM_238);
        item("voltaic_pile_du", 16 * URANIUM_238);
        item("lithium_ion_battery_du", 16 * URANIUM_238);
        item("rad_shielding_heavy", 12 * URANIUM_238);
        item("rtg_uranium", URANIUM_238 / 4);
        item("rtg_plutonium", PLUTONIUM_238 / 4);
        item("rtg_americium", AMERICIUM_241 / 4);
        item("rtg_californium", CALIFORNIUM_250 / 4);
        item("fission_source_radium_beryllium", RADIUM / 4);
        item("fission_source_polonium_beryllium", POLONIUM / 4);
        item("fission_source_californium", CALIFORNIUM_252 / 4);
        item("tritium_lamp", TRITIUM / 256);
        item("solidified_corium", CORIUM * FLUID_MULTIPLIER);
        tag("dusts/irradiated_borax", CAESIUM_137 / 4);

        isotope("uranium_233", URANIUM_233);
        isotope("uranium_235", URANIUM_235);
        isotope("uranium_238", URANIUM_238);
        isotope("neptunium_236", NEPTUNIUM_236);
        isotope("neptunium_237", NEPTUNIUM_237);
        isotope("plutonium_238", PLUTONIUM_238);
        isotope("plutonium_239", PLUTONIUM_239);
        isotope("plutonium_241", PLUTONIUM_241);
        isotope("plutonium_242", PLUTONIUM_242);
        isotope("americium_241", AMERICIUM_241);
        isotope("americium_242", AMERICIUM_242);
        isotope("americium_243", AMERICIUM_243);
        isotope("curium_243", CURIUM_243);
        isotope("curium_245", CURIUM_245);
        isotope("curium_246", CURIUM_246);
        isotope("curium_247", CURIUM_247);
        isotope("berkelium_247", BERKELIUM_247);
        isotope("berkelium_248", BERKELIUM_248);
        isotope("californium_249", CALIFORNIUM_249);
        isotope("californium_250", CALIFORNIUM_250);
        isotope("californium_251", CALIFORNIUM_251);
        isotope("californium_252", CALIFORNIUM_252);

        fuel("tbu", TBU, DEPLETED_TBU);
        fuel("leu_233", LEU_233, DEPLETED_LEU_233);
        fuel("heu_233", HEU_233, DEPLETED_HEU_233);
        fuel("leu_235", LEU_235, DEPLETED_LEU_235);
        fuel("heu_235", HEU_235, DEPLETED_HEU_235);
        fuel("len_236", LEN_236, DEPLETED_LEN_236);
        fuel("hen_236", HEN_236, DEPLETED_HEN_236);
        fuel("lep_239", LEP_239, DEPLETED_LEP_239);
        fuel("hep_239", HEP_239, DEPLETED_HEP_239);
        fuel("lep_241", LEP_241, DEPLETED_LEP_241);
        fuel("hep_241", HEP_241, DEPLETED_HEP_241);
        fuel("mix_239", MIX_239, DEPLETED_MIX_239);
        fuel("mix_241", MIX_241, DEPLETED_MIX_241);
        fuel("lea_242", LEA_242, DEPLETED_LEA_242);
        fuel("hea_242", HEA_242, DEPLETED_HEA_242);
        fuel("lecm_243", LECM_243, DEPLETED_LECM_243);
        fuel("hecm_243", HECM_243, DEPLETED_HECM_243);
        fuel("lecm_245", LECM_245, DEPLETED_LECM_245);
        fuel("hecm_245", HECM_245, DEPLETED_HECM_245);
        fuel("lecm_247", LECM_247, DEPLETED_LECM_247);
        fuel("hecm_247", HECM_247, DEPLETED_HECM_247);
        fuel("leb_248", LEB_248, DEPLETED_LEB_248);
        fuel("heb_248", HEB_248, DEPLETED_HEB_248);
        fuel("lecf_249", LECF_249, DEPLETED_LECF_249);
        fuel("hecf_249", HECF_249, DEPLETED_HECF_249);
        fuel("lecf_251", LECF_251, DEPLETED_LECF_251);
        fuel("hecf_251", HECF_251, DEPLETED_HECF_251);
    }

    private RadiationSources() {}

    public static double itemRate(ItemStack stack) {
        return stackSourceRate(stack) * stack.getCount();
    }

    static double stackSourceRate(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        var settings = RadiationSourceSettings.active();
        var item = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (item == null || settings.excludedItems().contains(item)) {
            return 0;
        }
        if (stack.getItem() instanceof BlockItem blockItem) {
            var block = ForgeRegistries.BLOCKS.getKey(blockItem.getBlock());
            if (block != null) {
                if (settings.excludedBlocks().contains(block)) {
                    return 0;
                }
                Double configured = settings.blocks().get(block);
                if (configured != null) {
                    return configured;
                }
            }
        }
        Double configured = settings.items().get(item);
        if (configured != null) {
            return configured;
        }
        if (!settings.itemTags().isEmpty()) {
            double override = stack.getTags().filter(tag -> !settings.excludedTags().contains(tag.location()))
                    .mapToDouble(tag -> settings.itemTags().getOrDefault(tag.location(), -1D)).max().orElse(-1);
            if (override >= 0) {
                return override;
            }
        }
        Double direct = ITEMS.get(item);
        if (direct != null) {
            return direct;
        }
        return stack.getTags()
                .filter(tag -> !settings.excludedTags().contains(tag.location()))
                .mapToDouble(tag -> ITEM_TAGS.getOrDefault(tag.location(), 0D))
                .max().orElse(0);
    }

    static double blockRate(BlockState state) {
        var settings = RadiationSourceSettings.active();
        var block = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        var item = ForgeRegistries.ITEMS.getKey(state.getBlock().asItem());
        if (block == null || settings.excludedBlocks().contains(block)
                || item != null && settings.excludedItems().contains(item)) {
            return 0;
        }
        Double configured = settings.blocks().get(block);
        if (configured != null) {
            return configured;
        }
        return state.getBlock() instanceof LiquidBlock ? fluidUnitRate(state.getFluidState().getType())
                : stackSourceRate(new ItemStack(state.getBlock()));
    }

    public static double fluidRate(FluidStack stack) {
        return stack.isEmpty() ? 0 : fluidUnitRate(stack.getFluid()) * (stack.getAmount() / 1000D);
    }

    private static double fluidUnitRate(Fluid fluid) {
        var settings = RadiationSourceSettings.active();
        var id = ForgeRegistries.FLUIDS.getKey(fluid);
        var source = fluid instanceof FlowingFluid flowing ? ForgeRegistries.FLUIDS.getKey(flowing.getSource()) : id;
        if (id == null || source == null || settings.excludedFluids().contains(id)
                || settings.excludedFluids().contains(source)) {
            return 0;
        }
        Double configured = settings.fluids().get(id);
        if (configured == null) {
            configured = settings.fluids().get(source);
        }
        if (configured != null) {
            return configured;
        }
        Double direct = FLUIDS.get(id);
        return direct != null ? direct : fluid.builtInRegistryHolder().tags()
                .mapToDouble(tag -> FLUID_TAGS.getOrDefault(tag.location(), 0D))
                .max().orElse(0);
    }

    public static double stockItemRate(String id) {
        return ITEMS.getOrDefault(NuclearCraft.id(id), 0D);
    }

    private static void material(String id, double rate) {
        fluid(id, rate);
        item(id + "_ingot", rate);
        item(id + "_dust", rate);
        item(id + "_ore", rate / 4);
        item(id + "_block", rate * 9);
        for (String form : List.of("ingots", "dusts", "clumps", "shards", "crystals", "crushed", "plates", "raw_materials", "sheetmetals")) {
            tag(form + "/" + id, rate);
        }
        for (String form : List.of("tiny_dusts", "nuggets")) {
            tag(form + "/" + id, rate / 9);
        }
        tag("rods/" + id, rate / 2);
        tag("coins/" + id, rate / 3);
        tag("ores/" + id, rate / 4);
        tag("small_dusts/" + id, rate / 4);
        tag("gears/" + id, rate * 4);
        tag("storage_blocks/" + id, rate * 9);
        tag("dense_plates/" + id, rate * 9);
    }

    private static void isotope(String id, double rate) {
        for (String suffix : ISOTOPE_FORMS) {
            material(id + suffix, rate);
        }
        for (String suffix : SALT_FORMS) {
            fluid(id + suffix, rate);
        }
    }

    private static void fuel(String id, double fresh, double depleted) {
        item(id + "_pellet", fresh);
        item(id + "_pellet_carbide", fresh);
        tag("ingots/" + id, fresh);
        tag("ingots/" + id + "_carbide", fresh);
        for (String suffix : SALT_FORMS) {
            fluid(id + suffix, fresh);
            fluid("depleted_" + id + suffix, depleted);
        }
        for (String suffix : FUEL_FORMS) {
            item(id + suffix, fresh);
            item("depleted_" + id + suffix, depleted);
            tag("ingots/" + id + suffix, fresh);
            tag("ingots/depleted_" + id + suffix, depleted);
        }
    }

    private static void fluid(String id, double rate) {
        double perBucket = rate * FLUID_MULTIPLIER;
        FLUIDS.put(NuclearCraft.id(id), perBucket);
        FLUIDS.put(NuclearCraft.id("flowing_" + id), perBucket);
        FLUID_TAGS.put(ResourceLocation.tryBuild("forge", "molten_" + id), perBucket);
    }

    private static void item(String id, double rate) {
        ITEMS.put(NuclearCraft.id(id), rate);
    }

    private static void tag(String path, double rate) {
        ITEM_TAGS.put(ResourceLocation.tryBuild("forge", path), rate);
    }

    private static double getFuelRadiation(double rad1, double amount1, double rad2, double amount2) {
        return (rad1 * amount1 + rad2 * amount2) / 9D;
    }

    private static double getDepletedFuelRadiation(double rad1, int amount1, double rad2, int amount2, double rad3, int amount3, double rad4, int amount4,
                                       double waste1, double waste2, double m, double r) {
        return (rad1 * amount1 + rad2 * amount2 + rad3 * amount3 + rad4 * amount4 + waste1 * m * r + waste2 * m * (1D - r)) / 9D;
    }
}
