package tienda.dominio;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Un pedido. Entidad: tiene identidad y estado. No puede existir uno inválido, y solo él cambia su estado. */
public final class Pedido {

    public static final Dinero TOPE = Dinero.pesos(5_000_000);

    /** Más de estas unidades de un mismo producto necesitan aprobación. */
    public static final int UNIDADES_SIN_APROBACION = 10;

    private final String id;
    private final String cliente;
    private final List<LineaDePedido> lineas;
    private final Instant creadoEn;
    private EstadoDePedido estado;

    private Pedido(String id, String cliente, List<LineaDePedido> lineas, EstadoDePedido estado, Instant creadoEn) {
        this.id = id;
        this.cliente = cliente;
        this.lineas = List.copyOf(lineas);                       // nadie de fuera puede añadir ni quitar líneas
        this.estado = estado;
        this.creadoEn = creadoEn;
    }

    /** La fecha llega de fuera: el pedido no sabe qué hora es. */
    public static Pedido crear(String id, String cliente, List<LineaDePedido> lineas, Instant creadoEn) {
        if (cliente == null || cliente.isBlank()) {
            throw new ReglaDeNegocioViolada("un pedido necesita un cliente");
        }
        if (lineas == null || lineas.isEmpty()) {
            throw new ReglaDeNegocioViolada("un pedido necesita al menos una línea");
        }
        Pedido pedido = new Pedido(id, cliente, lineas, EstadoDePedido.PENDIENTE, creadoEn);
        if (pedido.total().esMayorQue(TOPE)) {
            throw new ReglaDeNegocioViolada("el pedido supera el tope de " + TOPE.centavos() + " centavos");
        }
        if (pedido.unidadesPorProducto().values().stream().anyMatch(unidades -> unidades > UNIDADES_SIN_APROBACION)) {
            pedido.estado = EstadoDePedido.REQUIERE_APROBACION;
        }
        return pedido;
    }

    /** Reconstruye un pedido que ya existía. Lo usan los adaptadores que lo leen de donde esté guardado. */
    public static Pedido existente(String id, String cliente, List<LineaDePedido> lineas, EstadoDePedido estado, Instant creadoEn) {
        return new Pedido(id, cliente, lineas, estado, creadoEn);
    }

    public void aprobar() {
        if (estado != EstadoDePedido.REQUIERE_APROBACION) {
            throw new ReglaDeNegocioViolada("el pedido " + id + " no necesita aprobación; está " + estado);
        }
        estado = EstadoDePedido.PENDIENTE;
    }

    public void confirmar() {
        if (estado != EstadoDePedido.PENDIENTE) {
            throw new ReglaDeNegocioViolada("solo se confirma un pedido pendiente; " + id + " está " + estado);
        }
        estado = EstadoDePedido.CONFIRMADO;
    }

    /** Se cancela pendiente, por aprobar o confirmado: en todos los casos sus unidades estaban reservadas. */
    public void cancelar() {
        if (estado == EstadoDePedido.CANCELADO) {
            throw new ReglaDeNegocioViolada("el pedido " + id + " ya está cancelado");
        }
        estado = EstadoDePedido.CANCELADO;
    }

    public Dinero total() {
        Dinero total = Dinero.CERO;
        for (LineaDePedido linea : lineas) {
            total = total.mas(linea.subtotal());
        }
        return total;
    }

    /** Unidades pedidas de cada producto, sumando líneas repetidas. */
    public Map<String, Integer> unidadesPorProducto() {
        Map<String, Integer> unidades = new LinkedHashMap<>();
        for (LineaDePedido linea : lineas) {
            unidades.merge(linea.producto(), linea.cantidad(), Math::addExact);
        }
        return unidades;
    }

    public String id() {
        return id;
    }

    public String cliente() {
        return cliente;
    }

    public List<LineaDePedido> lineas() {
        return lineas;
    }

    public EstadoDePedido estado() {
        return estado;
    }

    public Instant creadoEn() {
        return creadoEn;
    }
}
