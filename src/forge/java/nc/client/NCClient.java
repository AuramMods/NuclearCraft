package nc.client;

import nc.NuclearCraft;
import nc.init.NCHeatExchangers;
import nc.init.NCMachines;
import nc.init.NCFission;
import nc.init.NCItemProcessors.Machine;
import nc.init.NCPebbleFission;
import nc.init.NCSaltFission;
import nc.init.NCRegistries;
import nc.init.NCRadiation;
import nc.init.NCTurbine;
import nc.multiblock.fission.SaltFissionPartMenu;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = NuclearCraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class NCClient {
    private NCClient() {}

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "radiation", RadiationHud::render);
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(NCRegistries.MELTER_MENU.get(), MelterScreen::new);
            MenuScreens.register(NCRadiation.SCRUBBER_MENU.get(), ScrubberScreen::new);
            MenuScreens.register(NCRegistries.ELECTROLYZER_MENU.get(), FluidProcessorScreen::new);
            MenuScreens.register(NCRegistries.SALT_MIXER_MENU.get(), FluidProcessorScreen::new);
            MenuScreens.register(NCRegistries.CHEMICAL_REACTOR_MENU.get(), FluidProcessorScreen::new);
            MenuScreens.register(NCRegistries.ENRICHER_MENU.get(), FluidProcessorScreen::new);
            MenuScreens.register(NCRegistries.SUPERCOOLER_MENU.get(), FluidProcessorScreen::new);
            MenuScreens.register(NCRegistries.CENTRIFUGE_MENU.get(), FluidProcessorScreen::new);
            for (Machine machine : Machine.values()) {
                MenuScreens.register(machine.menu(), ItemProcessorScreen::new);
            }
            MenuScreens.register(NCFission.MENU.get(), SolidFissionScreen::new);
            MenuScreens.register(NCPebbleFission.MENU.get(), SolidFissionScreen::new);
            MenuScreens.register(NCSaltFission.MENU.get(), SolidFissionScreen::new);
            for (var kind : SaltFissionPartMenu.Kind.values()) {
                MenuScreens.register(NCSaltFission.partMenu(kind), SaltFissionPartScreen::new);
            }
            MenuScreens.register(NCTurbine.MENU.get(), TurbineScreen::new);
            MenuScreens.register(NCHeatExchangers.CONDENSER_MENU.get(), ExchangerScreen::new);
            MenuScreens.register(NCHeatExchangers.MENU.get(), ExchangerScreen::new);
            MenuScreens.register(NCMachines.MENU.get(), DecayPoolScreen::new);
            MenuScreens.register(NCMachines.DISTILLER_MENU.get(), DistillerScreen::new);
            MenuScreens.register(NCMachines.INFILTRATOR_MENU.get(), InfiltratorScreen::new);
            MenuScreens.register(NCMachines.ELECTROLYZER_MENU.get(), LargeElectrolyzerScreen::new);
            BlockEntityRenderers.register(NCMachines.POOL.get(), context -> new DecayPoolRenderer());
            BlockEntityRenderers.register(NCMachines.DISTILLER.get(), context -> new DistillerRenderer());
            BlockEntityRenderers.register(NCMachines.INFILTRATOR.get(), context -> new InfiltratorRenderer());
            BlockEntityRenderers.register(NCMachines.ELECTROLYZER.get(), context -> new LargeElectrolyzerRenderer());
        });
    }
}
