package craftinspector.mixins.late.ae2;

import net.minecraft.inventory.InventoryCrafting;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.PlayerSource;
import appeng.api.storage.data.IAEStack;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import craftinspector.history.CraftHistory;
import craftinspector.history.CraftRecord;
import craftinspector.history.ICraftHistoryHolder;
import craftinspector.history.ICraftTrackerHolder;

@Mixin(value = CraftingCPUCluster.class, remap = false)
public abstract class MixinCraftingCPUCluster implements ICraftTrackerHolder {

    /** Statistics of the running order, null if the order is not tracked. */
    @Unique
    private CraftRecord craftinspector$tracker;

    /** Data collected while a new job (or a merged job) is being planned. */
    @Unique
    private CraftRecord craftinspector$planning;

    @Unique
    private CraftRecord craftinspector$planningOuter;

    @Unique
    private boolean craftinspector$merged;

    @Shadow
    public abstract IGrid getGrid();

    @Shadow
    protected abstract boolean hasRemainingTasks();

    @Shadow
    public abstract String getName();

    @Shadow
    public abstract int getCoProcessors();

    @Shadow
    public abstract long getElapsedTime();

    @Override
    public CraftRecord craftinspector$getTracker() {
        return this.craftinspector$tracker;
    }

    @Override
    public long craftinspector$getElapsed() {
        return this.getElapsedTime();
    }

    @Unique
    private static CraftHistory craftinspector$history(final IGrid grid) {
        if (grid == null) {
            return null;
        }
        final Object cache = grid.getCache(ICraftingGrid.class);
        return cache instanceof ICraftHistoryHolder holder ? holder.craftinspector$getHistory() : null;
    }

    // ------------------------------------------------------------------ job submission

    @Inject(method = "submitJob", at = @At("HEAD"))
    private void craftinspector$beforeSubmit(final IGrid g, final ICraftingJob<?> job, final BaseActionSource src,
        final ICraftingRequester requestingMachine, final CallbackInfoReturnable<ICraftingLink> cir) {
        this.craftinspector$merged = false;
        final CraftHistory history = craftinspector$history(g);
        // The decision to track is made once, when the order starts.
        this.craftinspector$planning = history != null && history.isEnabled() ? new CraftRecord() : null;
    }

    @Inject(method = "submitJob", at = @At("RETURN"))
    private void craftinspector$afterSubmit(final IGrid g, final ICraftingJob<?> job, final BaseActionSource src,
        final ICraftingRequester requestingMachine, final CallbackInfoReturnable<ICraftingLink> cir) {
        final CraftRecord planning = this.craftinspector$planning;
        this.craftinspector$planning = null;

        if (this.craftinspector$merged) {
            // handled by mergeJob
            this.craftinspector$merged = false;
            return;
        }
        if (cir.getReturnValue() == null || job.getOutput() == null) {
            return;
        }

        if (planning == null) {
            this.craftinspector$tracker = null;
            return;
        }

        String player = null;
        if (src instanceof PlayerSource ps && ps.player != null) {
            player = ps.player.getCommandSenderName();
        }
        planning.start(job.getOutput(), this.getName(), player, job.getByteTotal(), this.getCoProcessors());
        this.craftinspector$tracker = planning;
    }

    @Inject(method = "mergeJob", at = @At("HEAD"))
    private void craftinspector$beforeMerge(final IGrid g, final ICraftingJob<?> job, final BaseActionSource src,
        final ICraftingRequester requestingMachine, final CallbackInfoReturnable<ICraftingLink> cir) {
        this.craftinspector$planningOuter = this.craftinspector$planning;
        // Merged jobs are tracked only when the order they are merged into is tracked.
        this.craftinspector$planning = this.craftinspector$tracker != null ? new CraftRecord() : null;
    }

    @Inject(method = "mergeJob", at = @At("RETURN"))
    private void craftinspector$afterMerge(final IGrid g, final ICraftingJob<?> job, final BaseActionSource src,
        final ICraftingRequester requestingMachine, final CallbackInfoReturnable<ICraftingLink> cir) {
        final CraftRecord planning = this.craftinspector$planning;
        this.craftinspector$planning = this.craftinspector$planningOuter;
        this.craftinspector$planningOuter = null;

        if (cir.getReturnValue() == null) {
            return;
        }
        this.craftinspector$merged = true;
        if (planning != null && this.craftinspector$tracker != null) {
            this.craftinspector$tracker.merge(planning, job.getOutput(), job.getByteTotal());
        }
    }

    @Inject(method = "addStorage", at = @At("HEAD"))
    private void craftinspector$onAddStorage(final IAEStack<?> extractItems, final CallbackInfo ci) {
        if (this.craftinspector$planning != null) {
            this.craftinspector$planning.onStorage(extractItems);
        }
    }

    @Inject(method = "addEmitable", at = @At("HEAD"))
    private void craftinspector$onAddEmitable(final IAEStack<?> i, final CallbackInfo ci) {
        if (this.craftinspector$planning != null) {
            this.craftinspector$planning.onEmit(i);
        }
    }

    @Inject(method = "addCrafting", at = @At("HEAD"))
    private void craftinspector$onAddCrafting(final ICraftingPatternDetails details, final long crafts,
        final CallbackInfo ci) {
        if (this.craftinspector$planning != null) {
            this.craftinspector$planning.onPlanned(details, crafts);
        }
    }

    // ------------------------------------------------------------------ execution

    @WrapOperation(
        method = "executeCrafting",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/api/networking/crafting/ICraftingMedium;pushPattern(Lappeng/api/networking/crafting/ICraftingPatternDetails;Lnet/minecraft/inventory/InventoryCrafting;)Z"))
    private boolean craftinspector$onPushPattern(final ICraftingMedium medium, final ICraftingPatternDetails details,
        final InventoryCrafting table, final Operation<Boolean> original) {
        final boolean pushed = original.call(medium, details, table);
        if (pushed && this.craftinspector$tracker != null) {
            this.craftinspector$tracker.onPush(details);
        }
        return pushed;
    }

    /** Called by AE2 for every stack that comes back into the CPU while crafting. */
    @Inject(method = "updateElapsedTime", at = @At("HEAD"))
    private void craftinspector$onItemsReturned(final IAEStack<?> is, final CallbackInfo ci) {
        if (this.craftinspector$tracker != null) {
            this.craftinspector$tracker.onReturn(is);
        }
    }

    // ------------------------------------------------------------------ end of job

    @Inject(method = "completeJob", at = @At("HEAD"))
    private void craftinspector$onComplete(final CallbackInfo ci) {
        if (this.craftinspector$tracker != null && !this.hasRemainingTasks()) {
            this.craftinspector$finish(CraftRecord.STATUS_DONE);
        }
    }

    @Inject(method = "cancel", at = @At("HEAD"))
    private void craftinspector$onCancel(final CallbackInfo ci) {
        if (this.craftinspector$tracker != null) {
            this.craftinspector$finish(CraftRecord.STATUS_CANCELLED);
        }
    }

    @Unique
    private void craftinspector$finish(final int status) {
        final CraftRecord record = this.craftinspector$tracker;
        this.craftinspector$tracker = null;
        record.finish(status, this.getElapsedTime());
        final CraftHistory history = craftinspector$history(this.getGrid());
        if (history != null) {
            history.add(record);
        }
    }
}
