package com.pvpclient.module.visual;

import com.pvpclient.module.Category;
import com.pvpclient.module.ClientModule;
import com.pvpclient.setting.BooleanSetting;
import com.pvpclient.setting.ColorSetting;
import com.pvpclient.setting.ModeSetting;
import com.pvpclient.setting.NumberSetting;
import com.pvpclient.util.RenderUtil;
import com.pvpclient.util.Smooth;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

import java.util.*;

/**
 * Floating info panel above each player: status dot, nickname (team colored), armor row with
 * enchantments stacked above each piece, and the held item. World positions are projected to the
 * screen during the world pass and the panels are drawn in the HUD pass.
 */
public final class PlayerInfoModule extends ClientModule {
    private static final EquipmentSlot[] ARMOR = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final int GREEN = 0x22C55E;

    // Toggles
    private final BooleanSetting showNames = add(new BooleanSetting("Show Nicknames", true));
    private final BooleanSetting showDot = add(new BooleanSetting("Status Dot", true));
    private final BooleanSetting showArmor = add(new BooleanSetting("Show Armor", true));
    private final BooleanSetting showEnchants = add(new BooleanSetting("Show Enchantments", true));
    private final BooleanSetting showHeld = add(new BooleanSetting("Show Held Item", true));
    private final ModeSetting enchantFormat = add(new ModeSetting("Enchant Format", 0, "Short (P4)", "Full (Protection IV)"));
    // Sizing / look
    private final NumberSetting textSize = add(new NumberSetting("Text Size", 1.0, 0.6, 2.0, 0.05));
    private final NumberSetting panelScale = add(new NumberSetting("Panel Scale", 1.0, 0.5, 2.0, 0.05));
    private final NumberSetting opacity = add(new NumberSetting("Background Opacity", 0.7, 0.1, 1.0, 0.05));
    private final NumberSetting distance = add(new NumberSetting("Display Distance", 48, 8, 128, 1));
    // Colors
    private final ColorSetting background = add(new ColorSetting("Background Color", 0x0B0B14,
            0x0B0B14, 0x14101F, 0x0F172A, 0x1A1A1A, 0x000000));
    private final ColorSetting border = add(new ColorSetting("Border Color", 0x8B5CF6));
    private final BooleanSetting teamBorder = add(new BooleanSetting("Team Color Border", true));

    private record Anchor(AbstractClientPlayerEntity player, float x, float y, double dist) {}
    private record Line(String text, int rgb) {}
    private record Slot(ItemStack stack, List<Line> lines, int width, boolean armorSlot) {}

    private final List<Anchor> anchors = new ArrayList<>();
    private final Map<UUID, Smooth> fades = new HashMap<>();

    public PlayerInfoModule() {
        super("Player Info", "Floating panel with name, armor, enchants and held item (private worlds only)", Category.VISUALS);
        markRestricted();
    }

    @Override
    protected void onDisable() {
        anchors.clear();
        fades.clear();
    }

    // ------------------------------------------------------------------ world pass: project anchors

    @Override
    public void onWorldRender(WorldRenderContext ctx) {
        anchors.clear();
        if (mc.world == null || mc.player == null) return;

        Camera camera = ctx.camera();
        Vec3d cp = camera.getPos();
        Matrix4f view = new Matrix4f().rotation(camera.getRotation().conjugate(new Quaternionf()));
        Matrix4f proj = ctx.projectionMatrix();
        float td = mc.getRenderTickCounter().getTickDelta(false);
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        double range = distance.get();
        Vector4f v = new Vector4f();

        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player || p.isSpectator() || !p.isAlive()) continue;
            double dist = cp.distanceTo(p.getPos());
            if (dist > range) continue;

            double x = MathHelper.lerp(td, p.prevX, p.getX());
            double y = MathHelper.lerp(td, p.prevY, p.getY());
            double z = MathHelper.lerp(td, p.prevZ, p.getZ());
            v.set((float) (x - cp.x), (float) (y + p.getHeight() + 0.85 - cp.y), (float) (z - cp.z), 1f);
            view.transform(v);
            proj.transform(v);
            if (v.w <= 0.05f) continue;

            float sx = (v.x / v.w * 0.5f + 0.5f) * sw;
            float sy = (1f - (v.y / v.w * 0.5f + 0.5f)) * sh;
            if (sx < -200 || sx > sw + 200 || sy < -100 || sy > sh + 200) continue;
            anchors.add(new Anchor(p, sx, sy, dist));
        }
        anchors.sort(Comparator.comparingDouble(Anchor::dist).reversed());
        while (anchors.size() > 24) anchors.remove(0); // keep the nearest 24
    }

    // ------------------------------------------------------------------ hud pass: draw panels

    @Override
    public void onHudRender(DrawContext c, float tickDelta) {
        Set<UUID> seen = new HashSet<>();
        for (Anchor a : anchors) {
            UUID id = a.player().getUuid();
            seen.add(id);
            Smooth s = fades.computeIfAbsent(id, k -> new Smooth(0));
            s.to(1);
            double edge = MathHelper.clamp((distance.get() - a.dist()) / 8.0, 0.0, 1.0);
            drawPanel(c, a, s.update() * edge);
        }
        fades.keySet().retainAll(seen);
    }

    private void drawPanel(DrawContext c, Anchor a, double fade) {
        if (fade < 0.02) return;
        TextRenderer tr = mc.textRenderer;
        AbstractClientPlayerEntity p = a.player();

        float ts = textSize.getFloat();
        float es = ts * 0.55f;                       // enchant text scale
        float lineH = 9f * es + 1f;
        float distScale = MathHelper.clamp(16f / (float) Math.max(a.dist(), 1.0), 0.65f, 1.25f);
        float scale = panelScale.getFloat() * distScale;
        final int pad = 5;

        // ---- name row
        Text name = p.getDisplayName();
        boolean doName = showNames.get();
        boolean doDot = showDot.get();
        boolean nameRow = doName || doDot;
        float nameH = 9f * ts;
        float dot = 5f;
        float rowW = 0f;
        if (doDot) rowW += dot;
        if (doDot && doName) rowW += 4f;
        if (doName) rowW += tr.getWidth(name) * ts;

        // ---- item slots
        List<Slot> slots = new ArrayList<>();
        if (showArmor.get()) {
            for (EquipmentSlot es2 : ARMOR) slots.add(buildSlot(p.getEquippedStack(es2), true, es, tr));
        }
        ItemStack held = p.getMainHandStack();
        boolean doHeld = showHeld.get() && !held.isEmpty();
        Slot heldSlot = doHeld ? buildSlot(held, false, es, tr) : null;

        boolean hasItems = !slots.isEmpty() || doHeld;
        int itemsW = 0;
        for (Slot s : slots) itemsW += s.width();
        if (slots.size() > 1) itemsW += 3 * (slots.size() - 1);
        if (doHeld) itemsW += (slots.isEmpty() ? 0 : 9) + heldSlot.width();

        int maxLines = 0;
        for (Slot s : slots) maxLines = Math.max(maxLines, s.lines().size());
        if (doHeld) maxLines = Math.max(maxLines, heldSlot.lines().size());
        float enchH = maxLines > 0 ? maxLines * lineH + 2f : 0f;

        int W = (int) Math.ceil(Math.max(Math.max(rowW, itemsW), 36f)) + pad * 2;
        float contentH = 0f;
        if (nameRow) contentH += nameH;
        if (nameRow && hasItems) contentH += 4f;
        if (hasItems) contentH += enchH + 16f;
        int H = (int) Math.ceil(contentH) + pad * 2;
        if (!nameRow && !hasItems) return;

        // ---- colors
        int bg = RenderUtil.withAlpha(background.get(), opacity.get() * fade);
        int borderRgb = border.get();
        AbstractTeam team = p.getScoreboardTeam();
        if (teamBorder.get() && team != null && team.getColor().getColorValue() != null) {
            borderRgb = team.getColor().getColorValue();
        }

        // ---- transform: anchor at bottom-center, slide up while fading in
        MatrixStack m = c.getMatrices();
        m.push();
        m.translate(a.x(), a.y() - (1.0 - fade) * 8.0, 0.0);
        m.scale(scale, scale, 1f);
        m.translate(-W / 2f, -H - 5f, 0f);

        RenderUtil.roundedRect(c, -2, -2, W + 4, H + 4, 7, RenderUtil.withAlpha(0x000000, 0.22 * fade));
        RenderUtil.roundedRect(c, -1, -1, W + 2, H + 2, 6, RenderUtil.withAlpha(borderRgb, 0.85 * fade));
        RenderUtil.roundedRect(c, 0, 0, W, H, 5, bg);
        for (int i = 0; i < 4; i++) { // little pointer towards the player
            c.fill(W / 2 - (3 - i), H + i, W / 2 + (3 - i) + 1, H + i + 1, RenderUtil.withAlpha(background.get(), Math.max(opacity.get(), 0.5) * fade));
        }

        int white = RenderUtil.withAlpha(0xFFFFFF, fade);
        float y = pad;

        // ---- name row
        if (nameRow) {
            float x = (W - rowW) / 2f;
            if (doDot) {
                float dy = y + (nameH - dot) / 2f;
                RenderUtil.roundedRect(c, (int) x - 1, (int) dy - 1, 7, 7, 3, RenderUtil.withAlpha(GREEN, 0.28 * fade));
                RenderUtil.roundedRect(c, (int) x, (int) dy, 5, 5, 2, RenderUtil.withAlpha(GREEN, fade));
                x += dot + (doName ? 4f : 0f);
            }
            if (doName) RenderUtil.text(c, tr, name, x, y, ts, white, true);
            y += nameH + (hasItems ? 4f : 0f);
        }

        // ---- item row (enchants above, icons below)
        if (hasItems) {
            float x = (W - itemsW) / 2f;
            float iconY = y + enchH;
            for (int i = 0; i < slots.size(); i++) {
                x = drawSlot(c, tr, slots.get(i), x, y, iconY, maxLines, lineH, es, fade);
                x += 3;
            }
            if (doHeld) {
                if (!slots.isEmpty()) {
                    x += 3;
                    c.fill((int) x - 3, (int) iconY + 1, (int) x - 2, (int) iconY + 15, RenderUtil.withAlpha(0xFFFFFF, 0.16 * fade));
                    x += 3;
                }
                drawSlot(c, tr, heldSlot, x, y, iconY, maxLines, lineH, es, fade);
            }
        }
        m.pop();
    }

    private float drawSlot(DrawContext c, TextRenderer tr, Slot slot, float x, float top, float iconY,
                           int maxLines, float lineH, float es, double fade) {
        float cx = x + slot.width() / 2f;
        int n = slot.lines().size();
        float ly = top + (maxLines - n) * lineH;
        for (Line line : slot.lines()) {
            float lw = tr.getWidth(line.text()) * es;
            RenderUtil.text(c, tr, line.text(), cx - lw / 2f, ly, es, RenderUtil.withAlpha(line.rgb(), fade), true);
            ly += lineH;
        }
        int ix = Math.round(cx - 8f);
        int iy = Math.round(iconY);
        if (slot.stack().isEmpty()) {
            RenderUtil.roundedRect(c, ix, iy, 16, 16, 3, RenderUtil.withAlpha(0xFFFFFF, 0.06 * fade));
        } else if (fade > 0.4) {
            c.drawItem(slot.stack(), ix, iy);
            c.drawStackOverlay(tr, slot.stack(), ix, iy);
        }
        return x + slot.width();
    }

    // ------------------------------------------------------------------ enchantments

    private Slot buildSlot(ItemStack stack, boolean armorSlot, float es, TextRenderer tr) {
        List<Line> lines = showEnchants.get() && !stack.isEmpty() ? enchantLines(stack) : List.of();
        float widest = 0f;
        for (Line l : lines) widest = Math.max(widest, tr.getWidth(l.text()) * es);
        return new Slot(stack, lines, Math.max(20, (int) Math.ceil(widest) + 4), armorSlot);
    }

    private List<Line> enchantLines(ItemStack stack) {
        ItemEnchantmentsComponent comp = stack.getEnchantments();
        if (comp.isEmpty()) return List.of();

        List<Object2IntMap.Entry<RegistryEntry<Enchantment>>> entries = new ArrayList<>(comp.getEnchantmentEntries());
        entries.sort((x, y) -> Integer.compare(y.getIntValue(), x.getIntValue()));

        boolean full = enchantFormat.get() == 1;
        List<Line> out = new ArrayList<>();
        for (Object2IntMap.Entry<RegistryEntry<Enchantment>> e : entries) {
            if (out.size() >= 6) break;
            RegistryEntry<Enchantment> entry = e.getKey();
            int level = e.getIntValue();
            String path = entry.getKey().map(k -> k.getValue().getPath()).orElse("unknown");

            String text = full ? Enchantment.getName(entry, level).getString() : abbreviate(path) + level;
            int rgb = 0xD8CCFF;
            if (path.contains("curse")) rgb = 0xFF5555;
            else if (level > entry.value().getMaxLevel()) rgb = 0xFFD166;
            out.add(new Line(text, rgb));
        }
        return out;
    }

    private static String abbreviate(String path) {
        return switch (path) {
            case "protection" -> "P";
            case "fire_protection" -> "FP";
            case "blast_protection" -> "BP";
            case "projectile_protection" -> "PP";
            case "unbreaking" -> "U";
            case "mending" -> "M";
            case "thorns" -> "T";
            case "feather_falling" -> "FF";
            case "respiration" -> "R";
            case "aqua_affinity" -> "AA";
            case "depth_strider" -> "DS";
            case "frost_walker" -> "FW";
            case "soul_speed" -> "SS";
            case "swift_sneak" -> "SN";
            case "sharpness" -> "S";
            case "smite" -> "SM";
            case "bane_of_arthropods" -> "BA";
            case "knockback" -> "K";
            case "fire_aspect" -> "FA";
            case "looting" -> "L";
            case "sweeping_edge" -> "SE";
            case "efficiency" -> "E";
            case "silk_touch" -> "ST";
            case "fortune" -> "F";
            case "power" -> "PW";
            case "punch" -> "PU";
            case "flame" -> "FL";
            case "infinity" -> "I";
            case "density" -> "D";
            case "breach" -> "B";
            case "wind_burst" -> "WB";
            case "binding_curse" -> "BC";
            case "vanishing_curse" -> "VC";
            default -> initials(path);
        };
    }

    private static String initials(String path) {
        StringBuilder sb = new StringBuilder();
        for (String part : path.split("_")) {
            if (!part.isEmpty()) sb.append(Character.toUpperCase(part.charAt(0)));
        }
        return sb.toString();
    }
}
