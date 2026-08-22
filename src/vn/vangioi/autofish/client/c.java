package vn.vangioi.autofish.client;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public final class c {
    public static final String am = "1";
    private static final Base64.Encoder an = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder ao = Base64.getUrlDecoder();

    public record a(String ap, List<String> aq) {

        public String a(int i) {
            return (i < 0 || i >= this.aq.size()) ? "" : this.aq.get(i);
        }

        public String y() {
            return this.ap;
        }

        public List<String> z() {
            return this.aq;
        }
    }

    private c() {
    }

    public static String a(String str, String... strArr) {
        StringBuilder sbAppend = new StringBuilder(am).append('\t').append(str);
        int length = strArr.length;
        for (int i = 0; i < length; i++) {
            String str2 = strArr[i];
            sbAppend.append('\t').append(d(str2 == null ? "" : str2));
        }
        return sbAppend.toString();
    }

    public static a c(String str) {
        if (str == null) {
            return null;
        }
        String[] strArrSplit = str.split("\\t", -1);
        if (strArrSplit.length < 2 || !am.equals(strArrSplit[0])) {
            return null;
        }
        ArrayList arrayList = new ArrayList();
        for (int i = 2; i < strArrSplit.length; i++) {
            try {
                arrayList.add(e(strArrSplit[i]));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return new a(strArrSplit[1], arrayList);
    }

    private static String d(String str) {
        return an.encodeToString(str.getBytes(StandardCharsets.UTF_8));
    }

    private static String e(String str) {
        return str.isEmpty() ? "" : new String(ao.decode(str), StandardCharsets.UTF_8);
    }
}
