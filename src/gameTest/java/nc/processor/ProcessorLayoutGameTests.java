package nc.processor;

import com.google.gson.JsonObject;
import nc.NuclearCraft;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.GsonHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ProcessorLayoutGameTests {
    @GameTest(template = "empty")
    public static void scriptedLayoutRetainsLegacyCoordinates(GameTestHelper helper) {
        var standard = ProcessorLayout.read("qa_mixer", new JsonObject(), 2, 2, 2, 2, false);
        helper.assertTrue(standard.size().equals(new ProcessorLayout.Point(176, 189))
                        && standard.player().equals(new ProcessorLayout.Point(8, 107))
                        && standard.fluidOutputs().get(1).equals(new ProcessorLayout.Rect(131, 55, 16, 16)),
                "Existing scripted definitions should retain their generated positions");

        var configured = ProcessorLayout.read("glowstone_aggregator", GsonHelper.parse("""
                {
                  "gui_texture": "nuclearcraft:textures/gui/container/glowstone_aggregator.png",
                  "layout": {
                    "size": [176, 166],
                    "player": [8, 84],
                    "item_inputs": [[56, 35, 16, 16]],
                    "item_outputs": [[112, 31, 24, 24]],
                    "energy_upgrade": [146, 62, 20, 20],
                    "fluid_outputs": [[140, 31, 12, 24]],
                    "progress": [-1, -1, -1, -1, -1, -1]
                  },
                  "jei": {
                    "id": "nuclearcraft:legacy_glowstone",
                    "background": [50, 27, 107, 31],
                    "click_area": [73, 34, 38, 18]
                  }
                }
                """), 1, 1, 0, 1, true);
        var output = configured.itemOutputs().get(0);
        var tank = configured.fluidOutputs().get(0);
        helper.assertTrue(output.itemX() == 116 && output.itemY() == 35
                        && configured.energyUpgrade().itemX() == 148 && configured.energyUpgrade().itemY() == 64
                        && tank.width() == 12 && tank.height() == 24,
                "Large item wells should center their stacks and rectangular tanks should keep both dimensions");
        helper.assertTrue(configured.jei().id().equals(NuclearCraft.id("legacy_glowstone"))
                        && configured.jei().background().equals(new ProcessorLayout.Rect(50, 27, 107, 31))
                        && configured.jei().texture().equals(configured.texture())
                        && configured.jei().clickArea().contains(100, 40)
                        && !configured.progress().area().visible() && !configured.jei().tooltip().visible(),
                "A cropped category should inherit the GUI texture and use its configured click area with a disabled progress bar");
        helper.succeed();
    }
}
