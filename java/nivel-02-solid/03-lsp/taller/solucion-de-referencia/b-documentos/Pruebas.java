public class Pruebas {
    public static void main(String[] args) {
        int fallos = 0;
        DocumentoEditable d = new DocumentoEditable("a", "uno");
        d.editar("dos");
        if (!d.contenido().equals("dos")) fallos++;
        DocumentoFirmado f = d.firmar("Ana");
        d.editar("tres");
        if (!f.contenido().equals("dos")) fallos++;        // la firma congela el contenido
        if (!f.firmante().equals("Ana")) fallos++;
        try { d.firmar(" "); fallos++; } catch (IllegalArgumentException e) { }
        Documento comoLectura = f;
        if (!comoLectura.nombre().equals("a")) fallos++;
        System.out.println("5 pruebas, " + fallos + " fallos");
        if (fallos > 0) System.exit(1);
    }
}
