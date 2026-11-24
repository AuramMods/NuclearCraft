package nc.init;

import nc.energy.BatteryBlock;
import nc.energy.BatteryBlockEntity;
import nc.energy.BatteryItem;
import nc.recipe.ChargedCraftingRecipe;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.stream.Collectors;

public final class NCBatteries {
    public static final RegistryObject<RecipeSerializer<ChargedCraftingRecipe>> CHARGED_CRAFTING =
            NCRegistries.RECIPE_SERIALIZERS.register("charged_crafting", ChargedCraftingRecipe.Serializer::new);
    public static final RegistryObject<Item> CELL = NCRegistries.ITEMS.register("lithium_ion_cell",
            () -> new BatteryItem.Cell(NCBatteries::cellCapacity, NCBatteries::cellTransfer));

    public static final RegistryObject<BlockEntityType<BatteryBlockEntity>> SCRIPTED_BATTERY_ENTITY =
            NCRegistries.BLOCK_ENTITIES.register("scripted_battery", () -> new BlockEntityType<>(
                    BatteryBlockEntity::new, ForgeRegistries.BLOCKS.getValues().stream()
                            .filter(block -> block instanceof BatteryBlock battery && battery.isScriptedBattery())
                            .collect(Collectors.toUnmodifiableSet()), null));

    private static final ForgeConfigSpec SPEC;
    private static final Limits CELL_LIMITS;
    private static final EnumMap<Tier, Limits> LIMITS = new EnumMap<>(Tier.class);
    private static volatile int configRevision;

    static {
        var builder = new ForgeConfigSpec.Builder();
        for (Tier tier : Tier.values()) {
            LIMITS.put(tier, limits(builder, tier.id, tier.capacity, tier.transfer));
        }
        CELL_LIMITS = limits(builder, "lithium_ion_cell", 8_000_000, 80_000);
        SPEC = builder.build();
    }

    private NCBatteries() {}

    private static Limits limits(ForgeConfigSpec.Builder builder, String id, int capacity, int transfer) {
        builder.push(id);
        var values = new Limits(builder.defineInRange("capacity", capacity, 1, Integer.MAX_VALUE),
                builder.defineInRange("max_transfer", transfer, 1, Integer.MAX_VALUE));
        builder.pop();
        return values;
    }

    public static void register(FMLJavaModLoadingContext context) {
        context.registerConfig(ModConfig.Type.SERVER, SPEC, "nuclearcraft-batteries.toml");
        context.getModEventBus().addListener((ModConfigEvent.Reloading event) -> {
            if (event.getConfig().getSpec() == SPEC) {
                configRevision++;
            }
        });
    }

    static void displayItems(CreativeModeTab.Output output) {
        for (Tier tier : Tier.values()) {
            output.accept(tier.block());
        }
        output.accept(CELL.get());
    }

    public static int configRevision() {
        return configRevision;
    }

    private static int cellCapacity() {
        return get(CELL_LIMITS.capacity());
    }

    private static int cellTransfer() {
        return get(CELL_LIMITS.transfer());
    }

    private static int get(ForgeConfigSpec.IntValue setting) {
        return SPEC.isLoaded() ? setting.get() : setting.getDefault();
    }

    private record Limits(ForgeConfigSpec.IntValue capacity, ForgeConfigSpec.IntValue transfer) {}

    public enum Tier implements BatteryBlock.Definition {
        VOLTAIC_PILE_BASIC("voltaic_pile_basic", 1_600_000, 16_000),
        VOLTAIC_PILE_ADVANCED("voltaic_pile_advanced", 6_400_000, 64_000),
        VOLTAIC_PILE_DU("voltaic_pile_du", 25_600_000, 256_000),
        VOLTAIC_PILE_ELITE("voltaic_pile_elite", 102_400_000, 1_024_000),
        LITHIUM_ION_BATTERY_BASIC("lithium_ion_battery_basic", 32_000_000, 320_000),
        LITHIUM_ION_BATTERY_ADVANCED("lithium_ion_battery_advanced", 128_000_000, 1_280_000),
        LITHIUM_ION_BATTERY_DU("lithium_ion_battery_du", 512_000_000, 5_120_000),
        LITHIUM_ION_BATTERY_ELITE("lithium_ion_battery_elite", 2_048_000_000, 20_480_000);

        private final String id;
        private final int capacity;
        private final int transfer;
        private final RegistryObject<BatteryBlock> block;
        private final RegistryObject<BlockEntityType<BatteryBlockEntity>> entity;

        Tier(String id, int capacity, int transfer) {
            this.id = id;
            this.capacity = capacity;
            this.transfer = transfer;
            block = NCRegistries.BLOCKS.register(id, () -> new BatteryBlock(
                    BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK), this));
            NCRegistries.ITEMS.register(id, () -> new BatteryItem.BlockBattery(block.get()));
            entity = NCRegistries.BLOCK_ENTITIES.register(id, () -> BlockEntityType.Builder.of(
                    BatteryBlockEntity::new, block.get()).build(null));
        }

        public BatteryBlock block() {
            return block.get();
        }

        @Override
        public BlockEntityType<BatteryBlockEntity> entity() {
            return entity.get();
        }

        @Override
        public long capacity() {
            return get(LIMITS.get(this).capacity());
        }

        @Override
        public int transfer() {
            return get(LIMITS.get(this).transfer());
        }
    }
}
