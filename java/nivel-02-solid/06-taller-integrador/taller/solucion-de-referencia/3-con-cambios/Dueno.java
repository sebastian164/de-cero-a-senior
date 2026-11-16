public record Dueno(String nombre, String correo, String telefono, CanalAviso canal) {
    public Dueno {
        if (nombre == null || nombre.isBlank()) throw new IllegalArgumentException("Falta el nombre del dueño");
        if (correo == null || !correo.contains("@")) throw new IllegalArgumentException("Correo inválido");
        if (canal == null) throw new IllegalArgumentException("Falta el canal de aviso");
        if (canal == CanalAviso.WHATSAPP && (telefono == null || telefono.isBlank())) {
            throw new IllegalArgumentException("WhatsApp requiere teléfono");
        }
    }

    /** Compatibilidad: quien no elige, recibe correo. */
    public Dueno(String nombre, String correo) {
        this(nombre, correo, null, CanalAviso.CORREO);
    }
}
