# 04 — Los límites de TDD y la calidad de las pruebas

## Idea

Un detector de humo. Tenerlo instalado no prueba nada; lo que importa es si suena cuando hay humo. La única forma de saberlo es acercarle una cerilla. Un detector que nunca se probó con humo da la peor de las situaciones: la tranquilidad de estar protegido, sin estarlo. Con las pruebas ocurre igual: que existan y pasen no dice que detecten errores.

## Lo esencial

### La cobertura mide otra cosa

La cobertura dice qué líneas **se ejecutaron** durante las pruebas. No dice si alguna prueba se habría dado cuenta de que esas líneas hacen algo distinto.

| Cobertura | Qué te dice |
|---|---|
| Baja | Hay código que ninguna prueba ejecuta. Dato útil |
| Alta | Casi nada. Se puede tener 100 % con pruebas que no comprueban ningún resultado |

Sirve para encontrar lo que no está probado, no para certificar lo que sí. Convertida en objetivo ("mínimo 80 %"), produce pruebas escritas para subir el número.

### Pruebas de mutación

Una herramienta cambia tu código en un punto pequeño (`>=` por `>`, `+` por `-`, `true` por `false`) y ejecuta las pruebas. A cada versión alterada se la llama **mutante**.

| Resultado | Significa |
|---|---|
| Alguna prueba falla: el mutante **muere** | Tus pruebas detectan ese error |
| Todas pasan: el mutante **sobrevive** | Podrías introducir ese error y nadie se enteraría |

Es la cerilla del detector de humo: mide si las pruebas detectan, no si existen. En Java se hace con PIT; en JavaScript y TypeScript, con Stryker.

### Pruebas basadas en propiedades

En lugar de casos concretos ("2 + 3 da 5"), se declara algo que debe cumplirse **siempre** ("ordenar una lista no cambia su tamaño", "serializar y deserializar devuelve el original") y una herramienta genera cientos de entradas buscando un contraejemplo. Encuentran los casos que a ti no se te ocurrieron. En Java: jqwik.

### Cuándo TDD no es la herramienta

| Situación | Por qué | Qué hacer |
|---|---|---|
| No sabes qué quieres construir | No puedes escribir la prueba de algo que no entiendes | Un experimento desechable; luego, tirar el código y rehacerlo con pruebas |
| Aspecto visual, maquetación | Lo que se evalúa es cómo se ve | Revisión visual, pruebas de regresión visual |
| Código que solo conecta piezas | No hay lógica que diseñar | Una prueba de integración |
| Un guion de un solo uso | No va a cambiar | Nada |
| Rendimiento | No es una propiedad de pasa o falla por caso | Medir |

Y cuándo sí rinde más: lógica con muchos casos y límites, reglas de negocio, algoritmos, corrección de errores (primero la prueba que reproduce el error) y cualquier código que vaya a cambiar mucho.

### Olores en las pruebas

| Olor | Problema |
|---|---|
| Sin comprobaciones, o con una trivial | Pasa siempre |
| Varias razones para fallar en una misma prueba | Cuando falla, no dice qué se rompió |
| Lógica dentro de la prueba (bucles, condicionales) | La prueba necesita pruebas |
| Depende del orden o de otra prueba | Falla a veces, y nadie le hace caso |
| Preparación de cuarenta líneas | El código es difícil de usar |
| Copia la fórmula del código que prueba | Si la fórmula está mal, la prueba también |
| Se ignora "temporalmente" | Lleva dos años ignorada |

## Ejemplo

Dos juegos de pruebas con la misma cobertura frente a un mutante. Ejecútalo con `java Main.java`.

### Antes y después, juntos

<!-- file: ejemplo/Main.java -->
```java
import java.util.function.IntPredicate;

/** La misma cobertura, distinta capacidad de detectar un error. */
public class Main {

    static final IntPredicate ORIGINAL = edad -> edad >= 18;
    // Lo que haría una herramienta de mutación: cambiar >= por >
    static final IntPredicate MUTANTE = edad -> edad > 18;

    /** Antes: ejecutan toda la línea (cobertura del 100 %), pero lejos del límite. */
    static int pruebasDebiles(IntPredicate puedeVotar) {
        int fallos = 0;
        if (!puedeVotar.test(30)) fallos++;
        if (puedeVotar.test(5)) fallos++;
        return fallos;
    }

    /** Después: la misma cobertura, con casos justo en el límite. */
    static int pruebasFuertes(IntPredicate puedeVotar) {
        int fallos = 0;
        if (puedeVotar.test(17)) fallos++;
        if (!puedeVotar.test(18)) fallos++;
        if (!puedeVotar.test(19)) fallos++;
        return fallos;
    }

    public static void main(String[] args) {
        informar("Pruebas débiles", pruebasDebiles(ORIGINAL), pruebasDebiles(MUTANTE));
        informar("Pruebas fuertes", pruebasFuertes(ORIGINAL), pruebasFuertes(MUTANTE));
    }

    static void informar(String nombre, int fallosConOriginal, int fallosConMutante) {
        System.out.println(nombre + ":");
        System.out.println("  con el código original: " + (fallosConOriginal == 0 ? "pasan" : "fallan"));
        System.out.println("  con el mutante:         " + (fallosConMutante == 0
                ? "pasan  -> el mutante SOBREVIVE: ese error llegaría a producción"
                : "fallan -> el mutante MUERE: las pruebas lo detectan"));
    }
}
```

### Análisis

| | Cobertura de líneas | ¿Detecta `>=` cambiado por `>`? |
|---|---|---|
| Pruebas débiles | 100 % | No |
| Pruebas fuertes | 100 % | Sí |

El informe de cobertura de las dos es idéntico. Lo que cambia es que unas prueban el límite y las otras no. Una persona de 18 años que no puede votar es exactamente el tipo de error que aparece en producción.

## Errores comunes

- Fijar un porcentaje de cobertura como objetivo del equipo.
- Pruebas sin comprobaciones para "subir el número".
- Perseguir el 100 % de mutantes muertos: algunos son equivalentes al original y no se pueden matar.
- TDD dogmático en una exploración, o ningún TDD donde más rinde.
- Quedarse con el código del experimento "porque ya funciona".
- Pruebas basadas en propiedades que repiten la implementación.
- Ignorar las pruebas que fallan a veces en lugar de arreglarlas o borrarlas.

## Taller

**Objetivo:** medir la calidad real de tus pruebas, mejorarlas y decidir con criterio dónde aplicar TDD.

Sobre tu kata de la sesión 01 y tu caso de uso de la sesión 02.

1. Mide la cobertura de líneas y de ramas de la kata. Anótala.
2. Configura pruebas de mutación (PIT si es Java) siguiendo su documentación. Ejecútalas y anota cuántos mutantes sobrevivieron.
3. Para cada superviviente: ¿qué error real representa? Escribe la prueba que lo mata. ¿Encontraste alguno que no se pueda matar? Explica por qué.
4. Vuelve a medir la cobertura. ¿Cambió? ¿Qué te dice eso sobre la cobertura como medida?
5. Haz el experimento contrario: escribe pruebas sin ninguna comprobación hasta llegar al 100 % de cobertura sobre una copia del código. Ejecuta las pruebas de mutación. Guarda el informe: es el mejor argumento que tendrás contra los objetivos de cobertura.
6. Escribe tres pruebas basadas en propiedades para la calculadora de cadenas (por ejemplo: el orden de los números no cambia la suma). ¿Encontraron algún caso que tus pruebas no cubrían?
7. Revisa tus pruebas de las sesiones 01 a 03 con la tabla de olores. Corrige tres.
8. Corrige un error con TDD: introduce un error a propósito, escribe la prueba que lo reproduce, vela fallar y arréglalo. ¿Por qué ese orden y no arreglar primero?
9. Elige un trozo de tu proyecto donde **no** usarías TDD y justifícalo. Di qué harías en su lugar para tener confianza.
10. Si usas un asistente de IA para programar: pídele implementar una función primero sin pruebas y luego dándole tus pruebas como especificación. Compara los resultados y ejecuta las pruebas de mutación sobre los dos.
11. Escribe la política de pruebas que propondrías a un equipo, en una página: qué se prueba, a qué nivel, qué se mide y qué no se usa como objetivo.

**Criterios de aceptación**

| Criterio | Cómo se comprueba |
|---|---|
| Hay un informe de mutación antes y después | Los dos informes |
| Cada mutante superviviente tiene explicación | `ANALISIS.md` |
| El experimento del paso 5 está documentado | Informe de cobertura y de mutación |
| La política nombra qué **no** se mide | Lectura |

**Entrega:** rama `taller/tdd-04`, con el código en `taller/solucion/` y `ANALISIS.md`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## En el trabajo real

Los equipos que mejor prueban no discuten porcentajes: miran cuántos errores llegan a producción y cuánto tarda un cambio en desplegarse con confianza. Las pruebas de mutación suelen correr solo sobre el código modificado en cada cambio, porque sobre el proyecto entero son lentas.

Y una nota de época: con asistentes de IA escribiendo código, una buena batería de pruebas es la forma más precisa de decirle a la máquina qué quieres y de comprobar que lo hizo. Eso conecta con la parte de [desarrollo guiado por especificaciones](../../sdd/).

## Preguntas senior

1. ¿Por qué la cobertura no mide la calidad de las pruebas?
2. ¿Qué son las pruebas de mutación y qué te dicen?
3. ¿Cuándo no usarías TDD y qué harías en su lugar?
4. ¿Qué aportan las pruebas basadas en propiedades?
5. Tu gerente propone exigir 90 % de cobertura. ¿Qué respondes y qué propones a cambio?
