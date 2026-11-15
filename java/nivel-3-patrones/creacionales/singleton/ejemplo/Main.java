public class Main {

    public static void main(String[] args) {
        // Forma 1: el enum. Si de verdad hace falta un singleton clásico, esta es la forma segura.
        System.out.println(Consecutivo.INSTANCIA.siguiente());
        System.out.println(Consecutivo.INSTANCIA.siguiente());

        // Forma 2 (preferida): una sola instancia, creada en la raíz y entregada a quien la necesita.
        Configuracion configuracion = new Configuracion(19);
        ServicioPrecios precios = new ServicioPrecios(configuracion);
        ServicioFacturas facturas = new ServicioFacturas(configuracion);
        System.out.println(precios.conIva(100_000));
        System.out.println(facturas.resumen(100_000));
    }
}

/** Singleton con enum: seguro entre hilos, ante serialización y ante reflexión. */
enum Consecutivo {
    INSTANCIA;

    private final java.util.concurrent.atomic.AtomicLong contador = new java.util.concurrent.atomic.AtomicLong();

    long siguiente() {
        return contador.incrementAndGet();
    }
}

/** Una clase normal. Que haya una sola instancia es decisión de quien ensambla. */
final class Configuracion {

    private final int iva;

    Configuracion(int iva) {
        this.iva = iva;
    }

    int iva() {
        return iva;
    }
}

class ServicioPrecios {

    private final Configuracion configuracion;

    ServicioPrecios(Configuracion configuracion) {
        this.configuracion = configuracion;
    }

    long conIva(long valor) {
        return valor + valor * configuracion.iva() / 100;
    }
}

class ServicioFacturas {

    private final Configuracion configuracion;

    ServicioFacturas(Configuracion configuracion) {
        this.configuracion = configuracion;
    }

    String resumen(long valor) {
        return "IVA " + configuracion.iva() + "% sobre $" + valor;
    }
}
