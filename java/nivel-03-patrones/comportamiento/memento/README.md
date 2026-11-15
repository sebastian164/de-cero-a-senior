# Memento

Familia: comportamiento. Frecuencia en Java empresarial: baja como patrón; la idea (instantáneas de estado) es frecuente.

## El problema

Necesitas guardar el estado de un objeto para restaurarlo después (deshacer, puntos de control, reintentos) **sin exponer su interior**. Si para guardar el estado hay que abrir getters y setters de todos los campos, se destruye el encapsulamiento que tanto costó.

## Cómo reconocer que lo necesitas

- Hay que deshacer y la operación inversa es difícil o imposible de calcular.
- Se necesitan versiones o puntos de restauración.
- Antes de una operación riesgosa conviene guardar una copia a la que volver.

## Los tres papeles

| Papel | Qué hace |
|---|---|
| Originador | El objeto cuyo estado se guarda. Crea el memento y sabe restaurarse desde él. |
| Memento | La instantánea. Inmutable y opaca para todos menos para el originador. |
| Cuidador | Guarda los mementos y decide cuándo restaurar. No mira dentro. |

## Antes

```java
// Para "deshacer", el editor expone todo su interior
String textoGuardado = editor.getTexto();
int cursorGuardado = editor.getCursor();
// ...
editor.setTexto(textoGuardado);
editor.setCursor(cursorGuardado);   // y si mañana hay un campo nuevo, se olvida
```

## Después

<!-- file: ejemplo/Main.java -->
```java
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
```

El historial guarda instantáneas sin poder leerlas ni modificarlas. Si `Editor` gana un campo, solo cambia `Editor`.

## Alternativas

- **Command con `deshacer`**, si invertir la operación es más barato que copiar el estado.
- **Objetos inmutables**: cada cambio produce un objeto nuevo y el historial es simplemente la lista de versiones.
- **Event Sourcing**: en lugar de guardar estados, se guardan los hechos y el estado se reconstruye. Es la versión arquitectónica de la misma necesidad.
- **Versionado en base de datos** (tablas de auditoría, Hibernate Envers).

## En Spring y en Java

- *Savepoints* de transacciones (`Connection.setSavepoint`, propagación `NESTED`): un punto al que volver.
- Hibernate guarda una instantánea del estado cargado de cada entidad para detectar qué cambió (*dirty checking*).
- Hibernate Envers y las tablas de historial.
- Serialización de estado de sesión.

## Trade-offs

- A favor: restaura sin romper el encapsulamiento; el originador no gestiona su historial; simple de razonar.
- En contra: memoria, si el estado es grande o se guardan muchas instantáneas; hay que hacer copia profunda de lo mutable; decidir cuántas versiones conservar.

## Errores comunes

- Mementos mutables o que comparten referencias con el originador.
- Guardar una instantánea en cada pulsación sin límite.
- Mementos con getters públicos: se convierten en una puerta trasera al estado.

## Cuándo no usarlo

Cuando el estado es enorme y una operación inversa es barata; cuando los objetos ya son inmutables.

## Taller

Un configurador de producto (por ejemplo, un computador a medida: procesador, memoria, disco, accesorios) permite deshacer y rehacer cambios.

1. Implementa guardar y restaurar con Memento. Los accesorios son una lista: cuida la copia.
2. Limita el historial a 20 instantáneas.
3. Implementa la misma funcionalidad haciendo la configuración inmutable. Compara.
4. ¿En qué caso preferirías Command sobre Memento para deshacer? Da un criterio basado en tamaño de estado y costo de la operación inversa.

Entrega: rama `taller/n3-memento`.
