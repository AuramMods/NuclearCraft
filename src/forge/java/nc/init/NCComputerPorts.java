package nc.init;

import nc.multiblock.computer.ComputerPort;
import nc.multiblock.computer.ComputerPeripherals;
import nc.multiblock.fission.FissionPartBlock;
import nc.multiblock.hx.ExchangerPartBlock;
import nc.multiblock.machine.MachinePartBlock;
import nc.multiblock.turbine.TurbinePartBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

public final class NCComputerPorts {
    public static final RegistryObject<Block> FISSION = NCRegistries.BLOCKS.register(ComputerPort.Kind.FISSION.id(),
            () -> new FissionPartBlock.Computer(metal()));
    public static final RegistryObject<Block> EXCHANGER = NCRegistries.BLOCKS.register(ComputerPort.Kind.EXCHANGER.id(),
            () -> new ExchangerPartBlock.Computer(metal()));
    public static final RegistryObject<Block> TURBINE = NCRegistries.BLOCKS.register(ComputerPort.Kind.TURBINE.id(),
            () -> new TurbinePartBlock.Computer(metal()));

    public static final RegistryObject<BlockEntityType<ComputerPort>> BLOCK_ENTITY =
            NCRegistries.BLOCK_ENTITIES.register("computer_port",
                    () -> BlockEntityType.Builder.of(ComputerPort::new, FISSION.get(), EXCHANGER.get(), TURBINE.get(),
                            NCMachines.block(MachinePartBlock.Part.COMPUTER)).build(null));

    static {
        NCRegistries.ITEMS.register(ComputerPort.Kind.FISSION.id(), () -> new BlockItem(FISSION.get(), new Item.Properties()));
        NCRegistries.ITEMS.register(ComputerPort.Kind.EXCHANGER.id(), () -> new BlockItem(EXCHANGER.get(), new Item.Properties()));
        NCRegistries.ITEMS.register(ComputerPort.Kind.TURBINE.id(), () -> new BlockItem(TURBINE.get(), new Item.Properties()));
    }

    private NCComputerPorts() {}

    public static void register(FMLJavaModLoadingContext context) {
        context.getModEventBus().addListener(NCComputerPorts::setup);
    }

    private static void setup(FMLCommonSetupEvent event) {
        if (ModList.get().isLoaded("computercraft")) {
            event.enqueueWork(ComputerPeripherals::register);
        }
    }

    public static @Nullable ComputerPort.Kind kind(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof FissionPartBlock.Computer) return ComputerPort.Kind.FISSION;
        if (block instanceof ExchangerPartBlock.Computer) return ComputerPort.Kind.EXCHANGER;
        if (block instanceof TurbinePartBlock.Computer) return ComputerPort.Kind.TURBINE;
        if (block instanceof MachinePartBlock.Computer) return ComputerPort.Kind.MACHINE;
        return null;
    }

    static void displayItems(CreativeModeTab.Output output) {
        output.accept(FISSION.get());
        output.accept(EXCHANGER.get());
        output.accept(TURBINE.get());
    }

    private static BlockBehaviour.Properties metal() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2F, 15F)
                .sound(SoundType.METAL).requiresCorrectToolForDrops();
    }
}
