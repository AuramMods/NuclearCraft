package nc.multiblock.turbine;

import nc.NuclearCraft;
import nc.multiblock.Cuboid;
import nc.multiblock.turbine.TurbinePartBlock.Part;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TurbineDynamoGameTests {
    @GameTest(template = "turbine")
    public static void exactDynamoRuleRechecksConnectedCoils(GameTestHelper helper) {
        var bounds = new Cuboid(BlockPos.ZERO, new BlockPos(4, 4, 4));
        var northBearing = new BlockPos(2, 2, 0);
        var southBearing = new BlockPos(2, 2, 4);
        var northCoil = northBearing.west();
        var southCoil = southBearing.east();
        var copper = northCoil.above();
        var beryllium = northCoil.below();
        var disconnected = southCoil.above().west();
        var bearings = Set.of(northBearing, southBearing);
        var installed = new LinkedHashMap<>(Map.of(northCoil, Part.MAGNESIUM, southCoil, Part.MAGNESIUM,
                copper, Part.COPPER, disconnected, Part.COPPER));
        var text = List.of("one bearing && exactly zero beryllium coils", "one magnesium coil",
                "two magnesium coils", "one aluminum coil", "one magnesium coil at least",
                "one gold coil && one copper coil");
        var rules = TurbineDynamoRules.compile(text, "one of any coil", text, "one of any coil",
                message -> { throw new AssertionError(message); });

        var valid = rules.active(bounds, bearings, installed);
        helper.assertTrue(valid.keySet().equals(Set.of(northCoil, southCoil, copper)),
                "Connected qualifying dynamos should activate from each bearing");
        installed.put(beryllium, Part.BERYLLIUM);
        helper.assertTrue(rules.active(bounds, bearings, installed).keySet().equals(Set.of(southCoil)),
                "An invalidated exact rule must also disconnect its dependents");
        helper.succeed();
    }
}
