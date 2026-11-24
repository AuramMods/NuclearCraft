package nc.init;

import nc.NuclearCraft;
import nc.entity.FeralGhoul;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

public final class NCEntities {
    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, NuclearCraft.MOD_ID);

    public static final RegistryObject<EntityType<FeralGhoul>> FERAL_GHOUL = ghoul("feral_ghoul", FeralGhoul::new);
    public static final RegistryObject<EntityType<FeralGhoul>> GLOWING_GHOUL = ghoul("glowing_ghoul", FeralGhoul.Glowing::new);
    private static final RegistryObject<Item> FERAL_EGG = egg(FERAL_GHOUL, 0x967D73, 0x302C28);
    private static final RegistryObject<Item> GLOWING_EGG = egg(GLOWING_GHOUL, 0xAAC76A, 0x85714A);
    public static final RegistryObject<SoundEvent> CHARGE = sound("entity.nc.feral_ghoul_charge");
    public static final RegistryObject<SoundEvent> DEATH = sound("entity.nc.feral_ghoul_death");

    private static final ForgeConfigSpec.Builder CONFIG = new ForgeConfigSpec.Builder();
    private static final ForgeConfigSpec.ConfigValue<List<? extends Boolean>> REGISTER_ENTITY = CONFIG
            .comment("Enable the feral ghoul and glowing ghoul, in that order.")
            .defineList("register_entity", List.of(true, true), value -> value instanceof Boolean);
    private static final ForgeConfigSpec SPEC = CONFIG.build();

    private NCEntities() {}

    public static void register(FMLJavaModLoadingContext context) {
        var bus = context.getModEventBus();
        ENTITIES.register(bus);
        bus.addListener(NCEntities::registerAttributes);
        bus.addListener(NCEntities::registerSpawnPlacements);
        bus.addListener(NCEntities::addEggs);
        context.registerConfig(ModConfig.Type.SERVER, SPEC, "nuclearcraft-entities.toml");
    }

    public static boolean ghoulEnabled(boolean glowing) {
        if (!SPEC.isLoaded()) {
            return true;
        }
        var values = REGISTER_ENTITY.get();
        int index = glowing ? 1 : 0;
        return index >= values.size() || values.get(index);
    }

    private static RegistryObject<EntityType<FeralGhoul>> ghoul(String name, EntityType.EntityFactory<FeralGhoul> factory) {
        return ENTITIES.register(name, () -> EntityType.Builder.of(factory, MobCategory.MONSTER)
                .sized(0.6F, 1.95F).clientTrackingRange(4).updateInterval(1).build(NuclearCraft.id(name).toString()));
    }

    private static RegistryObject<Item> egg(RegistryObject<EntityType<FeralGhoul>> type, int base, int spots) {
        return NCRegistries.ITEMS.register(type.getId().getPath() + "_spawn_egg",
                () -> new ForgeSpawnEggItem(type, base, spots, new Item.Properties()));
    }

    private static RegistryObject<SoundEvent> sound(String name) {
        return NCRegistries.SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(NuclearCraft.id(name)));
    }

    private static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(FERAL_GHOUL.get(), FeralGhoul.attributes(false).build());
        event.put(GLOWING_GHOUL.get(), FeralGhoul.attributes(true).build());
    }

    private static void registerSpawnPlacements(SpawnPlacementRegisterEvent event) {
        event.register(FERAL_GHOUL.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                FeralGhoul::canSpawn, SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(GLOWING_GHOUL.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                FeralGhoul::canSpawnGlowing, SpawnPlacementRegisterEvent.Operation.REPLACE);
    }

    private static void addEggs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CreativeModeTabs.SPAWN_EGGS)) {
            event.accept(FERAL_EGG.get());
            event.accept(GLOWING_EGG.get());
        }
    }
}
