public class Main {

    public static void main(String[] args) {
        System.out.println(precio(3, 40_000, "VIP", true));
        System.out.println(precio(1, 40_000, "NORMAL", false));
    }

    static long precio(int cantidad, long unitario, String tipoCliente, boolean envioExpress) {
        long p = cantidad * unitario;
        if (tipoCliente.equals("VIP")) {
            p = p - p * 10 / 100;
        }
        if (p > 100_000) {
            p = p - 5_000;
        }
        if (envioExpress) {
            p = p + 15_000;
        } else {
            p = p + 8_000;
        }
        return p;
    }
}
