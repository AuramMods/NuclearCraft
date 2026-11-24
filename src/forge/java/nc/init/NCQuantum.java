package nc.init;

import nc.multiblock.quantum.QuantumBlock;
import nc.multiblock.quantum.QuantumConfig;
import nc.multiblock.quantum.QuantumPart;
import nc.multiblock.quantum.QuantumPartEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class NCQuantum {
    private static final Map<QuantumPart, RegistryObject<QuantumBlock>> BLOCKS = new EnumMap<>(QuantumPart.class);
    private static final Map<QuantumPart, RegistryObject<Item>> ITEMS = new EnumMap<>(QuantumPart.class);

    static {
        for (QuantumPart part : QuantumPart.values()) {
            RegistryObject<QuantumBlock> block = NCRegistries.BLOCKS.register(part.id(), () -> {
                var properties = BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                        .strength(2F, 15F).sound(SoundType.METAL).requiresCorrectToolForDrops();
                if (part.isQubit()) {
                    properties.noOcclusion();
                }
                return new QuantumBlock(properties, part);
            });
            BLOCKS.put(part, block);
            ITEMS.put(part, NCRegistries.ITEMS.register(part.id(), () -> new BlockItem(block.get(), new Item.Properties()) {
                @Override
                public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flags) {
                    if (part.isGate() || part == QuantumPart.QASM || part == QuantumPart.QISKIT) {
                        lines.add(Component.translatable(getDescriptionId() + ".desc"));
                    }
                }
            }));
        }
    }

    public static final RegistryObject<BlockEntityType<QuantumPartEntity>> PART_ENTITY =
            NCRegistries.BLOCK_ENTITIES.register("quantum_computer_part", () ->
                    BlockEntityType.Builder.of(QuantumPartEntity::new,
                            BLOCKS.values().stream().map(RegistryObject::get).toArray(Block[]::new)).build(null));

    private static final RegistryObject<CreativeModeTab> TAB = NCRegistries.TABS.register("quantum", () ->
            CreativeModeTab.builder().title(Component.translatable("itemGroup.nuclearcraft.quantum"))
                    .icon(() -> new ItemStack(ITEMS.get(QuantumPart.QUBIT).get()))
                    .displayItems((parameters, display) -> {
                        if (QuantumConfig.enabled()) {
                            ITEMS.values().forEach(item -> display.accept(item.get()));
                        }
                    }).build());

    private NCQuantum() {}

    public static void register(FMLJavaModLoadingContext context) {
        QuantumConfig.register(context);
    }

    public static QuantumBlock block(QuantumPart part) {
        return BLOCKS.get(part).get();
    }
}
