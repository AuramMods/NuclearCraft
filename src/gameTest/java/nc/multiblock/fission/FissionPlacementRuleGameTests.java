package nc.multiblock.fission;

import nc.NuclearCraft;
import nc.init.NCSaltCoolants;
import nc.init.NCSaltFission;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static nc.init.NCSaltCoolants.COPPER;
import static nc.init.NCSaltCoolants.CRYOTHEUM;
import static nc.init.NCSaltCoolants.IRON;
import static nc.init.NCSaltCoolants.NAK;
import static nc.multiblock.fission.FissionPlacementRules.Part.CASING;
import static nc.multiblock.fission.FissionPlacementRules.Part.HEATER;
import static nc.multiblock.fission.FissionPlacementRules.Part.MODERATOR;
import static nc.multiblock.fission.FissionPlacementRules.Part.SINK;
import static nc.multiblock.fission.FissionPlacementRules.Part.VESSEL;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FissionPlacementRuleGameTests {
    @GameTest(template = "empty", batch = "placement_rules")
    public static void placementRulesRespectGeometryAndCountLimits(GameTestHelper helper) {
        var warnings = new ArrayList<String>();
        var rules = compile(Map.of(NAK,
                "exactly two axial moderators && at most one casing || three moderators at exactly one vertex"), warnings);
        helper.assertTrue(warnings.isEmpty(), "Both alternatives in the rule should parse");

        var pos = BlockPos.ZERO;
        var eligible = Map.of(NAK, List.of(pos));
        var moderators = new HashSet<>(Set.of(pos.north(), pos.south()));
        var casings = new HashSet<>(Set.of(pos.above()));
        var nearby = Map.of(MODERATOR, moderators, CASING, casings);
        helper.assertTrue(rules.active(eligible, (part, neighbor) -> nearby.get(part).contains(neighbor)).containsKey(pos),
                "Two opposing moderators and one casing should satisfy the first alternative");
        casings.add(pos.west());
        helper.assertTrue(rules.active(eligible, (part, neighbor) -> nearby.get(part).contains(neighbor)).isEmpty(),
                "A second casing should fail the at-most condition");
        casings.clear();
        moderators.add(pos.above());
        helper.assertTrue(rules.active(eligible, (part, neighbor) -> nearby.get(part).contains(neighbor)).isEmpty(),
                "An extra axial neighbor should fail exact count and two parallel faces cannot form a vertex");
        moderators.remove(pos.south());
        moderators.add(pos.east());
        helper.assertTrue(rules.active(eligible, (part, neighbor) -> nearby.get(part).contains(neighbor)).containsKey(pos),
                "Three neighboring axes should satisfy the vertex alternative");

        var edge = compile(Map.of(NAK, "exactly two moderators along an edge"), warnings);
        moderators.remove(pos.east());
        helper.assertTrue(edge.active(eligible, (part, neighbor) -> moderators.contains(neighbor)).containsKey(pos),
                "The edge rule should accept two perpendicular faces");
        moderators.remove(pos.above());
        moderators.add(pos.south());
        helper.assertTrue(edge.active(eligible, (part, neighbor) -> moderators.contains(neighbor)).isEmpty(),
                "The edge rule should reject opposing faces");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "placement_rules")
    public static void recursiveHeaterRulesNeedAnActiveNeighbor(GameTestHelper helper) {
        var warnings = new ArrayList<String>();
        var rules = compile(Map.of(NAK, "one copper heater || one vessel", COPPER, "one standard heater",
                CRYOTHEUM, "one any heater"), warnings);
        var pos = BlockPos.ZERO;
        var eligible = Map.of(NAK, List.of(pos), COPPER, List.of(pos.east()), CRYOTHEUM, List.of(pos.east(2)));
        helper.assertTrue(warnings.isEmpty(), "Positive dependencies should compile");
        helper.assertTrue(rules.active(eligible, (part, neighbor) -> false).isEmpty(),
                "A group must not activate merely by referencing itself");
        helper.assertTrue(rules.active(eligible, (part, neighbor) -> part == VESSEL && neighbor.equals(pos.west())).size() == 3,
                "A working vessel should seed the dependent heater group and activate the wildcard rule");

        var fallback = compile(Map.of(NAK, "exactly zero copper heaters", IRON, "two moderators"), warnings);
        helper.assertTrue(warnings.size() == 1 && fallback.text(NAK).equals("one vessel"),
                "An exact-count cycle should restore its custom rule's default and report why");
        helper.assertTrue(fallback.text(IRON).equals("two moderators"), "An unrelated override should stay in effect");

        warnings.clear();
        var invalid = compile(Map.of(NAK, "one unobtainium heater"), warnings);
        helper.assertTrue(warnings.size() == 1 && invalid.text(NAK).equals("one vessel"),
                "An unknown coolant should restore its default and report why");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "placement_rules")
    public static void invalidScriptedSinkDoesNotDisableStockCooling(GameTestHelper helper) {
        var warnings = new ArrayList<String>();
        var rules = FissionPlacementRules.compile(SINK, name -> name, Map.of("water", "one cell", "scripted", ""),
                Map.of("water", "one scripted sink", "scripted", "exactly zero water sinks"), warnings::add);
        var pos = BlockPos.ZERO;
        var active = rules.active(Map.of("water", List.of(pos), "scripted", List.of(pos.east())),
                (part, near) -> part == FissionPlacementRules.Part.CELL && near.equals(pos.west()));
        helper.assertTrue(warnings.size() == 2 && active.equals(Map.of(pos, "water")) && rules.text("scripted").isEmpty(),
                "An inhibitory cycle falls back to the stock rule and leaves the scripted sink inactive");
        helper.succeed();
    }

    private static FissionPlacementRules<NCSaltCoolants> compile(Map<NCSaltCoolants, String> overrides, List<String> warnings) {
        var defaults = new EnumMap<NCSaltCoolants, String>(NCSaltCoolants.class);
        for (var coolant : NCSaltCoolants.values()) {
            defaults.put(coolant, NCSaltFission.placementRules().text(coolant));
        }
        return FissionPlacementRules.compile(HEATER, NCSaltCoolants::material, defaults,
                overrides, warnings::add);
    }
}
