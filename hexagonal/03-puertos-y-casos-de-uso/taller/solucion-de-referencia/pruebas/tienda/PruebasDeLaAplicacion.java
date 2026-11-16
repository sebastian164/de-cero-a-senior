package tienda;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import tienda.adaptadores.salida.memoria.CatalogoEnMemoria;
import tienda.adaptadores.salida.memoria.EventosEnMemoria;
import tienda.adaptadores.salida.memoria.InventarioEnMemoria;
import tienda.adaptadores.salida.memoria.PedidosEnMemoria;
import tienda.aplicacion.PedidoNoEncontrado;
import tienda.aplicacion.ProductoDesconocido;
import tienda.aplicacion.ServicioDePedidos;
import tienda.aplicacion.VistaDePedido;
import tienda.aplicacion.puertos.entrada.CrearPedido.Linea;
import tienda.aplicacion.puertos.entrada.CrearPedido.Solicitud;
import tienda.aplicacion.puertos.salida.Reloj;
import tienda.dominio.Dinero;
import tienda.dominio.Existencias;
import tienda.dominio.ReglaDeNegocioViolada;

/** Cada caso de uso con el servicio real y adaptadores de memoria: caminos felices y cada rechazo. */
public final class PruebasDeLaAplicacion {

    private static final Reloj RELOJ = () -> Instant.parse("2026-11-15T10:00:00Z");

    private PruebasDeLaAplicacion() {
    }

    public static void ejecutar() {
        System.out.println("Aplicación, crear:");
        InventarioEnMemoria inventario = inventario();
        EventosEnMemoria eventos = new EventosEnMemoria();
        ServicioDePedidos tienda = new ServicioDePedidos(new PedidosEnMemoria(), catalogo(), inventario, RELOJ, eventos);

        VistaDePedido creado = tienda.crear(solicitud("cliente-1", "teclado:2", "raton:1"));
        Comprobar.igual("crear devuelve el pedido pendiente con su total", List.of("pedido-1", "PENDIENTE", 36_000_000L),
                List.of(creado.id(), creado.estado(), creado.totalCentavos()));
        Comprobar.igual("descuenta las existencias", List.of(8, 2), List.of(unidades(inventario, "teclado"), unidades(inventario, "raton")));
        Comprobar.igual("publica PedidoCreado", "PedidoCreado", eventos.publicados().get(0).tipo());
        Comprobar.igual("se consulta por id", creado, tienda.porId("pedido-1").orElseThrow());
        Comprobar.igual("consultar uno que no existe devuelve vacío", true, tienda.porId("pedido-99").isEmpty());

        Comprobar.lanza("sin existencias se rechaza", ReglaDeNegocioViolada.class, () -> tienda.crear(solicitud("cliente-2", "raton:5")));
        Comprobar.lanza("un producto que no está en el catálogo se rechaza", ProductoDesconocido.class,
                () -> tienda.crear(solicitud("cliente-2", "raton:1", "lampara:1")));
        Comprobar.lanza("sin cliente se rechaza", ReglaDeNegocioViolada.class, () -> tienda.crear(solicitud("", "raton:1")));
        Comprobar.lanza("sin líneas se rechaza", ReglaDeNegocioViolada.class, () -> tienda.crear(solicitud("cliente-2")));
        Comprobar.lanza("cantidad cero se rechaza", ReglaDeNegocioViolada.class, () -> tienda.crear(solicitud("cliente-2", "raton:0")));
        Comprobar.lanza("sobre el tope se rechaza", ReglaDeNegocioViolada.class,
                () -> tienda.crear(solicitud("cliente-2", "monitor:2", "caro:1")));
        Comprobar.igual("ningún rechazo descontó existencias", List.of(8, 2, 2),
                List.of(unidades(inventario, "teclado"), unidades(inventario, "raton"), unidades(inventario, "monitor")));
        Comprobar.igual("ni publicó eventos", 1, eventos.publicados().size());

        System.out.println("Aplicación, confirmar y cancelar:");
        Comprobar.igual("confirmar un pendiente", "CONFIRMADO", tienda.confirmar("pedido-1").estado());
        Comprobar.igual("queda confirmado", "CONFIRMADO", tienda.porId("pedido-1").orElseThrow().estado());
        Comprobar.lanza("confirmar dos veces lo rechaza el dominio", ReglaDeNegocioViolada.class, () -> tienda.confirmar("pedido-1"));
        Comprobar.lanza("confirmar uno que no existe", PedidoNoEncontrado.class, () -> tienda.confirmar("pedido-99"));
        Comprobar.igual("cancelar un confirmado", "CANCELADO", tienda.cancelar("pedido-1").estado());
        Comprobar.igual("cancelar devuelve las existencias", List.of(10, 3), List.of(unidades(inventario, "teclado"), unidades(inventario, "raton")));
        Comprobar.lanza("cancelar dos veces se rechaza", ReglaDeNegocioViolada.class, () -> tienda.cancelar("pedido-1"));
        Comprobar.igual("y no devuelve otra vez", 10, unidades(inventario, "teclado"));
        Comprobar.lanza("cancelar uno que no existe", PedidoNoEncontrado.class, () -> tienda.cancelar("pedido-99"));
        Comprobar.igual("se publicaron creado, confirmado y cancelado", List.of("PedidoCreado", "PedidoConfirmado", "PedidoCancelado"),
                eventos.publicados().stream().map(evento -> evento.tipo()).toList());

        System.out.println("Aplicación, listar pedidos de un cliente (paso 8):");
        String primero = tienda.crear(solicitud("cliente-3", "teclado:1")).id();
        tienda.crear(solicitud("cliente-4", "teclado:1"));
        String tercero = tienda.crear(solicitud("cliente-3", "raton:1")).id();
        Comprobar.igual("solo los del cliente, en orden", List.of(primero, tercero),
                tienda.deCliente("cliente-3").stream().map(VistaDePedido::id).toList());
        Comprobar.igual("un cliente sin pedidos: lista vacía", List.of(), tienda.deCliente("nadie"));

        System.out.println("Aplicación, lo que se devuelve es una vista (paso 7):");
        Comprobar.lanza("la vista no deja modificar sus líneas", UnsupportedOperationException.class,
                () -> creado.lineas().add(new VistaDePedido.Linea("monitor", 1, 1)));
    }

    static Solicitud solicitud(String cliente, String... lineas) {
        return new Solicitud(cliente, java.util.Arrays.stream(lineas)
                .map(texto -> new Linea(texto.split(":")[0], Integer.parseInt(texto.split(":")[1]))).toList());
    }

    static CatalogoEnMemoria catalogo() {
        return new CatalogoEnMemoria(Map.of("teclado", new Dinero(15_000_000), "raton", new Dinero(6_000_000),
                "monitor", new Dinero(90_000_000), "caro", new Dinero(400_000_000)));
    }

    static InventarioEnMemoria inventario() {
        InventarioEnMemoria inventario = new InventarioEnMemoria();
        inventario.guardar(Existencias.de("teclado", 10));
        inventario.guardar(Existencias.de("raton", 3));
        inventario.guardar(Existencias.de("monitor", 2));
        inventario.guardar(Existencias.de("caro", 5));
        return inventario;
    }

    static int unidades(InventarioEnMemoria inventario, String producto) {
        return inventario.existenciasDe(producto).orElseThrow().unidades();
    }
}
