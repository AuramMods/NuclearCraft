package nc.init;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;

final class NCMaterials {
    private static final List<RegistryObject<Item>> MATERIALS = new ArrayList<>();
    private static final VoxelShape GLOWING_MUSHROOM_SHAPE = Block.box(5, 0, 5, 11, 6, 11);

    private static final RegistryObject<Item> SULFUR = item("sulfur_dust");

    private static final RegistryObject<CreativeModeTab> TAB = NCRegistries.TABS.register("materials", () ->
            CreativeModeTab.builder().title(Component.translatable("itemGroup.nuclearcraft.materials"))
                    .icon(() -> SULFUR.get().getDefaultInstance())
                    .displayItems((parameters, output) -> MATERIALS.forEach(item -> output.accept(item.get())))
                    .build());

    private NCMaterials() {}

    static void register() {
        for (String name : List.of("copper", "tin", "lead", "thorium", "uranium", "boron", "lithium", "magnesium")) {
            block(name + "_ore", BlockBehaviour.Properties.copy(Blocks.IRON_ORE));
        }
        block("glowing_mushroom", BlockBehaviour.Properties.copy(Blocks.BROWN_MUSHROOM).lightLevel(state -> 15));
        block("soulless_sand", BlockBehaviour.Properties.copy(Blocks.SAND).strength(0.5F));
        block("soulless_sandstone", BlockBehaviour.Properties.copy(Blocks.SANDSTONE).strength(0.8F));
        block("molybdenum_block", BlockBehaviour.Properties.copy(Blocks.STONE).strength(5F));
        block("copper_oxide_block", BlockBehaviour.Properties.copy(Blocks.STONE).strength(5F));
        item("flour");
        item("roasted_cocoa_beans");
        item("ground_cocoa_nibs", new Item.Properties().food(new FoodProperties.Builder().nutrition(1).saturationMod(0.2F).build()));
        item("cocoa_solids");
        item("cocoa_butter", new Item.Properties().food(new FoodProperties.Builder().nutrition(2).saturationMod(0.2F)
                .effect(() -> new MobEffectInstance(MobEffects.ABSORPTION, 300, 0, false, false), 1F).build()));
        item("unsweetened_chocolate", new Item.Properties().food(new FoodProperties.Builder().nutrition(2).saturationMod(0.2F)
                .effect(() -> new MobEffectInstance(MobEffects.DIG_SPEED, 300, 0, false, false), 1F).build()));
        item("dark_chocolate", new Item.Properties().food(new FoodProperties.Builder().nutrition(3).saturationMod(0.4F)
                .effect(() -> new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 300, 0, false, false), 1F)
                .effect(() -> new MobEffectInstance(MobEffects.DIG_SPEED, 300, 0, false, false), 1F).build()));
        item("milk_chocolate", new Item.Properties().food(new FoodProperties.Builder().nutrition(4).saturationMod(0.6F)
                .effect(() -> new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 300, 0, false, false), 1F)
                .effect(() -> new MobEffectInstance(MobEffects.DIG_SPEED, 300, 0, false, false), 1F)
                .effect(() -> new MobEffectInstance(MobEffects.ABSORPTION, 300, 0, false, false), 1F).build()));
        item("gelatin");
        item("marshmallow", new Item.Properties().food(new FoodProperties.Builder().nutrition(1).saturationMod(0.4F)
                .effect(() -> new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 300, 0, false, false), 1F).build()));
        item("graham_cracker", new Item.Properties().food(new FoodProperties.Builder().nutrition(1).saturationMod(0.2F).build()));
        for (String name : List.of("copper", "tin", "lead", "magnesium", "aluminum", "silver")) {
            metal(name, 3F, SoundType.METAL);
        }
        for (String name : List.of("thorium", "uranium", "zirconium", "manganese", "palladium",
                "holmium", "dysprosium", "gadolinium")) {
            metal(name, 5F, SoundType.METAL);
        }
        for (String name : List.of("boron", "beryllium", "manganese_oxide", "manganese_dioxide", "zirconia",
                "tin_oxide", "nickel_oxide", "cobalt_oxide", "ruthenium_oxide", "iridium_oxide")) {
            metal(name, 5F, SoundType.STONE);
        }
        metal("lithium", 1F, SoundType.METAL);
        metal("graphite", 3F, SoundType.STONE);
        for (String isotope : List.of("uranium_238", "neptunium_237", "plutonium_242", "americium_243",
                "curium_246", "berkelium_247", "californium_252")) {
            block(isotope + "_block", BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK).strength(5F, 6F));
        }
        for (String name : List.of("bronze", "tough", "hard_carbon", "magnesium_diboride", "lithium_manganese_dioxide",
                "steel", "ferroboron", "shibuichi", "tin_silver", "lead_platinum", "extreme", "thermoconducting",
                "zircaloy", "silicon_carbide", "sic_sic_cmc", "hsla_steel", "zirconium_molybdenum", "hastelloy", "samarium")) {
            item(name + "_ingot");
        }
        for (String name : List.of("rhodochrosite", "boron_nitride", "fluorite", "villiaumite", "carobbiite",
                "boron_arsenide", "silicon")) {
            item(name);
        }
        for (String name : List.of("diamond", "rhodochrosite", "quartz", "obsidian", "boron_nitride", "fluorite",
                "coal", "villiaumite", "carobbiite", "arsenic", "end_stone", "barite", "nichromite", "dysprholminite")) {
            item(name + "_dust");
        }
        for (String name : List.of("bismuth", "radium", "polonium", "tbp", "protactinium_233", "strontium_90",
                "molybdenum", "ruthenium_106", "caesium_137", "promethium_147", "europium_155")) {
            item(name + "_dust");
        }
        for (String name : List.of("calcium_sulfate", "crystal_binder", "energetic_blend", "sodium_fluoride",
                "potassium_fluoride", "sodium_hydroxide", "potassium_hydroxide", "borax", "irradiated_borax",
                "dimensional_blend", "c_mn_blend", "alugentum", "ammonium_sulfate", "ammonium_bisulfate",
                "ammonium_persulfate", "hydroquinone", "sodium_hydroquinone", "potassium_hydroquinone", "barium_sulfide",
                "silica", "plate_basic", "plate_advanced", "plate_du", "plate_elite", "wire_copper",
                "wire_magnesium_diboride", "bioplastic", "servo", "motor", "actuator", "chassis", "empty_frame",
                "steel_frame", "sic_fiber", "empty_sink", "pyrolytic_carbon", "sintered_steel", "sintered_zirconia",
                "polyethersulfone", "zirfon", "polytetrafluoroethene", "polydimethylsilylene",
                "polymethylsilylene_methylene", "empty_cooler", "empty_cooler_port")) {
            item(name);
        }
    }

    private static RegistryObject<Item> item(String id) {
        return item(id, new Item.Properties());
    }

    private static RegistryObject<Item> item(String id, Item.Properties properties) {
        var item = NCRegistries.ITEMS.register(id, () -> new Item(properties));
        MATERIALS.add(item);
        return item;
    }

    private static void metal(String name, float hardness, SoundType sound) {
        item(name + "_ingot");
        item(name + "_dust");
        block(name + "_block", BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK).strength(hardness, 6F).sound(sound));
    }

    private static void block(String id, BlockBehaviour.Properties properties) {
        RegistryObject<Block> block = NCRegistries.BLOCKS.register(id, () -> switch (id) {
            case "graphite_block" -> new Block(properties) {
                @Override
                public boolean isFireSource(BlockState state, LevelReader level, BlockPos pos, Direction side) {
                    return side == Direction.UP;
                }

                @Override
                public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
                    return 5;
                }

                @Override
                public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
                    return 5;
                }
            };
            case "glowing_mushroom" -> new BushBlock(properties) {
                @Override
                protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
                    return state.isFaceSturdy(level, pos, Direction.UP);
                }

                @Override
                public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
                    return GLOWING_MUSHROOM_SHAPE;
                }
            };
            default -> new Block(properties);
        });
        MATERIALS.add(NCRegistries.ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties())));
    }
}
