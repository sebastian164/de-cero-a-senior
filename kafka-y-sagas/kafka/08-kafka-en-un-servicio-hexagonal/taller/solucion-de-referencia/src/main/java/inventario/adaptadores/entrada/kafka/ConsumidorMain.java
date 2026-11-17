package inventario.adaptadores.entrada.kafka;

import comun.Kf;
import inventario.adaptadores.salida.jdbc.InventarioJdbc;
import inventario.adaptadores.salida.jdbc.UnidadJdbc;
import inventario.aplicacion.ServicioDeInventario;

/** El servicio de Inventario como proceso: raíz de composición. Argumento: muereTrasProcesar (0 = no muere). */
public final class ConsumidorMain {

    public static void main(String[] args) throws Exception {
        var unidad = new UnidadJdbc();
        var jdbc = new InventarioJdbc(unidad);
        var servicio = new ServicioDeInventario(jdbc, jdbc, unidad);
        var consumidor = new ConsumidorDePedidos(Kf.bootstrap(), "inventario", servicio);
        consumidor.morirTrasProcesar = Integer.parseInt(args[0]);
        consumidor.arrancar();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                consumidor.close();
            } catch (InterruptedException ignorada) {
                Thread.currentThread().interrupt();
            }
        }));
        Thread.sleep(Long.MAX_VALUE);
    }
}
