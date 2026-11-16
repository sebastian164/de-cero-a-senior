package tienda;

import static tienda.Datos.solicitud;
import static tienda.Datos.unidades;

import java.util.ArrayList;
import java.util.List;
import tienda.adaptadores.salida.memoria.AlmacenEnMemoria;
import tienda.aplicacion.EventoDePedido;
import tienda.aplicacion.ServicioDePedidos;
import tienda.aplicacion.puertos.entrada.CrearPedido.Resultado;
import tienda.dominio.ReglaDeNegocioViolada;

/** Atomicidad, idempotencia y bandeja de salida, provocando el fallo a voluntad. */
public final class PruebasDeTransacciones {

    private PruebasDeTransacciones() {
    }

    public static void ejecutar() {
        System.out.println("Transacciones, sin unidad de trabajo (paso 1):");
        sinTransaccion();
        System.out.println("Transacciones, con unidad de trabajo (paso 4):");
        conTransaccion();
        System.out.println("Idempotencia (paso 6):");
        idempotencia();
        System.out.println("Bandeja de salida (paso 7):");
        bandejaDeSalida();
    }

    private static void sinTransaccion() {
        AlmacenEnMemoria almacen = new AlmacenEnMemoria();
        Datos.sembrar(almacen, almacen);
        PedidosQueFallan pedidos = new PedidosQueFallan(almacen);
        ServicioDePedidos tienda = new ServicioDePedidos(pedidos, Datos.catalogo(), almacen, almacen, new SinTransaccion(), Datos.RELOJ, almacen);

        pedidos.averiar();
        Comprobar.lanza("crear falla al guardar el pedido", IllegalStateException.class,
                () -> tienda.crear(solicitud("k-1", "cliente-1", "teclado:2", "monitor:1")));
        Comprobar.igual("pero las existencias ya se descontaron", List.of(8, 1), List.of(unidades(almacen, "teclado"), unidades(almacen, "monitor")));
        Comprobar.igual("y el cliente no tiene ningún pedido", 0, tienda.deCliente("cliente-1").size());
        pedidos.reparar();
        tienda.crear(solicitud("k-1", "cliente-1", "teclado:2", "monitor:1"));
        Comprobar.igual("al reintentar se descuenta otra vez: 4 teclados menos por un pedido de 2", 6, unidades(almacen, "teclado"));
        Comprobar.lanza("y el último monitor ya no se puede vender a otro", ReglaDeNegocioViolada.class,
                () -> tienda.crear(solicitud("k-2", "cliente-2", "monitor:1")));
    }

    private static void conTransaccion() {
        AlmacenEnMemoria almacen = new AlmacenEnMemoria();
        Datos.sembrar(almacen, almacen);
        PedidosQueFallan pedidos = new PedidosQueFallan(almacen);
        ServicioDePedidos tienda = new ServicioDePedidos(pedidos, Datos.catalogo(), almacen, almacen, almacen, Datos.RELOJ, almacen);

        pedidos.averiar();
        Comprobar.lanza("crear falla al guardar el pedido", IllegalStateException.class,
                () -> tienda.crear(solicitud("k-1", "cliente-1", "teclado:2", "monitor:1")));
        Comprobar.igual("no queda ninguna existencia descontada", List.of(10, 2), List.of(unidades(almacen, "teclado"), unidades(almacen, "monitor")));
        Comprobar.igual("ni pedido", 0, tienda.deCliente("cliente-1").size());
        Comprobar.igual("ni la clave anotada", true, almacen.pedidoDe("k-1").isEmpty());
    }

    private static void idempotencia() {
        AlmacenEnMemoria almacen = new AlmacenEnMemoria();
        Datos.sembrar(almacen, almacen);
        PedidosQueFallan pedidos = new PedidosQueFallan(almacen);
        ServicioDePedidos tienda = new ServicioDePedidos(pedidos, Datos.catalogo(), almacen, almacen, almacen, Datos.RELOJ, almacen);

        Resultado primera = tienda.crear(solicitud("k-1", "cliente-1", "teclado:2"));
        Resultado segunda = tienda.crear(solicitud("k-1", "cliente-1", "teclado:2"));
        Comprobar.igual("la misma petición dos veces crea un solo pedido", 1, tienda.deCliente("cliente-1").size());
        Comprobar.igual("y descuenta una sola vez", 8, unidades(almacen, "teclado"));
        Comprobar.igual("la segunda respuesta es de éxito, con el mismo pedido", primera.pedido(), segunda.pedido());
        Comprobar.igual("y dice que no es nuevo", List.of(true, false), List.of(primera.nuevo(), segunda.nuevo()));

        pedidos.averiar();
        Comprobar.lanza("una petición que falla", IllegalStateException.class, () -> tienda.crear(solicitud("k-2", "cliente-2", "raton:1")));
        pedidos.reparar();
        Resultado reintento = tienda.crear(solicitud("k-2", "cliente-2", "raton:1"));
        Comprobar.igual("se puede reintentar con la misma clave y se crea", true, reintento.nuevo());
        Comprobar.igual("una sola vez", List.of(1, 2), List.of(tienda.deCliente("cliente-2").size(), unidades(almacen, "raton")));

        Comprobar.lanza("una petición rechazada por el negocio", ReglaDeNegocioViolada.class, () -> tienda.crear(solicitud("k-3", "cliente-3", "raton:9")));
        Comprobar.igual("no deja la clave anotada: se puede corregir y reintentar", true, almacen.pedidoDe("k-3").isEmpty());
    }

    private static void bandejaDeSalida() {
        AlmacenEnMemoria almacen = new AlmacenEnMemoria();
        Datos.sembrar(almacen, almacen);
        PedidosQueFallan pedidos = new PedidosQueFallan(almacen);
        ServicioDePedidos tienda = new ServicioDePedidos(pedidos, Datos.catalogo(), almacen, almacen, almacen, Datos.RELOJ, almacen);
        List<EventoDePedido> recibidos = new ArrayList<>();

        Comprobar.lanza("un pedido rechazado", ReglaDeNegocioViolada.class, () -> tienda.crear(solicitud("k-1", "cliente-1", "monitor:3")));
        pedidos.averiar();
        Comprobar.lanza("y uno que falla a mitad", IllegalStateException.class, () -> tienda.crear(solicitud("k-2", "cliente-1", "monitor:1")));
        pedidos.reparar();
        Comprobar.igual("no dejan eventos pendientes", 0, almacen.entregarPendientes(recibidos::add));

        tienda.crear(solicitud("k-3", "cliente-1", "monitor:1"));
        Comprobar.lanza("si el destino está caído, la entrega falla", IllegalStateException.class,
                () -> almacen.entregarPendientes(evento -> {
                    throw new IllegalStateException("correo no disponible");
                }));
        Comprobar.igual("pero el evento no se pierde: sale en el siguiente intento", 1, almacen.entregarPendientes(recibidos::add));
        Comprobar.igual("con un id estable", true, recibidos.get(0).id().startsWith("pedido-creado:"));
        Comprobar.igual("y no sale dos veces", 0, almacen.entregarPendientes(recibidos::add));
    }
}
