package nc.init;

import com.google.gson.JsonObject;
import nc.NuclearCraft;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

final class NCCondenserCoolants {
    static final String RECIPE_ID = "condenser_dissipation_fluid";

    private NCCondenserCoolants() {}

    static void addPack(AddPackFindersEvent event) {
        PackType type = event.getPackType();
        if (type != PackType.SERVER_DATA) {
            return;
        }
        var title = Component.translatable("pack.nuclearcraft.condenser_coolants");
        int format = SharedConstants.getCurrentVersion().getPackVersion(type);
        var info = new Pack.Info(title, format, format, FeatureFlagSet.of(), false);
        var source = PackSource.create(PackSource.BUILT_IN::decorate, false);
        event.addRepositorySource(packs -> packs.accept(Pack.create("nuclearcraft_condenser_coolants", title, true,
                id -> new GeneratedPackResources(id, type, recipes()), info, type, Pack.Position.BOTTOM, true, source)));
    }

    private static Map<ResourceLocation, byte[]> recipes() {
        var resources = new HashMap<ResourceLocation, byte[]>();
        for (var entry : ForgeRegistries.FLUIDS.getEntries()) {
            var fluid = entry.getValue();
            if (fluid == Fluids.EMPTY || !fluid.isSource(fluid.defaultFluidState())) {
                continue;
            }
            int temperature = fluid.getFluidType().getTemperature();
            if (temperature < 0 || temperature > 300) {
                continue;
            }
            ResourceLocation fluidId = entry.getKey().location();
            var input = new JsonObject();
            input.addProperty("fluid", fluidId.toString());
            var recipe = new JsonObject();
            recipe.addProperty("type", NuclearCraft.id(RECIPE_ID).toString());
            recipe.add("input", input);
            recipe.addProperty("temperature", temperature);
            recipe.addProperty("priority", -1);
            var path = NuclearCraft.id("recipes/" + RECIPE_ID + "/" + fluidId.getNamespace() + "/" + fluidId.getPath() + ".json");
            resources.put(path, recipe.toString().getBytes(StandardCharsets.UTF_8));
        }
        return resources;
    }
}
