# 06 — Java moderno

## Idea

El Java de hoy no es el de 2014. Varias cosas que antes exigían clases enteras, patrones o Lombok ahora son una línea del lenguaje. Conocerlas evita escribir de más y permite leer código reciente.

## Lo esencial (hasta Java 21, la versión LTS de referencia del curso)

| Característica | Desde | Qué resuelve |
|---|---|---|
| `var` | 10 | Inferencia de tipos en variables locales |
| Expresiones `switch` | 14 | `switch` que devuelve valor, sin `break` |
| Bloques de texto | 15 | Cadenas multilínea (SQL, JSON) |
| `record` | 16 | Portadores de datos inmutables |
| `instanceof` con patrón | 16 | Comprobar y convertir en un paso |
| Clases e interfaces `sealed` | 17 | Jerarquías cerradas |
| Patrones en `switch` | 21 | `switch` por tipo, exhaustivo |
| Patrones de `record` | 21 | Desestructurar |
| Hilos virtuales | 21 | Concurrencia ligera |
| Colecciones secuenciadas | 21 | `getFirst()`, `getLast()`, `reversed()` |

### `record`

```java
record Dinero(long centavos, String moneda) {
    Dinero {                                        // constructor compacto: validación
        if (centavos < 0) throw new IllegalArgumentException("negativo");
    }
    Dinero mas(Dinero otro) { return new Dinero(centavos + otro.centavos, moneda); }
}
```

Genera constructor, accesores, `equals`, `hashCode` y `toString`. No puede heredar de clases ni tener campos de instancia adicionales.

### Tipos algebraicos: `sealed` + `record` + `switch`

```java
sealed interface ResultadoPago permits Aprobado, Rechazado, Pendiente { }
record Aprobado(String autorizacion) implements ResultadoPago { }
record Rechazado(String motivo) implements ResultadoPago { }
record Pendiente(Duration reintentarEn) implements ResultadoPago { }

String mensaje = switch (resultado) {
    case Aprobado(String autorizacion) -> "Pago aprobado: " + autorizacion;
    case Rechazado(String motivo) -> "Pago rechazado: " + motivo;
    case Pendiente p -> "Reintentar en " + p.reintentarEn().toSeconds() + " s";
};   // sin default: si se agrega un caso, el compilador avisa aquí
```

Este trío reemplaza en muchos casos a Visitor, a jerarquías con métodos abstractos y a códigos de error.

### Otros

```java
if (figura instanceof Circulo c && c.radio() > 10) { ... }       // patrón con condición

String sql = """
        SELECT p.id, p.total
        FROM pedidos p
        WHERE p.estado = 'PENDIENTE'
        """;

var pedidosPorCliente = new HashMap<String, List<Pedido>>();     // var donde el tipo es obvio
```

## Errores comunes

- `var` donde oculta un tipo que el lector necesita (`var x = servicio.procesar()`).
- `record` para entidades JPA: son inmutables y sin constructor vacío; no encajan.
- Creer que un `record` es profundamente inmutable teniendo componentes mutables.
- `default` en un `switch` sobre `sealed`: anula la verificación de exhaustividad.
- Mantener Lombok para lo que el lenguaje ya da.
- Quedarse en Java 8 por inercia: se pierden mejoras de rendimiento y seguridad.

## En Spring

Spring Boot 3 exige Java 17 como mínimo. `record` sirve para DTOs de petición y respuesta, `@ConfigurationProperties` y proyecciones de Spring Data. Los bloques de texto hacen legibles las consultas de `@Query`.

## Taller

1. Modela con `sealed` y `record` las formas de pago (tarjeta, transferencia, efectivo, billetera), cada una con sus datos, y una función que calcule la comisión con `switch` exhaustivo.
2. Agrega una forma de pago y anota qué deja de compilar.
3. Toma una clase con getters, `equals`, `hashCode` y `toString` escritos a mano y conviértela en `record`. ¿Qué se perdió?
4. Reescribe un método con cadenas de `if/else instanceof` y conversiones usando patrones.
5. Implementa el mismo caso con Visitor clásico y compara.
6. Revisa las novedades de las versiones posteriores a la 21 y resume tres que usarías.

Rama `taller/n6-06-java-moderno`.

## Preguntas senior

1. ¿Cuándo usar `record` y cuándo una clase?
2. ¿Qué ventaja da `sealed` frente a una interfaz abierta?
3. ¿Qué patrones clásicos pierden sentido con el *pattern matching*?
4. ¿Cómo decides cuándo migrar de versión de Java en una empresa?
5. ¿Qué es una versión LTS y qué implica para la planeación?
