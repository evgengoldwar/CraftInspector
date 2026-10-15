package craftinspector.client.gui;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.inventory.Slot;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import appeng.api.config.DiagnosticSortButton;
import appeng.api.config.Settings;
import appeng.api.config.SortDir;
import appeng.api.config.TerminalStyle;
import appeng.api.config.YesNo;
import appeng.api.storage.data.IAEStack;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiAeButton;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.container.ContainerNull;
import appeng.core.AEConfig;
import appeng.util.Platform;
import appeng.util.ReadableNumberConverter;
import craftinspector.client.ClientHandler;
import craftinspector.history.CraftRecord;
import craftinspector.history.ItemStat;
import craftinspector.network.MsgAction;

/**
 * Shows the last crafting orders of the network. Looks like AE2's crafting status GUI: on the left the orders are
 * listed like crafting CPUs, on the right every component of the selected order is shown with the amount crafted, the
 * amount taken from storage and the time it took to craft it. Sorting by time puts the slowest components first, so
 * the bottlenecks of the network are easy to spot.
 */
public class GuiCraftHistory extends AEBaseGui {

    private static final int GUI_WIDTH = 238;
    private static final int GUI_HEIGHT = 184;

    private static final int DISPLAYED_ROWS = 6;
    private static final int SCROLLBAR_TOP = 19;
    private static final int TEXTURE_BELOW_TOP_ROW_Y = 41;
    private static final int TEXTURE_ABOVE_BOTTOM_ROW_Y = 51;
    private static final int COLUMNS = 3;
    private static final int SECTION_LENGTH = 67;
    private static final int SECTION_HEIGHT = 23;
    private static final int ITEMSTACK_LEFT_OFFSET = 9;
    private static final int ITEMSTACK_TOP_OFFSET = 22;

    private static final int TABLE_WIDTH = 94;
    private static final int TABLE_SLOT_X = -TABLE_WIDTH + 9;
    private static final int TABLE_SLOT_Y = 19;
    private static final int TABLE_SLOT_WIDTH = 67;
    private static final int TABLE_SLOT_HEIGHT = 23;

    private static final int COLOR_TEXT = 0x404040;
    private static final int COLOR_NAME = 0x202020;

    private static final DiagnosticSortButton[] SORT_MODES = { DiagnosticSortButton.TIME, DiagnosticSortButton.QTY,
        DiagnosticSortButton.NAME };

    private final GuiScreen parent;
    private final GuiScrollbar tableScrollbar = new GuiScrollbar();
    private final NumberFormat numberFormat = NumberFormat.getInstance();

    private List<CraftRecord> records;
    private boolean collectionEnabled;
    private int selected = 0;
    private DiagnosticSortButton sortMode = DiagnosticSortButton.TIME;
    private SortDir sortDir = SortDir.DESCENDING;
    private boolean onlyCrafted = false;
    private boolean tallMode;
    private int rows = DISPLAYED_ROWS;

    /** Entries of the selected order after filtering and sorting. */
    private final List<ItemStat> visible = new ArrayList<>();

    private GuiAeButton backButton;
    private GuiImgButton sortButton;
    private GuiImgButton sortDirButton;
    private GuiImgButton filterButton;
    private GuiImgButton tallModeButton;
    private GuiAeButton clearButton;
    private GuiAeButton refreshButton;
    private MEGuiTextField searchField;

    public GuiCraftHistory(final GuiScreen parent, final List<CraftRecord> records, final boolean enabled) {
        super(new ContainerNull());
        this.parent = parent;
        this.records = records;
        this.collectionEnabled = enabled;
        this.xSize = GUI_WIDTH;
        this.ySize = GUI_HEIGHT;
        this.tallMode = AEConfig.instance.getConfigManager()
            .getSetting(Settings.TERMINAL_STYLE) == TerminalStyle.TALL;
        this.setScrollBar(new GuiScrollbar());
        this.tableScrollbar.setLeft(-16)
            .setTop(SCROLLBAR_TOP)
            .setWidth(12);
    }

    /** Same sizing as AE2's crafting status GUI, so the tall terminal style works here too. */
    private void recalculateScreenSize() {
        this.xSize = GUI_WIDTH;
        if (this.tallMode) {
            final int maxAvailableHeight = this.height - 64;
            this.rows = Math
                .max(DISPLAYED_ROWS, (maxAvailableHeight - (SCROLLBAR_TOP + SECTION_HEIGHT)) / SECTION_HEIGHT);
            this.ySize = SCROLLBAR_TOP + SECTION_HEIGHT + 5 + this.rows * SECTION_HEIGHT;
        } else {
            this.rows = DISPLAYED_ROWS;
            this.ySize = GUI_HEIGHT;
        }
        this.tableScrollbar.setHeight(this.getTableHeight() - 27);
    }

    private int getTableHeight() {
        return this.rows * TABLE_SLOT_HEIGHT + 27;
    }

    public void setRecords(final List<CraftRecord> records) {
        this.records = records;
        if (this.selected >= records.size()) {
            this.selected = Math.max(0, records.size() - 1);
        }
        this.rebuild();
    }

    public void setEnabled(final boolean enabled) {
        this.collectionEnabled = enabled;
    }

    private CraftRecord getSelected() {
        return this.selected >= 0 && this.selected < this.records.size() ? this.records.get(this.selected) : null;
    }

    // ------------------------------------------------------------------ setup

    @Override
    public void initGui() {
        this.recalculateScreenSize();
        super.initGui();

        final int y = this.guiTop + this.ySize - 25;
        this.backButton = new GuiAeButton(0, this.guiLeft + 8, y, 50, 20, tr("back"), tr("back.tip"));
        this.clearButton = new GuiAeButton(3, this.guiLeft + 163, y, 50, 20, tr("clear"), tr("clear.tip"));
        this.refreshButton = new GuiAeButton(4, this.guiLeft - 20, this.guiTop + 2, 16, 16, "\u21BB", tr("refresh"));
        this.buttonList.add(this.backButton);
        this.buttonList.add(this.clearButton);
        this.buttonList.add(this.refreshButton);

        // sorting buttons on the right side, like in AE2's crafting confirmation GUI
        final int sideX = this.guiLeft + this.xSize + 2;
        this.sortButton = new GuiImgButton(sideX, this.guiTop + 8, Settings.DIAGNOSTIC_SORT_BY, this.sortMode);
        this.sortDirButton = new GuiImgButton(sideX, this.guiTop + 8 + 20, Settings.SORT_DIRECTION, this.sortDir);
        this.filterButton = new GuiImgButton(
            sideX,
            this.guiTop + 8 + 20 * 2,
            Settings.HIDE_STORED,
            this.onlyCrafted ? YesNo.YES : YesNo.NO);
        this.buttonList.add(this.sortButton);
        this.buttonList.add(this.sortDirButton);
        this.buttonList.add(this.filterButton);

        this.tallModeButton = new GuiImgButton(
            this.guiLeft - 18,
            this.guiTop + this.ySize - 18,
            Settings.TERMINAL_STYLE,
            this.tallMode ? TerminalStyle.TALL : TerminalStyle.SMALL);
        this.buttonList.add(this.tallModeButton);

        final String oldSearch = this.searchField == null ? "" : this.searchField.getText();
        this.searchField = new MEGuiTextField(76, 12, tr("search")) {

            @Override
            public void onTextChange(final String oldText) {
                super.onTextChange(oldText);
                GuiCraftHistory.this.rebuild();
            }
        };
        this.searchField.x = this.guiLeft + this.xSize - 101;
        this.searchField.y = this.guiTop + 5;
        this.searchField.setText(oldSearch, true);

        this.rebuild();
    }

    private void rebuild() {
        this.visible.clear();
        final CraftRecord record = this.getSelected();
        if (record != null) {
            final String search = this.searchField == null ? ""
                : this.searchField.getText()
                    .toLowerCase(Locale.ROOT)
                    .trim();
            for (final ItemStat stat : record.getStats()) {
                if (this.onlyCrafted && !stat.isCrafted()) {
                    continue;
                }
                if (!search.isEmpty() && !Platform.getItemDisplayName(stat.stack)
                    .toLowerCase(Locale.ROOT)
                    .contains(search)) {
                    continue;
                }
                this.visible.add(stat);
            }

            final long duration = record.duration;
            final Comparator<ItemStat> byName = Comparator.comparing(
                s -> Platform.getItemDisplayName(s.stack)
                    .toLowerCase(Locale.ROOT));
            Comparator<ItemStat> order = switch (this.sortMode) {
                case QTY -> Comparator.comparingLong(GuiCraftHistory::amountOf);
                case NAME -> byName;
                default -> Comparator.comparingLong((ItemStat s) -> s.getCraftTime(duration))
                    .thenComparing(Comparator.comparingLong(GuiCraftHistory::amountOf));
            };
            if (this.sortDir == SortDir.DESCENDING) {
                order = order.reversed();
            }
            this.visible.sort(order);
        }

        final int lines = (this.visible.size() + COLUMNS - 1) / COLUMNS;
        this.getScrollBar()
            .setTop(SCROLLBAR_TOP)
            .setLeft(218)
            .setHeight(this.ySize - 47);
        this.getScrollBar()
            .setRange(0, Math.max(0, lines - this.rows), 1);
        this.tableScrollbar.setRange(0, Math.max(0, this.records.size() - this.rows), 1);
    }

    private static long amountOf(final ItemStat stat) {
        return Math.max(Math.max(stat.returned, stat.planned), stat.fromStorage);
    }

    // ------------------------------------------------------------------ input

    @Override
    protected void actionPerformed(final GuiButton btn) {
        if (btn == this.backButton) {
            this.goBack();
        } else if (btn == this.sortButton) {
            int index = 0;
            for (int i = 0; i < SORT_MODES.length; i++) {
                if (SORT_MODES[i] == this.sortMode) {
                    index = i;
                }
            }
            final int step = Mouse.isButtonDown(1) ? SORT_MODES.length - 1 : 1;
            this.sortMode = SORT_MODES[(index + step) % SORT_MODES.length];
            this.sortButton.set(this.sortMode);
            this.rebuild();
        } else if (btn == this.sortDirButton) {
            this.sortDir = this.sortDir == SortDir.ASCENDING ? SortDir.DESCENDING : SortDir.ASCENDING;
            this.sortDirButton.set(this.sortDir);
            this.rebuild();
        } else if (btn == this.filterButton) {
            this.onlyCrafted = !this.onlyCrafted;
            this.filterButton.set(this.onlyCrafted ? YesNo.YES : YesNo.NO);
            this.rebuild();
        } else if (btn == this.tallModeButton) {
            this.tallMode = !this.tallMode;
            AEConfig.instance.getConfigManager()
                .putSetting(Settings.TERMINAL_STYLE, this.tallMode ? TerminalStyle.TALL : TerminalStyle.SMALL);
            this.setWorldAndResolution(this.mc, this.width, this.height);
        } else if (btn == this.clearButton) {
            if (isShiftKeyDown()) {
                ClientHandler.send(MsgAction.CLEAR);
            }
        } else if (btn == this.refreshButton) {
            ClientHandler.requestHistory(false);
        } else {
            super.actionPerformed(btn);
        }
    }

    private void goBack() {
        if (this.parent != null) {
            this.mc.displayGuiScreen(this.parent);
            // CPU updates were ignored while this screen was open, ask the server to send everything again
            ClientHandler.send(MsgAction.RESYNC);
        } else {
            this.mc.thePlayer.closeScreen();
        }
    }

    @Override
    protected void keyTyped(final char character, final int key) {
        if (key == Keyboard.KEY_ESCAPE) {
            this.goBack();
            return;
        }
        if (this.searchField.textboxKeyTyped(character, key)) {
            return;
        }
        super.keyTyped(character, key);
    }

    @Override
    protected void mouseClicked(final int xCoord, final int yCoord, final int btn) {
        super.mouseClicked(xCoord, yCoord, btn);
        this.searchField.mouseClicked(xCoord, yCoord, btn);
        this.tableScrollbar.click(this, xCoord - this.guiLeft, yCoord - this.guiTop);

        final int hit = this.hitRecord(xCoord - this.guiLeft, yCoord - this.guiTop);
        if (hit >= 0 && hit != this.selected) {
            this.selected = hit;
            this.getScrollBar()
                .setCurrentScroll(0);
            this.rebuild();
        }
    }

    @Override
    protected void handleMouseClick(final Slot slot, final int slotIdx, final int clickedButton, final int clickType) {
        // this screen has no real inventory, never send slot clicks to the server
    }

    @Override
    protected boolean checkHotbarKeys(final int keyCode) {
        return false;
    }

    @Override
    protected void mouseClickMove(final int x, final int y, final int c, final long d) {
        super.mouseClickMove(x, y, c, d);
        this.tableScrollbar.clickMove(y - this.guiTop);
    }

    @Override
    protected void mouseMovedOrUp(final int x, final int y, final int state) {
        super.mouseMovedOrUp(x, y, state);
        if (state >= 0) {
            this.tableScrollbar.release();
        }
    }

    @Override
    protected boolean mouseWheelEvent(final int x, final int y, final int wheel) {
        final int rx = x - this.guiLeft;
        final int ry = y - this.guiTop;
        if (rx >= -TABLE_WIDTH && rx < 0 && ry >= 0 && ry < this.getTableHeight()) {
            this.tableScrollbar.wheel(wheel);
            return true;
        }
        return false;
    }

    /** @return index of the order under the cursor, -1 if none. Coordinates are relative to the GUI. */
    private int hitRecord(final int x, final int y) {
        if (x < TABLE_SLOT_X || x >= TABLE_SLOT_X + TABLE_SLOT_WIDTH
            || y < TABLE_SLOT_Y
            || y >= TABLE_SLOT_Y + this.rows * TABLE_SLOT_HEIGHT) {
            return -1;
        }
        final int index = this.tableScrollbar.getCurrentScroll() + (y - TABLE_SLOT_Y) / TABLE_SLOT_HEIGHT;
        return index < this.records.size() ? index : -1;
    }

    /** @return index (into the visible part of the grid) of the entry under the cursor, -1 if none. */
    private int hitEntry(final int x, final int y) {
        for (int slot = 0; slot < COLUMNS * this.rows; slot++) {
            final int column = slot % COLUMNS;
            final int row = slot / COLUMNS;
            final int minX = ITEMSTACK_LEFT_OFFSET + column * (1 + SECTION_LENGTH);
            final int minY = ITEMSTACK_TOP_OFFSET - 4 + row * SECTION_HEIGHT;
            if (x > minX && x < minX + SECTION_LENGTH && y > minY && y < minY + SECTION_HEIGHT) {
                return slot;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void drawScreen(final int mouseX, final int mouseY, final float partialTicks) {
        this.clearButton.enabled = !this.records.isEmpty();
        this.clearButton.displayString = (isShiftKeyDown() ? EnumChatFormatting.RED : "") + tr("clear");
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.bindTexture("guis/craftingcpu.png");
        if (this.tallMode) {
            this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, TEXTURE_BELOW_TOP_ROW_Y);
            int y = TEXTURE_BELOW_TOP_ROW_Y;
            // first and last row are pre-baked
            for (int row = 1; row < this.rows - 1; row++) {
                this.drawTexturedModalRect(
                    offsetX,
                    offsetY + y,
                    0,
                    TEXTURE_BELOW_TOP_ROW_Y,
                    this.xSize,
                    SECTION_HEIGHT);
                y += SECTION_HEIGHT;
            }
            this.drawTexturedModalRect(
                offsetX,
                offsetY + y,
                0,
                GUI_HEIGHT - TEXTURE_ABOVE_BOTTOM_ROW_Y,
                this.xSize,
                TEXTURE_ABOVE_BOTTOM_ROW_Y);
        } else {
            this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);
        }

        this.bindTexture("guis/cpu_selector.png");
        final int tableLeft = offsetX - TABLE_WIDTH;
        if (this.tallMode) {
            this.drawTexturedModalRect(tableLeft, offsetY, 0, 0, TABLE_WIDTH, 41);
            int y = 41;
            for (int row = 1; row < this.rows - 1; row++) {
                this.drawTexturedModalRect(tableLeft, offsetY + y, 0, 41, TABLE_WIDTH, 23);
                y += 23;
            }
            this.drawTexturedModalRect(tableLeft, offsetY + y, 0, 133, TABLE_WIDTH, 31);
        } else {
            this.drawTexturedModalRect(tableLeft, offsetY, 0, 0, TABLE_WIDTH, this.getTableHeight());
        }
        // same 3 missing border pixels AE2 fixes for its CPU table
        this.drawTexturedModalRect(tableLeft + 94, offsetY, 93, 0, 1, 1);
        this.drawTexturedModalRect(tableLeft + 95, offsetY, 93, 0, 1, 1);
        this.drawTexturedModalRect(tableLeft + 94, offsetY + 1, 93, 1, 1, 1);

        this.bindTexture("guis/searchField.png");
        this.drawTexturedModalRect(offsetX + this.xSize - 101, offsetY + 5, 0, 0, 76, 12);
        this.searchField.drawTextBox();
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.drawHeader(mouseX - offsetX, mouseY - offsetY);
        this.drawTable(mouseX - offsetX, mouseY - offsetY);
        this.drawEntries(mouseX - offsetX, mouseY - offsetY);
    }

    private void drawHeader(final int mouseX, final int mouseY) {
        final CraftRecord record = this.getSelected();
        String title;
        if (record == null) {
            title = tr("title");
        } else {
            title = formatTime(record.duration) + " - " + Platform.getItemDisplayName(record.output);
        }
        title = this.trim(title, 125);
        this.fontRendererObj.drawString(title, 8, 7, COLOR_TEXT);

        if (record != null && mouseX >= 8 && mouseX < 133 && mouseY >= 5 && mouseY < 17) {
            this.drawTooltip(mouseX, mouseY, this.buildRecordTooltip(record));
        }

        if (this.visible.isEmpty()) {
            final String message;
            if (this.records.isEmpty()) {
                message = tr(this.collectionEnabled ? "empty" : "disabled");
            } else {
                message = tr("nothing");
            }
            final List<String> lines = this.fontRendererObj.listFormattedStringToWidth(message, 190);
            int y = SCROLLBAR_TOP + (this.ySize - 47) / 2 - lines.size() * 5;
            for (final String line : lines) {
                this.fontRendererObj
                    .drawString(line, 113 - this.fontRendererObj.getStringWidth(line) / 2, y, COLOR_TEXT);
                y += 10;
            }
        }
    }

    private void drawTable(final int mouseX, final int mouseY) {
        this.tableScrollbar.draw(this);
        final int first = this.tableScrollbar.getCurrentScroll();
        final int hovered = this.hitRecord(mouseX, mouseY);

        for (int i = first; i < first + this.rows && i < this.records.size(); i++) {
            final CraftRecord record = this.records.get(i);
            final int x = TABLE_SLOT_X;
            final int y = TABLE_SLOT_Y + (i - first) * TABLE_SLOT_HEIGHT;

            if (i == this.selected) {
                GL11.glColor4f(0.0F, 0.8352F, 1.0F, 1.0F);
            } else if (i == hovered) {
                GL11.glColor4f(0.65F, 0.9F, 1.0F, 1.0F);
            } else {
                GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            }
            this.bindTexture("guis/cpu_selector.png");
            this.drawTexturedModalRect(x, y, 100, 0, TABLE_SLOT_WIDTH, TABLE_SLOT_HEIGHT);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

            // first line: number of the order and its duration
            final String name = "#" + (i + 1) + " " + formatTime(record.duration);
            GL11.glPushMatrix();
            GL11.glTranslatef(x + 3, y + 3, 0);
            GL11.glScalef(0.8f, 0.8f, 1.0f);
            this.fontRendererObj.drawString(name, 0, 0, statusColor(record.status));
            GL11.glPopMatrix();

            // second line: requested amount
            String amount = this.numberFormat.format(record.requested);
            if (amount.length() > 9) {
                amount = ReadableNumberConverter.INSTANCE.toWideReadableForm(record.requested);
            }
            GL11.glPushMatrix();
            GL11.glTranslatef(x + 3, y + 12, 0);
            GL11.glScalef(0.75f, 0.75f, 1.0f);
            this.fontRendererObj.drawString(amount, 0, 0, COLOR_NAME);
            GL11.glPopMatrix();

            this.drawStack(record.output, x + TABLE_SLOT_WIDTH - 19, y + 3);

            // status bar at the bottom of the slot
            drawRect(
                x + 1,
                y + TABLE_SLOT_HEIGHT - 2,
                x + TABLE_SLOT_WIDTH - 2,
                y + TABLE_SLOT_HEIGHT - 1,
                statusBarColor(record.status));
        }

        if (hovered >= 0) {
            this.drawTooltip(mouseX, mouseY, this.buildRecordTooltip(this.records.get(hovered)));
        }
    }

    private void drawEntries(final int mouseX, final int mouseY) {
        final CraftRecord record = this.getSelected();
        if (record == null) {
            return;
        }
        final int start = this.getScrollBar()
            .getCurrentScroll() * COLUMNS;
        final int end = Math.min(start + COLUMNS * this.rows, this.visible.size());
        final int hovered = this.hitEntry(mouseX, mouseY);
        String tooltip = null;

        for (int index = start; index < end; index++) {
            final ItemStat stat = this.visible.get(index);
            final int slot = index - start;
            final int column = slot % COLUMNS;
            final int row = slot / COLUMNS;
            final int cellX = column * (1 + SECTION_LENGTH) + ITEMSTACK_LEFT_OFFSET;

            final long time = stat.getCraftTime(record.duration);

            final List<String> lines = new ArrayList<>();
            if (stat.isCrafted()) {
                lines.add(
                    tr("crafted") + ": "
                        + ReadableNumberConverter.INSTANCE.toWideReadableForm(Math.max(stat.returned, stat.planned)));
            }
            if (stat.fromStorage > 0) {
                lines.add(tr("stored") + ": " + ReadableNumberConverter.INSTANCE.toWideReadableForm(stat.fromStorage));
            }
            if (stat.emitted > 0) {
                lines.add(tr("missing") + ": " + ReadableNumberConverter.INSTANCE.toWideReadableForm(stat.emitted));
            }
            if (time > 0) {
                lines.add(tr("time") + ": " + formatTime(time));
            }

            GL11.glPushMatrix();
            GL11.glScaled(0.5, 0.5, 0.5);
            final int negY = ((lines.size() - 1) * 5) / 2;
            int downY = 0;
            for (final String line : lines) {
                final int width = 4 + this.fontRendererObj.getStringWidth(line);
                this.fontRendererObj.drawString(
                    line,
                    (int) ((cellX + SECTION_LENGTH - 19 - (width * 0.5)) * 2),
                    (row * SECTION_HEIGHT + ITEMSTACK_TOP_OFFSET + 6 - negY + downY) * 2,
                    COLOR_TEXT);
                downY += 5;
            }
            GL11.glPopMatrix();

            this.drawStack(stat.stack, cellX + SECTION_LENGTH - 19, row * SECTION_HEIGHT + ITEMSTACK_TOP_OFFSET);

            if (slot == hovered) {
                tooltip = this.buildStatTooltip(record, stat);
            }
        }

        if (tooltip != null) {
            this.drawTooltip(mouseX, mouseY, tooltip);
        }
    }

    private void drawStack(final IAEStack<?> stack, final int x, final int y) {
        if (stack == null) {
            return;
        }
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_LIGHTING_BIT);
        RenderHelper.enableGUIStandardItemLighting();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        stack.drawInGui(this.mc, x, y);
        GL11.glPopAttrib();
    }

    // ------------------------------------------------------------------ tooltips

    private String buildRecordTooltip(final CraftRecord record) {
        final EnumChatFormatting green = EnumChatFormatting.GREEN;
        final EnumChatFormatting reset = EnumChatFormatting.RESET;
        final StringBuilder sb = new StringBuilder();
        sb.append(Platform.getItemDisplayName(record.output))
            .append('\n');
        sb.append(green)
            .append(tr("requested"))
            .append(reset)
            .append(": ")
            .append(this.numberFormat.format(record.requested))
            .append('\n');
        sb.append(green)
            .append(tr("status"))
            .append(reset)
            .append(": ")
            .append(statusText(record.status))
            .append('\n');
        sb.append(green)
            .append(tr("started"))
            .append(reset)
            .append(": ")
            .append(new SimpleDateFormat("dd.MM HH:mm:ss").format(new Date(record.startTime)))
            .append('\n');
        sb.append(green)
            .append(tr("duration"))
            .append(reset)
            .append(": ")
            .append(formatTime(record.duration))
            .append('\n');
        if (record.aeElapsed > 0) {
            sb.append(green)
                .append(tr("aetime"))
                .append(reset)
                .append(": ")
                .append(formatTime(record.aeElapsed / 1_000_000L))
                .append('\n');
        }
        if (record.cpuName != null && !record.cpuName.isEmpty()) {
            sb.append(green)
                .append(tr("cpu"))
                .append(reset)
                .append(": ")
                .append(record.cpuName)
                .append('\n');
        }
        sb.append(green)
            .append(tr("player"))
            .append(reset)
            .append(": ")
            .append(record.player == null || record.player.isEmpty() ? tr("machine") : record.player)
            .append('\n');
        if (record.coProcessors > 0) {
            sb.append(green)
                .append(tr("coprocessors"))
                .append(reset)
                .append(": ")
                .append(this.numberFormat.format(record.coProcessors))
                .append('\n');
        }
        if (record.bytes > 0) {
            sb.append(green)
                .append(tr("bytes"))
                .append(reset)
                .append(": ")
                .append(this.numberFormat.format(record.bytes))
                .append('\n');
        }
        return sb.toString();
    }

    private String buildStatTooltip(final CraftRecord record, final ItemStat stat) {
        final EnumChatFormatting green = EnumChatFormatting.GREEN;
        final EnumChatFormatting reset = EnumChatFormatting.RESET;
        final StringBuilder sb = new StringBuilder();
        sb.append(Platform.getItemDisplayName(stat.stack))
            .append('\n');
        if (stat.isCrafted()) {
            sb.append(green)
                .append(tr("crafted"))
                .append(reset)
                .append(": ")
                .append(this.numberFormat.format(stat.returned))
                .append(" / ")
                .append(this.numberFormat.format(stat.planned))
                .append(' ')
                .append(tr("planned"))
                .append('\n');
            sb.append(green)
                .append(tr("pushes"))
                .append(reset)
                .append(": ")
                .append(this.numberFormat.format(stat.pushes))
                .append('\n');
        }
        if (stat.fromStorage > 0) {
            sb.append(green)
                .append(tr("stored"))
                .append(reset)
                .append(": ")
                .append(this.numberFormat.format(stat.fromStorage))
                .append('\n');
        }
        if (stat.emitted > 0) {
            sb.append(green)
                .append(tr("missing"))
                .append(reset)
                .append(": ")
                .append(this.numberFormat.format(stat.emitted))
                .append('\n');
        }
        final long time = stat.getCraftTime(record.duration);
        if (time > 0) {
            sb.append(green)
                .append(tr("time"))
                .append(reset)
                .append(": ")
                .append(formatTime(time));
            if (record.duration > 0) {
                sb.append(" (")
                    .append(EnumChatFormatting.GOLD)
                    .append(String.format("%.0f%%", 100.0 * time / record.duration))
                    .append(reset)
                    .append(' ')
                    .append(tr("oforder"))
                    .append(')');
            }
            sb.append('\n');
            sb.append(green)
                .append(tr("firstpush"))
                .append(reset)
                .append(": +")
                .append(formatTime(stat.firstPush))
                .append('\n');
            if (stat.lastReturn >= 0) {
                sb.append(green)
                    .append(tr("lastreturn"))
                    .append(reset)
                    .append(": +")
                    .append(formatTime(stat.lastReturn))
                    .append('\n');
            }
            if (stat.pushes > 0) {
                sb.append(green)
                    .append(tr("perpush"))
                    .append(reset)
                    .append(": ")
                    .append(formatTime(time / stat.pushes))
                    .append('\n');
            }
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ helpers

    private static int statusColor(final int status) {
        return switch (status) {
            case CraftRecord.STATUS_RUNNING -> 0x8A6A00;
            case CraftRecord.STATUS_CANCELLED -> 0xA02020;
            default -> COLOR_NAME;
        };
    }

    private static int statusBarColor(final int status) {
        return switch (status) {
            case CraftRecord.STATUS_RUNNING -> 0xFFE0C020;
            case CraftRecord.STATUS_CANCELLED -> 0xFFD03030;
            default -> 0xFF40C040;
        };
    }

    private static String statusText(final int status) {
        final String text = switch (status) {
            case CraftRecord.STATUS_RUNNING -> EnumChatFormatting.YELLOW + tr("status.running");
            case CraftRecord.STATUS_CANCELLED -> EnumChatFormatting.RED + tr("status.cancelled");
            default -> EnumChatFormatting.GREEN + tr("status.done");
        };
        return text + EnumChatFormatting.RESET;
    }

    private String trim(final String text, final int width) {
        if (this.fontRendererObj.getStringWidth(text) <= width) {
            return text;
        }
        return this.fontRendererObj.trimStringToWidth(text, width - this.fontRendererObj.getStringWidth("...")) + "...";
    }

    /** Formats milliseconds as "4.2s", "3:05" or "1:02:03". */
    public static String formatTime(final long millis) {
        if (millis < 0) {
            return "-";
        }
        if (millis < 60_000L) {
            return String.format("%.1f%s", millis / 1000.0, tr("seconds"));
        }
        final long seconds = millis / 1000L;
        if (seconds < 3600L) {
            return String.format("%d:%02d", seconds / 60, seconds % 60);
        }
        return String.format("%d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60);
    }

    private static String tr(final String key) {
        return StatCollector.translateToLocal("craftinspector.gui." + key);
    }
}
