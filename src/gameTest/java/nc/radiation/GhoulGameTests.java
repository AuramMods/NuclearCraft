package nc.radiation;

import com.mojang.authlib.GameProfile;
import nc.NuclearCraft;
import nc.entity.FeralGhoul;
import nc.init.NCEntities;
import nc.init.NCWasteland;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GhoulGameTests {
    private static final BlockPos CELL = new BlockPos(2, 2, 2);

    private GhoulGameTests() {}

    @GameTest(template = "empty")
    public static void ghoulAttacksBufferTheLegacyDoseAndPoisonTheTarget(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(CELL);
        var feral = NCEntities.FERAL_GHOUL.get().create(level);
        var glowing = NCEntities.GLOWING_GHOUL.get().create(level);
        helper.assertTrue(feral.getMaxHealth() == 16 && glowing.getMaxHealth() == 32
                        && feral.getAttributeBaseValue(Attributes.ARMOR) == 4
                        && glowing.getAttributeBaseValue(Attributes.ARMOR) == 8,
                "Each ghoul keeps its own legacy health and armor");
        feral.setBaby(true);
        helper.assertTrue(!feral.isBaby() && feral.canBreatheUnderwater(), "Ghouls stay adult and breathe underwater");

        var cow = EntityType.COW.create(level);
        feral.moveTo(pos, 0, 0);
        cow.moveTo(pos.east(), 0, 0);
        double scale = Mth.clamp(level.getCurrentDifficultyAt(pos).getEffectiveDifficulty(), 1F, 2F);
        helper.assertTrue(feral.doHurtTarget(cow) && cow.getHealth() == 8F && cow.hasEffect(MobEffects.POISON),
                "A feral ghoul deals two melee damage and applies poison");
        near(helper, MobRadiation.reading(cow).dose(), 0, "Radiation is buffered");
        var restored = EntityType.COW.create(level);
        restored.load(cow.saveWithoutId(new CompoundTag()));
        double feralDose = 10D * RadiationSources.CAESIUM_137 * scale;
        MobRadiation.tick(restored, 0, 30);
        near(helper, MobRadiation.reading(restored).dose(), feralDose / 2D, "Half the poison drains after a saved reload");
        MobRadiation.tick(restored, 0, 30);
        near(helper, MobRadiation.reading(restored).dose(),
                feralDose / 2D * Math.pow(1D - RadiationConfig.ENTITY_DECAY.get(), 30) + feralDose / 2D,
                "The rest drains over the legacy interval");

        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "GlowingGhoulTarget"));
        var playerPosition = pos.offset(65536, 0, 65536);
        player.moveTo(playerPosition, 0, 0);
        GhoulRadiation.poison(glowing, player, scale);
        PlayerRadiation.tick(player, 60);
        near(helper, PlayerRadiation.dose(player), 10D * RadiationSources.CALIFORNIUM_252 * scale
                        + 60D * WorldRadiation.rate(level, playerPosition),
                "The player absorbs the glowing ghoul's distinct radiation dose");
        MobRadiation.tick(glowing, 200D, 60);
        helper.assertTrue(glowing.isAlive() && glowing.hasEffect(MobEffects.MOVEMENT_SPEED),
                "Glowing ghouls gain buffs and survive fatal radiation");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 70)
    public static void feralGhoulSpawnsInSunlightAndGlowingGhoulNeedsIrradiatedDarkness(GameTestHelper helper) {
        var level = helper.getLevel();
        var open = new BlockPos(1, 165, 1);
        var dark = new BlockPos(4, 165, 4);
        helper.setBlock(open, Blocks.AIR);
        helper.setBlock(dark, Blocks.AIR);
        helper.setBlock(dark.above(), Blocks.AIR);
        helper.setBlock(open.below(), Blocks.STONE);
        for (BlockPos pos : BlockPos.betweenClosed(dark.offset(-1, -1, -1), dark.offset(1, 2, 1))) {
            if (!pos.equals(dark) && !pos.equals(dark.above())) {
                helper.setBlock(pos, Blocks.STONE);
            }
        }
        var exposed = helper.absolutePos(open);
        var shelter = helper.absolutePos(dark);
        var feral = NCEntities.FERAL_GHOUL.get();
        var glowing = NCEntities.GLOWING_GHOUL.get();
        WorldRadiation.addLeak(level, shelter, 4D);
        helper.succeedWhen(() -> {
            boolean outside = level.canSeeSky(exposed);
            boolean sheltered = !level.canSeeSky(shelter);
            int light = level.getMaxLocalRawBrightness(shelter);
            helper.assertTrue(outside && sheltered && light == 0, "The fixture has an exposed site and an unlit shelter "
                    + "(outside " + outside + ", sheltered " + sheltered + ", inside light " + light + ")");
            var random = RandomSource.create(51);
            helper.assertTrue(SpawnPlacements.checkSpawnRules(feral, level, MobSpawnType.NATURAL, exposed, random),
                    "A naturally spawned feral ghoul can appear in sunlight");
            helper.assertTrue(!SpawnPlacements.checkSpawnRules(glowing, level, MobSpawnType.NATURAL, exposed, random)
                    && SpawnPlacements.checkSpawnRules(glowing, level, MobSpawnType.NATURAL, shelter, random),
                    "Only a sheltered irradiated site can spawn glowing ghouls");
        });
    }

    @GameTest(template = "empty")
    public static void fatalRadiationConvertsVillagersAndPreservesTheirNames(GameTestHelper helper) {
        var level = helper.getLevel();
        var villager = helper.spawn(EntityType.VILLAGER, CELL);
        var name = Component.literal("Irradiated technician");
        villager.setCustomName(name);
        villager.setCustomNameVisible(true);
        villager.setNoAi(true);
        villager.setPersistenceRequired();
        var bounds = villager.getBoundingBox().inflate(2);
        MobRadiation.tick(villager, 1000D, 60D);
        var ghouls = level.getEntitiesOfClass(FeralGhoul.class, bounds,
                mob -> name.equals(mob.getCustomName()));
        helper.assertTrue(villager.isRemoved() && ghouls.size() == 1,
                "Fatal radiation converts an NPC once");
        var ghoul = ghouls.get(0);
        helper.assertTrue(ghoul.isNoAi() && ghoul.isCustomNameVisible() && ghoul.isPersistenceRequired()
                        && ghoul.getType() == NCEntities.FERAL_GHOUL.get(),
                "The replacement preserves the NPC's name, AI state and persistence");
        ghoul.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void npcsTurnIntoFeralGhoulsOnEnteringTheWasteland(GameTestHelper helper) {
        var wasteland = helper.getLevel().getServer().getLevel(NCWasteland.DIMENSION);
        var reference = helper.absolutePos(CELL);
        var column = new BlockPos(reference.getX() * 8, 0, reference.getZ() * 8);
        var landing = wasteland.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
        wasteland.getChunkSource().addRegionTicket(TicketType.PORTAL, new ChunkPos(landing), 3, landing);
        var villager = EntityType.VILLAGER.create(wasteland);
        var name = Component.literal("Wasteland visitor " + UUID.randomUUID());
        villager.moveTo(landing, 0, 0);
        villager.setCustomName(name);
        villager.setNoAi(true);
        helper.assertTrue(wasteland.addFreshEntity(villager), "The NPC enters the Wasteland");
        helper.succeedWhen(() -> {
            var converted = wasteland.getEntitiesOfClass(FeralGhoul.class, new AABB(landing).inflate(4),
                    mob -> name.equals(mob.getCustomName()));
            helper.assertTrue(villager.isRemoved() && converted.size() == 1,
                    "The Wasteland converts an NPC without a lethal dose");
            converted.get(0).discard();
        });
    }

    private static void near(GameTestHelper helper, double actual, double expected, String message) {
        helper.assertTrue(Math.abs(actual - expected) < 1E-8, message + " (" + actual + " vs " + expected + ")");
    }
}
