package com.pvpclient.gui;

import net.minecraft.client.gui.DrawContext;

/** One row in the scrolling content list. Coordinates are in menu-local (unscaled) space. */
interface GuiEntry {
    int height();

    void render(DrawContext c, int x, int y, int w, int mx, int my);

    boolean click(int x, int y, int w, int mx, int my, int button);

    default void drag(int mx) {}

    default void release() {}
}
