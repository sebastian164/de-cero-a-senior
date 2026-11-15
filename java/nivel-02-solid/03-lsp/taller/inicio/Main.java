import java.util.List;

public class Main {

    public static void main(String[] args) {
        List<Documento> documentos = List.of(
                new Documento("contrato.docx", "Texto del contrato"),
                new DocumentoFirmado("acta.pdf", "Texto del acta"));
        for (Documento documento : documentos) {
            documento.editar(documento.contenido() + " (revisado)");
            System.out.println(documento.nombre() + ": " + documento.contenido());
        }
    }
}

class Documento {
    private final String nombre;
    protected String contenido;

    Documento(String nombre, String contenido) {
        this.nombre = nombre;
        this.contenido = contenido;
    }

    void editar(String nuevoContenido) { this.contenido = nuevoContenido; }
    String nombre() { return nombre; }
    String contenido() { return contenido; }
}

class DocumentoFirmado extends Documento {

    DocumentoFirmado(String nombre, String contenido) {
        super(nombre, contenido);
    }

    @Override
    void editar(String nuevoContenido) {
        // Un documento firmado no se puede cambiar: se ignora en silencio.
    }
}
