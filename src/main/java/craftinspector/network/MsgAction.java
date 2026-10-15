package craftinspector.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Client to server: a request issued from the crafting status GUI.
 */
public class MsgAction implements IMessage {

    /** Ask for the current state of the "collect statistics" switch. */
    public static final byte STATE = 0;
    /** Flip the "collect statistics" switch. */
    public static final byte TOGGLE = 1;
    /** Ask for the order history. */
    public static final byte HISTORY = 2;
    /** The history screen was closed, re-send the crafting CPU contents. */
    public static final byte RESYNC = 3;
    /** Clear the order history. */
    public static final byte CLEAR = 4;

    public byte action;

    public MsgAction() {}

    public MsgAction(final byte action) {
        this.action = action;
    }

    @Override
    public void fromBytes(final ByteBuf buf) {
        this.action = buf.readByte();
    }

    @Override
    public void toBytes(final ByteBuf buf) {
        buf.writeByte(this.action);
    }

    public static class Handler implements IMessageHandler<MsgAction, IMessage> {

        @Override
        public IMessage onMessage(final MsgAction message, final MessageContext ctx) {
            ServerHandler.handle(ctx.getServerHandler().playerEntity, message.action);
            return null;
        }
    }
}
