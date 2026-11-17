package tienda.adaptadores.salida.memoria;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import tienda.aplicacion.puertos.salida.UnidadDeTrabajo;
import tienda.aplicacion.saga.Puertos.RepositorioDeSagas;
import tienda.dominio.saga.SagaDeCompra;

/**
 * El repositorio de sagas en memoria. No toca el AlmacenEnMemoria del servicio de pedidos: se acopla a su unidad de trabajo con un
 * decorador (unidad(...)). Lo que se guarda dentro de la unidad queda apartado; solo se hace visible si la unidad del almacén
 * (pedidos, existencias, eventos) confirma, y se descarta si se revierte. Así el estado de la saga y el del pedido se confirman o se
 * revierten juntos, sin que el almacén sepa que existen las sagas.
 */
public final class SagasEnMemoria implements RepositorioDeSagas {

    private final Map<String, SagaDeCompra> confirmadas = new LinkedHashMap<>();
    private final ThreadLocal<Map<String, SagaDeCompra>> apartadas = new ThreadLocal<>();

    @Override
    public synchronized Optional<SagaDeCompra> buscar(String id) {
        Map<String, SagaDeCompra> a = apartadas.get();
        SagaDeCompra s = a != null && a.containsKey(id) ? a.get(id) : confirmadas.get(id);
        return Optional.ofNullable(s).map(SagaDeCompra::copia);
    }

    @Override
    public void guardar(SagaDeCompra saga) {
        Map<String, SagaDeCompra> a = apartadas.get();
        if (a == null) throw new IllegalStateException("las sagas se guardan dentro de una unidad de trabajo");
        a.put(saga.id(), saga.copia());
    }

    @Override
    public synchronized List<SagaDeCompra> abiertas() {
        List<SagaDeCompra> abiertas = new ArrayList<>();
        confirmadas.values().stream().filter(s -> s.estado().abierta()).map(SagaDeCompra::copia).forEach(abiertas::add);
        return abiertas;
    }

    public synchronized List<SagaDeCompra> todas() {
        return confirmadas.values().stream().map(SagaDeCompra::copia).toList();
    }

    /** La unidad de trabajo del servicio de compras: la del almacén más las sagas. */
    public Unidad unidad(UnidadDeTrabajo base) {
        return new Unidad(base);
    }

    public final class Unidad implements UnidadDeTrabajo {
        private final UnidadDeTrabajo base;

        Unidad(UnidadDeTrabajo base) {
            this.base = base;
        }

        public boolean enTransaccion() {
            return apartadas.get() != null;
        }

        @Override
        public <T> T ejecutar(Supplier<T> trabajo) {
            if (apartadas.get() != null) return trabajo.get();            // ya hay una abierta: se une a ella
            apartadas.set(new LinkedHashMap<>());
            try {
                T resultado = base.ejecutar(trabajo);                      // si el almacén revierte, esto lanza y no se aplica nada
                synchronized (SagasEnMemoria.this) {
                    confirmadas.putAll(apartadas.get());
                }
                return resultado;
            } finally {
                apartadas.remove();
            }
        }
    }
}
