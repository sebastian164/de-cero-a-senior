package colecciones;

import java.util.Objects;

/** Sobrescribe equals pero no hashCode: rompe el contrato. */
public class ClienteSinHashCode {
    private final String cedula;

    public ClienteSinHashCode(String cedula) { this.cedula = cedula; }

    @Override public boolean equals(Object o) {
        return o instanceof ClienteSinHashCode c && Objects.equals(cedula, c.cedula);
    }
}
