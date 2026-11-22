package com.pvpclient.module.hud;

import com.pvpclient.util.Lang;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;

public final class PingModule extends HudModule {
    public PingModule() {
        super("Ping Counter", "Latency to the server", 1.5, 13.5);
    }

    @Override
    protected void draw(DrawContext c) {
        String text = Lang.tr("Ping: ") + "--";
        if (mc.isInSingleplayer()) {
            text = Lang.tr("Ping: local");
        } else if (mc.getNetworkHandler() != null && mc.player != null) {
            PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            if (entry != null) text = Lang.tr("Ping: ") + entry.getLatency() + Lang.tr(" ms");
        }
        textBox(c, text);
    }
}
