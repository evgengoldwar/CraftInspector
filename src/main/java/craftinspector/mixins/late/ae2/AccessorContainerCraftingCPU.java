package craftinspector.mixins.late.ae2;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.container.implementations.ContainerCraftingCPU;

@Mixin(value = ContainerCraftingCPU.class, remap = false)
public interface AccessorContainerCraftingCPU {

    @Accessor("network")
    IGrid craftinspector$getNetwork();

    @Invoker("setCPU")
    void craftinspector$setCPU(ICraftingCPU cpu);
}
