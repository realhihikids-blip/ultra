package com.pvpclient.gui;

import com.pvpclient.util.Lang;
import com.pvpclient.PvpClient;
import com.pvpclient.config.ConfigManager;
import com.pvpclient.module.Category;
import com.pvpclient.module.ClientModule;
import com.pvpclient.module.ModuleManager;
import com.pvpclient.setting.Setting;
import com.pvpclient.util.RenderUtil;
import com.pvpclient.util.Smooth;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** The menu. Everything is drawn in a fixed 540x330 local space that is scaled by the GUI Scale setting. */
public final class ClickGuiScreen extends Screen {
    static final int W = 540, H = 330, SIDE = 120, LIST_TOP = 40, GAP = 6;
    private static final Identifier BLIP_LOGO = Identifier.of("pvpclient", "textures/gui/blip_logo.png");
    private static Category remembered = Category.COMBAT;

    private final ModuleManager manager = PvpClient.modules();
    private final Map<Category, List<GuiEntry>> pages = new EnumMap<>(Category.class);
    private final Smooth open = new Smooth(0);
    private final Smooth pill = new Smooth(0);
    private final Smooth content = new Smooth(1);
    private final Smooth scroll = new Smooth(0);
    private final Smooth[] catAnim = new Smooth[Category.values().length];

    private Category category = remembered;
    private double scrollTarget;
    private ClientModule bindTarget;
    private float s = 1f;
    private int ox, oy;

    private final SearchBar search = new SearchBar();
    private final Map<ClientModule, ModuleCard> cards = new LinkedHashMap<>();
    private final List<GuiEntry> searchEntries = new ArrayList<>();
    private String lastQuery = "";

    public ClickGuiScreen() {
        super(Text.literal("Blip Client"));
        for (Category c : Category.values()) {
            List<GuiEntry> list = new ArrayList<>();
            for (ClientModule m : manager.inCategory(c)) {
                ModuleCard card = new ModuleCard(this, m);
                cards.put(m, card);
                list.add(card);
            }
            if (c == Category.SETTINGS) list.add(new KeybindsCard(this, manager.all()));
            pages.put(c, list);
        }
        for (int i = 0; i < catAnim.length; i++) catAnim[i] = new Smooth(0);
        pill.snap(pillY(category));
    }

    ModuleManager manager() { return manager; }
    ClientModule bindTarget() { return bindTarget; }
    void setBindTarget(ClientModule m) { bindTarget = m; }

    private static int pillY(Category c) { return 78 + c.ordinal() * 32; }

    // ---------------------------------------------------------------- transform helpers

    private void updateTransform() {
        double wanted = PvpClient.settings().guiScale.get();
        s = (float) Math.max(0.5, Math.min(wanted, Math.min((width - 10f) / W, (height - 10f) / H)));
        ox = Math.round(width / 2f - W * s / 2f);
        oy = Math.round(height / 2f - H * s / 2f);
    }

    private int lx(double mouse) { return (int) Math.floor((mouse - ox) / s); }
    private int ly(double mouse) { return (int) Math.floor((mouse - oy) / s); }

    /**
     * Scissor in menu-local coordinates. DrawContext (1.21.4) already applies the current matrix
     * (our translate + scale) to the rectangle, so local coordinates are passed through unchanged.
     */
    void scissor(DrawContext c, int x1, int y1, int x2, int y2) {
        c.enableScissor(x1, y1, x2, y2);
    }

    private int listX() { return SIDE + 6; }
    private int listW() { return W - listX() - 12; }
    private int listBottom() { return H - 10; }
    private int listStartY() { return Math.round((float) (LIST_TOP - scroll.get() + (1.0 - content.get()) * 12.0)); }

    // ---------------------------------------------------------------- render

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        updateTransform();
        open.to(1);
        double fade = open.update();
        Theme.fade = fade;
        c.fill(0, 0, width, height, Theme.f(0x99050509));

        int mx = lx(mouseX), my = ly(mouseY);
        MatrixStack ms = c.getMatrices();
        ms.push();
        ms.translate(ox, oy, 0f);
        ms.scale(s, s, 1f);

        // window
        for (int i = 4; i >= 1; i--) {
            RenderUtil.roundedRect(c, -i, -i, W + 2 * i, H + 2 * i, 9 + i, Theme.f(RenderUtil.withAlpha(0x000000, 0.06)));
        }
        RenderUtil.panel(c, 0, 0, W, H, 9, Theme.f(Theme.BG), Theme.f(0xFF2A2A40));
        RenderUtil.hGradient(c, 10, 0, W - 20, 2, Theme.f(Theme.a1()), Theme.f(Theme.a2()));

        renderSidebar(c, mx, my);
        renderContent(c, mx, my);

        ms.pop();
        Theme.fade = 1.0;
    }

    private void renderSidebar(DrawContext c, int mx, int my) {
        RenderUtil.roundedRect(c, 8, 8, SIDE - 8, H - 16, 7, Theme.f(Theme.SIDEBAR));
        // The shared brand texture keeps the ClickGUI and title screen visually consistent.
        c.drawTexture(RenderLayer::getGuiTextured, BLIP_LOGO,
                14, 8, 0.0F, 0.0F, 92, 61, 1200, 796);

        pill.to(pillY(category));
        int py = Math.round((float) pill.update());
        RenderUtil.roundedRect(c, 14, py, SIDE - 20, 26, 6, Theme.f(RenderUtil.withAlpha(Theme.a1() & 0xFFFFFF, 0.20)));
        c.fill(14, py + 6, 16, py + 20, Theme.f(Theme.a1()));

        for (Category cat : Category.values()) {
            int y = pillY(cat);
            boolean hov = mx >= 14 && mx < SIDE - 6 && my >= y && my < y + 26;
            Smooth a = catAnim[cat.ordinal()];
            a.to(cat == category ? 1 : hov ? 0.55 : 0);
            double t = a.update();
            int col = RenderUtil.lerpColor(Theme.DIM, Theme.TEXT, t);
            RenderUtil.roundedRect(c, 24, y + 10, 5, 5, 2, Theme.f(RenderUtil.lerpColor(0xFF3A3A55, Theme.a1(), t)));
            c.drawText(textRenderer, Lang.tr(cat.label), 36 + (int) Math.round(t * 2), y + 9, Theme.f(col), false);
        }
        RenderUtil.text(c, textRenderer, Lang.tr("=  open / close"), 18, H - 24, 0.7f, Theme.f(Theme.DIM), false);
    }

    private void renderContent(DrawContext c, int mx, int my) {
        int x0 = listX(), w = listW();
        String title = search.active() ? Lang.tr("Search results") : Lang.tr(category.label);
        RenderUtil.text(c, textRenderer, title, x0 + 2, 14, 1.4f, Theme.f(Theme.TEXT), true);
        RenderUtil.hGradient(c, x0 + 2, 31, 36, 2, Theme.f(Theme.a1()), Theme.f(Theme.a2()));
        search.render(c, textRenderer, x0 + w - 170, 9, 170, 18, mx, my);

        double cf = content.update();
        double saved = Theme.fade;
        Theme.fade = saved * cf;

        int viewTop = LIST_TOP, viewBottom = listBottom();
        int hy = (my >= viewTop && my <= viewBottom && mx >= x0 && mx <= x0 + w) ? my : -10000;

        scroll.to(scrollTarget);
        scroll.update();
        int y = listStartY();
        double total = 0;
        scissor(c, x0 - 4, viewTop, x0 + w + 4, viewBottom);
        for (GuiEntry e : activePage()) {
            if (y < viewBottom + 400 && y + e.height() > viewTop - 400) e.render(c, x0, y, w, mx, hy);
            int h = e.height() + GAP;
            y += h;
            total += h;
        }
        c.disableScissor();

        double viewH = viewBottom - viewTop;
        double max = Math.max(0, total - viewH);
        scrollTarget = Math.max(0, Math.min(max, scrollTarget));
        if (max > 0) {
            int th = (int) Math.max(24, viewH * viewH / total);
            int ty = viewTop + (int) ((viewH - th) * (scroll.get() / max));
            RenderUtil.roundedRect(c, W - 9, viewTop, 3, (int) viewH, 1, Theme.f(0xFF1C1C2E));
            RenderUtil.roundedRect(c, W - 9, ty, 3, th, 1, Theme.f(Theme.a1()));
        }
        Theme.fade = saved;
    }

    // ---------------------------------------------------------------- input

    private void select(Category cat) {
        boolean wasSearching = search.active();
        search.clear();
        if (cat == category && !wasSearching) return;
        category = cat;
        remembered = cat;
        scrollTarget = 0;
        scroll.snap(0);
        content.snap(0);
        content.to(1);
        bindTarget = null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        updateTransform();
        int mx = lx(mouseX), my = ly(mouseY);

        if (search.click(mx, my)) {
            bindTarget = null;
            return true;
        }

        for (Category cat : Category.values()) {
            int y = pillY(cat);
            if (mx >= 14 && mx < SIDE - 6 && my >= y && my < y + 26) {
                select(cat);
                return true;
            }
        }
        bindTarget = null; // clicking anywhere cancels listening; bind buttons re-arm it below

        if (my >= LIST_TOP && my <= listBottom() && mx >= listX() && mx <= listX() + listW()) {
            int y = listStartY();
            for (GuiEntry e : activePage()) {
                if (e.click(listX(), y, listW(), mx, my, button)) return true;
                y += e.height() + GAP;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        int mx = lx(mouseX);
        for (GuiEntry e : activePage()) e.drag(mx);
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (GuiEntry e : activePage()) e.release();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        scrollTarget -= vertical * 28.0;
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (bindTarget != null) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE || keyCode == GLFW.GLFW_KEY_DELETE) {
                bindTarget.setKeyCode(GLFW.GLFW_KEY_UNKNOWN);
            } else if (keyCode != GLFW.GLFW_KEY_ESCAPE) {
                bindTarget.setKeyCode(keyCode);
            }
            bindTarget = null;
            return true;
        }
        if (search.focused() && search.key(keyCode)) return true;
        if (PvpClient.isOpenKey(keyCode, scanCode)) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (search.charTyped(chr)) return true;
        return super.charTyped(chr, modifiers);
    }

    /** The category page, or the cross-category search results while the search field has text. */
    private List<GuiEntry> activePage() {
        if (!search.active()) {
            lastQuery = "";
            return pages.get(category);
        }
        String q = search.query();
        if (!q.equals(lastQuery)) {
            lastQuery = q;
            searchEntries.clear();
            for (Map.Entry<ClientModule, ModuleCard> e : cards.entrySet()) {
                if (matches(e.getKey(), q)) searchEntries.add(e.getValue());
            }
            scrollTarget = 0;
            scroll.snap(0);
        }
        return searchEntries;
    }

    /** Matches the English or Russian name/description of a module, or any of its setting names. */
    private static boolean matches(ClientModule m, String q) {
        if (has(m.getName(), q) || has(m.getDescription(), q)) return true;
        for (Setting<?> st : m.getSettings()) if (has(st.getName(), q)) return true;
        return false;
    }

    private static boolean has(String text, String q) {
        return text.toLowerCase(Locale.ROOT).contains(q) || Lang.tr(text).toLowerCase(Locale.ROOT).contains(q);
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void removed() {
        ConfigManager.save(manager);
        super.removed();
    }
}
