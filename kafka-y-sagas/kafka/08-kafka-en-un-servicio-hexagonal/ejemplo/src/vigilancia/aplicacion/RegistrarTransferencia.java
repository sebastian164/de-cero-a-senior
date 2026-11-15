package vigilancia.aplicacion;

import java.time.Instant;

/** Puerto de entrada. No dice de dónde llega la transferencia: hoy de un tema, mañana de donde sea. */
public interface RegistrarTransferencia {

    record Transferencia(String idDelEvento, String cuentaId, long centavos, Instant ocurridaEn) {
    }

    enum Resultado {
        REGISTRADA,
        REPETIDA
    }

    Resultado registrar(Transferencia transferencia);
}
