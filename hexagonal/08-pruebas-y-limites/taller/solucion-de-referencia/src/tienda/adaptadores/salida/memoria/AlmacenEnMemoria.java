package tienda.adaptadores.salida.memoria;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import tienda.aplicacion.ConflictoDeConcurrencia;
import tienda.aplicacion.EventoDePedido;
import tienda.aplicacion.puertos.salida.Inventario;
import tienda.aplicacion.puertos.salida.PeticionesProcesadas;
import tienda.aplicacion.puertos.salida.PublicadorDeEventos;
import tienda.aplicacion.puertos.salida.RepositorioDePedidos;
import tienda.aplicacion.puertos.salida.UnidadDeTrabajo;
import tienda.dominio.Existencias;
import tienda.dominio.Pedido;

/**
 * La "base de datos" de la tienda en memoria, con transacciones y control de versión.
 * La versión no viaja en el dominio: la lleva el almacén. Cada transacción anota la versión de lo que leyó y,
 * al guardar y al confirmar, exige que siga siendo la vigente. Es lo que hará @Version con JPA en la sesión 07.
 */
public final class AlmacenEnMemoria implements RepositorioDePedidos, Inventario, PeticionesProcesadas, PublicadorDeEventos, UnidadDeTrabajo {

    /** Lo confirmado. */
    private final Map<String, Pedido> pedidos = new LinkedHashMap<>();
    private final Map<String, Integer> existencias = new HashMap<>();
    private final Map<String, Long> versiones = new HashMap<>();          // "pedido:<id>" o "existencias:<producto>"
    private final Map<String, String> peticiones = new HashMap<>();
    private final List<EventoDePedido> bandejaDeSalida = new ArrayList<>();
    private final AtomicInteger secuencia = new AtomicInteger();

    /** Lo pendiente de una transacción: solo lo ve el hilo que la abrió. */
    private static final class Transaccion {
        private final Map<String, Long> leidas = new HashMap<>();
        private final Map<String, Pedido> pedidos = new LinkedHashMap<>();
        private final Map<String, Integer> existencias = new LinkedHashMap<>();
        private final Map<String, String> peticiones = new LinkedHashMap<>();
        private final List<EventoDePedido> eventos = new ArrayList<>();
    }

    private final ThreadLocal<Transaccion> abierta = new ThreadLocal<>();

    // ---- UnidadDeTrabajo ----

    @Override
    public <T> T ejecutar(Supplier<T> trabajo) {
        if (abierta.get() != null) {
            return trabajo.get();
        }
        Transaccion transaccion = new Transaccion();
        abierta.set(transaccion);
        try {
            T resultado = trabajo.get();
            confirmar(transaccion);
            return resultado;
        } finally {
            abierta.remove();
        }
    }

    private synchronized void confirmar(Transaccion transaccion) {
        for (String pedidoId : transaccion.pedidos.keySet()) {
            exigirVersionVigente(transaccion, "pedido:" + pedidoId);
        }
        for (String producto : transaccion.existencias.keySet()) {
            exigirVersionVigente(transaccion, "existencias:" + producto);
        }
        for (String clave : transaccion.peticiones.keySet()) {
            if (peticiones.containsKey(clave)) {
                throw new ConflictoDeConcurrencia("la petición " + clave);    // otra con la misma clave confirmó antes
            }
        }
        transaccion.pedidos.forEach((id, pedido) -> {
            pedidos.put(id, pedido);
            versiones.merge("pedido:" + id, 1L, Long::sum);
        });
        transaccion.existencias.forEach((producto, unidades) -> {
            existencias.put(producto, unidades);
            versiones.merge("existencias:" + producto, 1L, Long::sum);
        });
        peticiones.putAll(transaccion.peticiones);
        bandejaDeSalida.addAll(transaccion.eventos);
    }

    /** Si esta transacción leyó la fila, la versión confirmada debe seguir siendo la que leyó. */
    private void exigirVersionVigente(Transaccion transaccion, String fila) {
        Long leida = transaccion.leidas.get(fila);
        if (leida != null && leida.longValue() != versiones.getOrDefault(fila, 0L)) {
            throw new ConflictoDeConcurrencia(fila);
        }
    }

    private void anotarLectura(String fila) {
        Transaccion transaccion = abierta.get();
        if (transaccion != null) {
            transaccion.leidas.putIfAbsent(fila, versiones.getOrDefault(fila, 0L));   // se llama con el monitor tomado
        }
    }

    private void enTransaccion(Consumer<Transaccion> accion) {
        ejecutar(() -> {
            accion.accept(abierta.get());
            return null;
        });
    }

    // ---- RepositorioDePedidos ----

    @Override
    public String siguienteId() {
        return "pedido-" + secuencia.incrementAndGet();
    }

    @Override
    public void guardar(Pedido pedido) {
        enTransaccion(transaccion -> {
            synchronized (this) {
                exigirVersionVigente(transaccion, "pedido:" + pedido.id());   // falla pronto si ya se sabe
            }
            transaccion.pedidos.put(pedido.id(), copia(pedido));
        });
    }

    @Override
    public Optional<Pedido> buscar(String pedidoId) {
        Transaccion transaccion = abierta.get();
        if (transaccion != null && transaccion.pedidos.containsKey(pedidoId)) {
            return Optional.of(copia(transaccion.pedidos.get(pedidoId)));
        }
        synchronized (this) {
            anotarLectura("pedido:" + pedidoId);
            return Optional.ofNullable(pedidos.get(pedidoId)).map(AlmacenEnMemoria::copia);
        }
    }

    @Override
    public List<Pedido> deCliente(String cliente) {
        Map<String, Pedido> vistos;
        synchronized (this) {
            vistos = new LinkedHashMap<>(pedidos);
        }
        Transaccion transaccion = abierta.get();
        if (transaccion != null) {
            vistos.putAll(transaccion.pedidos);
        }
        return vistos.values().stream().filter(pedido -> pedido.cliente().equals(cliente)).map(AlmacenEnMemoria::copia).toList();
    }

    private static Pedido copia(Pedido pedido) {
        return Pedido.existente(pedido.id(), pedido.cliente(), pedido.lineas(), pedido.estado(), pedido.creadoEn());
    }

    // ---- Inventario ----

    @Override
    public Optional<Existencias> existenciasDe(String producto) {
        Transaccion transaccion = abierta.get();
        if (transaccion != null && transaccion.existencias.containsKey(producto)) {
            return Optional.of(Existencias.de(producto, transaccion.existencias.get(producto)));
        }
        synchronized (this) {
            anotarLectura("existencias:" + producto);
            return Optional.ofNullable(existencias.get(producto)).map(unidades -> Existencias.de(producto, unidades));
        }
    }

    @Override
    public void guardar(Existencias nuevas) {
        enTransaccion(transaccion -> {
            synchronized (this) {
                exigirVersionVigente(transaccion, "existencias:" + nuevas.producto());
            }
            transaccion.existencias.put(nuevas.producto(), nuevas.unidades());
        });
    }

    // ---- PeticionesProcesadas ----

    @Override
    public Optional<String> pedidoDe(String clave) {
        Transaccion transaccion = abierta.get();
        if (transaccion != null && transaccion.peticiones.containsKey(clave)) {
            return Optional.of(transaccion.peticiones.get(clave));
        }
        synchronized (this) {
            return Optional.ofNullable(peticiones.get(clave));
        }
    }

    @Override
    public void registrar(String clave, String pedidoId) {
        enTransaccion(transaccion -> transaccion.peticiones.put(clave, pedidoId));
    }

    // ---- PublicadorDeEventos: bandeja de salida ----

    @Override
    public void publicar(EventoDePedido evento) {
        enTransaccion(transaccion -> transaccion.eventos.add(evento));
    }

    public synchronized int entregarPendientes(Consumer<EventoDePedido> destino) {
        int entregados = 0;
        for (Iterator<EventoDePedido> pendientes = bandejaDeSalida.iterator(); pendientes.hasNext(); ) {
            destino.accept(pendientes.next());
            pendientes.remove();
            entregados++;
        }
        return entregados;
    }
}
