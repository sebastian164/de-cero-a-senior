package sagas;

import java.util.List;

/** Proceso hijo: orquesta una saga de tres pasos con efectos en PostgreSQL y se queda "ejecutando" el tercero hasta que lo matan. */
public final class SagaHija {

    private SagaHija() { }

    public static void main(String[] args) throws Exception {
        String url = args[0];
        long id = Long.parseLong(args[1]);
        var inv = new Servicios.EnBase(url, "inventario");
        var pag = new Servicios.EnBase(url, "pagos");
        var env = new Servicios.EnBase(url, "envios");
        env.antes = () -> {
            System.out.println("PASO2-LISTO");                        // inventario y pagos ya aplicados; envíos a punto de ejecutar
            System.out.flush();
            try { Thread.sleep(600_000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        };
        new Saga(url, List.of(inv, pag, env), 3).ejecutar(id);
    }
}
