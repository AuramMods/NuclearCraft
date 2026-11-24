package nc.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.crafting.Recipe;

public interface ProcessingRecipe extends Recipe<Container> {
    double radiation();

    static double readRadiation(JsonObject json, ResourceLocation id) {
        double radiation = GsonHelper.getAsDouble(json, "radiation", 0D);
        if (!Double.isFinite(radiation) || radiation < 0D) {
            throw new JsonSyntaxException("Invalid process radiation for " + id);
        }
        return radiation;
    }
}
