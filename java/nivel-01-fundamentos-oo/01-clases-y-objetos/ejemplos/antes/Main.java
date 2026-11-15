public class Main {

    static String[] nombres = new String[10];
    static long[] precios = new long[10];
    static int[] existencias = new int[10];
    static int total = 0;

    public static void main(String[] args) {
        agregar("Teclado", 120_000, 5);
        agregar("Mouse", 60_000, 0);
        vender(0, 2);
        vender(1, 1);
        imprimir();
    }

    static void agregar(String nombre, long precio, int cantidad) {
        nombres[total] = nombre;
        precios[total] = precio;
        existencias[total] = cantidad;
        total++;
    }

    static void vender(int indice, int cantidad) {
        if (existencias[indice] >= cantidad) {
            existencias[indice] -= cantidad;
        } else {
            System.out.println("Sin existencias de " + nombres[indice]);
        }
    }

    static void imprimir() {
        for (int i = 0; i < total; i++) {
            System.out.println(nombres[i] + " $" + precios[i] + " x" + existencias[i]);
        }
    }
}
