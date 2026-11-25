package nc.init;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import nc.NuclearCraft;
import nc.compat.tconstruct.TinkersHarvest;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.ModList;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

final class OreHarvest {
    static final int MAX_LEVEL = 15;

    private static final List<Tiers> VANILLA_TIERS = List.of(Tiers.WOOD, Tiers.STONE, Tiers.IRON, Tiers.DIAMOND, Tiers.NETHERITE);
    private static final int FIRST_EXTENDED_LEVEL = VANILLA_TIERS.size();
    private static final List<TagKey<Block>> EXTENDED_LEVELS = IntStream.rangeClosed(FIRST_EXTENDED_LEVEL, MAX_LEVEL)
            .mapToObj(level -> TagKey.create(Registries.BLOCK, NuclearCraft.id("needs_harvest_level/" + level)))
            .toList();

    private OreHarvest() {}

    static void register() {
        MinecraftForge.EVENT_BUS.addListener(OreHarvest::checkExtendedLevel);
    }

    static void addTags(Map<ResourceLocation, byte[]> resources) {
        var tags = new HashMap<TagKey<Block>, JsonArray>();
        for (var ore : NCOres.Ore.values()) {
            int level = ore.harvestLevel();
            if (level == 0) {
                continue;
            }
            var block = NuclearCraft.id(ore.getSerializedName() + "_ore").toString();
            var vanillaTier = VANILLA_TIERS.get(Math.min(level, FIRST_EXTENDED_LEVEL - 1));
            tags.computeIfAbsent(vanillaTier.getTag(), ignored -> new JsonArray()).add(block);
            if (level >= FIRST_EXTENDED_LEVEL) {
                var tag = EXTENDED_LEVELS.get(level - FIRST_EXTENDED_LEVEL);
                tags.computeIfAbsent(tag, ignored -> new JsonArray()).add(block);
            }
        }
        tags.forEach((tag, entries) -> {
            var id = tag.location();
            var json = new JsonObject();
            json.addProperty("replace", false);
            json.add("values", entries);
            resources.put(ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "tags/blocks/" + id.getPath() + ".json"),
                    json.toString().getBytes(StandardCharsets.UTF_8));
        });
    }

    private static void checkExtendedLevel(PlayerEvent.HarvestCheck event) {
        if (!event.canHarvest() || !event.getTargetBlock().is(Tiers.NETHERITE.getTag())) {
            return;
        }
        for (int index = EXTENDED_LEVELS.size() - 1; index >= 0; index--) {
            if (event.getTargetBlock().is(EXTENDED_LEVELS.get(index))) {
                event.setCanHarvest(toolLevel(event.getEntity().getMainHandItem()) >= FIRST_EXTENDED_LEVEL + index);
                return;
            }
        }
    }

    private static int toolLevel(ItemStack stack) {
        if (stack.getItem() instanceof TieredItem tool) {
            return tool.getTier().getLevel();
        }
        return ModList.get().isLoaded("tconstruct") ? TinkersHarvest.getLevel(stack) : 0;
    }
}
