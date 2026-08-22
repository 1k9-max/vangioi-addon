package vn.vangioi.autofish.client;

public final class i {
    private static final String by = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String[] bz = {"ᴀ", "ʙ", "ᴄ", "ᴅ", "ᴇ", "ꜰ", "ɢ", "ʜ", "ɪ", "ᴊ", "ᴋ", "ʟ", "ᴍ", "ɴ", "ᴏ", "ᴘ", "ǫ", "ʀ", "ѕ", "ᴛ", "ᴜ", "ᴠ", "ᴡ", "х", "ʏ", "ᴢ", "ᴀ", "ʙ", "ᴄ", "ᴅ", "ᴇ", "ꜰ", "ɢ", "ʜ", "ɪ", "ᴊ", "ᴋ", "ʟ", "ᴍ", "ɴ", "ᴏ", "ᴘ", "ǫ", "ʀ", "ѕ", "ᴛ", "ᴜ", "ᴠ", "ᴡ", "х", "ʏ", "ᴢ"};

    private i() {
    }

    public static String l(String str) {
        if (str == null || str.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(str.length() + 8);
        for (int i = 0; i < str.length(); i++) {
            char cCharAt = str.charAt(i);
            int iIndexOf = by.indexOf(cCharAt);
            if (iIndexOf >= 0) {
                sb.append(bz[iIndexOf]);
            } else {
                sb.append(cCharAt);
            }
        }
        return sb.toString();
    }
}
