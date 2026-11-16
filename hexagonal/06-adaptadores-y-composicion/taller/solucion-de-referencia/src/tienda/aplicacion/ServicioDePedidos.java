package tienda.aplicacion;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import tienda.aplicacion.puertos.entrada.CancelarPedido;
import tienda.aplicacion.puertos.entrada.ConfirmarPedido;
import tienda.aplicacion.puertos.entrada.ConsultarPedido;
import tienda.aplicacion.puertos.entrada.CrearPedido;
import tienda.aplicacion.puertos.entrada.ListarPedidosDeCliente;
import tienda.aplicacion.puertos.salida.Catalogo;
import tienda.aplicacion.puertos.salida.Inventario;
import tienda.aplicacion.puertos.salida.PeticionesProcesadas;
import tienda.aplicacion.puertos.salida.PublicadorDeEventos;
import tienda.aplicacion.puertos.salida.Reloj;
import tienda.aplicacion.puertos.salida.RepositorioDePedidos;
import tienda.aplicacion.puertos.salida.UnidadDeTrabajo;
import tienda.dominio.Dinero;
import tienda.dominio.Existencias;
import tienda.dominio.LineaDePedido;
import tienda.dominio.Pedido;
import tienda.dominio.Reserva;

/** Los casos de uso de los pedidos. Coordina; las reglas están en el dominio. Solo conoce puertos. */
public final class ServicioDePedidos implements CrearPedido, ConfirmarPedido, CancelarPedido, ConsultarPedido, ListarPedidosDeCliente {

    /** Intentos ante un conflicto de concurrencia. Al agotarlos, quien llamó recibe ConflictoDeConcurrencia (HTTP 409). */
    public static final int INTENTOS = 3;

    private final RepositorioDePedidos pedidos;
    private final Catalogo catalogo;
    private final Inventario inventario;
    private final PeticionesProcesadas peticiones;
    private final UnidadDeTrabajo unidadDeTrabajo;
    private final Reloj reloj;
    private final PublicadorDeEventos eventos;

    public ServicioDePedidos(RepositorioDePedidos pedidos, Catalogo catalogo, Inventario inventario, PeticionesProcesadas peticiones,
                             UnidadDeTrabajo unidadDeTrabajo, Reloj reloj, PublicadorDeEventos eventos) {
        this.pedidos = pedidos;
        this.catalogo = catalogo;
        this.inventario = inventario;
        this.peticiones = peticiones;
        this.unidadDeTrabajo = unidadDeTrabajo;
        this.reloj = reloj;
        this.eventos = eventos;
    }

    @Override
    public Resultado crear(Solicitud solicitud) {
        return conReintento(() -> unidadDeTrabajo.ejecutar(() -> {   // todo o nada; si otro se adelantó, desde el principio
            Optional<String> anterior = peticiones.pedidoDe(solicitud.clave());
            if (anterior.isPresent()) {
                return new Resultado(VistaDePedido.de(exigir(anterior.get())), false);   // la misma solicitud: el mismo pedido
            }
            Instant ahora = reloj.ahora();
            List<LineaDePedido> lineas = solicitud.lineas().stream()
                    .map(linea -> new LineaDePedido(linea.producto(), linea.cantidad(), precio(linea.producto())))
                    .toList();
            Pedido pedido = Pedido.crear(pedidos.siguienteId(), solicitud.cliente(), lineas, ahora);
            Map<String, Existencias> existencias = existenciasDe(pedido);

            Reserva.reservar(pedido, existencias);               // la regla, en el dominio

            existencias.values().forEach(inventario::guardar);
            pedidos.guardar(pedido);                             // si esto falla, lo anterior se revierte con la unidad de trabajo
            peticiones.registrar(solicitud.clave(), pedido.id());
            eventos.publicar(new EventoDePedido("pedido-creado:" + pedido.id(), "PedidoCreado", pedido.id(), pedido.cliente(), ahora));
            return new Resultado(VistaDePedido.de(pedido), true);
        }));
    }

    @Override
    public VistaDePedido confirmar(String pedidoId) {
        return conReintento(() -> unidadDeTrabajo.ejecutar(() -> {
            Pedido pedido = exigir(pedidoId);
            pedido.confirmar();
            pedidos.guardar(pedido);
            // El correo al cliente no se envía aquí: sale de la bandeja de salida cuando la transacción ya confirmó.
            eventos.publicar(new EventoDePedido("pedido-confirmado:" + pedidoId, "PedidoConfirmado", pedidoId, pedido.cliente(), reloj.ahora()));
            return VistaDePedido.de(pedido);
        }));
    }

    @Override
    public VistaDePedido cancelar(String pedidoId) {
        return conReintento(() -> unidadDeTrabajo.ejecutar(() -> {
            Pedido pedido = exigir(pedidoId);
            pedido.cancelar();
            Map<String, Existencias> existencias = existenciasDe(pedido);
            Reserva.liberar(pedido, existencias);
            existencias.values().forEach(inventario::guardar);
            pedidos.guardar(pedido);
            eventos.publicar(new EventoDePedido("pedido-cancelado:" + pedidoId, "PedidoCancelado", pedidoId, pedido.cliente(), reloj.ahora()));
            return VistaDePedido.de(pedido);
        }));
    }

    /** Una sola lectura: no necesita unidad de trabajo. */
    @Override
    public Optional<VistaDePedido> porId(String pedidoId) {
        return pedidos.buscar(pedidoId).map(VistaDePedido::de);
    }

    /** Una sola lectura: no necesita unidad de trabajo. */
    @Override
    public List<VistaDePedido> deCliente(String cliente) {
        return pedidos.deCliente(cliente).stream().map(VistaDePedido::de).toList();
    }

    /** Si otro cambió lo mismo a la vez, se repite el caso de uso entero: se lee y se decide otra vez. */
    private <T> T conReintento(Supplier<T> operacion) {
        for (int intento = 1; ; intento++) {
            try {
                return operacion.get();
            } catch (ConflictoDeConcurrencia conflicto) {
                if (intento == INTENTOS) {
                    throw conflicto;
                }
            }
        }
    }

    private Pedido exigir(String pedidoId) {
        return pedidos.buscar(pedidoId).orElseThrow(() -> new PedidoNoEncontrado(pedidoId));
    }

    private Dinero precio(String producto) {
        return catalogo.precioDe(producto).orElseThrow(() -> new ProductoDesconocido(producto));
    }

    /** Las existencias de cada producto del pedido. Si un producto no tiene registro, tiene cero unidades. */
    private Map<String, Existencias> existenciasDe(Pedido pedido) {
        Map<String, Existencias> existencias = new LinkedHashMap<>();
        for (String producto : pedido.unidadesPorProducto().keySet()) {
            existencias.put(producto, inventario.existenciasDe(producto).orElseGet(() -> Existencias.de(producto, 0)));
        }
        return existencias;
    }
}
