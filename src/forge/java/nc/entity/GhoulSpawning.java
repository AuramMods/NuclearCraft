package nc.entity;

import nc.NuclearCraft;
import nc.init.NCWasteland;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = NuclearCraft.MOD_ID)
public final class GhoulSpawning {
    private GhoulSpawning() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void checkPosition(MobSpawnEvent.PositionCheck event) {
        var mob = event.getEntity();
        var level = event.getLevel();
        if (mob instanceof FeralGhoul ghoul) {
            if (event.getSpawnType() != MobSpawnType.SPAWNER && level instanceof ServerLevel world
                    && chunkHasGhoul(world, ghoul)) {
                event.setResult(Event.Result.DENY);
            }
        } else if (!(mob instanceof Npc) && level.getLevel().dimension().equals(NCWasteland.DIMENSION)) {
            event.setResult(Event.Result.DENY);
        }
    }

    private static boolean chunkHasGhoul(ServerLevel level, FeralGhoul candidate) {
        var chunk = candidate.chunkPosition();
        var bounds = new AABB(chunk.getMinBlockX(), level.getMinBuildHeight(), chunk.getMinBlockZ(),
                chunk.getMaxBlockX() + 1, level.getMaxBuildHeight(), chunk.getMaxBlockZ() + 1);
        return !level.getEntitiesOfClass(FeralGhoul.class, bounds,
                ghoul -> ghoul != candidate && ghoul.chunkPosition().equals(chunk)).isEmpty();
    }
}
