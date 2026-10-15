package craftinspector.history;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEStack;

/**
 * One crafting order ("job") with all collected statistics. While the order runs the record is owned by the crafting
 * CPU and acts as the tracker, after it finishes it is moved into the {@link CraftHistory} of the network.
 */
public class CraftRecord {

    public static final int STATUS_RUNNING = 0;
    public static final int STATUS_DONE = 1;
    public static final int STATUS_CANCELLED = 2;

    /** Unique id, used to de-duplicate records when ME networks split and join. */
    public long uid;
    public IAEStack<?> output;
    public long requested;
    public String cpuName = "";
    public String player = "";
    public long bytes;
    public int coProcessors;
    /** Wall clock time (epoch millis) when the order started. */
    public long startTime;
    /** Wall clock duration of the order in millis (for running orders: time until the snapshot was taken). */
    public long duration;
    /** AE2's own crafting timer (nanoseconds), the one shown in the crafting status GUI. */
    public long aeElapsed;
    public int status = STATUS_RUNNING;

    private final Map<IAEStack<?>, ItemStat> stats = new LinkedHashMap<>();

    public CraftRecord() {
        this.uid = ThreadLocalRandom.current().nextLong();
    }

    public Collection<ItemStat> getStats() {
        return this.stats.values();
    }

    private ItemStat stat(final IAEStack<?> stack) {
        final IAEStack<?> key = normalize(stack);
        ItemStat stat = this.stats.get(key);
        if (stat == null) {
            stat = new ItemStat(key);
            this.stats.put(key, stat);
        }
        return stat;
    }

    private long now() {
        return this.startTime == 0 ? 0 : System.currentTimeMillis() - this.startTime;
    }

    // ---------------------------------------------------------------- planning

    public void onStorage(final IAEStack<?> stack) {
        if (stack != null && stack.getStackSize() > 0) {
            this.stat(stack).fromStorage += stack.getStackSize();
        }
    }

    public void onEmit(final IAEStack<?> stack) {
        if (stack != null && stack.getStackSize() > 0) {
            this.stat(stack).emitted += stack.getStackSize();
        }
    }

    public void onPlanned(final ICraftingPatternDetails details, final long crafts) {
        if (details == null || crafts <= 0) {
            return;
        }
        for (final IAEStack<?> out : details.getCondensedAEOutputs()) {
            if (out != null) {
                this.stat(out).planned += out.getStackSize() * crafts;
            }
        }
    }

    public void start(final IAEStack<?> output, final String cpuName, final String player, final long bytes,
            final int coProcessors) {
        this.output = normalize(output);
        this.requested = output.getStackSize();
        this.cpuName = cpuName == null ? "" : cpuName;
        this.player = player == null ? "" : player;
        this.bytes = bytes;
        this.coProcessors = coProcessors;
        this.startTime = System.currentTimeMillis();
    }

    /** Adds a job that was merged into the running order. */
    public void merge(final CraftRecord planning, final IAEStack<?> addedOutput, final long addedBytes) {
        for (final ItemStat other : planning.stats.values()) {
            this.stat(other.stack).mergeFrom(other);
        }
        if (addedOutput != null) {
            this.requested += addedOutput.getStackSize();
        }
        this.bytes += addedBytes;
    }

    // ---------------------------------------------------------------- execution

    public void onPush(final ICraftingPatternDetails details) {
        final IAEStack<?>[] outputs = details.getCondensedAEOutputs();
        if (outputs == null) {
            return;
        }
        final long t = this.now();
        boolean primary = true;
        for (final IAEStack<?> out : outputs) {
            if (out == null) {
                continue;
            }
            final ItemStat stat = this.stat(out);
            if (primary) {
                stat.pushes++;
                primary = false;
            }
            if (stat.firstPush < 0) {
                stat.firstPush = t;
            }
            stat.lastPush = t;
        }
    }

    public void onReturn(final IAEStack<?> stack) {
        if (stack == null || stack.getStackSize() <= 0) {
            return;
        }
        final long t = this.now();
        final ItemStat stat = this.stat(stack);
        stat.returned += stack.getStackSize();
        if (stat.firstReturn < 0) {
            stat.firstReturn = t;
        }
        stat.lastReturn = t;
    }

    public void finish(final int status, final long aeElapsed) {
        this.status = status;
        this.aeElapsed = aeElapsed;
        this.duration = this.now();
    }

    /** Copy of a running record with the current duration filled in, for sending to clients. */
    public CraftRecord snapshot(final long aeElapsed) {
        final CraftRecord copy = readFromNBT(this.writeToNBT());
        if (copy != null) {
            copy.duration = this.now();
            copy.aeElapsed = aeElapsed;
        }
        return copy;
    }

    public long getEndTime() {
        return this.startTime + this.duration;
    }

    public List<ItemStat> getCraftedStats() {
        final List<ItemStat> list = new ArrayList<>();
        for (final ItemStat stat : this.stats.values()) {
            if (stat.isCrafted()) {
                list.add(stat);
            }
        }
        return list;
    }

    public static IAEStack<?> normalize(final IAEStack<?> stack) {
        final IAEStack<?> copy = stack.copy();
        copy.reset();
        copy.setStackSize(1);
        return copy;
    }

    // ---------------------------------------------------------------- NBT

    public NBTTagCompound writeToNBT() {
        final NBTTagCompound tag = new NBTTagCompound();
        tag.setLong("uid", this.uid);
        if (this.output != null) {
            tag.setTag("out", this.output.toNBTGeneric());
        }
        tag.setLong("req", this.requested);
        tag.setString("cpu", this.cpuName);
        tag.setString("pl", this.player);
        tag.setLong("bytes", this.bytes);
        tag.setInteger("cop", this.coProcessors);
        tag.setLong("start", this.startTime);
        tag.setLong("dur", this.duration);
        tag.setLong("ae", this.aeElapsed);
        tag.setByte("status", (byte) this.status);
        final NBTTagList list = new NBTTagList();
        for (final ItemStat stat : this.stats.values()) {
            list.appendTag(stat.writeToNBT());
        }
        tag.setTag("stats", list);
        return tag;
    }

    public static CraftRecord readFromNBT(final NBTTagCompound tag) {
        final CraftRecord record = new CraftRecord();
        record.uid = tag.getLong("uid");
        record.output = tag.hasKey("out") ? IAEStack.fromNBTGeneric(tag.getCompoundTag("out")) : null;
        if (record.output == null) {
            return null;
        }
        record.requested = tag.getLong("req");
        record.cpuName = tag.getString("cpu");
        record.player = tag.getString("pl");
        record.bytes = tag.getLong("bytes");
        record.coProcessors = tag.getInteger("cop");
        record.startTime = tag.getLong("start");
        record.duration = tag.getLong("dur");
        record.aeElapsed = tag.getLong("ae");
        record.status = tag.getByte("status");
        final NBTTagList list = tag.getTagList("stats", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            final ItemStat stat = ItemStat.readFromNBT(list.getCompoundTagAt(i));
            if (stat != null) {
                record.stats.put(stat.stack, stat);
            }
        }
        return record;
    }
}
