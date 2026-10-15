package craftinspector.mixins.late.ae2;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import appeng.api.networking.IGridStorage;
import appeng.me.cache.CraftingGridCache;
import craftinspector.history.CraftHistory;
import craftinspector.history.ICraftHistoryHolder;

@Mixin(value = CraftingGridCache.class, remap = false)
public abstract class MixinCraftingGridCache implements ICraftHistoryHolder {

    @Unique
    private final CraftHistory craftinspector$history = new CraftHistory();

    @Override
    public CraftHistory craftinspector$getHistory() {
        return this.craftinspector$history;
    }

    @Inject(method = "onJoin", at = @At("HEAD"))
    private void craftinspector$onJoin(final IGridStorage sourceStorage, final CallbackInfo ci) {
        if (sourceStorage != null && sourceStorage.dataObject().hasKey(CraftHistory.NBT_KEY)) {
            // records carry unique ids, so joining the same storage twice does not duplicate them
            this.craftinspector$history.mergeFromNBT(sourceStorage.dataObject().getCompoundTag(CraftHistory.NBT_KEY));
        }
    }

    @Inject(method = "onSplit", at = @At("TAIL"))
    private void craftinspector$onSplit(final IGridStorage destinationStorage, final CallbackInfo ci) {
        // both halves of a split network keep the history and the setting
        destinationStorage.dataObject().setTag(CraftHistory.NBT_KEY, this.craftinspector$history.writeToNBT());
    }

    @Inject(method = "populateGridStorage", at = @At("TAIL"))
    private void craftinspector$save(final IGridStorage destinationStorage, final CallbackInfo ci) {
        destinationStorage.dataObject().setTag(CraftHistory.NBT_KEY, this.craftinspector$history.writeToNBT());
    }
}
