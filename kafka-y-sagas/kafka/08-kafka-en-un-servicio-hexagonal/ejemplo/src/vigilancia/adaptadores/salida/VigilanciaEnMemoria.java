package vigilancia.adaptadores.salida;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import vigilancia.aplicacion.PuertosDeSalida.EnviosPorCuenta;
import vigilancia.aplicacion.PuertosDeSalida.EventosVistos;
import vigilancia.dominio.EnviosDelDia;

public final class VigilanciaEnMemoria implements EnviosPorCuenta, EventosVistos {

    private final Map<String, EnviosDelDia> envios = new HashMap<>();
    private final Set<String> vistos = new HashSet<>();

    @Override
    public Optional<EnviosDelDia> buscar(String cuentaId, LocalDate dia) {
        return Optional.ofNullable(envios.get(cuentaId + "@" + dia))
                .map(guardado -> new EnviosDelDia(guardado.cuentaId(), guardado.dia(), guardado.totalCentavos()));
    }

    @Override
    public void guardar(EnviosDelDia delDia) {
        envios.put(delDia.cuentaId() + "@" + delDia.dia(), delDia);
    }

    @Override
    public boolean registrarSiEsNuevo(String idDelEvento) {
        return vistos.add(idDelEvento);
    }
}
