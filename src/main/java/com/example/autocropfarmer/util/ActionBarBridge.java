package com.example.autocropfarmer.util;

import net.minecraft.text.Text;

/**
 * Cau noi don gian giua InGameHudMixin (bat text ActionBar) va module AutoFish (doc text do).
 */
public final class ActionBarBridge {
    private static volatile Text lastMessage;
    private static volatile long lastMessageId = 0;

    private ActionBarBridge() {
    }

    public static void onActionBar(Text message) {
        lastMessage = message;
        lastMessageId++;
    }

    public static Text getLastMessage() {
        return lastMessage;
    }

    /**
     * Tang moi lan co ActionBar moi - dung de phat hien "co tin nhan moi hay khong" ma khong can
     * so sanh noi dung text (tranh bo lo khi 2 lan lien tiep co cung noi dung).
     */
    public static long getLastMessageId() {
        return lastMessageId;
    }
}
