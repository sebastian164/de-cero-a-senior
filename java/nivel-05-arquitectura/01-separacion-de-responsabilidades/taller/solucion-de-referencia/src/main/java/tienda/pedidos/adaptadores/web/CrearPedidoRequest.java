package tienda.pedidos.adaptadores.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import tienda.pedidos.aplicacion.CrearPedidoComando;

record CrearPedidoRequest(@NotBlank String cliente, @NotNull @Positive Integer cantidad) {
    CrearPedidoComando aComando() {
        return new CrearPedidoComando(cliente, cantidad);
    }
}
