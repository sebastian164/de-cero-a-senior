public class Main {

    public static void main(String[] args) {
        Dispositivo oficina = new Multifuncional();
        Dispositivo recepcion = new ImpresoraBasica();

        oficina.imprimir("contrato.pdf");
        oficina.escanear("cedula");

        recepcion.imprimir("turno-17");
        try {
            recepcion.escanear("cedula");
        } catch (UnsupportedOperationException e) {
            System.out.println("FALLO: " + e.getMessage());
        }
    }
}

interface Dispositivo {
    void imprimir(String documento);
    String escanear(String nombre);
    void enviarFax(String documento, String numero);
    void grapar(String documento);
}

class Multifuncional implements Dispositivo {
    @Override public void imprimir(String documento) { System.out.println("Imprimiendo " + documento); }
    @Override public String escanear(String nombre) { System.out.println("Escaneando " + nombre); return nombre + ".png"; }
    @Override public void enviarFax(String documento, String numero) { System.out.println("Fax a " + numero); }
    @Override public void grapar(String documento) { System.out.println("Grapando " + documento); }
}

class ImpresoraBasica implements Dispositivo {
    @Override public void imprimir(String documento) { System.out.println("Imprimiendo " + documento); }

    @Override public String escanear(String nombre) {
        throw new UnsupportedOperationException("Esta impresora no escanea");
    }

    @Override public void enviarFax(String documento, String numero) {
        throw new UnsupportedOperationException("Esta impresora no envía fax");
    }

    @Override public void grapar(String documento) {
        // no hace nada
    }
}
