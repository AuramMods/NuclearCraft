package nc.compat.patchouli;

import nc.NuclearCraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.wrapper.PlayerMainInvWrapper;
import vazkii.patchouli.api.PatchouliAPI;

@Mod.EventBusSubscriber(modid = NuclearCraft.MOD_ID)
public final class Guidebook {
    private static final String GIVEN = "nuclearcraft:guide_given";
    private static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.BooleanValue GIVE_ON_JOIN;

    static {
        var builder = new ForgeConfigSpec.Builder();
        GIVE_ON_JOIN = builder.comment("Give one NuclearCraft guide per player when Patchouli is installed.")
                .define("give_guidebook", true);
        SPEC = builder.build();
    }

    private Guidebook() {}

    public static void register(FMLJavaModLoadingContext context) {
        context.registerConfig(ModConfig.Type.SERVER, SPEC, "nuclearcraft-misc.toml");
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            give(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player
                && player.tickCount % 20 == 0) {
            give(player);
        }
    }

    private static void give(ServerPlayer player) {
        if (!GIVE_ON_JOIN.get() || !ModList.get().isLoaded("patchouli")) {
            return;
        }
        var forgeData = player.getPersistentData();
        var persisted = forgeData.getCompound(Player.PERSISTED_NBT_TAG);
        if (persisted.getBoolean(GIVEN)) {
            return;
        }
        var guide = PatchouliAPI.get().getBookStack(NuclearCraft.id("guide"));
        if (!guide.isEmpty() && ItemHandlerHelper.insertItemStacked(new PlayerMainInvWrapper(player.getInventory()),
                guide, false).isEmpty()) {
            persisted.putBoolean(GIVEN, true);
            forgeData.put(Player.PERSISTED_NBT_TAG, persisted);
        }
    }
}
