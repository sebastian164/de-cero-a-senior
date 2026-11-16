package tienda;

import static tienda.Datos.solicitud;
import static tienda.Datos.unidades;

import java.util.ArrayList;
import java.util.List;
import tienda.adaptadores.salida.memoria.AlmacenEnMemoria;
import tienda.aplicacion.EventoDePedido;
import tienda.aplicacion.PedidoNoEncontrado;
import tienda.aplicacion.ProductoDesconocido;
import tienda.aplicacion.ServicioDePedidos;
import tienda.aplicacion.VistaDePedido;
import tienda.dominio.ReglaDeNegocioViolada;

/** Cada caso de uso con el servicio real y adaptadores de memoria: caminos felices y cada rechazo. */
public final class PruebasDeLaAplicacion {

    private PruebasDeLaAplicacion() {
    }

    public static void ejecutar() {
        System.out.println("Aplicación, crear:");
        AlmacenEnMemoria almacen = new AlmacenEnMemoria();
        Datos.sembrar(almacen, almacen);
        ServicioDePedidos tienda = new ServicioDePedidos(almacen, Datos.catalogo(), almacen, almacen, almacen, Datos.RELOJ, almacen);

        VistaDePedido creado = tienda.crear(solicitud("k-1", "cliente-1", "teclado:2", "raton:1")).pedido();
        Comprobar.igual("crear devuelve el pedido pendiente con su total", List.of("PENDIENTE", 36_000_000L),
                List.of(creado.estado(), creado.totalCentavos()));
        Comprobar.igual("descuenta las existencias", List.of(8, 2), List.of(unidades(almacen, "teclado"), unidades(almacen, "raton")));
        Comprobar.igual("se consulta por id", creado, tienda.porId(creado.id()).orElseThrow());
        Comprobar.igual("consultar uno que no existe devuelve vacío", true, tienda.porId("pedido-99").isEmpty());

        Comprobar.lanza("sin existencias se rechaza", ReglaDeNegocioViolada.class, () -> tienda.crear(solicitud("k-2", "cliente-2", "raton:5")));
        Comprobar.lanza("un producto que no está en el catálogo se rechaza", ProductoDesconocido.class,
                () -> tienda.crear(solicitud("k-3", "cliente-2", "raton:1", "lampara:1")));
        Comprobar.lanza("sin cliente se rechaza", ReglaDeNegocioViolada.class, () -> tienda.crear(solicitud("k-4", "", "raton:1")));
        Comprobar.lanza("sin líneas se rechaza", ReglaDeNegocioViolada.class, () -> tienda.crear(solicitud("k-5", "cliente-2")));
        Comprobar.lanza("cantidad cero se rechaza", ReglaDeNegocioViolada.class, () -> tienda.crear(solicitud("k-6", "cliente-2", "raton:0")));
        Comprobar.lanza("sobre el tope se rechaza", ReglaDeNegocioViolada.class,
                () -> tienda.crear(solicitud("k-7", "cliente-2", "monitor:2", "caro:1")));
        Comprobar.igual("ningún rechazo descontó existencias", List.of(8, 2, 2),
                List.of(unidades(almacen, "teclado"), unidades(almacen, "raton"), unidades(almacen, "monitor")));

        System.out.println("Aplicación, confirmar y cancelar:");
        Comprobar.igual("confirmar un pendiente", "CONFIRMADO", tienda.confirmar(creado.id()).estado());
        Comprobar.lanza("confirmar dos veces lo rechaza el dominio", ReglaDeNegocioViolada.class, () -> tienda.confirmar(creado.id()));
        Comprobar.lanza("confirmar uno que no existe", PedidoNoEncontrado.class, () -> tienda.confirmar("pedido-99"));
        Comprobar.igual("cancelar un confirmado", "CANCELADO", tienda.cancelar(creado.id()).estado());
        Comprobar.igual("cancelar devuelve las existencias", List.of(10, 3), List.of(unidades(almacen, "teclado"), unidades(almacen, "raton")));
        Comprobar.lanza("cancelar dos veces se rechaza", ReglaDeNegocioViolada.class, () -> tienda.cancelar(creado.id()));
        Comprobar.lanza("cancelar uno que no existe", PedidoNoEncontrado.class, () -> tienda.cancelar("pedido-99"));
        List<EventoDePedido> eventos = new ArrayList<>();
        almacen.entregarPendientes(eventos::add);
        Comprobar.igual("salieron creado, confirmado y cancelado, y nada de los rechazos",
                List.of("PedidoCreado", "PedidoConfirmado", "PedidoCancelado"), eventos.stream().map(EventoDePedido::tipo).toList());

        System.out.println("Aplicación, listar pedidos de un cliente:");
        String primero = tienda.crear(solicitud("k-8", "cliente-3", "teclado:1")).pedido().id();
        tienda.crear(solicitud("k-9", "cliente-4", "teclado:1"));
        String tercero = tienda.crear(solicitud("k-10", "cliente-3", "raton:1")).pedido().id();
        Comprobar.igual("solo los del cliente, en orden", List.of(primero, tercero),
                tienda.deCliente("cliente-3").stream().map(VistaDePedido::id).toList());
        Comprobar.lanza("la vista no deja modificar sus líneas", UnsupportedOperationException.class,
                () -> creado.lineas().add(new VistaDePedido.Linea("monitor", 1, 1)));
    }
}
