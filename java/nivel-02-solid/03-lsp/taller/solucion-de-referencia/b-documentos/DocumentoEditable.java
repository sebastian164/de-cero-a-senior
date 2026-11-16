/** Un borrador: se edita y, cuando está listo, se firma. */
public final class DocumentoEditable implements Documento {
    private final String nombre;
    private String contenido;

    public DocumentoEditable(String nombre, String contenido) {
        this.nombre = nombre;
        this.contenido = contenido;
    }

    public void editar(String nuevoContenido) {
        if (nuevoContenido == null) throw new IllegalArgumentException("Contenido nulo");
        this.contenido = nuevoContenido;
    }

    /** La transición: produce un documento firmado con el contenido de este momento. */
    public DocumentoFirmado firmar(String firmante) {
        return new DocumentoFirmado(nombre, contenido, firmante);
    }

    public String nombre() { return nombre; }
    public String contenido() { return contenido; }
}
