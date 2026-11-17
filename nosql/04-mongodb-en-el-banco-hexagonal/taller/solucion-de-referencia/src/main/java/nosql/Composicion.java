package nosql;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import java.time.Clock;
import nosql.aplicacion.AdministrarCatalogo;
import nosql.aplicacion.CambiarPrecioDeProducto;
import nosql.aplicacion.ConsultaDeProductos;
import nosql.aplicacion.ConsultarProducto;
import nosql.aplicacion.CrearPedidoAcotado;
import nosql.aplicacion.PoliticaDeReintento;
import nosql.aplicacion.ServicioDeCatalogo;
import nosql.aplicacion.ServicioDePedidosConEspera;
import tienda.adaptadores.salida.memoria.AlmacenEnMemoria;
import tienda.adaptadores.salida.memoria.CatalogoMutable;
import tienda.adaptadores.salida.mongo.BandejaMongo;
import tienda.adaptadores.salida.mongo.CatalogoMongo;
import tienda.adaptadores.salida.mongo.EsquemaMongo;
import tienda.adaptadores.salida.mongo.InventarioMongo;
import tienda.adaptadores.salida.mongo.PedidosMongo;
import tienda.adaptadores.salida.mongo.PeticionesMongo;
import tienda.adaptadores.salida.mongo.UnidadDeTrabajoMongo;
import tienda.aplicacion.ServicioDePedidos;
import tienda.aplicacion.puertos.entrada.AprobarPedido;
import tienda.aplicacion.puertos.entrada.CancelarPedido;
import tienda.aplicacion.puertos.entrada.ConfirmarPedido;
import tienda.aplicacion.puertos.entrada.ConsultarPedido;
import tienda.aplicacion.puertos.entrada.CrearPedido;
import tienda.aplicacion.puertos.entrada.ListarPedidosDeCliente;
import tienda.aplicacion.puertos.salida.Inventario;
import tienda.aplicacion.puertos.salida.PeticionesProcesadas;
import tienda.aplicacion.puertos.salida.PublicadorDeEventos;
import tienda.aplicacion.puertos.salida.Reloj;
import tienda.aplicacion.puertos.salida.RepositorioDePedidos;
import tienda.aplicacion.puertos.salida.UnidadDeTrabajo;
import tienda.dominio.Dinero;

/**
 * La raíz de composición: el único sitio que elige entre memoria y MongoDB, con un parámetro. Quien recibe el {@link Sistema}
 * solo ve puertos de entrada; no sabe qué hay detrás.
 */
public final class Composicion {

    private Composicion() {
    }

    /** Cómo se siembra un producto en la tienda elegida (solo lo usan las pruebas y el arranque). */
    public interface Siembra {
        void producto(String id, String nombre, long precioCentavos, int existencias);
    }

    /** Todo lo que MongoDB aporta además de los puertos del núcleo: lo usan las piezas de las sesiones 05 a 07. */
    public record Almacen(MongoClient cliente, MongoDatabase db, UnidadDeTrabajoMongo unidad, PedidosMongo pedidos, InventarioMongo inventario,
                          CatalogoMongo catalogo, PeticionesMongo peticiones, BandejaMongo bandeja, Reloj reloj) {
    }

    public record Sistema(String adaptador, CrearPedido crear, AprobarPedido aprobar, ConfirmarPedido confirmar, CancelarPedido cancelar,
                          ConsultarPedido consultar, ListarPedidosDeCliente listar, ConsultarProducto productos, CambiarPrecioDeProducto precios,
                          Siembra siembra, Almacen mongo, Runnable borrarYCerrar) {
    }

    /** El parámetro: "memoria" o "mongo" (propiedad pedidos.adaptador o variable PEDIDOS_ADAPTADOR). */
    public static String adaptadorElegido() {
        return System.getProperty("pedidos.adaptador", System.getenv().getOrDefault("PEDIDOS_ADAPTADOR", "memoria"));
    }

    /** {@code politica} nulo: el servicio original del módulo hexagonal (3 intentos sin espera). */
    public static Sistema elegir(String adaptador, String urlMongo, String baseDeDatos, PoliticaDeReintento politica, Clock reloj) {
        return switch (adaptador) {
            case "memoria" -> memoria(politica, reloj);
            case "mongo" -> mongo(urlMongo, baseDeDatos, politica, reloj);
            default -> throw new IllegalArgumentException("adaptador desconocido: " + adaptador);
        };
    }

    public static Sistema memoria(PoliticaDeReintento politica, Clock reloj) {
        AlmacenEnMemoria almacen = new AlmacenEnMemoria();
        CatalogoMutable catalogo = new CatalogoMutable();
        Siembra siembra = (id, nombre, precio, existencias) -> {
            catalogo.definir(id, nombre, new Dinero(precio));
            almacen.ejecutar(() -> {
                almacen.guardar(tienda.dominio.Existencias.de(id, existencias));
                return null;
            });
        };
        return armar("memoria", almacen, catalogo, almacen, almacen, almacen, almacen, catalogo, politica, reloj::instant, siembra, null, () -> {
        });
    }

    public static Sistema mongo(String url, String baseDeDatos, PoliticaDeReintento politica, Clock reloj) {
        MongoClient cliente = MongoClients.create(url);
        MongoDatabase db = cliente.getDatabase(baseDeDatos);
        EsquemaMongo.crear(db);
        UnidadDeTrabajoMongo unidad = new UnidadDeTrabajoMongo(cliente);
        PedidosMongo pedidos = new PedidosMongo(db, unidad);
        InventarioMongo inventario = new InventarioMongo(db, unidad);
        CatalogoMongo catalogo = new CatalogoMongo(db, unidad);
        PeticionesMongo peticiones = new PeticionesMongo(db, unidad);
        BandejaMongo bandeja = new BandejaMongo(db, unidad);
        Reloj ahora = reloj::instant;
        Almacen almacen = new Almacen(cliente, db, unidad, pedidos, inventario, catalogo, peticiones, bandeja, ahora);
        Siembra siembra = (id, nombre, precio, existencias) -> {
            catalogo.definir(id, nombre, new Dinero(precio));
            unidad.ejecutar(() -> {
                inventario.guardar(tienda.dominio.Existencias.de(id, existencias));
                return null;
            });
        };
        return armar("mongo", pedidos, catalogo, inventario, peticiones, unidad, bandeja, catalogo, politica, ahora, siembra, almacen, () -> {
            db.drop();
            cliente.close();
        });
    }

    @SuppressWarnings("java:S107")
    private static Sistema armar(String adaptador, RepositorioDePedidos pedidos, tienda.aplicacion.puertos.salida.Catalogo catalogo, Inventario inventario,
                                 PeticionesProcesadas peticiones, UnidadDeTrabajo unidad, PublicadorDeEventos eventos,
                                 AdministrarCatalogo administrar, PoliticaDeReintento politica, Reloj reloj, Siembra siembra, Almacen almacen,
                                 Runnable borrarYCerrar) {
        ConsultarProducto productos = new ConsultaDeProductos(catalogo, inventario);
        CambiarPrecioDeProducto precios = new ServicioDeCatalogo(administrar, unidad, eventos, reloj);
        if (politica == null) {
            var s = new ServicioDePedidos(pedidos, catalogo, inventario, peticiones, unidad, reloj, eventos);
            return new Sistema(adaptador, new CrearPedidoAcotado(s), s, s, s, s, s, productos, precios, siembra, almacen, borrarYCerrar);
        }
        var s = new ServicioDePedidosConEspera(pedidos, catalogo, inventario, peticiones, unidad, reloj, eventos, politica);
        return new Sistema(adaptador, new CrearPedidoAcotado(s), s, s, s, s, s, productos, precios, siembra, almacen, borrarYCerrar);
    }
}
