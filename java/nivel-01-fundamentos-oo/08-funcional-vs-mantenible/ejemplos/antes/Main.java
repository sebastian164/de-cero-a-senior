public class Main {

    public static void main(String[] args) {
        System.out.println(calc(4_000_000, 1, 3, true));
        System.out.println(calc(1_500_000, 2, 0, false));
    }

    // t: 1 = fijo, 2 = por horas (s es el valor de 160 horas)
    static long calc(long s, int t, int a, boolean x) {
        long r = s;
        if (t == 1) {
            if (a > 2) {
                r = r + s * 5 / 100;
            }
            if (a > 5) {
                r = r + s * 5 / 100;
            }
        }
        if (x) {
            r = r + 200_000;
        }
        r = r - s * 4 / 100;
        r = r - s * 4 / 100;
        if (s <= 2_847_000) {
            r = r + 200_000;
        }
        return r;
    }
}
