package nc.init;

import nc.automation.AutomationBlock;
import nc.automation.MachineInterface;
import nc.automation.UniversalBin;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class NCAutomation {
    public static final RegistryObject<AutomationBlock> BIN = block("bin", false);
    public static final RegistryObject<AutomationBlock> MACHINE_INTERFACE = block("machine_interface", true);
    public static final RegistryObject<BlockEntityType<UniversalBin>> BIN_ENTITY =
            NCRegistries.BLOCK_ENTITIES.register("bin", () -> BlockEntityType.Builder.of(UniversalBin::new, BIN.get()).build(null));
    public static final RegistryObject<BlockEntityType<MachineInterface>> INTERFACE_ENTITY =
            NCRegistries.BLOCK_ENTITIES.register("machine_interface",
                    () -> BlockEntityType.Builder.of(MachineInterface::new, MACHINE_INTERFACE.get()).build(null));

    private NCAutomation() {}

    static void register() {}

    static void displayItems(CreativeModeTab.Output output) {
        output.accept(MACHINE_INTERFACE.get());
        output.accept(BIN.get());
    }

    private static RegistryObject<AutomationBlock> block(String id, boolean forwards) {
        var block = NCRegistries.BLOCKS.register(id,
                () -> new AutomationBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK), forwards));
        NCRegistries.ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties()) {
            @Override
            public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
                tooltip.add(Component.translatable("tooltip.nuclearcraft." + id));
            }
        });
        return block;
    }
}
