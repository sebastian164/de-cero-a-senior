# Interpreter

Familia: comportamiento. Frecuencia en Java empresarial: baja en implementación propia; alta en uso (cada lenguaje de expresiones que configuras).

## El problema

Hay reglas que cambian a menudo y que deberían poder definirse como **datos** (texto en configuración, filas en una tabla, lo que escribe un usuario) en lugar de como código que requiere compilar y desplegar: reglas de descuento, filtros de búsqueda, permisos, fórmulas.

El patrón propone representar cada elemento de un lenguaje pequeño como una clase, y evaluar una frase recorriendo el árbol que forman esos objetos.

## Cómo reconocer que lo necesitas

- El negocio pide cambiar reglas sin esperar un despliegue.
- Las reglas se combinan: "A y (B o no C)".
- Aparecen `if` anidados que en realidad son una gramática.

## Antes

```java
boolean aplicaPromo(Cliente c, Pedido p) {
    return (c.esVip() && p.total() > 200_000) || (p.categoria().equals("LIBROS") && !c.esNuevo());
    // cada cambio de la regla: modificar, compilar, probar, desplegar
}
```

## Después

<!-- file: ejemplo/Main.java -->
```java
import java.util.Map;

public class Main {

    public static void main(String[] args) {
        // (vip Y total > 200000) O (categoria = LIBROS Y NO nuevo)
        Regla promo = new O(
                new Y(new EsVerdadero("vip"), new MayorQue("total", 200_000)),
                new Y(new Igual("categoria", "LIBROS"), new No(new EsVerdadero("nuevo"))));

        Map<String, Object> pedido1 = Map.of("vip", true, "total", 350_000L, "categoria", "HOGAR", "nuevo", false);
        Map<String, Object> pedido2 = Map.of("vip", false, "total", 40_000L, "categoria", "LIBROS", "nuevo", true);

        System.out.println(promo.describir());
        System.out.println("Pedido 1: " + promo.evaluar(pedido1));
        System.out.println("Pedido 2: " + promo.evaluar(pedido2));
    }
}

/** Cada elemento de la gramática es una clase que sabe evaluarse en un contexto. */
interface Regla {
    boolean evaluar(Map<String, Object> contexto);
    String describir();
}

// ----- Expresiones terminales -----

record EsVerdadero(String campo) implements Regla {
    @Override public boolean evaluar(Map<String, Object> contexto) { return Boolean.TRUE.equals(contexto.get(campo)); }
    @Override public String describir() { return campo; }
}

record MayorQue(String campo, long valor) implements Regla {
    @Override public boolean evaluar(Map<String, Object> contexto) { return ((Long) contexto.get(campo)) > valor; }
    @Override public String describir() { return campo + " > " + valor; }
}

record Igual(String campo, String valor) implements Regla {
    @Override public boolean evaluar(Map<String, Object> contexto) { return valor.equals(contexto.get(campo)); }
    @Override public String describir() { return campo + " = " + valor; }
}

// ----- Expresiones compuestas -----

record Y(Regla izquierda, Regla derecha) implements Regla {
    @Override public boolean evaluar(Map<String, Object> contexto) { return izquierda.evaluar(contexto) && derecha.evaluar(contexto); }
    @Override public String describir() { return "(" + izquierda.describir() + " Y " + derecha.describir() + ")"; }
}

record O(Regla izquierda, Regla derecha) implements Regla {
    @Override public boolean evaluar(Map<String, Object> contexto) { return izquierda.evaluar(contexto) || derecha.evaluar(contexto); }
    @Override public String describir() { return "(" + izquierda.describir() + " O " + derecha.describir() + ")"; }
}

record No(Regla regla) implements Regla {
    @Override public boolean evaluar(Map<String, Object> contexto) { return !regla.evaluar(contexto); }
    @Override public String describir() { return "NO " + regla.describir(); }
}
```

El árbol de reglas es un Composite cuyos nodos saben evaluarse. Lo que falta para que sea configurable es un analizador que convierta texto en ese árbol; eso queda fuera del patrón.

## Alternativas

- **Un lenguaje de expresiones existente**: SpEL, MVEL, JEXL. Casi siempre es mejor que escribir uno.
- **Un motor de reglas** (Drools) o tablas de decisión, si las reglas son muchas y las administra el negocio.
- **Composición de predicados** (`Predicate.and/or/negate`), si las reglas se definen en código.
- **Datos estructurados** (JSON con operadores) evaluados por un intérprete pequeño como el del ejemplo.

## En Spring y en Java

- **SpEL**: `@Value("#{…}")`, `@PreAuthorize("hasRole('ADMIN') and #pedido.cliente == principal.name")`, condiciones de `@Cacheable`.
- Expresiones `cron` de `@Scheduled`.
- Consultas derivadas de Spring Data: `findByClienteAndEstadoOrderByFechaDesc` es un pequeño lenguaje que se interpreta.
- `java.util.regex.Pattern`: compila una expresión a una estructura que luego se evalúa.

## Trade-offs

- A favor: reglas como datos, modificables sin desplegar; gramática fácil de extender con nuevas clases; cada regla es pequeña y probable.
- En contra: gramáticas grandes producen jerarquías inmanejables; rendimiento inferior al código compilado; evaluar expresiones de origen no confiable es un **riesgo de seguridad** serio (inyección de expresiones).

## Errores comunes

- Inventar un lenguaje cuando SpEL o predicados bastaban.
- Evaluar expresiones escritas por usuarios sin restringir qué pueden invocar.
- No validar tipos: el ejemplo falla con `ClassCastException` si `total` no es `Long`.
- Mezclar análisis sintáctico y evaluación en las mismas clases.

## Cuándo no usarlo

Cuando las reglas cambian poco; cuando la gramática es grande; cuando existe una herramienta estándar que la cubre.

## Taller

1. Extiende el ejemplo con `MenorQue`, `Entre` y `EnLista`.
2. Escribe un analizador mínimo que convierta texto como `vip Y total > 200000` en el árbol (sin paréntesis en la primera versión).
3. Haz que un error de tipos produzca un mensaje claro que indique el campo y la regla.
4. Implementa la misma regla del ejemplo con `Predicate<Map<String,Object>>` compuestos. ¿Qué perdiste respecto al árbol de objetos? (Pista: `describir`.)
5. Explica el riesgo de permitir que un usuario escriba una expresión SpEL y cómo se mitiga.

Entrega: rama `taller/n3-interpreter`.
