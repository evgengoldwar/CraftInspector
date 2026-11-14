package craftinspector.mixins;

import javax.annotation.Nonnull;

import com.gtnewhorizon.gtnhmixins.builders.IMixins;
import com.gtnewhorizon.gtnhmixins.builders.MixinBuilder;

public enum Mixins implements IMixins {

    AE2_CRAFTING_STATISTICS(new MixinBuilder("Collect statistics of AE2 crafting orders").setPhase(Phase.LATE)
        .addRequiredMod(TargetMods.AE2)
        .addCommonMixins(
            "ae2.MixinCraftingCPUCluster",
            "ae2.MixinCraftingGridCache",
            "ae2.AccessorContainerCraftingCPU")),
    AE2_CRAFTING_STATUS_GUI(
        new MixinBuilder("Crafting history buttons in the AE2 crafting status GUI").setPhase(Phase.LATE)
            .addRequiredMod(TargetMods.AE2)
            .addClientMixins("ae2.MixinGuiCraftingStatus"));

    private final MixinBuilder builder;

    Mixins(MixinBuilder builder) {
        this.builder = builder;
    }

    @Nonnull
    @Override
    public MixinBuilder getBuilder() {
        return builder;
    }
}
