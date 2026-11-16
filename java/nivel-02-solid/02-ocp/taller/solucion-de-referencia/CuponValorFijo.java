public record CuponValorFijo(String codigo, long valor) implements Cupon {
    public long descuento(Desglose d) { return valor; }
}
