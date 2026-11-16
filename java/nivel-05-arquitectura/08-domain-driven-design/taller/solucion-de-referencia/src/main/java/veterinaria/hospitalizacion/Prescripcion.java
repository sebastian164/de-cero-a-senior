package veterinaria.hospitalizacion;

import java.time.Duration;
import java.time.LocalDateTime;

/** Entidad interna del agregado: solo se modifica a través de Hospitalizacion. */
public final class Prescripcion {
    private final int numero;
    private final String medicamento;
    private final Dosis dosis;
    private final Duration cadaCuanto;
    private final LocalDateTime primeraToma;
    private LocalDateTime ultimaAdministracion;
    private boolean suspendida;

    Prescripcion(int numero, String medicamento, Dosis dosis, Duration cadaCuanto, LocalDateTime primeraToma) {
        this.numero = numero;
        this.medicamento = medicamento;
        this.dosis = dosis;
        this.cadaCuanto = cadaCuanto;
        this.primeraToma = primeraToma;
    }

    LocalDateTime proximaToma() {
        return ultimaAdministracion == null ? primeraToma : ultimaAdministracion.plus(cadaCuanto);
    }

    void registrarAdministracion(LocalDateTime cuando) { ultimaAdministracion = cuando; }
    void suspender() { suspendida = true; }

    public int numero() { return numero; }
    public String medicamento() { return medicamento; }
    public Dosis dosis() { return dosis; }
    public boolean suspendida() { return suspendida; }
}
