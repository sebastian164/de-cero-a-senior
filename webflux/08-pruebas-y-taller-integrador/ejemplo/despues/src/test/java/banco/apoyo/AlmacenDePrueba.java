package banco.apoyo;

import banco.aplicacion.ConflictoDeConcurrencia;
import banco.aplicacion.EventoDeCuenta;
import banco.aplicacion.reactiva.puertos.salida.OrdenesProcesadas;
import banco.aplicacion.reactiva.puertos.salida.PublicadorDeEventos;
import banco.aplicacion.reactiva.puertos.salida.RegistroDeMovimientos;
import banco.aplicacion.reactiva.puertos.salida.RepositorioDeCuentas;
import banco.aplicacion.reactiva.puertos.salida.UnidadDeTrabajo;
import banco.dominio.Cuenta;
import banco.dominio.Movimiento;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Los puertos de salida en memoria, para probar el servicio sin base de datos. Respeta lo que prometen los puertos:
 * versión (conflictos), unicidad de órdenes y todo o nada (si el trabajo falla, se restaura la foto anterior).
 * Puede fingir conflictos: los próximos "conflictosPorFingir" guardados fallan como si otro hubiera escrito antes.
 */
public final class AlmacenDePrueba implements RepositorioDeCuentas, RegistroDeMovimientos, OrdenesProcesadas,
        PublicadorDeEventos, UnidadDeTrabajo {

    private Map<String, Cuenta> cuentas = new HashMap<>();
    private List<Movimiento> movimientos = new ArrayList<>();
    private Set<String> ordenes = new HashSet<>();
    private List<EventoDeCuenta> eventos = new ArrayList<>();
    private int siguiente = 1;

    public final AtomicInteger transacciones = new AtomicInteger();
    public int conflictosPorFingir = 0;

    // ---- UnidadDeTrabajo ----

    @Override
    public <T> Mono<T> ejecutar(Supplier<Mono<T>> trabajo) {
        return Mono.defer(() -> {
            transacciones.incrementAndGet();
            Map<String, Cuenta> fotoCuentas = new HashMap<>(cuentas);
            List<Movimiento> fotoMovimientos = new ArrayList<>(movimientos);
            Set<String> fotoOrdenes = new HashSet<>(ordenes);
            List<EventoDeCuenta> fotoEventos = new ArrayList<>(eventos);
            return trabajo.get().doOnError(error -> {             // revertir: volver a la foto
                cuentas = fotoCuentas;
                movimientos = fotoMovimientos;
                ordenes = fotoOrdenes;
                eventos = fotoEventos;
            });
        });
    }

    // ---- RepositorioDeCuentas ----

    @Override
    public String siguienteId() {
        return "cuenta-" + siguiente++;
    }

    @Override
    public Mono<Void> guardar(Cuenta cuenta) {
        return Mono.fromRunnable(() -> {
            Cuenta guardada = cuentas.get(cuenta.id());
            long versionGuardada = guardada == null ? 0 : guardada.version();
            if (conflictosPorFingir > 0 || versionGuardada != cuenta.version()) {
                if (conflictosPorFingir > 0) {
                    conflictosPorFingir--;
                }
                throw new ConflictoDeConcurrencia("la cuenta " + cuenta.id());
            }
            cuentas.put(cuenta.id(), copia(cuenta, cuenta.version() + 1));
        });
    }

    @Override
    public Mono<Cuenta> buscar(String id) {
        return Mono.fromSupplier(() -> cuentas.get(id)).map(guardada -> copia(guardada, guardada.version()));
    }

    private static Cuenta copia(Cuenta cuenta, long version) {   // quien lee recibe una copia, como de una base real
        return Cuenta.existente(cuenta.id(), cuenta.titular(), cuenta.saldo(), cuenta.enviadoHoy(), cuenta.diaDelTope(), version);
    }

    // ---- RegistroDeMovimientos ----

    @Override
    public Mono<Void> registrar(Movimiento movimiento) {
        return Mono.fromRunnable(() -> movimientos.add(movimiento));
    }

    @Override
    public Flux<Movimiento> de(String cuentaId) {
        return Flux.defer(() -> Flux.fromIterable(List.copyOf(movimientos)))
                .filter(movimiento -> movimiento.cuentaId().equals(cuentaId));
    }

    // ---- OrdenesProcesadas ----

    @Override
    public Mono<Boolean> registrarSiEsNueva(String clave) {
        return Mono.fromSupplier(() -> ordenes.add(clave));
    }

    // ---- PublicadorDeEventos ----

    @Override
    public Mono<Void> publicar(EventoDeCuenta evento) {
        return Mono.fromRunnable(() -> eventos.add(evento));
    }

    public int cuantosMovimientos() {
        return movimientos.size();
    }

    public int cuantosEventos() {
        return eventos.size();
    }
}
