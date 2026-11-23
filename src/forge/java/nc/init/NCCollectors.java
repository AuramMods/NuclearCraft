package nc.init;

import nc.processor.PassiveCollectorBlock;
import nc.processor.PassiveCollectorBlockEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.registries.RegistryObject;

import java.util.Arrays;

public final class NCCollectors {
    private static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.IntValue UPDATE_INTERVAL;
    private static final ForgeConfigSpec.BooleanValue AUTO_OUTPUT;
    private static final ForgeConfigSpec.DoubleValue COBBLESTONE_RATE;
    private static final ForgeConfigSpec.DoubleValue WATER_RATE;
    private static final ForgeConfigSpec.DoubleValue NITROGEN_RATE;
    private static final ForgeConfigSpec.DoubleValue COBBLESTONE_POWER;

    static {
        var builder = new ForgeConfigSpec.Builder();
        builder.push("collectors");
        UPDATE_INTERVAL = builder.comment("Ticks between automatic output attempts.")
                .defineInRange("update_interval", 20, 1, 1200);
        AUTO_OUTPUT = builder.comment("Push stored products to adjacent inventories or tanks.")
                .define("auto_output", true);
        COBBLESTONE_RATE = builder.comment("Items per tick from a basic Cobblestone Generator.")
                .defineInRange("cobblestone_rate", 0.125D, 0D, 4000D);
        WATER_RATE = builder.comment("Millibuckets per tick from a basic Water Source.")
                .defineInRange("water_rate", 10D, 0D, 4000D);
        NITROGEN_RATE = builder.comment("Millibuckets per tick from a basic Nitrogen Collector.")
                .defineInRange("nitrogen_rate", 5D, 0D, 4000D);
        COBBLESTONE_POWER = builder.comment("Forge Energy per tick for a basic Cobblestone Generator.")
                .defineInRange("cobblestone_power", 0D, 0D, 255D);
        builder.pop();
        SPEC = builder.build();
    }

    public static final RegistryObject<BlockEntityType<PassiveCollectorBlockEntity>> ENTITY =
            NCRegistries.BLOCK_ENTITIES.register("passive_collector", () -> BlockEntityType.Builder.of(
                    PassiveCollectorBlockEntity::new, Arrays.stream(Collector.values())
                            .map(Collector::block).toArray(Block[]::new)).build(null));

    private NCCollectors() {}

    public static void register(ModLoadingContext context) {
        context.registerConfig(ModConfig.Type.SERVER, SPEC, "nuclearcraft-collectors.toml");
        Collector.values();
    }

    static void displayItems(CreativeModeTab.Output output) {
        for (Collector collector : Collector.values()) {
            output.accept(collector.block());
        }
    }

    public static int updateInterval() {
        return UPDATE_INTERVAL.get();
    }

    public static boolean autoOutput() {
        return AUTO_OUTPUT.get();
    }

    public enum Family {
        COBBLESTONE, WATER, NITROGEN
    }

    public enum Collector {
        COBBLESTONE("cobblestone_generator", Family.COBBLESTONE, 1),
        COBBLESTONE_COMPACT("cobblestone_generator_compact", Family.COBBLESTONE, 8),
        COBBLESTONE_DENSE("cobblestone_generator_dense", Family.COBBLESTONE, 64),
        WATER("water_source", Family.WATER, 1),
        WATER_COMPACT("water_source_compact", Family.WATER, 8),
        WATER_DENSE("water_source_dense", Family.WATER, 64),
        NITROGEN("nitrogen_collector", Family.NITROGEN, 1),
        NITROGEN_COMPACT("nitrogen_collector_compact", Family.NITROGEN, 8),
        NITROGEN_DENSE("nitrogen_collector_dense", Family.NITROGEN, 64);

        private final String id;
        private final Family family;
        private final int multiplier;
        private final RegistryObject<PassiveCollectorBlock> block;

        Collector(String id, Family family, int multiplier) {
            this.id = id;
            this.family = family;
            this.multiplier = multiplier;
            block = NCRegistries.BLOCKS.register(id, () -> new PassiveCollectorBlock(
                    BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK), this));
            NCRegistries.ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties()));
        }

        public String id() {
            return id;
        }

        public PassiveCollectorBlock block() {
            return block.get();
        }

        public boolean producesItems() {
            return family == Family.COBBLESTONE;
        }

        public Fluid fluid() {
            return family == Family.WATER ? Fluids.WATER : NCFluids.NITROGEN.source().get();
        }

        public double rate() {
            double base = switch (family) {
                case COBBLESTONE -> COBBLESTONE_RATE.get();
                case WATER -> WATER_RATE.get();
                case NITROGEN -> NITROGEN_RATE.get();
            };
            return multiplier * base;
        }

        public double power() {
            return producesItems() ? multiplier * COBBLESTONE_POWER.get() : 0;
        }

        public int capacity() {
            return Math.max(1, (int) Math.ceil(6 * updateInterval() * rate()));
        }
    }
}
