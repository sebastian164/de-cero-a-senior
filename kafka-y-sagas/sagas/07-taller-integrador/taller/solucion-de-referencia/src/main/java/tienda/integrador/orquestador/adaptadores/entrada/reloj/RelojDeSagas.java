package tienda.integrador.orquestador.adaptadores.entrada.reloj;

import org.springframework.scheduling.annotation.Scheduled;
import tienda.integrador.orquestador.aplicacion.Puertos.VigilarSagas;

/** Adaptador de entrada: el paso del tiempo, como otro motivo para llamar a un puerto. */
public final class RelojDeSagas {
    private final VigilarSagas puerto;

    public RelojDeSagas(VigilarSagas puerto) {
        this.puerto = puerto;
    }

    @Scheduled(fixedDelay = 500)
    void cadaMedioSegundo() {
        puerto.vigilar();
    }
}
