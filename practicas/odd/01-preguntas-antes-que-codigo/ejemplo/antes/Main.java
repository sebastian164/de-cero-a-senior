/** Un servicio de pagos que "tiene logs". */
public class Main {

    public static void main(String[] args) {
        pagar("pedido-1", 800, "tarjeta");
        pagar("pedido-2", 5000, "tarjeta");
        pagar("pedido-3", 300, "transferencia");
        pagar("pedido-4", 450, "transferencia");
    }

    static void pagar(String pedido, int valor, String medio) {
        System.out.println("Iniciando pago");
        try {
            cobrar(valor, medio);
            System.out.println("Pago OK");
        } catch (RuntimeException error) {
            System.out.println("Error!!! " + error.getMessage());
        }
    }

    static void cobrar(int valor, String medio) {
        if (valor > 1000) {
            throw new IllegalArgumentException("fondos insuficientes");
        }
        if (medio.equals("transferencia")) {
            throw new IllegalStateException("el banco no responde");
        }
    }
}
