package nc.worldgen;

import nc.NuclearCraft;
import nc.init.NCOres.Ore;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.placement.CountPlacement;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OreWorldgenGameTests {
    private OreWorldgenGameTests() {}

    @GameTest(template = "empty")
    public static void stockOresExposeVanillaWorldgenData(GameTestHelper helper) {
        var placedFeatures = helper.getLevel().registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
        for (Ore ore : Ore.values()) {
            var id = NuclearCraft.id("ore_" + ore.getSerializedName());
            var placed = placedFeatures.getOrThrow(ResourceKey.create(Registries.PLACED_FEATURE, id));
            var configured = placed.feature();
            helper.assertTrue(configured.is(ResourceKey.create(Registries.CONFIGURED_FEATURE, id)),
                    id + " uses its registered configured feature");
            helper.assertTrue(configured.value().config() instanceof OreConfiguration config && config.size == ore.size(),
                    id + " exposes its configured vein size as a vanilla ore feature");
            helper.assertTrue(placed.placement().stream().anyMatch(CountPlacement.class::isInstance),
                    id + " exposes its attempt count as a vanilla placement modifier");
            helper.assertTrue(placed.placement().stream().anyMatch(HeightRangePlacement.class::isInstance),
                    id + " exposes its height range as a vanilla placement modifier");
        }
        helper.succeed();
    }
}
