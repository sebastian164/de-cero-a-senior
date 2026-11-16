package mensajeria.despacho.dominio;

import java.util.EnumSet;
import java.util.Set;

public enum EstadoEnvio {
    SOLICITADO, ASIGNADO, RECOGIDO, EN_RUTA, ENTREGADO, FALLIDO, CANCELADO;

    public Set<EstadoEnvio> siguientes() {
        return switch (this) {
            case SOLICITADO -> EnumSet.of(ASIGNADO, CANCELADO);
            case ASIGNADO -> EnumSet.of(RECOGIDO, SOLICITADO, CANCELADO);   // SOLICITADO = el mensajero rechazó
            case RECOGIDO -> EnumSet.of(EN_RUTA);
            case EN_RUTA -> EnumSet.of(ENTREGADO, FALLIDO);
            case ENTREGADO, FALLIDO, CANCELADO -> EnumSet.noneOf(EstadoEnvio.class);
        };
    }
}
