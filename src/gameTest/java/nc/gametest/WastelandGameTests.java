package nc.gametest;

import nc.NuclearCraft;
import nc.init.NCWasteland;
import nc.worldgen.WastelandChunkGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.Set;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WastelandGameTests {
    private WastelandGameTests() {}

    @GameTest(template = "empty")
    public static void wastelandUsesItsDimensionTypeAndLegacyBiomes(GameTestHelper helper) {
        var level = helper.getLevel().getServer().getLevel(NCWasteland.DIMENSION);
        helper.assertTrue(level != null, "The Wasteland datapack creates a server dimension");
        var generator = level.getChunkSource().getGenerator();
        helper.assertTrue(generator instanceof WastelandChunkGenerator && generator.getSeaLevel() == 63,
                "The Wasteland uses its own generator at legacy sea level");
        helper.assertTrue(level.dimensionType().coordinateScale() == 0.125D && !level.dimensionType().natural()
                && !level.dimensionType().bedWorks() && level.dimensionType().hasSkyLight()
                && level.getMinBuildHeight() == 0 && level.getMaxBuildHeight() == 256,
                "The dimension type preserves the legacy travel scale, skylight, and height");
        var source = generator.getBiomeSource();
        var sampler = level.getChunkSource().randomState().sampler();
        Set<ResourceLocation> observed = new HashSet<>();
        for (int x = -180; x <= 180; x += 13) {
            for (int z = -180; z <= 180; z += 13) {
                var low = source.getNoiseBiome(x, 5, z, sampler);
                var high = source.getNoiseBiome(x, 48, z, sampler);
                helper.assertTrue(low.equals(high), "A Wasteland biome occupies the whole column");
                observed.add(low.unwrapKey().orElseThrow().location());
            }
        }
        helper.assertTrue(observed.equals(Set.of(NCWasteland.BIOME.location(), Biomes.DESERT.location(),
                Biomes.SAVANNA.location(), Biomes.FOREST.location(), Biomes.SWAMP.location(),
                Biomes.OCEAN.location(), Biomes.STONY_SHORE.location())),
                "The seeded map yields all seven legacy biomes");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void wastelandPortalMovesAnEntityToDryGroundAndBack(GameTestHelper helper) {
        var overworld = helper.getLevel();
        var wasteland = overworld.getServer().getLevel(NCWasteland.DIMENSION);
        var relative = new BlockPos(2, 1, 2);
        var absolute = helper.absolutePos(relative);
        var portal = NCWasteland.PORTAL.get();
        helper.setBlock(relative, portal.defaultBlockState());
        var cow = helper.spawn(EntityType.COW, relative.above());
        cow.setPos(absolute.getX() + 0.5D, absolute.getY() + 1.125D, absolute.getZ() + 0.5D);
        var id = cow.getUUID();
        cow.move(MoverType.SELF, new Vec3(0, -0.25D, 0));
        helper.assertTrue(cow.isRemoved(), "A physical portal collision changed dimensions");
        helper.startSequence().thenWaitUntil(() -> {
            var traveler = wasteland.getEntity(id);
            helper.assertTrue(traveler != null, "The destination begins tracking the transferred entity");
        }).thenExecute(() -> {
            var traveler = wasteland.getEntity(id);
            var landing = traveler.blockPosition();
            helper.assertTrue(wasteland.getFluidState(landing).isEmpty()
                            && wasteland.getFluidState(landing.below()).isEmpty()
                            && !wasteland.getBlockState(landing.below()).is(portal),
                    "The portal lands the entity on a solid dry surface");
            helper.assertTrue(Math.abs(landing.getX() - (absolute.getX() + 0.5D) * 8D) <= 7D
                    && Math.abs(landing.getZ() - (absolute.getZ() + 0.5D) * 8D) <= 7D,
                    "The Wasteland scales horizontal coordinates");
            var before = wasteland.getBlockState(landing);
            wasteland.setBlockAndUpdate(landing, portal.defaultBlockState());
            portal.entityInside(portal.defaultBlockState(), wasteland, landing, traveler);
            helper.assertTrue(wasteland.getEntity(id) == traveler, "A cooldown protects travelers from looping");
            traveler.setPortalCooldown(0);
            portal.entityInside(portal.defaultBlockState(), wasteland, landing, traveler);
            helper.assertTrue(traveler.isRemoved(), "The return portal changed dimensions");
            wasteland.setBlockAndUpdate(landing, before);
        }).thenWaitUntil(() -> {
            var returned = overworld.getEntity(id);
            helper.assertTrue(returned != null, "The same entity returned to the Overworld");
            returned.discard();
        }).thenSucceed();
    }
}
