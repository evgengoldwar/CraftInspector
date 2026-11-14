package craftinspector.network;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import craftinspector.CraftInspector;

public final class CINetwork {

    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(CraftInspector.MODID);

    private CINetwork() {}

    public static void init() {
        CHANNEL.registerMessage(MsgAction.Handler.class, MsgAction.class, 0, Side.SERVER);
        CHANNEL.registerMessage(MsgState.Handler.class, MsgState.class, 1, Side.CLIENT);
        CHANNEL.registerMessage(MsgHistoryPart.Handler.class, MsgHistoryPart.class, 2, Side.CLIENT);
    }
}
