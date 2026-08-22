package vn.vangioi.autofish.client;

import java.util.Optional;

public final class d {
    public static final String ar = "[[VGFISH1]]|";
    public static final String as = "[[VGFISH_ACK]]|";

    public record a(double at, double au, double av, double aw, int ax, int ay, String az, double aA, double aB, int aC, long aD) {

        public double A() {
            return this.at;
        }

        public double B() {
            return this.au;
        }

        public double C() {
            return this.av;
        }

        public double D() {
            return this.aw;
        }

        public int E() {
            return this.ax;
        }

        public int F() {
            return this.ay;
        }

        public String G() {
            return this.az;
        }

        public double H() {
            return this.aA;
        }

        public double I() {
            return this.aB;
        }

        public int J() {
            return this.aC;
        }

        public long K() {
            return this.aD;
        }
    }

    public boolean f(String str) {
        return str != null && (str.startsWith(ar) || str.startsWith(as));
    }

    public Optional<Boolean> g(String str) {
        if (str == null || !str.startsWith(as)) {
            return Optional.empty();
        }
        String strTrim = str.substring(as.length()).trim();
        if (strTrim.equals(c.am)) {
            return Optional.of(true);
        }
        return strTrim.equals("0") ? Optional.of(false) : Optional.empty();
    }

    public Optional<a> h(String str) {
        if (str == null || !str.startsWith(ar)) {
            return Optional.empty();
        }
        String[] strArrSplit = str.substring(ar.length()).split("\\|", -1);
        if (strArrSplit.length != 10) {
            return Optional.empty();
        }
        try {
            double dA = a(Double.parseDouble(strArrSplit[0]));
            double dA2 = a(Double.parseDouble(strArrSplit[1]));
            double dMax = Math.max(0.0d, Math.min(0.5d, Double.parseDouble(strArrSplit[2])));
            double dMax2 = Math.max(0.0d, Math.min(0.25d, Double.parseDouble(strArrSplit[3])));
            int i = Integer.parseInt(strArrSplit[4]) < 0 ? -1 : 1;
            int iMax = Math.max(0, Math.min(10000, Integer.parseInt(strArrSplit[5])));
            String strTrim = strArrSplit[6].trim();
            return (strTrim.equals("moving") || strTrim.equals("resting")) ? Optional.of(new a(dA, dA2, dMax, dMax2, i, iMax, strTrim, Math.max(0.0d, Math.min(0.25d, Double.parseDouble(strArrSplit[7]))), Math.max(0.0d, Math.min(0.25d, Double.parseDouble(strArrSplit[8]))), Math.max(0, Math.min(100, Integer.parseInt(strArrSplit[9]))), System.nanoTime())) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static double a(double d) {
        if (Double.isFinite(d)) {
            return Math.max(0.0d, Math.min(1.0d, d));
        }
        throw new NumberFormatException("non-finite");
    }
}
