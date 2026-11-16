package tienda;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import tienda.adaptadores.salida.memoria.CatalogoEnMemoria;
import tienda.aplicacion.puertos.entrada.CrearPedido.Linea;
import tienda.aplicacion.puertos.entrada.CrearPedido.Solicitud;
import tienda.aplicacion.puertos.salida.Inventario;
import tienda.aplicacion.puertos.salida.Reloj;
import tienda.aplicacion.puertos.salida.UnidadDeTrabajo;
import tienda.dominio.Dinero;
import tienda.dominio.Existencias;

/** Los datos de la tienda del taller 01, para armar pruebas. */
final class Datos {

    static final Reloj RELOJ = () -> Instant.parse("2026-11-15T10:00:00Z");

    private Datos() {
    }

    static CatalogoEnMemoria catalogo() {
        return new CatalogoEnMemoria(Map.of("teclado", new Dinero(15_000_000), "raton", new Dinero(6_000_000),
                "monitor", new Dinero(90_000_000), "caro", new Dinero(400_000_000)));
    }

    /** Siembra existencias: teclado 10, raton 3, monitor 2, caro 5. */
    static void sembrar(Inventario inventario, UnidadDeTrabajo unidadDeTrabajo) {
        unidadDeTrabajo.ejecutar(() -> {
            inventario.guardar(Existencias.de("teclado", 10));
            inventario.guardar(Existencias.de("raton", 3));
            inventario.guardar(Existencias.de("monitor", 2));
            inventario.guardar(Existencias.de("caro", 5));
            return null;
        });
    }

    /** solicitud("clave", "cliente", "teclado:2", "raton:1") */
    static Solicitud solicitud(String clave, String cliente, String... lineas) {
        return new Solicitud(clave, cliente, Arrays.stream(lineas)
                .map(texto -> new Linea(texto.split(":")[0], Integer.parseInt(texto.split(":")[1]))).toList());
    }

    static int unidades(Inventario inventario, String producto) {
        return inventario.existenciasDe(producto).orElseThrow().unidades();
    }
}
