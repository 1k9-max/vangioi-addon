package vn.vangioi.autofish.client;

import java.util.ArrayList;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class e {
    private static final Pattern aE = Pattern.compile("\\[([^\\[\\]]{8,80})]\\s*(\\d{1,3})%");
    private static final int aF = 127775;
    private static final int aG = 9608;

    public record a(String aH, String aI, int aJ, int aK, int aL, int aM, double aN, long aO) {

        public String L() {
            return this.aH;
        }

        public String M() {
            return this.aI;
        }

        public int J() {
            return this.aJ;
        }

        public int N() {
            return this.aK;
        }

        public int O() {
            return this.aL;
        }

        public int P() {
            return this.aM;
        }

        public double Q() {
            return this.aN;
        }

        public long K() {
            return this.aO;
        }
    }

    public Optional<a> i(String str) {
        if (str == null || str.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = aE.matcher(str);
        a aVar = null;
        while (matcher.find()) {
            String strGroup = matcher.group(1);
            int[] array = strGroup.codePoints().toArray();
            ArrayList arrayList = new ArrayList();
            int iMin = Integer.MAX_VALUE;
            int iMax = -1;
            for (int i = 0; i < array.length; i++) {
                if (array[i] == aF) {
                    arrayList.add(Integer.valueOf(i));
                }
                if (array[i] == aG) {
                    iMin = Math.min(iMin, i);
                    iMax = Math.max(iMax, i);
                }
            }
            if (arrayList.size() == 1 && iMax >= 0) {
                try {
                    aVar = new a(str, strGroup, Math.max(0, Math.min(100, Integer.parseInt(matcher.group(2)))), ((Integer) arrayList.getFirst()).intValue(), iMin, iMax, ((double) (iMin + iMax)) / 2.0d, System.nanoTime());
                } catch (NumberFormatException e) {
                }
            }
        }
        return Optional.ofNullable(aVar);
    }
}
