package nc.init;

import nc.NuclearCraft;
import nc.radiation.GeigerCounterItem;
import nc.radiation.GeigerBlock;
import nc.radiation.GeigerBlockEntity;
import nc.radiation.MedicineItem;
import nc.radiation.RadiationConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.RegistryObject;

public final class NCRadiation {
    public static final RegistryObject<SoundEvent> RADAWAY_SOUND = sound("player.radaway");
    public static final RegistryObject<SoundEvent> RAD_X_SOUND = sound("player.rad_x");

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

    private static final RegistryObject<CreativeModeTab> TAB = NCRegistries.TABS.register("radiation", () ->
            CreativeModeTab.builder().title(Component.translatable("itemGroup.nuclearcraft.radiation"))
                    .icon(() -> GEIGER_COUNTER.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(GEIGER_COUNTER.get());
                        output.accept(GEIGER_BLOCK_ITEM.get());
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
