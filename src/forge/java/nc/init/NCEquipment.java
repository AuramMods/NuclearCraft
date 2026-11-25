package nc.init;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import nc.NuclearCraft;
import nc.equipment.EquipmentArmor;
import nc.equipment.EquipmentConfig;
import nc.equipment.EquipmentFamily;
import nc.equipment.EquipmentTools;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.common.crafting.conditions.IConditionSerializer;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.RegistryObject;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

public final class NCEquipment {
    private static final List<Entry> ENTRIES = Arrays.stream(EquipmentFamily.values()).flatMap(family -> {
        var tools = Arrays.stream(EquipmentTools.Kind.values())
                .map(kind -> register(family, false, kind.id(family), () -> kind.create(family)));
        var armor = Arrays.stream(ArmorItem.Type.values())
                .map(type -> register(family, true, armorId(family, type), () -> new EquipmentArmor(family, type)));
        return Stream.concat(tools, armor);
    }).toList();

    private static final RegistryObject<CreativeModeTab> TAB = NCClientPresentation.category("equipment",
            () -> new ItemStack(ENTRIES.get(EquipmentTools.Kind.SPAXELHOE.ordinal()).item().get()),
            (parameters, output) -> ENTRIES.stream()
                    .filter(entry -> EquipmentConfig.isEnabled(entry.family(), entry.armor()))
                    .forEach(entry -> output.accept(entry.item().get())));

    private NCEquipment() {}

    public static void register(FMLJavaModLoadingContext context) {
        EquipmentConfig.register(context);
        context.getModEventBus().addListener(NCEquipment::registerRecipeCondition);
        MinecraftForge.EVENT_BUS.addListener(EquipmentTools::updateCombatAttributes);
    }

    private static void registerRecipeCondition(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> CraftingHelper.register(EquipmentEnabled.SERIALIZER));
    }

    private static Entry register(EquipmentFamily family, boolean armor, String id, Supplier<Item> item) {
        return new Entry(family, armor, NCRegistries.ITEMS.register(id, item));
    }

    private static String armorId(EquipmentFamily family, ArmorItem.Type type) {
        return switch (type) {
            case HELMET -> "helm_";
            case CHESTPLATE -> "chest_";
            case LEGGINGS -> "legs_";
            case BOOTS -> "boots_";
        } + family.id();
    }

    private record Entry(EquipmentFamily family, boolean armor, RegistryObject<Item> item) {}

    private record EquipmentEnabled(EquipmentFamily family, boolean armor) implements ICondition {
        private static final ResourceLocation ID = NuclearCraft.id("equipment_enabled");
        private static final IConditionSerializer<EquipmentEnabled> SERIALIZER = new IConditionSerializer<>() {
            @Override
            public void write(JsonObject json, EquipmentEnabled condition) {
                json.addProperty("family", condition.family().id());
                json.addProperty("armor", condition.armor());
            }

            @Override
            public EquipmentEnabled read(JsonObject json) {
                String id = GsonHelper.getAsString(json, "family");
                for (EquipmentFamily family : EquipmentFamily.values()) {
                    if (family.id().equals(id)) {
                        return new EquipmentEnabled(family, GsonHelper.getAsBoolean(json, "armor"));
                    }
                }
                throw new JsonSyntaxException("Unknown NuclearCraft equipment family: " + id);
            }

            @Override public ResourceLocation getID() { return ID; }
        };

        @Override public ResourceLocation getID() { return ID; }

        @Override
        public boolean test(IContext context) {
            return EquipmentConfig.isEnabled(family, armor);
        }
    }
}
