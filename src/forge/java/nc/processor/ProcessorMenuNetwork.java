package nc.processor;

import nc.NuclearCraft;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ProcessorMenuNetwork {
    private static final String PROTOCOL = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(NuclearCraft.id("processor_menu"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private ProcessorMenuNetwork() {}

    public static void register() {
        CHANNEL.messageBuilder(Button.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder((button, buffer) -> {
                    buffer.writeVarInt(button.container());
                    buffer.writeVarInt(button.action());
                })
                .decoder(buffer -> new Button(buffer.readVarInt(), buffer.readVarInt()))
                .consumerMainThread((button, context) -> {
                    var player = context.get().getSender();
                    if (player != null && player.containerMenu instanceof ItemProcessorMenu menu
                            && menu.machine().scripted() && menu.containerId == button.container()) {
                        menu.clickMenuButton(player, button.action());
                    }
                }).add();
    }

    public static void click(int container, int action) {
        CHANNEL.sendToServer(new Button(container, action));
    }

    private record Button(int container, int action) {}
}
