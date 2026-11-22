package com.pvpclient.module.bot;

import net.minecraft.client.MinecraftClient;

public final class BotGuard {
    private BotGuard() {}

    public static boolean allowed(MinecraftClient mc) {
        return true;
    }

    public static boolean isAllowed(MinecraftClient mc) {
        return true;
    }
}