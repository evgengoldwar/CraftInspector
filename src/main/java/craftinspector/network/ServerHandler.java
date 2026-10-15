package craftinspector.network;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import appeng.api.config.SecurityPermissions;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.security.ISecurityGrid;
import appeng.container.implementations.ContainerCraftingCPU;
import craftinspector.CraftInspector;
import craftinspector.history.CraftHistory;
import craftinspector.history.CraftRecord;
import craftinspector.history.ICraftHistoryHolder;
import craftinspector.history.ICraftTrackerHolder;
import craftinspector.mixins.late.ae2.AccessorContainerCraftingCPU;

public final class ServerHandler {

    private static int transferCounter = 0;

    private ServerHandler() {}

    public static void handle(final EntityPlayerMP player, final byte action) {
        if (player == null || !(player.openContainer instanceof ContainerCraftingCPU container)) {
            return;
        }
        final AccessorContainerCraftingCPU accessor = (AccessorContainerCraftingCPU) (Object) container;

        if (action == MsgAction.RESYNC) {
            // the client dropped CPU updates while the history screen was open, force a full resend
            final ICraftingCPU cpu = container.getCpu();
            if (cpu != null) {
                accessor.craftinspector$setCPU(null);
                accessor.craftinspector$setCPU(cpu);
            }
            return;
        }

        final IGrid grid = accessor.craftinspector$getNetwork();
        final CraftHistory history = getHistory(grid);
        if (history == null) {
            return;
        }

        switch (action) {
            case MsgAction.TOGGLE -> {
                if (canModify(grid, player)) {
                    history.setEnabled(!history.isEnabled());
                }
                sendState(player, history);
            }
            case MsgAction.CLEAR -> {
                if (canModify(grid, player)) {
                    history.clear();
                }
                sendHistory(player, grid, history);
            }
            case MsgAction.HISTORY -> sendHistory(player, grid, history);
            default -> sendState(player, history);
        }
    }

    private static CraftHistory getHistory(final IGrid grid) {
        if (grid == null) {
            return null;
        }
        final Object cache = grid.getCache(ICraftingGrid.class);
        return cache instanceof ICraftHistoryHolder holder ? holder.craftinspector$getHistory() : null;
    }

    private static boolean canModify(final IGrid grid, final EntityPlayerMP player) {
        final Object security = grid.getCache(ISecurityGrid.class);
        return !(security instanceof ISecurityGrid sg) || sg.hasPermission(player, SecurityPermissions.CRAFT);
    }

    private static void sendState(final EntityPlayerMP player, final CraftHistory history) {
        CINetwork.CHANNEL.sendTo(new MsgState(history.isEnabled(), history.getRecords().size()), player);
    }

    private static void sendHistory(final EntityPlayerMP player, final IGrid grid, final CraftHistory history) {
        final NBTTagCompound root = new NBTTagCompound();
        root.setBoolean("enabled", history.isEnabled());

        final NBTTagList list = new NBTTagList();
        // orders that are still running come first
        final Object craftingGrid = grid.getCache(ICraftingGrid.class);
        if (craftingGrid instanceof ICraftingGrid cg) {
            final List<CraftRecord> running = new ArrayList<>();
            for (final ICraftingCPU cpu : cg.getCpus()) {
                if (cpu instanceof ICraftTrackerHolder holder && holder.craftinspector$getTracker() != null) {
                    final CraftRecord snapshot = holder.craftinspector$getTracker()
                            .snapshot(holder.craftinspector$getElapsed());
                    if (snapshot != null) {
                        running.add(snapshot);
                    }
                }
            }
            running.sort((a, b) -> Long.compare(b.startTime, a.startTime));
            for (final CraftRecord record : running) {
                list.appendTag(record.writeToNBT());
            }
        }
        for (final CraftRecord record : history.getRecords()) {
            list.appendTag(record.writeToNBT());
        }
        root.setTag("records", list);

        final byte[] data;
        try {
            data = CompressedStreamTools.compress(root);
        } catch (final IOException e) {
            CraftInspector.LOG.error("Failed to serialize crafting history", e);
            return;
        }

        final int transfer = ++transferCounter;
        final int total = Math.max(1, (data.length + MsgHistoryPart.PART_SIZE - 1) / MsgHistoryPart.PART_SIZE);
        for (int part = 0; part < total; part++) {
            final int from = part * MsgHistoryPart.PART_SIZE;
            final int to = Math.min(data.length, from + MsgHistoryPart.PART_SIZE);
            CINetwork.CHANNEL
                    .sendTo(new MsgHistoryPart(transfer, part, total, Arrays.copyOfRange(data, from, to)), player);
        }
    }
}
