package com.example.vangioi.util;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class TextNormalizer {
    private static final Map<Character, Character> STYLE_MAP = buildStyleMap();

    private TextNormalizer() {
    }

    public static String normalize(String text) {
        String lower = text == null ? "" : text.toLowerCase(Locale.forLanguageTag("vi"));
        StringBuilder result = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char character = lower.charAt(i);
            result.append(STYLE_MAP.getOrDefault(character, character));
        }
        return Normalizer.normalize(result.toString(), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .replace("đ", "d");
    }

    private static Map<Character, Character> buildStyleMap() {
        Map<Character, Character> map = new HashMap<>();
        String stylized = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀѕᴛᴜᴠᴡхʏᴢ";
        String normal = "abcdefghijklmnopqrstuvwxyz";
        for (int i = 0; i < stylized.length(); i++) map.put(stylized.charAt(i), normal.charAt(i));
        return map;
    }
}