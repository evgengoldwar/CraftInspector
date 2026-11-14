package craftinspector.history;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import craftinspector.Config;

/**
 * Per ME network history of finished crafting orders plus the "collect statistics" switch. Lives inside AE2's
 * {@code CraftingGridCache} (see the mixin) and is saved together with the network's grid storage.
 */
public class CraftHistory {

    public static final String NBT_KEY = "CraftInspectorHistory";

    private boolean enabled = false;
    /** Newest first. */
    private final List<CraftRecord> records = new ArrayList<>();

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    public List<CraftRecord> getRecords() {
        return this.records;
    }

    public void add(final CraftRecord record) {
        if (record.status == CraftRecord.STATUS_CANCELLED && !Config.recordCancelledOrders) {
            return;
        }
        for (final CraftRecord existing : this.records) {
            if (existing.uid == record.uid) {
                return;
            }
        }
        this.records.add(0, record);
        this.trim();
    }

    public void clear() {
        this.records.clear();
    }

    /** Removes the finished order with the given uid. */
    public void remove(final long uid) {
        this.records.removeIf(record -> record.uid == uid);
    }

    private void trim() {
        while (this.records.size() > Math.max(1, Config.historySize)) {
            this.records.remove(this.records.size() - 1);
        }
    }

    public NBTTagCompound writeToNBT() {
        final NBTTagCompound tag = new NBTTagCompound();
        tag.setBoolean("enabled", this.enabled);
        final NBTTagList list = new NBTTagList();
        for (final CraftRecord record : this.records) {
            list.appendTag(record.writeToNBT());
        }
        tag.setTag("records", list);
        return tag;
    }

    /** Merges saved data into this history (used when loading or when networks join). */
    public void mergeFromNBT(final NBTTagCompound tag) {
        if (tag == null || tag.hasNoTags()) {
            return;
        }
        this.enabled |= tag.getBoolean("enabled");
        final NBTTagList list = tag.getTagList("records", 10);
        outer: for (int i = 0; i < list.tagCount(); i++) {
            final CraftRecord record = CraftRecord.readFromNBT(list.getCompoundTagAt(i));
            if (record == null) {
                continue;
            }
            for (final CraftRecord existing : this.records) {
                if (existing.uid == record.uid) {
                    continue outer;
                }
            }
            this.records.add(record);
        }
        this.records.sort(
            Comparator.comparingLong(CraftRecord::getEndTime)
                .reversed());
        this.trim();
    }
}
