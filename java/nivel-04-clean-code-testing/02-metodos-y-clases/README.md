# 02 — Responsabilidad de métodos y clases

## Idea sencilla

Una receta bien escrita dice: "prepara la masa, haz el relleno, arma y hornea". Cada paso se explica aparte. Una receta mal escrita es un solo párrafo de dos páginas donde amasar, picar cebolla y precalentar el horno están mezclados. Las dos producen la misma empanada; solo una se puede seguir.

## Explicación técnica

### Métodos

- **Hacen una sola cosa.** Prueba: ¿puedes extraer un fragmento con un nombre que no sea una repetición del nombre del método? Entonces hacía más de una.
- **Un solo nivel de abstracción.** No mezcles `confirmarPedido()` con `texto.substring(3, 7)` en el mismo método.
- **Cortos.** No hay un número mágico, pero si no cabe en la pantalla, sospecha.
- **Pocos parámetros.** Cero a dos es cómodo; tres pide justificación; más pide un objeto.
- **Sin parámetros bandera.** `generar(true)` son dos métodos disfrazados.
- **Separación comando-consulta (CQS):** un método cambia estado o devuelve información, no ambas. `boolean guardar()` que además devuelve si existía confunde.
- **Sin efectos ocultos.** `validar()` que además crea una sesión miente con su nombre.
- **Cláusulas de guarda** en lugar de `if` anidados: sal temprano de los casos especiales.

### Clases

- Pequeñas, medidas por responsabilidades (SRP), no por líneas.
- Cohesivas: los métodos usan la mayoría de los atributos. Si un grupo de métodos usa solo un subconjunto, hay otra clase escondida.
- Ordenadas para leerse de arriba hacia abajo: lo público y general primero, los detalles después.

## Código con problemas

<!-- file: ejemplos/antes/Main.java -->
```java
import java.util.List;

public class Main {

    public static void main(String[] args) {
        System.out.println(procesar(List.of("ana;34;CO", "luis;17;CO", ";40;MX", "eva;x;CO"), true));
    }

    static String procesar(List<String> lineas, boolean soloAdultos) {
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (String linea : lineas) {
            String[] p = linea.split(";");
            if (p.length == 3) {
                if (!p[0].isBlank()) {
                    try {
                        int edad = Integer.parseInt(p[1]);
                        if (!soloAdultos || edad >= 18) {
                            sb.append(p[0].substring(0, 1).toUpperCase()).append(p[0].substring(1))
                              .append(" (").append(edad).append(") ").append(p[2]).append("\n");
                            n++;
                        }
                    } catch (NumberFormatException e) {
                        // ignorar
                    }
                }
            }
        }
        sb.append("Total: ").append(n);
        return sb.toString();
    }
}
```

## Análisis

Un método que analiza texto, valida, filtra, da formato y cuenta. Cuatro niveles de anidamiento. Un parámetro bandera. Un `catch` vacío que oculta datos corruptos. Mezcla una decisión de negocio (mayoría de edad) con manipulación de caracteres. Para probar el filtro de edad hay que construir texto con punto y coma.

## Código mejorado

<!-- file: ejemplos/despues/Main.java -->
```java
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Main {

    public static void main(String[] args) {
        List<Persona> personas = leerPersonas(List.of("ana;34;CO", "luis;17;CO", ";40;MX", "eva;x;CO"));
        System.out.println(reporte(soloAdultos(personas)));
    }

    static List<Persona> leerPersonas(List<String> lineas) {
        List<Persona> personas = new ArrayList<>();
        for (String linea : lineas) {
            Persona.desde(linea).ifPresent(personas::add);
        }
        return personas;
    }

    static List<Persona> soloAdultos(List<Persona> personas) {
        return personas.stream().filter(Persona::esAdulta).toList();
    }

    static String reporte(List<Persona> personas) {
        StringBuilder texto = new StringBuilder();
        for (Persona persona : personas) {
            texto.append(persona.comoLinea()).append("\n");
        }
        return texto.append("Total: ").append(personas.size()).toString();
    }
}

record Persona(String nombre, int edad, String pais) {

    private static final int MAYORIA_DE_EDAD = 18;

    static Optional<Persona> desde(String linea) {
        String[] campos = linea.split(";");
        if (campos.length != 3 || campos[0].isBlank() || !esNumero(campos[1])) {
            return Optional.empty();
        }
        return Optional.of(new Persona(campos[0], Integer.parseInt(campos[1]), campos[2]));
    }

    boolean esAdulta() {
        return edad >= MAYORIA_DE_EDAD;
    }

    String comoLinea() {
        return capitalizar(nombre) + " (" + edad + ") " + pais;
    }

    private static boolean esNumero(String texto) {
        return !texto.isEmpty() && texto.chars().allMatch(Character::isDigit);
    }

    private static String capitalizar(String texto) {
        return texto.substring(0, 1).toUpperCase() + texto.substring(1);
    }
}
```

`main` ahora se lee como la receta: leer, filtrar, reportar. La bandera desapareció: quien quiere solo adultos llama a `soloAdultos`. Cada parte se prueba sola. La anidación bajó de cuatro niveles a uno gracias a la cláusula de guarda.

Una decisión que sigue abierta: las líneas inválidas se descartan en silencio. Es un problema real que se trata en la lección de excepciones.

## Taller

<!-- file: taller/inicio/Main.java -->
```java
public class Main {

    public static void main(String[] args) {
        System.out.println(liquidar(3_000_000, 10, 2, true, false, "CO"));
    }

    static String liquidar(long salario, int diasTrabajados, int horasExtra, boolean nocturnas,
                           boolean incluirDetalle, String pais) {
        long base = salario / 30 * diasTrabajados;
        long valorHora = salario / 240;
        long extra;
        if (nocturnas) {
            extra = valorHora * horasExtra * 175 / 100;
        } else {
            extra = valorHora * horasExtra * 125 / 100;
        }
        long total = base + extra;
        long descuento = 0;
        if (pais.equals("CO")) {
            descuento = total * 8 / 100;
        } else if (pais.equals("MX")) {
            descuento = total * 5 / 100;
        }
        String texto = "Neto: " + (total - descuento);
        if (incluirDetalle) {
            texto += " (base " + base + ", extra " + extra + ", descuento " + descuento + ")";
        }
        return texto;
    }
}
```

1. Lista cada cosa distinta que hace `liquidar`.
2. Refactoriza: sin parámetros bandera, sin más de dos parámetros por método, un nivel de abstracción por método.
3. Señala qué métodos de tu versión son consultas y cuáles comandos.
4. Comprueba que el resultado numérico no cambió.

Se revisará: nombres, tamaño de métodos, ausencia de banderas, y que no haya sobre-fragmentación. Rama `taller/n4-02-metodos`.

## Aplicación profesional

- **Controladores delgados:** reciben, delegan y responden. Un método de controlador con lógica de negocio mezcla niveles de abstracción.
- **Métodos `@Transactional` enfocados:** una transacción que además llama a un servicio externo y envía un correo mantiene la conexión abierta y complica los fallos.
- **Objetos de petición** en lugar de endpoints con ocho `@RequestParam`.
- **Streams:** una cadena de quince operaciones con lambdas de varias líneas es un método largo con otra sintaxis. Extrae y nombra.

## Preguntas senior

1. ¿Cuándo extraer un método empeora la legibilidad?
2. ¿Por qué un parámetro booleano es un olor? ¿Y un `enum` en su lugar?
3. ¿Qué excepciones razonables tiene CQS? (Piensa en `Iterator.next` o en una pila.)
4. ¿Cómo detectas una clase escondida dentro de otra mirando solo qué atributos usa cada método?
5. ¿Qué relación hay entre el tamaño de los métodos y la facilidad de escribir pruebas?

## Cuándo no exagerar

Métodos de una línea que solo renombran otra llamada; saltar entre veinte métodos privados para entender diez líneas de lógica; aplicar límites numéricos rígidos sin criterio.

Siguiente: [03 — Code smells](../03-code-smells/).
