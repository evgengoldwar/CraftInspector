package craftinspector;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import craftinspector.network.CINetwork;
import craftinspector.network.MsgHistoryPart;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        Config.synchronizeConfiguration(event.getSuggestedConfigurationFile());
        CINetwork.init();
    }

    public void init(FMLInitializationEvent event) {}

    /** Server told the client the state of the "collect statistics" switch. Client only. */
    public void onState(boolean enabled, int records) {}

    /** Server sent a part of the order history. Client only. */
    public void onHistoryPart(MsgHistoryPart part) {}
}
