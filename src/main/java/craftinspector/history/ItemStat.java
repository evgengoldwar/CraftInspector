package craftinspector.history;

import net.minecraft.nbt.NBTTagCompound;

import appeng.api.storage.data.IAEStack;

/**
 * Statistics of one item (or fluid) type inside a single crafting order.
 * <p>
 * All timestamps are milliseconds relative to the start of the order, {@code -1} means "never happened".
 */
public class ItemStat {

    /** Normalized stack (size 1) that identifies this entry. */
    public final IAEStack<?> stack;

    /** Amount taken from the ME storage when the order was planned. */
    public long fromStorage;
    /** Amount the order planned to craft (outputs of all scheduled pattern runs). */
    public long planned;
    /** Amount that came back from machines / interfaces while the order ran. */
    public long returned;
    /** Amount that was emitted or missing (level emitters, "ignore missing" mode). */
    public long emitted;
    /** How many times a pattern producing this as primary output was pushed into a machine. */
    public long pushes;

    public long firstPush = -1;
    public long lastPush = -1;
    public long firstReturn = -1;
    public long lastReturn = -1;

    public ItemStat(final IAEStack<?> stack) {
        this.stack = stack;
    }

    public boolean isCrafted() {
        return this.planned > 0 || this.pushes > 0;
    }

    /**
     * Time spent crafting this component: from the first pattern push until the last output returned.
     *
     * @param orderDuration total duration of the order, used when outputs never returned (fake crafting, cancel)
     */
    public long getCraftTime(final long orderDuration) {
        if (this.firstPush < 0) {
            return 0;
        }
        final long end = this.lastReturn >= this.firstPush ? this.lastReturn : orderDuration;
        return Math.max(0, end - this.firstPush);
    }

    void mergeFrom(final ItemStat other) {
        this.fromStorage += other.fromStorage;
        this.planned += other.planned;
        this.returned += other.returned;
        this.emitted += other.emitted;
        this.pushes += other.pushes;
        this.firstPush = minTime(this.firstPush, other.firstPush);
        this.firstReturn = minTime(this.firstReturn, other.firstReturn);
        this.lastPush = Math.max(this.lastPush, other.lastPush);
        this.lastReturn = Math.max(this.lastReturn, other.lastReturn);
    }

    private static long minTime(final long a, final long b) {
        if (a < 0) return b;
        if (b < 0) return a;
        return Math.min(a, b);
    }

    public NBTTagCompound writeToNBT() {
        final NBTTagCompound tag = new NBTTagCompound();
        tag.setTag("s", this.stack.toNBTGeneric());
        if (this.fromStorage != 0) tag.setLong("st", this.fromStorage);
        if (this.planned != 0) tag.setLong("pl", this.planned);
        if (this.returned != 0) tag.setLong("rt", this.returned);
        if (this.emitted != 0) tag.setLong("em", this.emitted);
        if (this.pushes != 0) tag.setLong("pu", this.pushes);
        if (this.firstPush >= 0) tag.setLong("fp", this.firstPush);
        if (this.lastPush >= 0) tag.setLong("lp", this.lastPush);
        if (this.firstReturn >= 0) tag.setLong("fr", this.firstReturn);
        if (this.lastReturn >= 0) tag.setLong("lr", this.lastReturn);
        return tag;
    }

    public static ItemStat readFromNBT(final NBTTagCompound tag) {
        final IAEStack<?> stack = IAEStack.fromNBTGeneric(tag.getCompoundTag("s"));
        if (stack == null) {
            return null;
        }
        final ItemStat stat = new ItemStat(stack);
        stat.fromStorage = tag.getLong("st");
        stat.planned = tag.getLong("pl");
        stat.returned = tag.getLong("rt");
        stat.emitted = tag.getLong("em");
        stat.pushes = tag.getLong("pu");
        stat.firstPush = tag.hasKey("fp") ? tag.getLong("fp") : -1;
        stat.lastPush = tag.hasKey("lp") ? tag.getLong("lp") : -1;
        stat.firstReturn = tag.hasKey("fr") ? tag.getLong("fr") : -1;
        stat.lastReturn = tag.hasKey("lr") ? tag.getLong("lr") : -1;
        return stat;
    }
}
