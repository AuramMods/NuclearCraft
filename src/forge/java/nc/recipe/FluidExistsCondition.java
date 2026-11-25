package nc.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import nc.NuclearCraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.common.crafting.conditions.IConditionSerializer;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.ForgeRegistries;

public record FluidExistsCondition(ResourceLocation fluid) implements ICondition {
    private static final ResourceLocation ID = NuclearCraft.id("fluid_exists");
    private static final IConditionSerializer<FluidExistsCondition> SERIALIZER = new IConditionSerializer<>() {
        @Override
        public void write(JsonObject json, FluidExistsCondition condition) {
            json.addProperty("fluid", condition.fluid.toString());
        }

        @Override
        public FluidExistsCondition read(JsonObject json) {
            var id = ResourceLocation.tryParse(GsonHelper.getAsString(json, "fluid"));
            if (id == null) {
                throw new JsonSyntaxException("Invalid fluid ID in " + ID);
            }
            return new FluidExistsCondition(id);
        }

        @Override
        public ResourceLocation getID() {
            return ID;
        }
    };

    public static void register(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> CraftingHelper.register(SERIALIZER));
    }

    @Override
    public ResourceLocation getID() {
        return ID;
    }

    @Override
    public boolean test(IContext context) {
        return ForgeRegistries.FLUIDS.containsKey(fluid) && ForgeRegistries.FLUIDS.getValue(fluid) != Fluids.EMPTY;
    }
}
