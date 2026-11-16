public record CuponPorcentaje(String codigo, int porcentaje) implements Cupon {
    public long descuento(Desglose d) { return d.base() * porcentaje / 100; }
}
