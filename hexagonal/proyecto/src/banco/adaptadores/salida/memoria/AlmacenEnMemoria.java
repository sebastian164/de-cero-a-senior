package banco.adaptadores.salida.memoria;

import banco.aplicacion.ConflictoDeConcurrencia;
import banco.aplicacion.EventoDeCuenta;
import banco.aplicacion.puertos.salida.OrdenesProcesadas;
import banco.aplicacion.puertos.salida.PublicadorDeEventos;
import banco.aplicacion.puertos.salida.RegistroDeMovimientos;
import banco.aplicacion.puertos.salida.RepositorioDeCuentas;
import banco.aplicacion.puertos.salida.UnidadDeTrabajo;
import banco.dominio.Cuenta;
import banco.dominio.Movimiento;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Una "base de datos" en memoria, con transacciones y control de versión. */
public final class AlmacenEnMemoria implements RepositorioDeCuentas, RegistroDeMovimientos, OrdenesProcesadas,
        PublicadorDeEventos, UnidadDeTrabajo {

    /** Lo confirmado: lo que ve todo el mundo. */
    private final Map<String, Cuenta> cuentas = new LinkedHashMap<>();
    private final List<Movimiento> movimientos = new ArrayList<>();
    private final Set<String> ordenes = new LinkedHashSet<>();
    private final List<EventoDeCuenta> bandejaDeSalida = new ArrayList<>();
    private final AtomicInteger secuencia = new AtomicInteger();

    /** Lo pendiente de una transacción: solo lo ve quien la abrió. */
    private static final class Transaccion {
        private final Map<String, Cuenta> cuentas = new LinkedHashMap<>();
        private final List<Movimiento> movimientos = new ArrayList<>();
        private final Set<String> ordenes = new LinkedHashSet<>();
        private final List<EventoDeCuenta> eventos = new ArrayList<>();
    }

    private final ThreadLocal<Transaccion> abierta = new ThreadLocal<>();

    // ---- UnidadDeTrabajo (sesión 04) ----

    @Override
    public <T> T ejecutar(Supplier<T> trabajo) {
        if (abierta.get() != null) {
            return trabajo.get();                                // ya hay una abierta: se une a ella
        }
        Transaccion transaccion = new Transaccion();
        abierta.set(transaccion);
        try {
            T resultado = trabajo.get();
            confirmar(transaccion);                              // todo lo pendiente pasa a ser visible, de una vez
            return resultado;
        } finally {
            abierta.remove();                                    // si algo falló, lo pendiente se descarta: eso es revertir
        }
    }

    private synchronized void confirmar(Transaccion transaccion) {
        for (Cuenta cuenta : transaccion.cuentas.values()) {
            exigirVersionVigente(cuenta);
        }
        for (String clave : transaccion.ordenes) {
            if (ordenes.contains(clave)) {
                throw new ConflictoDeConcurrencia("la orden " + clave);
            }
        }
        for (Cuenta cuenta : transaccion.cuentas.values()) {
            cuentas.put(cuenta.id(), copia(cuenta, cuenta.version() + 1));
        }
        movimientos.addAll(transaccion.movimientos);
        ordenes.addAll(transaccion.ordenes);
        bandejaDeSalida.addAll(transaccion.eventos);
    }

    /** Fuera de una transacción, cada operación es una transacción de un solo paso. */
    private void enTransaccion(Consumer<Transaccion> accion) {
        ejecutar(() -> {
            accion.accept(abierta.get());
            return null;
        });
    }

    // ---- RepositorioDeCuentas ----

    @Override
    public String siguienteId() {
        return "cuenta-" + secuencia.incrementAndGet();          // como una secuencia de base de datos: no se revierte
    }

    @Override
    public void guardar(Cuenta cuenta) {
        enTransaccion(transaccion -> {
            synchronized (this) {
                exigirVersionVigente(cuenta);                    // sesión 05
            }
            transaccion.cuentas.put(cuenta.id(), copia(cuenta, cuenta.version()));
        });
    }

    @Override
    public Optional<Cuenta> buscar(String id) {
        Transaccion transaccion = abierta.get();
        if (transaccion != null && transaccion.cuentas.containsKey(id)) {
            Cuenta pendiente = transaccion.cuentas.get(id);      // quien la cambió ve su propio cambio
            return Optional.of(copia(pendiente, pendiente.version()));
        }
        synchronized (this) {
            return Optional.ofNullable(cuentas.get(id)).map(cuenta -> copia(cuenta, cuenta.version()));
        }
    }

    private void exigirVersionVigente(Cuenta cuenta) {
        Cuenta confirmada = cuentas.get(cuenta.id());
        if (confirmada != null && confirmada.version() != cuenta.version()) {
            throw new ConflictoDeConcurrencia("la cuenta " + cuenta.id());   // alguien la cambió desde que se leyó
        }
    }

    /** Se entregan y se guardan copias: quien tiene una cuenta en la mano no modifica lo guardado. */
    private static Cuenta copia(Cuenta cuenta, long version) {
        return Cuenta.existente(cuenta.id(), cuenta.titular(), cuenta.saldo(), cuenta.enviadoHoy(), cuenta.diaDelTope(), version);
    }

    // ---- RegistroDeMovimientos ----

    @Override
    public void registrar(Movimiento movimiento) {
        enTransaccion(transaccion -> transaccion.movimientos.add(movimiento));
    }

    @Override
    public List<Movimiento> de(String cuentaId) {
        List<Movimiento> resultado = new ArrayList<>();
        synchronized (this) {
            resultado.addAll(movimientos);
        }
        Transaccion transaccion = abierta.get();
        if (transaccion != null) {
            resultado.addAll(transaccion.movimientos);
        }
        resultado.removeIf(movimiento -> !movimiento.cuentaId().equals(cuentaId));
        return resultado;
    }

    // ---- OrdenesProcesadas ----

    @Override
    public boolean registrarSiEsNueva(String clave) {
        boolean[] nueva = new boolean[1];
        enTransaccion(transaccion -> {
            boolean confirmada;
            synchronized (this) {
                confirmada = ordenes.contains(clave);
            }
            nueva[0] = !confirmada && transaccion.ordenes.add(clave);
        });
        return nueva[0];
    }

    // ---- PublicadorDeEventos: una bandeja de salida (sesión 04) ----

    @Override
    public void publicar(EventoDeCuenta evento) {
        enTransaccion(transaccion -> transaccion.eventos.add(evento));
    }

    /** Entrega lo confirmado a su destino real. Si el destino falla, el evento se queda para el siguiente intento. */
    public synchronized int entregarPendientes(Consumer<EventoDeCuenta> destino) {
        int entregados = 0;
        for (Iterator<EventoDeCuenta> pendientes = bandejaDeSalida.iterator(); pendientes.hasNext(); ) {
            destino.accept(pendientes.next());
            pendientes.remove();
            entregados++;
        }
        return entregados;
    }
}
