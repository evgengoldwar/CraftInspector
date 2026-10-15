package craftinspector.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import craftinspector.CraftInspector;
import io.netty.buffer.ByteBuf;

/**
 * Server to client: state of the "collect statistics" switch of the network the player is looking at.
 */
public class MsgState implements IMessage {

    public boolean enabled;
    public int records;

    public MsgState() {}

    public MsgState(final boolean enabled, final int records) {
        this.enabled = enabled;
        this.records = records;
    }

    @Override
    public void fromBytes(final ByteBuf buf) {
        this.enabled = buf.readBoolean();
        this.records = buf.readInt();
    }

    @Override
    public void toBytes(final ByteBuf buf) {
        buf.writeBoolean(this.enabled);
        buf.writeInt(this.records);
    }

    public static class Handler implements IMessageHandler<MsgState, IMessage> {

        @Override
        public IMessage onMessage(final MsgState message, final MessageContext ctx) {
            CraftInspector.proxy.onState(message.enabled, message.records);
            return null;
        }
    }
}
