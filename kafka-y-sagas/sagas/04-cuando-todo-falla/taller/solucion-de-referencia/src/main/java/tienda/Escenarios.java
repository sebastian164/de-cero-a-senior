package tienda;

import tienda.Broker.Accion;

/** Escenarios reutilizados por Main y por las pruebas. */
public final class Escenarios {

    private Escenarios() {
    }

    public static Broker duplicarTodo() {
        Broker b = new Broker();
        for (int n = 1; n <= 500; n++) {
            b.con(n, Accion.DUPLICAR);
        }
        return b;
    }

    /** Los cuatro caminos con 3 unidades: éxito, rechazo, envío imposible y sin existencias (cuarta compra). */
    public static Tienda cuatroCaminos(Broker broker, boolean idempotente) {
        Tienda t = new Tienda(3, broker).idempotente(idempotente);
        t.envios.sinCobertura.add("envio");
        t.comprar("exito", 200_000);
        t.comprar("rechazo", 5_000_000);
        t.comprar("envio", 100_000);
        t.comprar("otra", 100_000);
        t.comprar("agotado", 100_000);
        t.correr(150);
        return t;
    }

    /**
     * La orden de reservar se retrasa 30 ciclos y sus dos reintentos se pierden: el orquestador vence, compensa
     * (liberar) y cancela. Treinta ciclos después llega la reserva original.
     */
    public static Tienda compensacionAntesQueLaAccion(boolean conLapidas) {
        Broker b = new Broker().con(1, Accion.RETRASAR).con(2, Accion.PERDER).con(3, Accion.PERDER);
        b.retraso = 30;
        Tienda t = new Tienda(1, b).conLapidas(conLapidas);
        t.comprar("s", 100_000);
        t.correr(60);
        return t;
    }

    public static long ventasConfirmadas(Tienda t) {
        return t.almacen.sagas.values().stream().filter(s -> s.estado == Estado.COMPLETADA).count();
    }
}
