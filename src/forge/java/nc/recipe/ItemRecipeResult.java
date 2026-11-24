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

final class ItemRecipeResult {
    private ItemRecipeResult() {}

    static ItemStack read(JsonObject json, ICondition.IContext context) {
        if (json.has("tag")) {
            var id = ResourceLocation.tryParse(GsonHelper.getAsString(json, "tag"));
            if (json.has("item") || id == null) {
                throw new JsonSyntaxException("Expected an item or a valid item tag in output " + json);
            }
            var tag = TagKey.create(Registries.ITEM, id);
            var entries = context == ICondition.IContext.EMPTY
                    ? BuiltInRegistries.ITEM.getTag(tag).stream().flatMap(set -> set.stream())
                    : context.getTag(tag).stream();
            var item = entries.map(Holder::value).filter(value -> value != Items.AIR).findFirst()
                    .orElseThrow(() -> new JsonSyntaxException("Empty item output tag " + id));
            json = json.deepCopy();
            json.remove("tag");
            json.addProperty("item", BuiltInRegistries.ITEM.getKey(item).toString());
        }
        return CraftingHelper.getItemStack(json, true);
    }
}
