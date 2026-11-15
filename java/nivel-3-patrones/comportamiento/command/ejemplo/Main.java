import java.util.ArrayDeque;
import java.util.Deque;

public class Main {

    public static void main(String[] args) {
        Documento documento = new Documento();
        Historial historial = new Historial();

        historial.ejecutar(new Escribir(documento, "Hola"));
        historial.ejecutar(new Escribir(documento, " mundo"));
        System.out.println(documento.texto());

        historial.deshacer();
        System.out.println(documento.texto());
    }
}

/** La acción convertida en objeto. */
interface Comando {
    void ejecutar();
    void deshacer();
}

/** El receptor: quien realmente sabe hacer el trabajo. */
class Documento {
    private final StringBuilder contenido = new StringBuilder();

    void agregar(String texto) { contenido.append(texto); }
    void quitarUltimos(int cantidad) { contenido.setLength(contenido.length() - cantidad); }
    String texto() { return contenido.toString(); }
}

class Escribir implements Comando {

    private final Documento documento;
    private final String texto;

    Escribir(Documento documento, String texto) {
        this.documento = documento;
        this.texto = texto;
    }

    @Override public void ejecutar() { documento.agregar(texto); }
    @Override public void deshacer() { documento.quitarUltimos(texto.length()); }
}

/** El invocador: ejecuta comandos sin saber qué hacen y recuerda el orden. */
class Historial {

    private final Deque<Comando> ejecutados = new ArrayDeque<>();

    void ejecutar(Comando comando) {
        comando.ejecutar();
        ejecutados.push(comando);
    }

    void deshacer() {
        if (!ejecutados.isEmpty()) {
            ejecutados.pop().deshacer();
        }
    }
}
