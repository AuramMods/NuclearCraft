package nc.init;

import nc.NuclearCraft;
import nc.generator.DecayGeneratorBlock;
import nc.generator.DecayGeneratorBlockEntity;
import nc.generator.PassiveGeneratorBlock;
import nc.generator.PassiveGeneratorBlockEntity;
import nc.recipe.DecayGeneratorRecipe;
import nc.radiation.RadiationSources;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.Locale;

public final class NCGenerators {
    private static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.IntValue DECAY_INTERVAL;
    private static final EnumMap<DecayFamily, DecaySettings> DECAY_SETTINGS = new EnumMap<>(DecayFamily.class);
    private static final EnumMap<PassiveGenerator, ForgeConfigSpec.IntValue> POWER = new EnumMap<>(PassiveGenerator.class);
    private static volatile int configRevision;

    static {
        var builder = new ForgeConfigSpec.Builder();
        builder.push("decay_generator");
        DECAY_INTERVAL = builder.defineInRange("update_interval", 20, 1, 1200);
        for (DecayFamily family : DecayFamily.values()) {
            builder.push(family.name().toLowerCase(Locale.ROOT));
            DECAY_SETTINGS.put(family, new DecaySettings(
                    builder.defineInRange("lifetime", family.lifetime, 1D, 16_777_215D),
                    builder.defineInRange("power", family.power, 0D, 32_767D)));
            builder.pop();
        }
        builder.pop();
        for (PassiveGenerator generator : PassiveGenerator.values()) {
            builder.push(generator.id);
            POWER.put(generator, builder.comment(generator.solar ? "Maximum daytime output in FE per tick." : "Output in FE per tick.")
                    .defineInRange("power", generator.defaultPower, 1, Integer.MAX_VALUE));
            builder.pop();
        }
        SPEC = builder.build();
    }

    private static final String DECAY_ID = "decay_generator";
    public static final RegistryObject<DecayGeneratorBlock> DECAY_GENERATOR = NCRegistries.BLOCKS.register(DECAY_ID,
            () -> new DecayGeneratorBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)));
    public static final RegistryObject<Item> DECAY_ITEM = NCRegistries.ITEMS.register(DECAY_ID,
            () -> new BlockItem(DECAY_GENERATOR.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<DecayGeneratorBlockEntity>> DECAY_ENTITY =
            NCRegistries.BLOCK_ENTITIES.register(DECAY_ID, () -> BlockEntityType.Builder.of(
                    DecayGeneratorBlockEntity::new, DECAY_GENERATOR.get()).build(null));
    public static final RegistryObject<RecipeType<DecayGeneratorRecipe>> DECAY_RECIPES =
            NCRegistries.RECIPE_TYPES.register(DECAY_ID, () -> RecipeType.simple(NuclearCraft.id(DECAY_ID)));
    public static final RegistryObject<RecipeSerializer<DecayGeneratorRecipe>> DECAY_SERIALIZER =
            NCRegistries.RECIPE_SERIALIZERS.register(DECAY_ID, DecayGeneratorRecipe.Serializer::new);

    private NCGenerators() {}

    public static void register(FMLJavaModLoadingContext context) {
        context.registerConfig(ModConfig.Type.SERVER, SPEC, "nuclearcraft-generators.toml");
        context.getModEventBus().addListener((ModConfigEvent.Reloading event) -> {
            if (event.getConfig().getSpec() == SPEC) {
                configRevision++;
            }
        });
    }

    static void displayItems(CreativeModeTab.Output output) {
        output.accept(DECAY_ITEM.get());
        for (PassiveGenerator generator : PassiveGenerator.values()) {
            output.accept(generator.block());
        }
    }

    public static int decayInterval() {
        return DECAY_INTERVAL.get();
    }

    private record DecaySettings(ForgeConfigSpec.DoubleValue lifetime, ForgeConfigSpec.DoubleValue power) {}

    public static int configRevision() {
        return configRevision;
    }

    public enum PassiveGenerator {
        RTG_URANIUM("rtg_uranium", 1, false),
        RTG_PLUTONIUM("rtg_plutonium", 40, false),
        RTG_AMERICIUM("rtg_americium", 10, false),
        RTG_CALIFORNIUM("rtg_californium", 200, false),
        SOLAR_BASIC("solar_panel_basic", 5, true),
        SOLAR_ADVANCED("solar_panel_advanced", 20, true),
        SOLAR_DU("solar_panel_du", 80, true),
        SOLAR_ELITE("solar_panel_elite", 320, true);

        private final String id;
        private final int defaultPower;
        private final boolean solar;
        private final double radiation;
        private final RegistryObject<PassiveGeneratorBlock> block;
        private final RegistryObject<BlockEntityType<PassiveGeneratorBlockEntity>> entity;

        PassiveGenerator(String id, int power, boolean solar) {
            this.id = id;
            this.defaultPower = power;
            this.solar = solar;
            // In 2o.9.7 an RTG emits half as much when placed as when carried.
            this.radiation = solar ? 0 : RadiationSources.stockItemRate(id) / 2;
            block = NCRegistries.BLOCKS.register(id, () -> new PassiveGeneratorBlock(
                    BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK), this));
            NCRegistries.ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties()));
            entity = NCRegistries.BLOCK_ENTITIES.register(id, () -> BlockEntityType.Builder.of(
                    (pos, state) -> new PassiveGeneratorBlockEntity(pos, state, this), block.get()).build(null));
        }

        public PassiveGeneratorBlock block() {
            return block.get();
        }

        public BlockEntityType<PassiveGeneratorBlockEntity> entity() {
            return entity.get();
        }

        public int power() {
            return SPEC.isLoaded() ? POWER.get(this).get() : defaultPower;
        }

        public boolean solar() {
            return solar;
        }

        public double radiation() {
            return radiation;
        }
    }

    public enum DecayFamily {
        THORIUM(165260, 1.45), URANIUM(136120, 1.75), URANIUM_238(145415, 1.65), NEPTUNIUM_237(19740, 12.15),
        PLUTONIUM_242(6360, 37.75), AMERICIUM_243(1680, 142.80), CURIUM_246(1545, 155.10),
        BERKELIUM_247(1265, 190.10), CALIFORNIUM_252(645, 370.70);

        private final double lifetime;
        private final double power;
        private final double radiation;

        DecayFamily(double lifetime, double power) {
            this.lifetime = lifetime;
            this.power = power;
            this.radiation = RadiationSources.stockItemRate(name().toLowerCase(Locale.ROOT) + "_ingot");
        }

        public double radiation() {
            return radiation;
        }

        public double lifetime() {
            return SPEC.isLoaded() ? DECAY_SETTINGS.get(this).lifetime().get() : lifetime;
        }

        public double power() {
            return SPEC.isLoaded() ? DECAY_SETTINGS.get(this).power().get() : power;
        }
    }
}
