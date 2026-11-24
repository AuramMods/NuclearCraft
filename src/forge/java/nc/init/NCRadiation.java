package nc.init;

import nc.NuclearCraft;
import nc.processor.RadiationScrubber;
import nc.processor.ScrubberBlock;
import nc.processor.ScrubberMenu;
import nc.recipe.ArmorShieldingRecipe;
import nc.recipe.ScrubberRecipe;
import nc.radiation.ContainerShieldingLoot;
import nc.radiation.GeigerCounterItem;
import nc.radiation.GeigerBlock;
import nc.radiation.GeigerBlockEntity;
import nc.radiation.HazmatMaterial;
import nc.radiation.MedicineItem;
import nc.radiation.RadiationConfig;
import nc.radiation.RadiationHudConfig;
import nc.radiation.RadiationSync;
import nc.radiation.ShieldingItem;
import nc.radiation.ShieldingItem.Tier;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class NCRadiation {
    public static final RegistryObject<SoundEvent> GEIGER_TICK = sound("player.geiger_tick");
    public static final RegistryObject<SoundEvent> CHEMS_WEAR_OFF = sound("player.chems_wear_off");
    public static final RegistryObject<SoundEvent> RADAWAY_SOUND = sound("player.radaway");
    public static final RegistryObject<SoundEvent> RAD_X_SOUND = sound("player.rad_x");
    public static final RegistryObject<SoundEvent> RAD_POISONING = sound("player.rad_poisoning");

    public static final RegistryObject<Item> GEIGER_COUNTER = NCRegistries.ITEMS.register("geiger_counter",
            () -> new GeigerCounterItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<GeigerBlock> GEIGER_BLOCK = NCRegistries.BLOCKS.register("geiger_block",
            () -> new GeigerBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)));
    private static final RegistryObject<Item> GEIGER_BLOCK_ITEM = NCRegistries.ITEMS.register("geiger_block",
            () -> new BlockItem(GEIGER_BLOCK.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<GeigerBlockEntity>> GEIGER_ENTITY =
            NCRegistries.BLOCK_ENTITIES.register("geiger_block", () -> BlockEntityType.Builder.of(
                    GeigerBlockEntity::new, GEIGER_BLOCK.get()).build(null));
    public static final RegistryObject<Item> RADAWAY = medicine(MedicineItem.Kind.RADAWAY);
    public static final RegistryObject<Item> SLOW_RADAWAY = medicine(MedicineItem.Kind.SLOW_RADAWAY);
    public static final RegistryObject<Item> RAD_X = medicine(MedicineItem.Kind.RAD_X);
    private static final Map<Tier, RegistryObject<ShieldingItem>> SHIELDING = new EnumMap<>(Tier.class);
    static {
        for (Tier tier : Tier.values()) {
            SHIELDING.put(tier, NCRegistries.ITEMS.register(tier.id(), () -> new ShieldingItem(tier, new Item.Properties())));
        }
        NCRegistries.LOOT_MODIFIERS.register("container_shielding", () -> ContainerShieldingLoot.CODEC);
    }
    public static final RegistryObject<RecipeSerializer<ArmorShieldingRecipe>> ARMOR_SHIELDING =
            NCRegistries.RECIPE_SERIALIZERS.register("armor_shielding",
                    () -> new SimpleCraftingRecipeSerializer<>(ArmorShieldingRecipe::new));
    private static final List<RegistryObject<ArmorItem>> HAZMAT = List.of(
            hazmat("helm_hazmat", ArmorItem.Type.HELMET), hazmat("chest_hazmat", ArmorItem.Type.CHESTPLATE),
            hazmat("legs_hazmat", ArmorItem.Type.LEGGINGS), hazmat("boots_hazmat", ArmorItem.Type.BOOTS));

    private static final String SCRUBBER_ID = "radiation_scrubber";
    public static final RegistryObject<ScrubberBlock> SCRUBBER = NCRegistries.BLOCKS.register(SCRUBBER_ID,
            () -> new ScrubberBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK).strength(3F, 15F)));
    private static final RegistryObject<Item> SCRUBBER_ITEM = NCRegistries.ITEMS.register(SCRUBBER_ID,
            () -> new BlockItem(SCRUBBER.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<RadiationScrubber>> SCRUBBER_ENTITY =
            NCRegistries.BLOCK_ENTITIES.register(SCRUBBER_ID, () -> BlockEntityType.Builder.of(
                    RadiationScrubber::new, SCRUBBER.get()).build(null));
    public static final RegistryObject<MenuType<ScrubberMenu>> SCRUBBER_MENU = NCRegistries.MENUS.register(SCRUBBER_ID,
            () -> new MenuType<>(ScrubberMenu::new, FeatureFlags.DEFAULT_FLAGS));
    public static final RegistryObject<RecipeType<ScrubberRecipe>> SCRUBBER_RECIPES =
            NCRegistries.RECIPE_TYPES.register(SCRUBBER_ID, () -> RecipeType.simple(NuclearCraft.id(SCRUBBER_ID)));
    public static final RegistryObject<RecipeSerializer<ScrubberRecipe>> SCRUBBER_SERIALIZER =
            NCRegistries.RECIPE_SERIALIZERS.register(SCRUBBER_ID, ScrubberRecipe.Serializer::new);

    private static final RegistryObject<CreativeModeTab> TAB = NCRegistries.TABS.register("radiation", () ->
            CreativeModeTab.builder().title(Component.translatable("itemGroup.nuclearcraft.radiation"))
                    .icon(() -> GEIGER_COUNTER.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(GEIGER_COUNTER.get());
                        output.accept(GEIGER_BLOCK_ITEM.get());
                        output.accept(SCRUBBER_ITEM.get());
                        output.accept(RADAWAY.get());
                        output.accept(SLOW_RADAWAY.get());
                        output.accept(RAD_X.get());
                        SHIELDING.values().forEach(item -> output.accept(item.get()));
                        HAZMAT.forEach(item -> output.accept(item.get()));
                    }).build());

    private NCRadiation() {}

    public static void register(FMLJavaModLoadingContext context) {
        RadiationConfig.register(context);
        RadiationHudConfig.register(context);
        RadiationSync.register();
    }

    private static RegistryObject<SoundEvent> sound(String path) {
        return NCRegistries.SOUNDS.register(path, () -> SoundEvent.createVariableRangeEvent(NuclearCraft.id(path)));
    }

    private static RegistryObject<Item> medicine(MedicineItem.Kind kind) {
        return NCRegistries.ITEMS.register(kind.id(), () -> new MedicineItem(kind, new Item.Properties()));
    }

    public static ShieldingItem shielding(Tier tier) {
        return SHIELDING.get(tier).get();
    }

    private static RegistryObject<ArmorItem> hazmat(String id, ArmorItem.Type type) {
        return NCRegistries.ITEMS.register(id, () -> new ArmorItem(HazmatMaterial.INSTANCE, type,
                new Item.Properties().stacksTo(1)));
    }
}
