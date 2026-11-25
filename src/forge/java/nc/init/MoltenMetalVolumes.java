package nc.init;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import nc.NuclearCraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

final class MoltenMetalVolumes {
    private static final String MOLTEN = "molten_";
    private static final Set<String> SHARED_METALS = Set.of("aluminum", "chromium", "copper", "enderium", "gold",
            "iron", "lead", "nickel", "silver", "steel", "tin", "uranium");

    private MoltenMetalVolumes() {}

    static @Nullable ResourceLocation standardTag(@Nullable ResourceLocation tag) {
        if (tag == null || !tag.getNamespace().equals("forge") || !tag.getPath().startsWith(MOLTEN)
                || !SHARED_METALS.contains(tag.getPath().substring(MOLTEN.length()))) {
            return null;
        }
        return NuclearCraft.id("tinkers/" + tag.getPath());
    }

    static @Nullable JsonArray metalAlternatives(JsonObject input) {
        var tag = ResourceLocation.tryParse(GsonHelper.getAsString(input, "tag", ""));
        var standard = standardTag(tag);
        int amount = GsonHelper.getAsInt(input, "amount", 0);
        if (standard == null || amount <= 0) {
            return null;
        }
        var material = tag.getPath().substring(MOLTEN.length());
        // Round fractional amounts up so processing the cast product again cannot create metal.
        return alternatives(input, material, (amount * 144L + 89) / 90, standard.toString());
    }

    static JsonArray alternatives(JsonObject input, String material, long legacyAmount, String standardTag) {
        var legacy = new JsonObject();
        legacy.addProperty("fluid", NuclearCraft.id(material).toString());
        legacy.addProperty("amount", legacyAmount);
        var standard = input.deepCopy();
        standard.remove("catalyst");
        standard.addProperty("tag", standardTag);
        var result = new JsonArray();
        result.add(legacy);
        result.add(standard);
        return result;
    }
}
