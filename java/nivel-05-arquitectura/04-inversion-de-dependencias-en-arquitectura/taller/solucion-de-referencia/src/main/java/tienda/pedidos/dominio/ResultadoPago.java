package tienda.pedidos.dominio;

public sealed interface ResultadoPago {
    record Aprobado(String autorizacion) implements ResultadoPago { }
    record Rechazado(String motivo) implements ResultadoPago { }
}
