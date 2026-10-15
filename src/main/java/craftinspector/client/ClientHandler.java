package craftinspector.client;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import craftinspector.CraftInspector;
import craftinspector.client.gui.GuiCraftHistory;
import craftinspector.client.gui.ICraftStatusGui;
import craftinspector.history.CraftRecord;
import craftinspector.network.CINetwork;
import craftinspector.network.MsgAction;
import craftinspector.network.MsgHistoryPart;

public final class ClientHandler {

    private static int transfer = -1;
    private static byte[][] parts;
    private static int received;
    /** Set when the player clicked the history button, so the answer opens the history screen. */
    private static boolean openRequested;

    private ClientHandler() {}

    public static void send(final byte action) {
        CINetwork.CHANNEL.sendToServer(new MsgAction(action));
    }

    public static void requestHistory(final boolean open) {
        openRequested |= open;
        send(MsgAction.HISTORY);
    }

    public static void onState(final boolean enabled, final int records) {
        final GuiScreen screen = Minecraft.getMinecraft().currentScreen;
        if (screen instanceof ICraftStatusGui gui) {
            gui.craftinspector$setState(enabled, records);
        } else if (screen instanceof GuiCraftHistory gui) {
            gui.setEnabled(enabled);
        }
    }

    public static void onHistoryPart(final MsgHistoryPart part) {
        if (part.transfer != transfer || parts == null || parts.length != part.total) {
            transfer = part.transfer;
            parts = new byte[part.total][];
            received = 0;
        }
        if (part.part < 0 || part.part >= parts.length || parts[part.part] != null) {
            return;
        }
        parts[part.part] = part.data;
        received++;
        if (received < parts.length) {
            return;
        }

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (final byte[] data : parts) {
            out.write(data, 0, data.length);
        }
        parts = null;
        transfer = -1;

        final NBTTagCompound root;
        try {
            root = CompressedStreamTools.readCompressed(new ByteArrayInputStream(out.toByteArray()));
        } catch (final IOException e) {
            CraftInspector.LOG.error("Failed to read crafting history", e);
            return;
        }

        final List<CraftRecord> records = new ArrayList<>();
        final NBTTagList list = root.getTagList("records", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            final CraftRecord record = CraftRecord.readFromNBT(list.getCompoundTagAt(i));
            if (record != null) {
                records.add(record);
            }
        }
        final boolean enabled = root.getBoolean("enabled");

        final Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen instanceof GuiCraftHistory gui) {
            gui.setRecords(records);
            gui.setEnabled(enabled);
        } else if (openRequested && mc.currentScreen instanceof ICraftStatusGui) {
            mc.displayGuiScreen(new GuiCraftHistory(mc.currentScreen, records, enabled));
        }
        openRequested = false;
    }
}
