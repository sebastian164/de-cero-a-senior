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
import tienda.aplicacion.EventoDePedido;
import tienda.aplicacion.puertos.salida.Inventario;
import tienda.aplicacion.puertos.salida.PeticionesProcesadas;
import tienda.aplicacion.puertos.salida.PublicadorDeEventos;
import tienda.aplicacion.puertos.salida.RepositorioDePedidos;
import tienda.aplicacion.puertos.salida.UnidadDeTrabajo;
import tienda.dominio.Existencias;
import tienda.dominio.Pedido;

/** La "base de datos" de la tienda en memoria: pedidos, existencias, claves y bandeja de salida, con transacciones. */
public final class AlmacenEnMemoria implements RepositorioDePedidos, Inventario, PeticionesProcesadas, PublicadorDeEventos, UnidadDeTrabajo {

    /** Lo confirmado. */
    private final Map<String, Pedido> pedidos = new LinkedHashMap<>();
    private final Map<String, Integer> existencias = new HashMap<>();
    private final Map<String, String> peticiones = new HashMap<>();
    private final List<EventoDePedido> bandejaDeSalida = new ArrayList<>();
    private final AtomicInteger secuencia = new AtomicInteger();

    /** Lo pendiente de una transacción: solo lo ve el hilo que la abrió. */
    private static final class Transaccion {
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
            return trabajo.get();                                // ya hay una: se une a ella
        }
        Transaccion transaccion = new Transaccion();
        abierta.set(transaccion);
        try {
            T resultado = trabajo.get();
            confirmar(transaccion);
            return resultado;
        } finally {
            abierta.remove();                                    // si algo falló, lo pendiente se descarta
        }
    }

    private synchronized void confirmar(Transaccion transaccion) {
        pedidos.putAll(transaccion.pedidos);
        existencias.putAll(transaccion.existencias);
        peticiones.putAll(transaccion.peticiones);               // dos transacciones simultáneas con la misma clave: sesión 05
        bandejaDeSalida.addAll(transaccion.eventos);
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
        return "pedido-" + secuencia.incrementAndGet();          // como una secuencia de base de datos: no se revierte
    }

    @Override
    public void guardar(Pedido pedido) {
        enTransaccion(transaccion -> transaccion.pedidos.put(pedido.id(), copia(pedido)));
    }

    @Override
    public Optional<Pedido> buscar(String pedidoId) {
        Transaccion transaccion = abierta.get();
        if (transaccion != null && transaccion.pedidos.containsKey(pedidoId)) {
            return Optional.of(copia(transaccion.pedidos.get(pedidoId)));
        }
        synchronized (this) {
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
            return Optional.ofNullable(existencias.get(producto)).map(unidades -> Existencias.de(producto, unidades));
        }
    }

    @Override
    public void guardar(Existencias nuevas) {
        enTransaccion(transaccion -> transaccion.existencias.put(nuevas.producto(), nuevas.unidades()));
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

    /** El mensajero: entrega lo confirmado. Si el destino falla, el evento se queda para el siguiente intento. */
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
