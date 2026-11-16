package citas;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** Recuerda qué cita creó cada Idempotency-Key y con qué cuerpo. La clave primaria impide duplicados concurrentes. */
@Entity
public class ClaveIdempotencia {
    @Id
    private String clave;
    private String huellaDelCuerpo;
    private Long citaId;

    protected ClaveIdempotencia() { }

    ClaveIdempotencia(String clave, String huellaDelCuerpo, Long citaId) {
        this.clave = clave;
        this.huellaDelCuerpo = huellaDelCuerpo;
        this.citaId = citaId;
    }

    String getHuellaDelCuerpo() { return huellaDelCuerpo; }
    Long getCitaId() { return citaId; }
}
