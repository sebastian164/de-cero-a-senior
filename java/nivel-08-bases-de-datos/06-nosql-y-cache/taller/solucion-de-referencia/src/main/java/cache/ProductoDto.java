package cache;

import java.io.Serializable;
import java.math.BigDecimal;

/** Se guarda un DTO, no una entidad. La serialización por defecto de la caché de Redis es la de Java. */
public record ProductoDto(Long id, String nombre, BigDecimal precio) implements Serializable {
}
