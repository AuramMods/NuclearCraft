package nc.radiation;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import nc.NuclearCraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = NuclearCraft.MOD_ID)
public final class RadiationCommands {
    private static final String RADIATION = "radiation_level";
    private static final String RADIUS = "chunk_radius";
    private static final String PLAYER = "player";
    private static final String MESSAGE = "commands.nuclearcraft.radiation.";
    private static final DynamicCommandExceptionType INVALID_LEVEL = new DynamicCommandExceptionType(value ->
            Component.translatable(MESSAGE + "invalid_level", value));
    private static final DynamicCommandExceptionType TOO_MANY_CHUNKS = new DynamicCommandExceptionType(limit ->
            Component.translatable(MESSAGE + "too_many_chunks", limit));

    private RadiationCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        var dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("nc_set_chunk_radiation").requires(source -> source.hasPermission(2))
                .then(level().executes(context -> setChunk(context, sourceChunk(context.getSource())))
                        .then(Commands.argument("coord_x", IntegerArgumentType.integer())
                                .then(Commands.argument("coord_z", IntegerArgumentType.integer())
                                        .executes(context -> setChunk(context, new ChunkPos(
                                                IntegerArgumentType.getInteger(context, "coord_x") >> 4,
                                                IntegerArgumentType.getInteger(context, "coord_z") >> 4)))))));
        dispatcher.register(Commands.literal("nc_set_chunk_radius_radiation").requires(source -> source.hasPermission(2))
                .then(level().then(Commands.argument(RADIUS, IntegerArgumentType.integer(0))
                        .executes(RadiationCommands::setRadius))));
        dispatcher.register(Commands.literal("nc_set_world_radiation").requires(source -> source.hasPermission(2))
                .then(level().executes(RadiationCommands::setWorld)));
        dispatcher.register(Commands.literal("nc_set_player_radiation").requires(source -> source.hasPermission(2))
                .then(level().executes(context -> setPlayer(context, context.getSource().getPlayerOrException()))
                        .then(Commands.argument(PLAYER, EntityArgument.player())
                                .executes(context -> setPlayer(context, EntityArgument.getPlayer(context, PLAYER))))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> level() {
        // A word preserves scientific notation accepted by the original commands.
        return Commands.argument(RADIATION, StringArgumentType.word());
    }

    private static double value(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String word = StringArgumentType.getString(context, RADIATION);
        try {
            double value = Double.parseDouble(word);
            if (Double.isFinite(value)) {
                return Math.max(0, value);
            }
        } catch (NumberFormatException ignored) {
        }
        throw INVALID_LEVEL.create(word);
    }

    private static ChunkPos sourceChunk(CommandSourceStack source) {
        return new ChunkPos(BlockPos.containing(source.getPosition()));
    }

    private static int setChunk(CommandContext<CommandSourceStack> context, ChunkPos pos) throws CommandSyntaxException {
        double rate = value(context);
        WorldRadiation.setChunkRate(context.getSource().getLevel(), pos, rate);
        return reportChunks(context.getSource(), 1, rate);
    }

    private static int setRadius(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var source = context.getSource();
        double rate = value(context);
        int radius = IntegerArgumentType.getInteger(context, RADIUS);
        long width = 1 + 2L * radius;
        int limit = source.getLevel().getGameRules().getInt(GameRules.RULE_COMMAND_MODIFICATION_BLOCK_LIMIT);
        if (width > limit / width) {
            throw TOO_MANY_CHUNKS.create(limit);
        }
        int count = WorldRadiation.setRegionRate(source.getLevel(), sourceChunk(source), radius, rate);
        return reportChunks(source, count, rate);
    }

    private static int setWorld(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var source = context.getSource();
        double rate = value(context);
        return reportChunks(source, WorldRadiation.setLoadedRate(source.getLevel(), rate), rate);
    }

    private static int reportChunks(CommandSourceStack source, int count, double rate) {
        source.sendSuccess(() -> Component.translatable(MESSAGE + "chunks", count, Double.toString(rate)), true);
        return count;
    }

    private static int setPlayer(CommandContext<CommandSourceStack> context, ServerPlayer player) throws CommandSyntaxException {
        double dose = PlayerRadiation.setDose(player, value(context));
        context.getSource().sendSuccess(() -> Component.translatable(MESSAGE + "player", player.getDisplayName(),
                Double.toString(dose)), true);
        return 1;
    }
}
