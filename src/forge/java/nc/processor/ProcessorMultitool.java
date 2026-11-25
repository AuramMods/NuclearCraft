package nc.processor;

import nc.NuclearCraft;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public final class ProcessorMultitool {
    private static final String TOOL = "ncMultitool";
    private static final String CONFIG = "processorConfig";

    private ProcessorMultitool() {}

    public static InteractionResult useOn(ItemStack tool, UseOnContext context) {
        var level = context.getLevel();
        var pos = context.getClickedPos();
        var player = context.getPlayer();
        if (player == null || !(level.getBlockEntity(pos) instanceof UpgradableProcessorBlockEntity processor)
                || !processor.allowsSettingsCopy() || !processor.getBlockState().hasProperty(ProcessorBlock.FACING)) {
            return InteractionResult.PASS;
        }
        var toolData = tool.getTagElement(TOOL);
        boolean copying = player.isShiftKeyDown();
        if (!copying && (toolData == null || !toolData.contains(CONFIG, Tag.TAG_COMPOUND))) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        var block = processor.getBlockState().getBlock();
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
        if (id == null) {
            return InteractionResult.PASS;
        }
        Direction front = processor.getBlockState().getValue(ProcessorBlock.FACING);
        ProcessorItemPorts items = processor.itemPorts();
        ProcessorFluidTanks fluids = processor.fluidTanks();
        int[] shape = {items.inputs(), items.slots(), fluids == null ? 0 : fluids.inputs(), fluids == null ? 0 : fluids.size()};
        if (copying) {
            var config = new CompoundTag();
            config.putString("machine", id.toString());
            config.putIntArray("shape", shape);
            config.putBoolean("redstoneControl", processor.redstoneControl());
            var itemSettings = new CompoundTag();
            items.saveSettings(itemSettings, front);
            config.put("items", itemSettings);
            if (fluids != null) {
                var fluidSettings = new CompoundTag();
                fluids.saveSettings(fluidSettings, front);
                config.put("fluids", fluidSettings);
            }
            tool.getOrCreateTagElement(TOOL).put(CONFIG, config);
            message(player, "save_processor_config", block.getName());
        } else {
            var config = toolData.getCompound(CONFIG);
            boolean legacy = !config.contains("machine", Tag.TAG_STRING);
            String source = config.getString(legacy ? "infoName" : "machine");
            if (!matches(id, source, config, shape, legacy)) {
                String displayName = config.getString("displayName");
                message(player, "invalid_processor_config", displayName.isEmpty() ? source : displayName, block.getName());
                return InteractionResult.CONSUME;
            }
            int changed = legacy ? items.loadLegacySettings(config.getCompound("inventoryConnections"),
                    config.getCompound("slotSettings"), front) : items.loadSettings(config.getCompound("items"), front);
            if (fluids != null) {
                if (legacy) {
                    fluids.loadLegacySettings(config.getCompound("fluidConnections"), config.getCompound("tankSettings"), front);
                } else {
                    fluids.loadSettings(config.getCompound("fluids"), front);
                }
            }
            if (config.contains("redstoneControl", Tag.TAG_ANY_NUMERIC)) {
                processor.setRedstoneControl(config.getBoolean("redstoneControl"));
            }
            if (fluids != null) {
                fluids.refreshFaces();
            }
            items.refreshFaces(changed);
            processor.setChanged();
            level.updateNeighborsAt(pos, block);
            message(player, "load_processor_config", block.getName());
        }
        play(player);
        return InteractionResult.CONSUME;
    }

    private static boolean matches(ResourceLocation id, String source, CompoundTag data, int[] shape, boolean legacy) {
        if (legacy) {
            return id.toString().equals(source) || (id.getNamespace().equals(NuclearCraft.MOD_ID) && id.getPath().equals(source));
        }
        return id.toString().equals(source) && Arrays.equals(shape, data.getIntArray("shape"))
                && data.contains("items", Tag.TAG_COMPOUND)
                && (shape[3] == 0 || data.contains("fluids", Tag.TAG_COMPOUND));
    }

    static Direction side(Direction relative, @Nullable Direction front) {
        if (front == null) {
            return relative;
        }
        // The legacy profile stores down, up, left, right, front, and back in this order.
        return switch (relative) {
            case DOWN, UP -> relative;
            case NORTH -> front.getClockWise();
            case SOUTH -> front.getCounterClockWise();
            case WEST -> front;
            case EAST -> front.getOpposite();
        };
    }

    public static boolean clear(Player player, ItemStack tool) {
        var data = tool.getTagElement(TOOL);
        if (data == null || !data.contains(CONFIG)) {
            return false;
        }
        data.remove(CONFIG);
        message(player, "clear_processor_config");
        play(player);
        return true;
    }

    private static void message(Player player, String key, Object... arguments) {
        player.displayClientMessage(Component.translatable("info.nuclearcraft.multitool." + key, arguments), false);
    }

    private static void play(Player player) {
        player.level().playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5F, 1F);
    }
}
