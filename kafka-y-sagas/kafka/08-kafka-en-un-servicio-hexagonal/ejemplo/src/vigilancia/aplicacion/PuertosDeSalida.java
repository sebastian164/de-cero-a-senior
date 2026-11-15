package vigilancia.aplicacion;

import java.time.LocalDate;
import java.util.Optional;
import vigilancia.dominio.EnviosDelDia;

/** Lo que el caso de uso necesita del exterior. Agrupados en un archivo por brevedad. */
public final class PuertosDeSalida {

    private PuertosDeSalida() {
    }

    public interface EnviosPorCuenta {

        Optional<EnviosDelDia> buscar(String cuentaId, LocalDate dia);

        void guardar(EnviosDelDia envios);
    }

    public interface EventosVistos {

        /** Devuelve falso si ese evento ya se había atendido. */
        boolean registrarSiEsNuevo(String idDelEvento);
    }

    public interface Notificador {

        void avisar(String cuentaId, long totalCentavos);
    }
}
