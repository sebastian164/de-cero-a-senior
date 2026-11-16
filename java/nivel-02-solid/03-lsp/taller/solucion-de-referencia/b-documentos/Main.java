import java.util.List;

public class Main {
    public static void main(String[] args) {
        DocumentoEditable contrato = new DocumentoEditable("contrato.docx", "Texto del contrato");
        DocumentoFirmado acta = new DocumentoEditable("acta.pdf", "Texto del acta").firmar("Gerencia");

        // Solo lo editable se edita: el compilador separa los dos casos.
        List<DocumentoEditable> editables = List.of(contrato);
        for (DocumentoEditable documento : editables) documento.editar(documento.contenido() + " (revisado)");

        // Para leer, todos son Documento.
        List<Documento> todos = List.of(contrato, acta);
        for (Documento documento : todos) System.out.println(documento.nombre() + ": " + documento.contenido());

        DocumentoFirmado contratoFirmado = contrato.firmar("Ana");
        contrato.editar("Borrador v2");
        System.out.println("Firmado: " + contratoFirmado.contenido() + " | borrador: " + contrato.contenido());
    }
}
