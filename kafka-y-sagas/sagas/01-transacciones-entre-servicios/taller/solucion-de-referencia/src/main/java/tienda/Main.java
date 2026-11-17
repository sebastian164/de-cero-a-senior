package tienda;

import tienda.SagaDeCompra.Compra;

/** Los cuatro escenarios del paso 6, el quinto paso (7) y la compensación que falla (8), imprimiendo el estado. */
public class Main {

    public static void main(String[] args) {
        escenario("1. Compra exitosa", saga -> saga.ejecutar(new Compra("p-1", "ana", "camisa", 2, 200_000, "Bogotá")));
        escenario("2. Pago rechazado", saga -> saga.ejecutar(new Compra("p-2", "ana", "camisa", 2, 5_000_000, "Bogotá")));
        escenario("3. Sin existencias", saga -> saga.ejecutar(new Compra("p-3", "ana", "camisa", 50, 200_000, "Bogotá")));
        escenario("4. Fallo en el envío", saga -> saga.ejecutar(new Compra("p-4", "ana", "camisa", 2, 200_000, "Leticia")));
        escenario("7. Con puntos de fidelidad", saga -> {
            saga.conPuntosDeFidelidad = true;
            return saga.ejecutar(new Compra("p-5", "ana", "camisa", 2, 200_000, "Bogotá"));
        });
        escenario("8. Falla la compensación de inventario", saga -> {
            saga.inventario.compensacionRota = true;
            return saga.ejecutar(new Compra("p-6", "ana", "camisa", 2, 5_000_000, "Bogotá"));
        });
    }

    static void escenario(String nombre, java.util.function.Function<SagaDeCompra, SagaDeCompra.Resultado> ejecutar) {
        SagaDeCompra saga = nueva();
        System.out.println(nombre);
        SagaDeCompra.Resultado resultado = ejecutar.apply(saga);
        saga.traza.forEach(linea -> System.out.println("  " + linea));
        System.out.println("  resultado: " + resultado);
        System.out.println("  " + saga.estado());
        System.out.println();
    }

    static SagaDeCompra nueva() {
        SagaDeCompra saga = new SagaDeCompra();
        saga.inventario.existencias.put("camisa", 10);
        saga.envios.ciudadesSinCobertura.add("Leticia");
        return saga;
    }
}
