# Factory Method

Familia: creacional. Frecuencia en Java empresarial: muy alta (sobre todo en su variante simple).

## El problema

El código necesita un objeto, pero no debería decidir **cuál clase concreta** instanciar. Cada `new ClaseConcreta()` ata al código que lo escribe con esa clase. Si la elección depende de un dato, de la configuración o del subtipo que esté en uso, esa decisión de creación termina repetida y dispersa.

## Dos cosas que se llaman igual

| Nombre | Qué es |
|---|---|
| **Factory Method** (GoF) | Una superclase define un método de creación abstracto y cada subclase decide qué objeto crear. |
| **Método de fábrica estático / fábrica simple** | Un método (`of`, `from`, `crear`) que encapsula la decisión de qué instanciar. No es un patrón GoF, pero es lo que más se usa. |

## Cómo reconocer que lo necesitas

- El mismo `switch` que decide qué clase instanciar aparece en varios sitios.
- Crear el objeto requiere pasos o validaciones que no deben repetirse.
- Una superclase conoce el procedimiento general pero no el tipo exacto del objeto con el que trabaja.
- Quieres que el constructor tenga un nombre que explique la intención (`Dinero.enPesos(…)`).

## Antes

```java
void exportar(String formato, List<String> filas) {
    Exportador exportador;
    if (formato.equals("CSV")) {
        exportador = new ExportadorCsv(";", true);
    } else if (formato.equals("JSON")) {
        exportador = new ExportadorJson(2);
    } else {
        throw new IllegalArgumentException(formato);
    }
    exportador.exportar(filas);
}
// ...y el mismo bloque repetido en el módulo de reportes y en el de correos.
```

## Después

<!-- file: ejemplo/Main.java -->
```java
import java.util.List;

public class Main {

    public static void main(String[] args) {
        List<String> filas = List.of("Teclado", "Mouse");

        // Variante 1: fábrica simple. Un único lugar decide la clase concreta.
        Exportador exportador = Exportadores.para("JSON");
        System.out.println(exportador.exportar(filas));

        // Variante 2: Factory Method de GoF. La subclase decide qué crear.
        GeneradorReporte diario = new ReporteParaContabilidad();
        System.out.println(diario.generar(filas));
    }
}

interface Exportador {
    String exportar(List<String> filas);
}

class ExportadorCsv implements Exportador {
    @Override public String exportar(List<String> filas) { return String.join(";", filas); }
}

class ExportadorJson implements Exportador {
    @Override public String exportar(List<String> filas) {
        return "[\"" + String.join("\",\"", filas) + "\"]";
    }
}

/** Fábrica simple: encapsula la decisión de qué instanciar. */
final class Exportadores {

    private Exportadores() { }

    static Exportador para(String formato) {
        return switch (formato) {
            case "CSV" -> new ExportadorCsv();
            case "JSON" -> new ExportadorJson();
            default -> throw new IllegalArgumentException("Formato no soportado: " + formato);
        };
    }
}

/** Factory Method: el algoritmo es fijo; la creación se delega a la subclase. */
abstract class GeneradorReporte {

    final String generar(List<String> filas) {
        Exportador exportador = crearExportador();   // el método de fábrica
        return "REPORTE\n" + exportador.exportar(filas);
    }

    protected abstract Exportador crearExportador();
}

class ReporteParaContabilidad extends GeneradorReporte {
    @Override protected Exportador crearExportador() { return new ExportadorCsv(); }
}

class ReporteParaApi extends GeneradorReporte {
    @Override protected Exportador crearExportador() { return new ExportadorJson(); }
}
```

## Alternativas

- **Inyección de dependencias:** si el objeto se puede recibir ya creado, no hace falta fábrica. Es la alternativa preferida para servicios.
- **Un `Map<String, Supplier<Exportador>>`** en lugar del `switch`, si las variantes se registran dinámicamente.
- **`new` directo**, cuando no hay decisión que encapsular.
- **Strategy**, cuando lo que se quiere variar es un comportamiento y no una creación.

## En Spring

- Los métodos `@Bean` de una clase `@Configuration` son métodos de fábrica: encapsulan cómo se construye un objeto.
- `FactoryBean<T>` es una fábrica que el contenedor reconoce.
- `BeanFactory` y `ApplicationContext` son fábricas de objetos a gran escala.
- En el JDK: `List.of`, `Optional.of`, `LocalDate.of`, `Calendar.getInstance`, `NumberFormat.getInstance`.

## Ventajas, desventajas y trade-offs

| A favor | En contra |
|---|---|
| El cliente no conoce las clases concretas | Un nivel más de indirección |
| La lógica de creación está en un solo lugar | La fábrica simple se modifica con cada tipo nuevo (viola OCP en ese punto) |
| Los métodos de fábrica tienen nombre | La versión GoF exige una subclase por producto |
| Puede devolver subtipos o instancias en caché | Oculta qué clase se usa: más difícil de seguir al depurar |

## Errores comunes

- Fábricas que solo hacen `return new X()` sin decisión ni lógica: indirección gratuita.
- Fábricas con decenas de parámetros: el problema es de construcción compleja, no de elección de tipo (ver Builder).
- Usar una fábrica para servicios que el contenedor ya puede inyectar.
- Jerarquías paralelas (una subclase creadora por cada producto) que solo existen por el patrón.

## Cuándo no usarlo

Cuando hay una única clase concreta y nada indica que habrá otra; cuando la inyección de dependencias ya resuelve la creación; cuando el objeto es un valor simple que se crea con un constructor claro.

## Taller

Un sistema lee archivos de extractos bancarios en tres formatos: CSV, XML y un formato de ancho fijo. Según la extensión del archivo debe usarse un lector distinto, y todos producen una lista de movimientos (fecha, descripción, valor).

1. Define el contrato del lector y tres implementaciones (pueden simular la lectura a partir de un texto).
2. Crea una fábrica que elija el lector por extensión. ¿Qué pasa con una extensión desconocida? Decídelo y justifica.
3. Agrega un cuarto formato. ¿Qué clases existentes modificaste? Rediseña la fábrica para que agregar un formato no la modifique (pista: registro).
4. Agrega métodos de fábrica estáticos con nombre a la clase `Movimiento`: `Movimiento.ingreso(…)` y `Movimiento.egreso(…)`. ¿Qué ganan frente a un constructor con un `boolean`?
5. Explica por qué en una aplicación Spring probablemente no escribirías esta fábrica y qué usarías.

Entrega: rama `taller/n3-factory-method`.
