package nosql.adaptadores.redis;

import nosql.aplicacion.BandejaDeEventos;

/** Lee la bandeja de salida y borra de la caché las claves de los productos cuyo precio cambió, sea quien sea quien lo cambió. */
public final class PublicadorDeInvalidaciones implements AutoCloseable {
    private final Thread hilo;
    private volatile boolean activo = true;

    public PublicadorDeInvalidaciones(BandejaDeEventos bandeja, CatalogoConCache cache, long intervaloMs) {
        hilo = new Thread(() -> {
            while (activo) {
                try {
                    for (var e : bandeja.pendientes(100)) {
                        if (e.tipo().equals("PrecioCambiado")) cache.invalidar(e.pedidoId());   // en este evento, la referencia es el producto
                        bandeja.marcarPublicado(e.id());
                    }
                    Thread.sleep(intervaloMs);
                } catch (InterruptedException x) { return; } catch (RuntimeException x) { /* se reintenta en la vuelta siguiente */ }
            }
        }, "publicador-invalidaciones");
        hilo.setDaemon(true);
        hilo.start();
    }

    @Override public void close() { activo = false; hilo.interrupt(); }
}
