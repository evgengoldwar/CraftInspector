package craftinspector.mixins.late.ae2;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.client.gui.implementations.GuiCraftingCPU;
import appeng.client.gui.implementations.GuiCraftingStatus;
import appeng.client.gui.widgets.GuiCraftingCPUTable;
import craftinspector.client.ClientHandler;
import craftinspector.client.gui.ICraftStatusGui;
import craftinspector.client.gui.IconButton;
import craftinspector.network.MsgAction;

/**
 * Adds two buttons below the CPU list of the crafting status GUI: one switches statistics collection on and off for
 * the network, the other opens the order history.
 */
@Mixin(value = GuiCraftingStatus.class, remap = false)
public abstract class MixinGuiCraftingStatus extends GuiCraftingCPU implements ICraftStatusGui {

    @Unique
    private static final int craftinspector$BUTTON_SIZE = 18;

    @Unique
    private IconButton craftinspector$toggleButton;

    @Unique
    private IconButton craftinspector$historyButton;

    @Unique
    private boolean craftinspector$enabled;

    @Unique
    private int craftinspector$records;

    private MixinGuiCraftingStatus() {
        super(null, null);
    }

    @Unique
    private int craftinspector$buttonX() {
        return this.guiLeft - GuiCraftingCPUTable.CPU_TABLE_WIDTH + 4;
    }

    @Unique
    private int craftinspector$buttonY() {
        return this.guiTop + GuiCraftingCPUTable.CPU_TABLE_HEIGHT + 1;
    }

    // initGui is a Minecraft method, so both the dev and the SRG name are listed (the class is not remapped)
    @Inject(method = { "initGui", "func_73866_w_" }, at = @At("TAIL"), require = 1)
    private void craftinspector$initGui(final CallbackInfo ci) {
        final int x = this.craftinspector$buttonX();
        final int y = this.craftinspector$buttonY();

        this.craftinspector$toggleButton = new IconButton(0x43490001, x, y, new ItemStack(Items.writable_book), "");
        this.craftinspector$historyButton = new IconButton(
            0x43490002,
            x + craftinspector$BUTTON_SIZE + 2,
            y,
            new ItemStack(Items.clock),
            "");
        this.buttonList.add(this.craftinspector$toggleButton);
        this.buttonList.add(this.craftinspector$historyButton);
        this.craftinspector$updateButtons();

        ClientHandler.send(MsgAction.STATE);
    }

    @Inject(method = { "actionPerformed", "func_146284_a" }, at = @At("HEAD"), cancellable = true, require = 1)
    private void craftinspector$actionPerformed(final GuiButton btn, final CallbackInfo ci) {
        if (btn == null) {
            return;
        }
        if (btn == this.craftinspector$toggleButton) {
            ClientHandler.send(MsgAction.TOGGLE);
            ci.cancel();
        } else if (btn == this.craftinspector$historyButton) {
            ClientHandler.requestHistory(true);
            ci.cancel();
        }
    }

    /** Keeps NEI from drawing its item panel over the buttons. */
    @Inject(method = "hideItemPanelSlot", at = @At("RETURN"), cancellable = true, require = 0)
    private void craftinspector$hideItemPanelSlot(final int x, final int y, final int w, final int h,
        final CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            return;
        }
        final int bx = this.craftinspector$buttonX();
        final int by = this.craftinspector$buttonY();
        final int bw = craftinspector$BUTTON_SIZE * 2 + 2;
        final int bh = craftinspector$BUTTON_SIZE;
        if (x + w >= bx && x <= bx + bw && y + h >= by && y <= by + bh) {
            cir.setReturnValue(true);
        }
    }

    @Override
    public void craftinspector$setState(final boolean enabled, final int records) {
        this.craftinspector$enabled = enabled;
        this.craftinspector$records = records;
        this.craftinspector$updateButtons();
    }

    @Unique
    private void craftinspector$updateButtons() {
        if (this.craftinspector$toggleButton != null) {
            this.craftinspector$toggleButton.setDotColor(this.craftinspector$enabled ? 0xFF3FD13F : 0xFFD13F3F);
            this.craftinspector$toggleButton.setTootipString(
                StatCollector.translateToLocal("craftinspector.button.toggle") + "\n"
                    + (this.craftinspector$enabled
                        ? EnumChatFormatting.GREEN + StatCollector.translateToLocal("craftinspector.button.toggle.on")
                        : EnumChatFormatting.RED + StatCollector.translateToLocal("craftinspector.button.toggle.off"))
                    + EnumChatFormatting.RESET
                    + "\n"
                    + EnumChatFormatting.GRAY
                    + StatCollector.translateToLocal("craftinspector.button.toggle.tip"));
        }
        if (this.craftinspector$historyButton != null) {
            this.craftinspector$historyButton.setTootipString(
                StatCollector.translateToLocal("craftinspector.button.history") + "\n"
                    + EnumChatFormatting.GRAY
                    + StatCollector
                        .translateToLocalFormatted("craftinspector.button.history.tip", this.craftinspector$records));
        }
    }
}
