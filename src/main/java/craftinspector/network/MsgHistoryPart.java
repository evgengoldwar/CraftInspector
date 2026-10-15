package craftinspector.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import craftinspector.CraftInspector;
import io.netty.buffer.ByteBuf;

/**
 * Server to client: one part of the compressed order history. The history can be large, so it is split into parts
 * that each fit into a single vanilla custom payload packet.
 */
public class MsgHistoryPart implements IMessage {

    public static final int PART_SIZE = 30000;

    public int transfer;
    public int part;
    public int total;
    public byte[] data;

    public MsgHistoryPart() {}

    public MsgHistoryPart(final int transfer, final int part, final int total, final byte[] data) {
        this.transfer = transfer;
        this.part = part;
        this.total = total;
        this.data = data;
    }

    @Override
    public void fromBytes(final ByteBuf buf) {
        this.transfer = buf.readInt();
        this.part = buf.readInt();
        this.total = buf.readInt();
        this.data = new byte[buf.readInt()];
        buf.readBytes(this.data);
    }

    @Override
    public void toBytes(final ByteBuf buf) {
        buf.writeInt(this.transfer);
        buf.writeInt(this.part);
        buf.writeInt(this.total);
        buf.writeInt(this.data.length);
        buf.writeBytes(this.data);
    }

    public static class Handler implements IMessageHandler<MsgHistoryPart, IMessage> {

        @Override
        public IMessage onMessage(final MsgHistoryPart message, final MessageContext ctx) {
            CraftInspector.proxy.onHistoryPart(message);
            return null;
        }
    }
}
