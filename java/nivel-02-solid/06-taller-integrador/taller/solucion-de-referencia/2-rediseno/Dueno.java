public record Dueno(String nombre, String correo) {
    public Dueno {
        if (nombre == null || nombre.isBlank()) throw new IllegalArgumentException("Falta el nombre del dueño");
        if (correo == null || !correo.contains("@")) throw new IllegalArgumentException("Correo inválido");
    }
}
