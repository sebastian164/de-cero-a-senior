package interbancaria.aplicacion;

import java.util.List;
import java.util.Optional;

/** Lo que la saga necesita del exterior, además de los puertos que ya tenía el banco. */
public final class PuertosDeSalida {

    private PuertosDeSalida() {
    }

    public interface RedInterbancaria {

        enum Respuesta {
            ACEPTADO,
            RECHAZADO
        }

        /** El id del envío viaja siempre: el otro banco lo usa para no abonar dos veces. */
        Respuesta enviar(String idDelEnvio, String bancoDestino, String cuentaDestino, long centavos);
    }

    /** No hubo respuesta. No dice si el otro banco recibió la orden o no: no se sabe. */
    public static final class RedSinRespuesta extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public RedSinRespuesta(String detalle) {
            super(detalle);
        }
    }

    public interface RepositorioDeEnvios {

        Optional<Envio> buscar(String id);

        void guardar(Envio envio);

        List<Envio> abiertos();
    }
}
