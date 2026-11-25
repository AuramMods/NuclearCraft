package nc.gametest;

import nc.NuclearCraft;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Set;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AdvancementGameTests {
    @GameTest(template = "empty")
    public static void legacyAdvancementTreeLoadsWithOriginalAssemblyCriteria(GameTestHelper helper) {
        var manager = helper.getLevel().getServer().getAdvancements();
        long count = manager.getAllAdvancements().stream()
                .filter(advancement -> advancement.getId().getNamespace().equals(NuclearCraft.MOD_ID))
                .count();
        helper.assertTrue(count == 65, "All 65 NuclearCraft advancements load");

        var assemblyGoals = List.of(
                "large_machines/electrolyzer_assembled", "large_machines/distiller_assembled",
                "large_machines/infiltrator_assembled", "large_machines/decay_pool_assembled",
                "fission/pebble_fission_reactor_assembled", "fission/solid_fission_reactor_assembled",
                "fission/salt_fission_reactor_assembled", "heat_exchanger/heat_exchanger_assembled",
                "heat_exchanger/condenser_assembled", "turbine/turbine_assembled");
        for (String path : assemblyGoals) {
            var advancement = manager.getAdvancement(NuclearCraft.id(path));
            String criterion = path.substring(path.indexOf('/') + 1);
            helper.assertTrue(advancement != null && advancement.getParent() != null
                            && advancement.getCriteria().keySet().equals(Set.of(criterion))
                            && advancement.getCriteria().get(criterion).getTrigger().getCriterion().equals(NuclearCraft.id(criterion)),
                    "Original advancement and assembly criterion load for " + path);
        }
        helper.succeed();
    }
}
