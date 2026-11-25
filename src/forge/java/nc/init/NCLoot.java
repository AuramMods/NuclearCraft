package nc.init;

import nc.NuclearCraft;
import nc.entity.FeralGhoul;
import nc.loot.OreBonusLoot;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public final class NCLoot {
    private static final List<String> FERAL_DUSTS = List.of("bismuth", "radium", "molybdenum");
    private static final List<String> GLOWING_DUSTS = List.of("polonium", "protactinium_233", "strontium_90",
            "ruthenium_106", "caesium_137", "promethium_147", "europium_155");
    private static final Set<ResourceLocation> DUNGEON_CHESTS = Set.of(
            BuiltInLootTables.SIMPLE_DUNGEON, BuiltInLootTables.VILLAGE_WEAPONSMITH,
            BuiltInLootTables.ABANDONED_MINESHAFT, BuiltInLootTables.STRONGHOLD_LIBRARY,
            BuiltInLootTables.STRONGHOLD_CROSSING, BuiltInLootTables.STRONGHOLD_CORRIDOR,
            BuiltInLootTables.WOODLAND_MANSION);
    private static final List<ChestDrop> DUNGEON_DROPS = List.of(
            new ChestDrop("dominos", 4, 2, 4), new ChestDrop("smore", 4, 2, 4),
            new ChestDrop("moresmore", 2, 1, 2), new ChestDrop("record_end_of_the_world", 1, 1, 1),
            new ChestDrop("record_money_for_nothing", 1, 1, 1), new ChestDrop("record_wanderer", 1, 1, 1),
            new ChestDrop("record_hyperspace", 1, 1, 1));
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    private static final ForgeConfigSpec.BooleanValue RARE_DROPS = BUILDER
            .comment("Allow monsters and NPCs to drop Dominos with a 1% chance.")
            .define("rare_drops", false);
    private static final ForgeConfigSpec.BooleanValue DUNGEON_LOOT = BUILDER
            .comment("Add NuclearCraft foods and records to the primary loot pools of legacy dungeon chests.")
            .define("dungeon_loot", false);
    private static final ForgeConfigSpec.ConfigValue<List<Boolean>> ORE_DROPS = BUILDER
            .comment("Enable legacy bonuses for vanilla ores.",
                    "Order: rhodochrosite, beryllium dust, zirconium dust, fluorite, sulfur dust, villiaumite, carobbiite.")
            .define("ore_drops", Collections.nCopies(7, false),
                    value -> value instanceof List<?> list && list.size() == 7
                            && list.stream().allMatch(Boolean.class::isInstance));
    private static final ForgeConfigSpec SPEC = BUILDER.build();

    private NCLoot() {}

    public static void register(FMLJavaModLoadingContext context) {
        context.registerConfig(ModConfig.Type.COMMON, SPEC, "nuclearcraft-loot.toml");
        NCRegistries.LOOT_MODIFIERS.register("ore_bonus", () -> OreBonusLoot.CODEC);
        MinecraftForge.EVENT_BUS.addListener(NCLoot::onLivingDrops);
        MinecraftForge.EVENT_BUS.addListener(NCLoot::onLootTableLoad);
    }

    public static boolean oreDropEnabled(int index) {
        return ORE_DROPS.get().get(index);
    }

    private static void onLivingDrops(LivingDropsEvent event) {
        var entity = event.getEntity();
        if (entity.level().isClientSide || !entity.level().getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)) {
            return;
        }
        var random = entity.getRandom();
        if (entity instanceof FeralGhoul) {
            boolean glowing = entity instanceof FeralGhoul.Glowing;
            int odds = glowing ? 25 : 50;
            for (String dust : glowing ? GLOWING_DUSTS : FERAL_DUSTS) {
                if (random.nextInt(odds) == 0) {
                    addDrop(event, dust + "_dust", 1 + random.nextInt(2));
                }
            }
        }
        if (RARE_DROPS.get() && (entity instanceof Monster || entity instanceof Npc) && random.nextInt(100) == 0) {
            addDrop(event, "dominos", 1);
        }
    }

    private static void addDrop(LivingDropsEvent event, String id, int count) {
        var entity = event.getEntity();
        var stack = new ItemStack(ForgeRegistries.ITEMS.getValue(NuclearCraft.id(id)), count);
        var drop = new ItemEntity(entity.level(), entity.getX(), entity.getY(), entity.getZ(), stack);
        drop.setDefaultPickUpDelay();
        event.getDrops().add(drop);
    }

    private static void onLootTableLoad(LootTableLoadEvent event) {
        if (!DUNGEON_LOOT.get() || !DUNGEON_CHESTS.contains(event.getName())) {
            return;
        }
        var table = event.getTable();
        var pool = table.getPool("main");
        if (pool == null) {
            var builder = LootPool.lootPool().name("main").setRolls(UniformGenerator.between(3, 8));
            DUNGEON_DROPS.forEach(drop -> builder.add(drop.entry()));
            table.addPool(builder.build());
            return;
        }

        // Add the new weights before loot is rolled and preserve the pool's conditions and roll count.
        int offset = pool.entries.length;
        var entries = Arrays.copyOf(pool.entries, offset + DUNGEON_DROPS.size());
        for (var drop : DUNGEON_DROPS) {
            entries[offset++] = drop.entry().build();
        }
        pool.entries = entries;
    }

    private record ChestDrop(String name, int weight, int minimum, int maximum) {
        LootPoolEntryContainer.Builder<?> entry() {
            var item = ForgeRegistries.ITEMS.getValue(NuclearCraft.id(name));
            var entry = LootItem.lootTableItem(item).setWeight(weight);
            if (minimum != maximum) {
                entry.apply(SetItemCountFunction.setCount(UniformGenerator.between(minimum, maximum)));
            }
            return entry;
        }
    }
}
