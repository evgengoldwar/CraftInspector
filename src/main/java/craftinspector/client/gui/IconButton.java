package craftinspector.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import appeng.client.gui.widgets.GuiAeButton;

/**
 * Small square button that shows an item icon and an optional colored status dot.
 */
public class IconButton extends GuiAeButton {

    private static final RenderItem RENDER_ITEM = new RenderItem();

    private final ItemStack icon;
    /** ARGB color of the status dot, 0 = no dot. */
    private int dotColor;

    public IconButton(final int id, final int x, final int y, final ItemStack icon, final String tooltip) {
        super(id, x, y, 18, 18, "", tooltip);
        this.icon = icon;
    }

    public void setDotColor(final int dotColor) {
        this.dotColor = dotColor;
    }

    @Override
    public void drawButton(final Minecraft mc, final int mouseX, final int mouseY) {
        if (!this.visible) {
            return;
        }
        super.drawButton(mc, mouseX, mouseY);

        if (this.icon != null) {
            GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_LIGHTING_BIT);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            RenderHelper.enableGUIStandardItemLighting();
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            RENDER_ITEM.renderItemAndEffectIntoGUI(
                mc.fontRenderer,
                mc.getTextureManager(),
                this.icon,
                this.xPosition + 1,
                this.yPosition + 1);
            RenderHelper.disableStandardItemLighting();
            GL11.glPopAttrib();
        }

        if (this.dotColor != 0) {
            GL11.glPushMatrix();
            GL11.glTranslatef(0, 0, 300);
            final int x = this.xPosition + this.width - 6;
            final int y = this.yPosition + this.height - 6;
            Gui.drawRect(x - 1, y - 1, x + 5, y + 5, 0xFF202020);
            Gui.drawRect(x, y, x + 4, y + 4, this.dotColor);
            GL11.glPopMatrix();
        }
    }
}
