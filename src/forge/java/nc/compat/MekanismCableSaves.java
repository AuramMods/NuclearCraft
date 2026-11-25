package nc.compat;

import nc.NuclearCraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Set;

@Mod.EventBusSubscriber(modid = NuclearCraft.MOD_ID)
public final class MekanismCableSaves {
    private static final Set<ResourceLocation> CABLE_TYPES = Set.of(
            new ResourceLocation("mekanism", "basic_universal_cable"),
            new ResourceLocation("mekanism", "advanced_universal_cable"),
            new ResourceLocation("mekanism", "elite_universal_cable"),
            new ResourceLocation("mekanism", "ultimate_universal_cable"));

    private MekanismCableSaves() {}

    @SubscribeEvent
    public static void saveBuffersOnUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel && event.getChunk() instanceof LevelChunk chunk && !chunk.isUnsaved()) {
            for (var tile : chunk.getBlockEntities().values()) {
                var type = ForgeRegistries.BLOCK_ENTITY_TYPES.getKey(tile.getType());
                if (type != null && CABLE_TYPES.contains(type)) {
                    // Saving the chunk takes a fresh snapshot even if its cable network changes during demotion.
                    chunk.setUnsaved(true);
                    return;
                }
            }
        }
    }
}
