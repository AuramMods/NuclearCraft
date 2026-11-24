package nc.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.conditions.ICondition;

import java.util.Optional;

final class ItemRecipeResult {
    private ItemRecipeResult() {}

    static ItemStack read(JsonObject json, ICondition.IContext context) {
        if (json.has("tag") && json.has("item")) {
            throw new JsonSyntaxException("Expected an item or an item tag in output " + json);
        }
        var selected = json.has("preferred_tag") ? selectTag(json, "preferred_tag", context)
                : Optional.<ResourceLocation>empty();
        if (selected.isEmpty() && json.has("tag")) {
            selected = selectTag(json, "tag", context);
            if (selected.isEmpty()) {
                throw new JsonSyntaxException("Empty item output tag " + json.get("tag"));
            }
        }
        if (selected.isEmpty()) {
            return CraftingHelper.getItemStack(json, true);
        }
        var resolved = json.deepCopy();
        resolved.remove("tag");
        resolved.remove("preferred_tag");
        resolved.addProperty("item", selected.get().toString());
        return CraftingHelper.getItemStack(resolved, true);
    }

    private static Optional<ResourceLocation> selectTag(JsonObject json, String key, ICondition.IContext context) {
        var id = ResourceLocation.tryParse(GsonHelper.getAsString(json, key));
        if (id == null) {
            throw new JsonSyntaxException("Invalid item output tag in " + json);
        }
        var tag = TagKey.create(Registries.ITEM, id);
        var entries = context == ICondition.IContext.EMPTY
                ? BuiltInRegistries.ITEM.getTag(tag).stream().flatMap(set -> set.stream())
                : context.getTag(tag).stream();
        return entries.map(Holder::value).filter(item -> item != Items.AIR)
                .map(BuiltInRegistries.ITEM::getKey).findFirst();
    }
}
