# 02 — De afuera hacia adentro, y los dobles de prueba

## Idea

Un simulador de vuelo. El piloto practica con una cabina real y un mundo falso: no hay avión, ni clima, ni torre de control de verdad. Eso permite ensayar una tormenta sin esperar a que llueva. Pero si el simulador comprobara *qué botones tocó el piloto y en qué orden* en vez de *si el avión aterrizó*, cualquier forma nueva y mejor de aterrizar contaría como un fallo. Los dobles de prueba son ese mundo falso, y el error típico es justo ese: comprobar los botones.

## Lo esencial

### Dos formas de avanzar

| | Clásica (de dentro hacia fuera) | De afuera hacia adentro |
|---|---|---|
| Se empieza por | Las piezas pequeñas del dominio | Lo que ve el usuario del sistema: un caso de uso |
| Las pruebas comprueban | El estado o el resultado | El resultado, y la conversación con los colaboradores que aún no existen |
| Dobles | Pocos; solo en los bordes | Para cada colaborador que se descubre |
| Riesgo | Construir piezas que luego no encajan | Pruebas atadas a cómo está hecho el código |

No son bandos. Lo habitual es empezar por fuera para descubrir qué piezas hacen falta y qué forma deben tener, y construir cada pieza de lógica por dentro.

### Tipos de doble

| Doble | Qué es | Para qué |
|---|---|---|
| **Falso** (*fake*) | Una implementación real pero simple: un repositorio en memoria | Colaboradores con estado |
| **Sustituto** (*stub*) | Devuelve respuestas fijas: un reloj que siempre dice la misma fecha | Controlar lo que entra |
| **Espía** | Anota lo que le pidieron: un notificador que guarda los mensajes | Comprobar un efecto hacia fuera |
| **Simulado** (*mock*) | Un espía con expectativas programadas, normalmente con una biblioteca | Lo mismo, con menos código y más riesgo de abuso |

### Qué doblar y qué no

| Doblar | No doblar |
|---|---|
| Lo que cruza un borde: base de datos, red, reloj, azar, sistema de archivos | Objetos de valor y entidades del dominio |
| Lo lento o no determinista | Colaboradores que son detalle interno del módulo |
| Lo que es de otro equipo, detrás de una interfaz **tuya** | Tipos que no son tuyos, directamente |

### La regla que evita las pruebas frágiles

Comprueba **qué se logró**, no **cómo**. Una prueba que verifica la secuencia exacta de llamadas internas falla cuando refactorizas sin cambiar el comportamiento. Esa prueba no te protege: te castiga por mejorar el código.

Hay una excepción legítima: cuando la llamada *es* el resultado. "Se envió un correo al socio" solo se puede comprobar mirando lo que recibió el notificador.

## Ejemplo

Un caso de uso de biblioteca: prestar un libro. Un socio no puede tener más de tres préstamos activos, y el préstamo vence a los catorce días.

El programa contiene dos versiones del caso de uso con **el mismo comportamiento** (la segunda consulta los préstamos de otra manera, como quedaría tras un refactor) y dos juegos de pruebas: uno atado a la implementación y otro de comportamiento. Ejecútalo con `java Main.java` y mira cuál sobrevive al refactor.

<!-- file: ejemplo/Main.java -->
```java
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

/** Dos juegos de pruebas frente a un refactor que no cambia el comportamiento. */
public class Main {

    // ----- Puertos: lo que el caso de uso necesita del mundo -----
    interface Prestamos {
        int activosDe(String socio);
        List<String> librosDe(String socio);
        void registrar(String socio, String libro, LocalDate vence);
    }

    interface Reloj {
        LocalDate hoy();
    }

    interface PrestarLibro {
        String ejecutar(String socio, String libro);
    }

    // ----- El caso de uso, antes y después de un refactor interno -----
    static PrestarLibro version1(Prestamos prestamos, Reloj reloj) {
        return (socio, libro) -> {
            if (prestamos.activosDe(socio) >= 3) {
                return "RECHAZADO";
            }
            prestamos.registrar(socio, libro, reloj.hoy().plusDays(14));
            return "PRESTADO";
        };
    }

    static PrestarLibro version2(Prestamos prestamos, Reloj reloj) {
        return (socio, libro) -> {
            if (prestamos.librosDe(socio).size() >= 3) {      // misma regla, consultada de otra forma
                return "RECHAZADO";
            }
            prestamos.registrar(socio, libro, reloj.hoy().plusDays(14));
            return "PRESTADO";
        };
    }

    // ----- Dobles -----
    /** Falso: un repositorio de verdad, en memoria. */
    static final class PrestamosEnMemoria implements Prestamos {
        final Map<String, Map<String, LocalDate>> porSocio = new LinkedHashMap<>();

        public int activosDe(String socio) {
            return librosDe(socio).size();
        }

        public List<String> librosDe(String socio) {
            return new ArrayList<>(porSocio.getOrDefault(socio, Map.of()).keySet());
        }

        public void registrar(String socio, String libro, LocalDate vence) {
            porSocio.computeIfAbsent(socio, s -> new LinkedHashMap<>()).put(libro, vence);
        }
    }

    /** Espía que anota cada llamada: la herramienta de la prueba atada a la implementación. */
    static final class PrestamosEspia implements Prestamos {
        final List<String> llamadas = new ArrayList<>();

        public int activosDe(String socio) {
            llamadas.add("activosDe");
            return 0;
        }

        public List<String> librosDe(String socio) {
            llamadas.add("librosDe");
            return List.of();
        }

        public void registrar(String socio, String libro, LocalDate vence) {
            llamadas.add("registrar");
        }
    }

    static final Reloj QUINCE_DE_NOVIEMBRE = () -> LocalDate.of(2026, 11, 15);

    // ----- Antes: una prueba que comprueba CÓMO trabaja el caso de uso -----
    static void pruebaAtadaALaImplementacion(BiFunction<Prestamos, Reloj, PrestarLibro> fabrica) {
        PrestamosEspia espia = new PrestamosEspia();
        fabrica.apply(espia, QUINCE_DE_NOVIEMBRE).ejecutar("ana", "Rayuela");
        comprobar("llama a activosDe y después a registrar", List.of("activosDe", "registrar"), espia.llamadas);
    }

    // ----- Después: pruebas que comprueban QUÉ logra -----
    static void pruebasDeComportamiento(BiFunction<Prestamos, Reloj, PrestarLibro> fabrica) {
        PrestamosEnMemoria prestamos = new PrestamosEnMemoria();
        PrestarLibro prestar = fabrica.apply(prestamos, QUINCE_DE_NOVIEMBRE);

        comprobar("un socio sin préstamos recibe el libro", "PRESTADO", prestar.ejecutar("ana", "Rayuela"));
        comprobar("el préstamo vence a los catorce días",
                LocalDate.of(2026, 11, 29), prestamos.porSocio.get("ana").get("Rayuela"));

        prestar.ejecutar("ana", "Ficciones");
        prestar.ejecutar("ana", "Pedro Páramo");
        comprobar("con tres préstamos activos se rechaza el cuarto", "RECHAZADO", prestar.ejecutar("ana", "El túnel"));
        comprobar("el rechazo no registra nada", 3, prestamos.activosDe("ana"));
        comprobar("el límite es por socio", "PRESTADO", prestar.ejecutar("luis", "El túnel"));
    }

    public static void main(String[] args) {
        System.out.println("Versión 1, prueba atada a la implementación:");
        pruebaAtadaALaImplementacion(Main::version1);
        System.out.println("Versión 1, pruebas de comportamiento:");
        pruebasDeComportamiento(Main::version1);

        System.out.println();
        System.out.println("Se refactoriza. El comportamiento es el mismo.");
        System.out.println();

        System.out.println("Versión 2, prueba atada a la implementación:");
        pruebaAtadaALaImplementacion(Main::version2);
        System.out.println("Versión 2, pruebas de comportamiento:");
        pruebasDeComportamiento(Main::version2);
    }

    static void comprobar(String nombre, Object esperado, Object obtenido) {
        if (Objects.equals(esperado, obtenido)) {
            System.out.println("  ok    " + nombre);
        } else {
            System.out.println("  FALLA " + nombre + ": esperaba " + esperado + " y obtuve " + obtenido);
        }
    }
}
```

### Análisis

Con la versión 1 pasan los dos juegos. Con la versión 2:

| Prueba | Resultado | Qué significa |
|---|---|---|
| Atada a la implementación | **Falla** | Nada se rompió. La prueba se queja de que el código cambió por dentro |
| De comportamiento | Pasan las cinco | El refactor fue seguro, y las pruebas lo confirman |

La prueba del espía además es peor en otro sentido: con la versión 1 pasa aunque el caso de uso calculara mal la fecha o ignorara el límite. Comprueba que se "llamó a registrar", no qué se registró.

## Errores comunes

- Un simulado para cada clase, incluidos los objetos de valor.
- Verificar el número y el orden de las llamadas sin necesidad.
- Simular tipos de una biblioteca externa en lugar de envolverlos en un puerto propio.
- Falsos tan complejos que necesitarían sus propias pruebas.
- Usar la fecha real del sistema y tener pruebas que fallan los fines de mes.
- Pruebas que preparan veinte dobles: el caso de uso tiene demasiadas responsabilidades.
- Confiar solo en dobles y nunca probar el adaptador real contra la base o el servicio de verdad.

## Taller

**Objetivo:** construir un caso de uso de afuera hacia adentro, descubriendo sus puertos con las pruebas.

**El caso: devolver una bicicleta de alquiler.** Al devolverla se calcula el cobro, se registra el pago y la bicicleta vuelve a estar disponible. Reglas:

- Los primeros 30 minutos son gratis.
- Después, se cobra por cada bloque de 15 minutos iniciado.
- Hay un tope diario.
- Los abonados tienen la primera hora gratis.
- Si la bicicleta se devuelve en una estación distinta de la de origen, hay un recargo.
- Si el alquiler no existe o ya se cerró, es un error.

1. Escribe primero **una** prueba del caso de uso completo, en el lenguaje del negocio: "dado un alquiler iniciado a las 10:00, cuando se devuelve a las 10:50 en la misma estación, se cobran dos bloques y la bicicleta queda disponible". Va a fallar porque no existe nada.
2. Haz que compile creando solo lo que la prueba nombra. Anota qué puertos aparecieron y por qué.
3. Ponla en verde con dobles hechos a mano: un reloj fijo, un repositorio en memoria, un espía para el cobro.
4. Añade las reglas de una en una, con el ciclo. Cuando la lógica del precio crezca, sácala a una función pura y pruébala por dentro, sin dobles.
5. Escribe a propósito una prueba atada a la implementación para una de las reglas. Haz un refactor interno y observa cuál prueba se rompe.
6. Sustituye un doble hecho a mano por uno de Mockito. Compara legibilidad y cuánto sabe la prueba del interior.
7. ¿Qué tres pruebas necesitarías para confiar en el repositorio real contra una base de datos? Escríbelas en texto.
8. Cuenta: ¿cuántas pruebas usan dobles y cuántas no? ¿Qué te dice la proporción sobre dónde quedó la lógica?

**Criterios de aceptación**

- La primera prueba que escribiste es del caso de uso, y el historial lo muestra.
- La lógica del precio está en funciones puras, probadas sin dobles.
- Ninguna prueba depende de la fecha u hora reales.
- Un refactor interno del caso de uso no rompe ninguna prueba, salvo la del paso 5.

**Entrega:** rama `taller/tdd-02`, con el código en `taller/solucion/` y `ANALISIS.md`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## En el trabajo real

En un servicio con arquitectura hexagonal, este es el flujo normal: la prueba del caso de uso define los puertos, los adaptadores reales se prueban aparte con pruebas de integración, y la lógica de negocio queda en funciones que se prueban en milisegundos. Las suites que tardan veinte minutos y se rompen con cada refactor casi siempre abusaron de los simulados.

## Preguntas senior

1. Compara las dos formas de avanzar con TDD y cuándo usas cada una.
2. ¿Qué diferencia hay entre un falso, un sustituto, un espía y un simulado?
3. ¿Por qué una prueba atada a la implementación es peor que no tener prueba?
4. ¿Qué doblas y qué no?
5. Si todas tus pruebas usan dobles, ¿qué no estás probando?
