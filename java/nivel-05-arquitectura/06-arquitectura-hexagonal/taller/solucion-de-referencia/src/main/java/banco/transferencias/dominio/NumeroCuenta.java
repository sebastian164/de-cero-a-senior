package banco.transferencias.dominio;

public record NumeroCuenta(String valor) {
    public NumeroCuenta { if (valor == null || !valor.matches("\\d{4,12}")) throw new IllegalArgumentException("número de cuenta inválido"); }
}
