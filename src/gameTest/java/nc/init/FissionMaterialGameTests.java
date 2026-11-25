package nc.init;

import com.google.gson.JsonObject;
import nc.NuclearCraft;
import nc.init.NCFissionMaterials.Form;
import nc.init.NCFissionMaterials.Material;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.util.GsonHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Map;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FissionMaterialGameTests {
    @GameTest(template = "empty")
    public static void generatesScriptedFissionMaterials(GameTestHelper helper) {
        var resources = new FissionMaterialResources();
        var fuel = new Material("qa_fuel", "qafuel", true, EnumSet.allOf(Form.class));
        resources.addItem(fuel.item(Form.RAW, false), fuel.ingotTag(Form.RAW, false),
                "QA Fuel Pellet", "nuclearcraft:item/leu_235_pellet");
        resources.addFluid("qa_fuel", "Molten QA Fuel");
        resources.formRecipes(fuel, true);
        resources.fuelRecipes(fuel, GsonHelper.parse("""
                {"fission": {"ticks": 1000, "heat": 250, "efficiency": 1.5, "criticality": 80,
                    "intrinsic_flux": 10, "decay_factor": 0.075, "radiation": 0.00045, "self_priming": true}}
                """), true);
        var data = resources.bake(PackType.SERVER_DATA);
        var cast = json(data, "nuclearcraft:recipes/ingot_former/qa_fuel.json");
        helper.assertTrue(cast.getAsJsonObject("fluid_input").get("amount").getAsInt() == 144
                && cast.getAsJsonObject("result").get("item").getAsString().equals("nuclearcraft:qa_fuel_pellet"),
                "Casting should restore the registered raw pellet from one ingot volume");
        var nuggets = json(data, "nuclearcraft:recipes/alloy_furnace/qa_fuel_pellet_carbide_from_nuggets.json");
        helper.assertTrue(nuggets.get("ticks").getAsDouble() == 400D / 9
                        && nuggets.get("energy_per_tick").getAsInt() == 10
                        && nuggets.getAsJsonObject("result").get("tag").getAsString().equals("forge:nuggets/qafuel_carbide"),
                "Generated alloys use the legacy nugget timing and produce the corresponding nugget form");
        var triso = json(data, "nuclearcraft:recipes/pebble_fission/qa_fuel_triso.json");
        helper.assertTrue(triso.get("ticks").getAsInt() == 900 && triso.get("heat").getAsInt() == 277
                && triso.get("criticality").getAsInt() == 72 && triso.get("intrinsic_flux").getAsInt() == 10
                && triso.getAsJsonObject("fuel").get("tag").getAsString().equals("forge:ingots/qafuel_triso"),
                "TRISO should keep its legacy scaling, intrinsic flux, and scripted material tag");
        var nitride = json(data, "nuclearcraft:recipes/solid_fission/qa_fuel_nitride.json");
        helper.assertTrue(nitride.get("ticks").getAsInt() == 1_250 && nitride.get("heat").getAsInt() == 200
                && nitride.get("criticality").getAsInt() == 100 && nitride.get("intrinsic_flux").getAsInt() == 0,
                "Solid fuel should use its own form multipliers");
        var salt = json(data, "nuclearcraft:recipes/salt_fission/qa_fuel.json");
        var depleted = salt.getAsJsonObject("result");
        helper.assertTrue(salt.get("ticks").getAsDouble() == 1_250D / 144D
                && salt.get("decay_factor").getAsDouble() == 0.075
                && salt.get("radiation").getAsDouble() == 0.00045
                && depleted.get("amount").getAsInt() == 1
                && depleted.get("fluid").getAsString().equals("nuclearcraft:depleted_qa_fuel_fluoride_flibe"),
                "Salt should retain fractional burn time and yield one millibucket of depleted salt");
        var fluidTag = json(data, "forge:tags/fluids/molten_qa_fuel.json").getAsJsonArray("values");
        helper.assertTrue(fluidTag.size() == 2 && fluidTag.get(1).getAsString().equals("nuclearcraft:flowing_qa_fuel"),
                "The shared fluid tag should contain source and flowing variants");
        var client = resources.bake(PackType.CLIENT_RESOURCES);
        var bucket = json(client, "nuclearcraft:models/item/qa_fuel_bucket.json");
        helper.assertTrue(bucket.get("loader").getAsString().equals("forge:fluid_container")
                && bucket.get("fluid").getAsString().equals("nuclearcraft:qa_fuel"),
                "The bucket should use Forge's tinted fluid container model");
        helper.succeed();
    }

    private static JsonObject json(Map<ResourceLocation, byte[]> resources, String path) {
        return GsonHelper.parse(new String(resources.get(ResourceLocation.tryParse(path)), StandardCharsets.UTF_8));
    }
}
