package nc.init;

import nc.NuclearCraft;
import nc.item.PortableEnderChestItem;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.RecordItem;
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

    private static final RegistryObject<CreativeModeTab> TAB = NCClientPresentation.category("materials",
            () -> SULFUR.get().getDefaultInstance(),
            (parameters, output) -> MATERIALS.forEach(item -> output.accept(item.get())));

    private NCMaterials() {}

    static void register() {
        for (String name : List.of("copper", "tin", "lead", "thorium", "uranium", "boron", "lithium", "magnesium")) {
            block(name + "_ore", BlockBehaviour.Properties.copy(Blocks.IRON_ORE));
        }
        MATERIALS.add(NCWasteland.GLOWING_MUSHROOM_ITEM);
        MATERIALS.add(NCWasteland.GLOWING_MUSHROOM_BLOCK_ITEM);
        block("soulless_sand", BlockBehaviour.Properties.copy(Blocks.SAND).strength(0.5F));
        block("soulless_sandstone", BlockBehaviour.Properties.copy(Blocks.SANDSTONE).strength(0.8F));
        block("molybdenum_block", BlockBehaviour.Properties.copy(Blocks.STONE).strength(5F));
        block("copper_oxide_block", BlockBehaviour.Properties.copy(Blocks.STONE).strength(5F));
        for (String metal : List.of("cobalt", "nickel", "platinum")) {
            block(metal + "_block", BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK).strength(5F, 15F));
        }
        block("tritium_lamp", BlockBehaviour.Properties.copy(Blocks.GLASS).strength(3F, 15F)
                .requiresCorrectToolForDrops().lightLevel(state -> 15).noOcclusion());
        MATERIALS.add(NCRegistries.ITEMS.register("portable_ender_chest",
                () -> new PortableEnderChestItem(new Item.Properties())));
        musicDisc("wanderer", 1, 3323);
        musicDisc("end_of_the_world", 2, 3587);
        musicDisc("money_for_nothing", 3, 6294);
        musicDisc("hyperspace", 4, 3687);
        food("dominos", 16, 1.8F, true, 600, 1, true, MobEffects.MOVEMENT_SPEED, MobEffects.DIG_SPEED);
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
        food("smore", 8, 1.4F, false, 300, 1, true,
                MobEffects.MOVEMENT_SPEED, MobEffects.DIG_SPEED, MobEffects.ABSORPTION);
        food("moresmore", 20, 3.8F, false, 300, 2, true,
                MobEffects.MOVEMENT_SPEED, MobEffects.DIG_SPEED, MobEffects.ABSORPTION);
        food("foursmore", 48, 8.6F, false, 300, 3, false,
                MobEffects.MOVEMENT_SPEED, MobEffects.DIG_SPEED, MobEffects.ABSORPTION);
        food("fish_a_la_fred", 6, 0.8F, false, 300, 1, false,
                MobEffects.WATER_BREATHING, MobEffects.NIGHT_VISION, MobEffects.HUNGER);
        food("pikelet", 6, 1.2F, false, 300, 0, true, MobEffects.MOVEMENT_SPEED, MobEffects.DIG_SPEED, MobEffects.WATER_BREATHING);
        food("congress_tart", 8, 4.4F, false, 300, 1, true, MobEffects.DAMAGE_BOOST, MobEffects.FIRE_RESISTANCE, MobEffects.ABSORPTION);
        food("ricecake", 2, 0.2F, false, 300, 0, true, MobEffects.DAMAGE_RESISTANCE);
        food("fish_and_ricecake", 10, 2.4F, true, 300, 1, false, MobEffects.DAMAGE_BOOST, MobEffects.REGENERATION, MobEffects.DAMAGE_RESISTANCE);
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

    private static void food(String id, int nutrition, float saturation, boolean meat, int ticks, int amplifier,
                             boolean display, MobEffect... effects) {
        var food = new FoodProperties.Builder().nutrition(nutrition).saturationMod(saturation);
        if (meat) {
            food.meat();
        }
        for (MobEffect effect : effects) {
            food.effect(() -> new MobEffectInstance(effect, ticks, amplifier, false, false), 1F);
        }
        var properties = new Item.Properties().food(food.build());
        var item = NCRegistries.ITEMS.register(id, () -> new Item(properties));
        if (display) {
            MATERIALS.add(item);
        }
    }

    private static void musicDisc(String name, int signal, int duration) {
        String soundName = "music.nc." + name;
        var sound = NCRegistries.SOUNDS.register(soundName,
                () -> SoundEvent.createVariableRangeEvent(NuclearCraft.id(soundName)));
        MATERIALS.add(NCRegistries.ITEMS.register("record_" + name,
                () -> new RecordItem(signal, sound, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC), duration)));
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
            default -> new Block(properties);
        });
        MATERIALS.add(NCRegistries.ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties())));
    }
}
