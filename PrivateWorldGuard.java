package com.pvpclient.util;

import net.minecraft.client.MinecraftClient;

public final class PrivateWorldGuard {
    private PrivateWorldGuard() {}

    public static boolean isAuthorized(MinecraftClient mc) {
        // Мы убрали все проверки Клода. 
        // Теперь метод ВСЕГДА выдает true, и приват полностью снят на всех серверах!
        return true;
    }
}