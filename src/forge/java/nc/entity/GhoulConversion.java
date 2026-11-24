package nc.entity;

import nc.NuclearCraft;
import nc.init.NCEntities;
import nc.init.NCWasteland;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Npc;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Mod.EventBusSubscriber(modid = NuclearCraft.MOD_ID)
public final class GhoulConversion {
    private static final ConcurrentMap<ServerLevel, Set<Mob>> PENDING = new ConcurrentHashMap<>();

    private GhoulConversion() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension().equals(NCWasteland.DIMENSION)
                && event.getEntity() instanceof Mob mob && mob instanceof Npc && NCEntities.ghoulEnabled(false)) {
            // Entity join can run while a chunk is still loading. Conversion needs a loaded chunk.
            PENDING.computeIfAbsent(level, ignored -> ConcurrentHashMap.newKeySet()).add(mob);
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        Set<Mob> pending = PENDING.get(level);
        if (pending == null || pending.isEmpty()) {
            return;
        }
        var iterator = pending.iterator();
        while (iterator.hasNext()) {
            Mob mob = iterator.next();
            if (!mob.isAlive() || mob.level() != level) {
                iterator.remove();
            } else if (level.getChunkSource().getChunkNow(mob.chunkPosition().x, mob.chunkPosition().z) != null) {
                iterator.remove();
                if (level.getEntity(mob.getUUID()) == mob) {
                    convert(mob);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            PENDING.remove(level);
        }
    }

    public static boolean convert(Mob mob) {
        if (!(mob instanceof Npc) || !mob.isAlive() || !(mob.level() instanceof ServerLevel level)
                || !NCEntities.ghoulEnabled(false)) {
            return false;
        }
        var type = NCEntities.FERAL_GHOUL.get();
        if (!ForgeEventFactory.canLivingConvert(mob, type, delay -> {})) {
            return false;
        }
        FeralGhoul ghoul = type.create(level);
        if (ghoul == null) {
            return false;
        }
        ghoul.copyPosition(mob);
        ForgeEventFactory.onFinalizeSpawn(ghoul, level, level.getCurrentDifficultyAt(mob.blockPosition()),
                MobSpawnType.CONVERSION, null, null);
        if (ghoul.isSpawnCancelled()) {
            return false;
        }
        ghoul.setNoAi(mob.isNoAi());
        ghoul.setCustomName(mob.getCustomName());
        ghoul.setCustomNameVisible(mob.isCustomNameVisible());
        if (mob.isPersistenceRequired()) {
            ghoul.setPersistenceRequired();
        }
        if (!level.addFreshEntity(ghoul)) {
            return false;
        }
        ForgeEventFactory.onLivingConvert(mob, ghoul);
        mob.discard();
        return true;
    }
}
