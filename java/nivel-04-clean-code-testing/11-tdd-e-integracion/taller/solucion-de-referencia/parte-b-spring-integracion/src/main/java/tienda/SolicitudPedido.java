package tienda;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record SolicitudPedido(@NotBlank String cliente, @NotEmpty List<@Valid Item> items) {

    public record Item(@NotBlank String producto, @Positive int cantidad, @Positive long precioUnitario) {
    }

    long total() {
        return items.stream().mapToLong(i -> i.cantidad() * i.precioUnitario()).sum();
    }
}
