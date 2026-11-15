package interbancaria.adaptadores;

import banco.aplicacion.puertos.salida.UnidadDeTrabajo;
import interbancaria.aplicacion.Envio;
import interbancaria.aplicacion.PuertosDeSalida.RepositorioDeEnvios;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/** Repositorio de sagas en memoria que participa en la transacción del banco. Para un solo hilo. */
public final class EnviosEnMemoria implements RepositorioDeEnvios {

    private Map<String, Envio> envios = new LinkedHashMap<>();

    @Override
    public Optional<Envio> buscar(String id) {
        return Optional.ofNullable(envios.get(id)).map(Envio::copia);
    }

    @Override
    public void guardar(Envio envio) {
        envios.put(envio.id(), envio.copia());
    }

    @Override
    public List<Envio> abiertos() {
        return envios.values().stream().filter(envio -> envio.estado() == Envio.Estado.DEBITADO).map(Envio::copia).toList();
    }

    /** Una unidad de trabajo que abarca las cuentas y los envíos: si la del banco falla, los envíos vuelven atrás. */
    public UnidadDeTrabajo juntoCon(UnidadDeTrabajo delBanco) {
        return new UnidadDeTrabajo() {
            @Override
            public <T> T ejecutar(Supplier<T> trabajo) {
                Map<String, Envio> antes = new LinkedHashMap<>(envios);
                try {
                    return delBanco.ejecutar(trabajo);
                } catch (RuntimeException error) {
                    envios = antes;
                    throw error;
                }
            }
        };
    }
}
