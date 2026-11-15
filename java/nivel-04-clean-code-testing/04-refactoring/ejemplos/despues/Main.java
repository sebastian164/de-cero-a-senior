public class Main {

    public static void main(String[] args) {
        long vip = Precios.calcular(3, 40_000, TipoCliente.VIP, Envio.EXPRESS);
        long normal = Precios.calcular(1, 40_000, TipoCliente.NORMAL, Envio.ESTANDAR);
        System.out.println(vip);
        System.out.println(normal);

        // La red de seguridad: los valores caracterizados antes de empezar.
        comprobar(vip == 118_000, "caso VIP");
        comprobar(normal == 48_000, "caso normal");
    }

    static void comprobar(boolean condicion, String caso) {
        if (!condicion) {
            throw new AssertionError("Cambió el comportamiento en: " + caso);
        }
    }
}

enum TipoCliente {
    NORMAL(0), VIP(10);

    final int porcentajeDescuento;

    TipoCliente(int porcentajeDescuento) {
        this.porcentajeDescuento = porcentajeDescuento;
    }
}

enum Envio {
    ESTANDAR(8_000), EXPRESS(15_000);

    final long costo;

    Envio(long costo) {
        this.costo = costo;
    }
}

final class Precios {

    private static final long UMBRAL_DESCUENTO_VOLUMEN = 100_000;
    private static final long DESCUENTO_VOLUMEN = 5_000;

    private Precios() { }

    static long calcular(int cantidad, long unitario, TipoCliente cliente, Envio envio) {
        long subtotal = cantidad * unitario;
        long conDescuentoCliente = subtotal - descuentoCliente(subtotal, cliente);
        long conDescuentoVolumen = conDescuentoCliente - descuentoVolumen(conDescuentoCliente);
        return conDescuentoVolumen + envio.costo;
    }

    private static long descuentoCliente(long subtotal, TipoCliente cliente) {
        return subtotal * cliente.porcentajeDescuento / 100;
    }

    private static long descuentoVolumen(long valor) {
        return valor > UMBRAL_DESCUENTO_VOLUMEN ? DESCUENTO_VOLUMEN : 0;
    }
}
