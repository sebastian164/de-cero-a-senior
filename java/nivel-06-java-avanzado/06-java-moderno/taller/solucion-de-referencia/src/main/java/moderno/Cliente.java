package moderno;

/** La misma clase como record: la validación pasa al constructor compacto. */
public record Cliente(String documento, String nombre, String correo) {
    public Cliente {
        if (documento == null || documento.isBlank()) throw new IllegalArgumentException("documento obligatorio");
    }
}
