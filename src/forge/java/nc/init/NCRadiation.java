package nc.init;

import nc.NuclearCraft;
import nc.radiation.GeigerCounterItem;
import nc.radiation.MedicineItem;
import nc.radiation.RadiationConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.RegistryObject;

public final class NCRadiation {
    public static final RegistryObject<SoundEvent> RADAWAY_SOUND = sound("player.radaway");
    public static final RegistryObject<SoundEvent> RAD_X_SOUND = sound("player.rad_x");

    public static final RegistryObject<Item> GEIGER_COUNTER = NCRegistries.ITEMS.register("geiger_counter",
            () -> new GeigerCounterItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> RADAWAY = medicine(MedicineItem.Kind.RADAWAY);
    public static final RegistryObject<Item> SLOW_RADAWAY = medicine(MedicineItem.Kind.SLOW_RADAWAY);
    public static final RegistryObject<Item> RAD_X = medicine(MedicineItem.Kind.RAD_X);

    private static final RegistryObject<CreativeModeTab> TAB = NCRegistries.TABS.register("radiation", () ->
            CreativeModeTab.builder().title(Component.translatable("itemGroup.nuclearcraft.radiation"))
                    .icon(() -> GEIGER_COUNTER.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(GEIGER_COUNTER.get());
                        output.accept(RADAWAY.get());
                        output.accept(SLOW_RADAWAY.get());
                        output.accept(RAD_X.get());
                    }).build());

    private NCRadiation() {}

    public static void register(FMLJavaModLoadingContext context) {
        RadiationConfig.register(context);
    }

    private static RegistryObject<SoundEvent> sound(String path) {
        return NCRegistries.SOUNDS.register(path, () -> SoundEvent.createVariableRangeEvent(NuclearCraft.id(path)));
    }

    private static RegistryObject<Item> medicine(MedicineItem.Kind kind) {
        return NCRegistries.ITEMS.register(kind.id(), () -> new MedicineItem(kind, new Item.Properties()));
    }
}
