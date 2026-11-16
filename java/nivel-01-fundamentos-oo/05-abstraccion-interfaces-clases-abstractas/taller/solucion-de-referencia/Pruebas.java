import java.util.Set;

public class Pruebas {
    private static int fallos = 0, total = 0;

    public static void main(String[] args) {
        Caja caja = new Caja();
        igual("crédito 2 %", 51_000L, caja.cobrar(new TarjetaCredito("4111111111111111"), 50_000).valorCobrado());
        igual("débito sin comisión", 50_000L, caja.cobrar(new TarjetaDebito("5500000000000004"), 50_000).valorCobrado());
        igual("últimos cuatro", "Crédito terminada en 1111", new TarjetaCredito("4111111111111111").pagar(1).descripcion());
        igual("efectivo cambio", "Efectivo, cambio 30000", caja.cobrar(new Efectivo(80_000), 50_000).descripcion());
        igual("calcularCambio", 20_000L, new Efectivo(70_000).calcularCambio(50_000));
        igual("transferencia", 50_000L, caja.cobrar(new Transferencia("B1", Set.of("B1")), 50_000).valorCobrado());
        igual("billetera", "Billetera digital 300****4567", new BilleteraDigital("3001234567").pagar(1).descripcion());
        lanza("tarjeta 15 dígitos", () -> new TarjetaCredito("411111111111111"));
        lanza("tarjeta con letras", () -> new TarjetaDebito("41111111111111ab"));
        lanza("banco no permitido", () -> new Transferencia("B2", Set.of("B1")));
        lanza("monto negativo", () -> new TarjetaDebito("5500000000000004").pagar(-1));
        lanza("efectivo insuficiente", () -> new Efectivo(10).pagar(50));
        System.out.println(total + " pruebas, " + fallos + " fallos");
        if (fallos > 0) System.exit(1);
    }

    static void igual(String n, Object e, Object r) { total++; if (!e.equals(r)) { fallos++; System.out.println("FALLA " + n + ": " + e + " != " + r); } }

    static void lanza(String n, Runnable a) {
        total++;
        try { a.run(); fallos++; System.out.println("FALLA " + n + ": no lanzó"); }
        catch (IllegalArgumentException | IllegalStateException x) { }
    }
}
