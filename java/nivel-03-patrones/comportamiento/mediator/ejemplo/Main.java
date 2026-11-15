public class Main {

    public static void main(String[] args) {
        FormularioRegistro formulario = new FormularioRegistro();
        formulario.pais.cambiar("CO");
        formulario.ciudad.cambiar("Tunja");
    }
}

/** El mediador: único lugar donde vive la regla de coordinación. */
interface Mediador {
    void notificar(Campo origen);
}

/** Los participantes solo conocen al mediador. */
class Campo {

    final String nombre;
    private final Mediador mediador;
    private String valor = "";

    Campo(String nombre, Mediador mediador) {
        this.nombre = nombre;
        this.mediador = mediador;
    }

    void cambiar(String nuevoValor) {
        this.valor = nuevoValor;
        mediador.notificar(this);
    }

    void fijarSinAvisar(String nuevoValor) {
        this.valor = nuevoValor;
    }

    String valor() {
        return valor;
    }
}

class FormularioRegistro implements Mediador {

    final Campo pais = new Campo("pais", this);
    final Campo ciudad = new Campo("ciudad", this);
    final Campo indicativo = new Campo("indicativo", this);
    private boolean envioHabilitado = false;

    @Override
    public void notificar(Campo origen) {
        if (origen == pais) {
            ciudad.fijarSinAvisar("");
            indicativo.fijarSinAvisar(pais.valor().equals("CO") ? "+57" : "+52");
        }
        envioHabilitado = !pais.valor().isEmpty() && !ciudad.valor().isEmpty();
        System.out.println("pais=" + pais.valor() + " ciudad=" + ciudad.valor()
                + " indicativo=" + indicativo.valor() + " envío=" + envioHabilitado);
    }
}
