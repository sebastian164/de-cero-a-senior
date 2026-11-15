public class Main {

    public static void main(String[] args) {
        Multifuncional multifuncional = new Multifuncional();
        ImpresoraBasica basica = new ImpresoraBasica();

        emitirTurno(basica, "turno-17");
        emitirTurno(multifuncional, "turno-18");
        digitalizar(multifuncional, "cedula");
        // digitalizar(basica, "cedula");  // no compila: no es un Escaner
    }

    static void emitirTurno(Impresora impresora, String turno) {
        impresora.imprimir(turno);
    }

    static void digitalizar(Escaner escaner, String nombre) {
        System.out.println("Guardado en " + escaner.escanear(nombre));
    }
}

interface Impresora {
    void imprimir(String documento);
}

interface Escaner {
    String escanear(String nombre);
}

interface Fax {
    void enviarFax(String documento, String numero);
}

class ImpresoraBasica implements Impresora {
    @Override public void imprimir(String documento) { System.out.println("Imprimiendo " + documento); }
}

class Multifuncional implements Impresora, Escaner, Fax {
    @Override public void imprimir(String documento) { System.out.println("Imprimiendo " + documento); }
    @Override public String escanear(String nombre) { System.out.println("Escaneando " + nombre); return nombre + ".png"; }
    @Override public void enviarFax(String documento, String numero) { System.out.println("Fax a " + numero); }
}
