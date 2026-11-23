package nc.init;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;

final class NCMaterials {
    private static final List<RegistryObject<Item>> MATERIALS = new ArrayList<>();

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
        block("soulless_sand", BlockBehaviour.Properties.copy(Blocks.SAND).strength(0.5F));
        block("soulless_sandstone", BlockBehaviour.Properties.copy(Blocks.SANDSTONE).strength(0.8F));
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
        var item = NCRegistries.ITEMS.register(id, () -> new Item(new Item.Properties()));
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
            default -> new Block(properties);
        });
        MATERIALS.add(NCRegistries.ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties())));
    }
}
