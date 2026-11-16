/** Inmutable: no tiene editar(). No hereda de DocumentoEditable. */
public record DocumentoFirmado(String nombre, String contenido, String firmante) implements Documento {
    public DocumentoFirmado {
        if (firmante == null || firmante.isBlank()) throw new IllegalArgumentException("Falta el firmante");
    }
}
