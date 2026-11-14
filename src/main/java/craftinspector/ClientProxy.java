package craftinspector;

import craftinspector.client.ClientHandler;
import craftinspector.network.MsgHistoryPart;

public class ClientProxy extends CommonProxy {

    @Override
    public void onState(boolean enabled, int records) {
        ClientHandler.onState(enabled, records);
    }

    @Override
    public void onHistoryPart(MsgHistoryPart part) {
        ClientHandler.onHistoryPart(part);
    }
}
