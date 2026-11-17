package nosql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import comun.Entorno;
import comun.Salida;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tienda.ContratoDelInventario;
import tienda.ContratoDelRepositorioDePedidos;
import tienda.adaptadores.salida.memoria.AlmacenEnMemoria;
import tienda.adaptadores.salida.mongo.CatalogoMongo;
import tienda.adaptadores.salida.mongo.EsquemaMongo;
import tienda.adaptadores.salida.mongo.InventarioMongo;
import tienda.adaptadores.salida.mongo.PedidosMongo;
import tienda.adaptadores.salida.mongo.UnidadDeTrabajoMongo;
import tienda.dominio.Dinero;

/**
 * Paso 3: los contratos del módulo hexagonal (ContratoDelRepositorioDePedidos y ContratoDelInventario), sin tocarlos,
 * contra el adaptador en memoria y contra MongoDB real. Una lista vacía de incumplimientos es que el adaptador los cumple todos.
 */
class ContratoDelNucleoTest {
    static final String R = "contratos.txt";

    @Test
    void repositorioDePedidosContraMemoriaYMongo() {
        var memoria = ContratoDelRepositorioDePedidos.incumplimientos(() -> {
            AlmacenEnMemoria a = new AlmacenEnMemoria();
            return new ContratoDelRepositorioDePedidos.Escenario(a, a);
        });
        Salida.linea(R, "ContratoDelRepositorioDePedidos contra memoria: " + (memoria.isEmpty() ? "cumple todas las cláusulas" : "incumple " + memoria));
        assertEquals(List.of(), memoria);

        String bd = "t04_" + UUID.randomUUID().toString().substring(0, 8);
        try (var cliente = Entorno.mongo()) {
            try {
                var mongo = ContratoDelRepositorioDePedidos.incumplimientos(() -> {
                    var db = cliente.getDatabase(bd);
                    EsquemaMongo.crear(db);
                    var unidad = new UnidadDeTrabajoMongo(cliente);
                    return new ContratoDelRepositorioDePedidos.Escenario(new PedidosMongo(db, unidad), unidad);
                });
                Salida.linea(R, "ContratoDelRepositorioDePedidos contra MongoDB: " + (mongo.isEmpty() ? "cumple todas las cláusulas" : "incumple " + mongo));
                assertEquals(List.of(), mongo);
            } finally { cliente.getDatabase(bd).drop(); }
        }
    }

    @Test
    void inventarioContraMemoriaYMongo() {
        var memoria = ContratoDelInventario.incumplimientos(() -> {
            AlmacenEnMemoria a = new AlmacenEnMemoria();
            return new ContratoDelInventario.Escenario(a, a, "teclado");
        });
        Salida.linea(R, "ContratoDelInventario contra memoria: " + (memoria.isEmpty() ? "cumple todas las cláusulas" : "incumple " + memoria));
        assertEquals(List.of(), memoria);

        String bd = "t04_" + UUID.randomUUID().toString().substring(0, 8);
        try (var cliente = Entorno.mongo()) {
            try {
                var mongo = ContratoDelInventario.incumplimientos(() -> {
                    var db = cliente.getDatabase(bd);
                    EsquemaMongo.crear(db);
                    var unidad = new UnidadDeTrabajoMongo(cliente);
                    new CatalogoMongo(db, unidad).definir("teclado", "Teclado", new Dinero(15_000_000));   // en una base real el producto existe en el catálogo
                    return new ContratoDelInventario.Escenario(new InventarioMongo(db, unidad), unidad, "teclado");
                });
                Salida.linea(R, "ContratoDelInventario contra MongoDB: " + (mongo.isEmpty() ? "cumple todas las cláusulas" : "incumple " + mongo));
                assertEquals(List.of(), mongo);
            } finally { cliente.getDatabase(bd).drop(); }
        }
    }
}
