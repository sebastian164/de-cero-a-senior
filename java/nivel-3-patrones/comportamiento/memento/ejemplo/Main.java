import java.util.ArrayDeque;
import java.util.Deque;

public class Main {

    public static void main(String[] args) {
        Editor editor = new Editor();
        Deque<Editor.Instantanea> historial = new ArrayDeque<>();   // el cuidador

        editor.escribir("Hola");
        historial.push(editor.guardar());

        editor.escribir(" mundo cruel");
        System.out.println(editor);

        editor.restaurar(historial.pop());
        System.out.println(editor);
    }
}

/** El originador. */
class Editor {

    private String texto = "";
    private int cursor = 0;

    void escribir(String nuevo) {
        texto += nuevo;
        cursor = texto.length();
    }

    Instantanea guardar() {
        return new Instantanea(texto, cursor);
    }

    void restaurar(Instantanea instantanea) {
        this.texto = instantanea.texto;
        this.cursor = instantanea.cursor;
    }

    @Override
    public String toString() {
        return "\"" + texto + "\" (cursor en " + cursor + ")";
    }

    /** El memento: inmutable, y sus datos solo son accesibles para Editor. */
    static final class Instantanea {
        private final String texto;
        private final int cursor;

        private Instantanea(String texto, int cursor) {
            this.texto = texto;
            this.cursor = cursor;
        }
    }
}
