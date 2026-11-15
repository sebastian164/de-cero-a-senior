# 01 — Rojo, verde, refactor

## Idea

Escalar con cuerda. Antes de subir cada tramo, el escalador clava un seguro. Clavarlo no lo hace subir más rápido en ese momento; lo que hace es que, si resbala, caiga un metro y no veinte. Por eso puede intentar movimientos que sin cuerda no se atrevería a hacer. Una prueba escrita antes del código es ese seguro: es lo que te permite cambiar el diseño sin miedo.

## Lo esencial

### El ciclo

| Paso | Qué haces | Cuánto dura |
|---|---|---|
| **Rojo** | Escribes una prueba pequeña de algo que el código aún no hace. La ejecutas y **la ves fallar** | Un par de minutos |
| **Verde** | Escribes lo mínimo para que pase. Feo vale | Un par de minutos |
| **Refactor** | Con todas las pruebas en verde, limpias: nombres, duplicación, estructura. No añades comportamiento | Lo que haga falta |

Ver fallar la prueba no es un trámite: es la única comprobación de que la prueba prueba algo. Una prueba que nunca viste en rojo puede estar pasando por la razón equivocada.

### Pasos pequeños

| Técnica | Cuándo |
|---|---|
| Devolver una constante | Para la primera prueba. Sí, de verdad |
| Triangular | Añadir un segundo caso que obligue a generalizar la constante |
| Implementación obvia | Cuando la solución es evidente y corta, se escribe directamente |

Si una prueba te obliga a escribir veinte líneas para ponerla en verde, la prueba era demasiado grande. Vuelve atrás y elige un caso más simple.

### La lista de pruebas

Antes de empezar, apunta los casos que se te ocurran, de más simple a más complejo. Mientras programas aparecerán otros: se añaden a la lista, no se atienden en el momento. La lista es lo que te permite concentrarte en una sola cosa.

### Qué no es TDD

- Escribir todas las pruebas y luego todo el código.
- Escribir el código y luego las pruebas "para cubrir".
- Tener muchas pruebas.

TDD es una técnica de **diseño**: las pruebas son el primer cliente de tu código, y si cuesta probarlo, cuesta usarlo.

## Ejemplo

Convertir un número a romano. Primero, cómo se suele hacer; después, el recorrido con el ciclo.

### Antes

```java
// Se escribe todo de una vez, "pensándolo bien"
static String aRomano(int n) {
    String r = "";
    while (n >= 1000) { r += "M"; n -= 1000; }
    if (n >= 500) { r += "D"; n -= 500; }
    while (n >= 100) { r += "C"; n -= 100; }
    if (n >= 50) { r += "L"; n -= 50; }
    while (n >= 10) { r += "X"; n -= 10; }
    if (n >= 5) { r += "V"; n -= 5; }
    while (n >= 1) { r += "I"; n -= 1; }
    return r;
}
// Se prueba a mano con 3, 8 y 2000. Funciona. Se entrega.
```

### Análisis

| Problema | Consecuencia |
|---|---|
| Se probó con los casos en que el autor estaba pensando | `aRomano(4)` devuelve `IIII` y `aRomano(9)`, `VIIII`. Nadie lo vio |
| No hay pruebas guardadas | El siguiente cambio puede romper lo que funcionaba, y no hay forma de saberlo |
| Arreglar el 4 y el 9 ahora significa añadir casos especiales a una estructura que no los esperaba | El código empeora con cada corrección |
| `aRomano(0)` devuelve cadena vacía y `aRomano(5000)`, `MMMMM` | Nadie decidió qué debía pasar: lo decidió el accidente |

### Después

El recorrido. Cada fila es una vuelta completa del ciclo:

| Prueba añadida | Cambio mínimo para ponerla en verde |
|---|---|
| 1 es `I` | `return "I";` |
| 2 es `II` | Un `if`. Feo, pero verde |
| 3 es `III` | Aparece el patrón: un bucle que resta 1 y añade `I` |
| 5 es `V` | Otro `if` delante del bucle |
| 6 es `VI` | Ya pasa: no se toca nada. Se deja la prueba |
| 4 es `IV` | Un caso especial… y al refactorizar se ve que `IV` puede ser un "símbolo" más, con valor 4 |
| 10 es `X`, 9 es `IX` | Otros dos pares. La duplicación pide a gritos una tabla |
| *refactor* | Los `if` se convierten en dos arreglos paralelos y un bucle |
| 40, 90, 400, 1994, 3999 | Solo añadir filas a la tabla |
| 0 y 4000 lanzan error | Una validación al principio. **Decidida**, no accidental |

El resultado, con las pruebas en el orden en que se escribieron. Ejecútalo con `java Main.java`:

<!-- file: ejemplo/Main.java -->
```java
import java.util.Objects;

/** El resultado de construir "a números romanos" con el ciclo rojo, verde, refactor. */
public class Main {

    private static final int[] VALORES = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
    private static final String[] SIMBOLOS = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};

    static String aRomano(int numero) {
        if (numero < 1 || numero > 3999) {
            throw new IllegalArgumentException("fuera del rango de 1 a 3999: " + numero);
        }
        StringBuilder romano = new StringBuilder();
        int resto = numero;
        for (int i = 0; i < VALORES.length; i++) {
            while (resto >= VALORES[i]) {
                romano.append(SIMBOLOS[i]);
                resto -= VALORES[i];
            }
        }
        return romano.toString();
    }

    public static void main(String[] args) {
        comprobar("1 es I", "I", aRomano(1));
        comprobar("2 es II", "II", aRomano(2));
        comprobar("3 es III", "III", aRomano(3));
        comprobar("5 es V", "V", aRomano(5));
        comprobar("6 es VI", "VI", aRomano(6));
        comprobar("4 es IV", "IV", aRomano(4));
        comprobar("10 es X", "X", aRomano(10));
        comprobar("9 es IX", "IX", aRomano(9));
        comprobar("14 es XIV", "XIV", aRomano(14));
        comprobar("40 es XL", "XL", aRomano(40));
        comprobar("90 es XC", "XC", aRomano(90));
        comprobar("400 es CD", "CD", aRomano(400));
        comprobar("1994 es MCMXCIV", "MCMXCIV", aRomano(1994));
        comprobar("3999 es MMMCMXCIX", "MMMCMXCIX", aRomano(3999));
        comprobarQueRechaza("0 no tiene representación", 0);
        comprobarQueRechaza("4000 queda fuera del rango", 4000);

        System.out.println(fallos == 0 ? "Todas pasan." : fallos + " fallan.");
        if (fallos > 0) {
            System.exit(1);
        }
    }

    // Un ejecutor de pruebas mínimo, para que el ejemplo corra sin instalar nada.
    // En el taller usarás un marco de pruebas de verdad.
    static int fallos = 0;

    static void comprobar(String nombre, Object esperado, Object obtenido) {
        if (Objects.equals(esperado, obtenido)) {
            System.out.println("  ok    " + nombre);
        } else {
            fallos++;
            System.out.println("  FALLA " + nombre + ": esperaba " + esperado + " y obtuve " + obtenido);
        }
    }

    static void comprobarQueRechaza(String nombre, int numero) {
        try {
            aRomano(numero);
            fallos++;
            System.out.println("  FALLA " + nombre + ": no lanzó error");
        } catch (IllegalArgumentException esperada) {
            System.out.println("  ok    " + nombre);
        }
    }
}
```

La tabla de símbolos no se diseñó de antemano: **salió** de eliminar duplicación con las pruebas en verde. Y el 4 y el 9, que en el "antes" eran errores ocultos, aquí fueron simplemente la siguiente prueba.

## Errores comunes

- No ejecutar la prueba antes de escribir el código.
- Pruebas enormes que exigen media solución para ponerse en verde.
- Saltarse el refactor: se acumula el código feo que se escribió "solo para pasar".
- Refactorizar con una prueba en rojo.
- Cambiar código y pruebas a la vez durante el refactor.
- Probar la implementación (qué métodos se llaman) en vez del comportamiento (qué resultado da).
- Nombres de prueba que no dicen nada: `test1`, `pruebaCalcular`.

## Taller

**Objetivo:** hacer una kata completa con el ciclo estricto y dejar el recorrido registrado en el historial de git.

Usa el proyecto con JUnit que montaste en el [Nivel 4 de Java](../../../java/nivel-04-clean-code-testing/09-junit5/). Si vienes de una ruta de frontend, Vitest o Jest sirven igual: la técnica no depende del lenguaje.

**La kata: calculadora de cadenas.** Una función `sumar(String)` que recibe números en un texto y devuelve su suma. Los requisitos llegan de uno en uno; **no leas el siguiente hasta terminar el anterior**:

1. Una cadena vacía da 0.
2. Un solo número da ese número.
3. Dos números separados por coma dan su suma.
4. Cualquier cantidad de números.
5. Un salto de línea también separa: `"1\n2,3"` da 6.
6. Se puede declarar otro separador en la primera línea: `"//;\n1;2"` da 3.
7. Un número negativo lanza un error cuyo mensaje lista **todos** los negativos encontrados.
8. Los números mayores que 1000 se ignoran.
9. El separador puede tener varios caracteres: `"//[***]\n1***2***3"` da 6.
10. Puede haber varios separadores: `"//[*][%]\n1*2%3"` da 6.

Reglas del taller:

- **Un commit por cada paso del ciclo**, con prefijo: `rojo:`, `verde:` o `refactor:`. Un commit `rojo:` debe dejar exactamente una prueba fallando.
- Antes de empezar, escribe tu lista de pruebas en `ANALISIS.md`. Al terminar, marca cuáles añadiste por el camino.
- Si un paso a verde te lleva más de cinco minutos, deshazlo (`git reset`) y elige una prueba más pequeña. Anota cada vez que te pasó.

Al terminar:

11. Cuenta los commits de cada tipo. ¿Cuántos refactor hiciste? Si son muy pocos, revisa el código: ¿hay duplicación que dejaste pasar?
12. Elige el refactor del que estés más orgulloso y explica qué te lo permitió.
13. Borra la implementación y vuelve a hacer la kata otro día, sin mirar. Compara los dos diseños.

**Criterios de aceptación**

| Criterio | Cómo se comprueba |
|---|---|
| El historial muestra el ciclo | `git log --oneline` alterna rojo, verde y refactor |
| Cada commit `verde:` deja todas las pruebas pasando | Revisando commits al azar |
| Los nombres de las pruebas describen comportamiento | Lectura |
| Los diez requisitos están cubiertos | Las pruebas |

**Entrega:** rama `taller/tdd-01`, con el código en `taller/solucion/` y `ANALISIS.md`.

## En el trabajo real

Casi nadie hace TDD estricto todo el tiempo, y quien dice que sí suele exagerar. Lo que distingue a quien lo domina es que puede **elegir**: cuando el problema es de lógica con muchos casos, lo saca con el ciclo; cuando es exploratorio, no. Esa elección solo la tiene quien practicó el ciclo hasta que le salió natural.

## Preguntas senior

1. ¿Por qué hay que ver fallar la prueba antes de escribir el código?
2. ¿Qué papel tiene el refactor y qué pasa si se omite?
3. ¿En qué sentido TDD es una técnica de diseño y no de pruebas?
4. ¿Qué te indica una prueba que es difícil de escribir?
5. ¿Qué diferencia hay entre probar comportamiento y probar implementación?
