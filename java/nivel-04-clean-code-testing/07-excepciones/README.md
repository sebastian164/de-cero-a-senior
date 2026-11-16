# 07 — Manejo de excepciones

## Idea sencilla

En un restaurante, si se acaba un ingrediente, el cocinero no sirve el plato incompleto en silencio, ni cierra el restaurante. Avisa al mesero, y el mesero le dice al cliente algo que pueda entender: "no hay salmón, ¿le ofrezco trucha?". El problema se comunica hacia quien puede decidir, en un idioma que esa persona entiende.

Las excepciones son ese aviso: un mecanismo para que un error llegue a quien puede hacer algo con él.

## Explicación técnica

### Tipos en Java

| Tipo | Hereda de | El compilador obliga a tratarla | Uso |
|---|---|---|---|
| Comprobada (*checked*) | `Exception` | Sí | Fallos recuperables que el llamador debe considerar (`IOException`) |
| No comprobada (*unchecked*) | `RuntimeException` | No | Errores de programación y violaciones de reglas |
| `Error` | `Error` | No | Fallos graves de la JVM. No se capturan |

La práctica actual en aplicaciones empresariales favorece las no comprobadas: las comprobadas contaminan las firmas, obligan a capturar en lugares que no saben qué hacer y no encajan con lambdas ni streams.

### Reglas

1. **No tragues excepciones.** Un `catch` vacío convierte un fallo visible en un dato corrupto que aparece semanas después.
2. **Captura donde puedas hacer algo:** recuperarte, reintentar, traducir o informar. Si no puedes, deja que suba.
3. **Falla pronto.** Valida al entrar; un error cerca de su causa es fácil de diagnosticar.
4. **No uses excepciones para el flujo normal.** "El usuario no existe" en una búsqueda es un resultado esperado: `Optional`. "El pedido que debía existir no existe" es una excepción.
5. **Sé específico.** Captura `IOException`, no `Exception`. Lanza `PedidoNoEncontradoException`, no `RuntimeException`.
6. **Conserva la causa.** `throw new MiExcepcion("contexto", e)`. Perder el origen hace imposible el diagnóstico.
7. **Traduce en las fronteras.** El dominio no debe lanzar `SQLException`; la infraestructura la envuelve en una excepción con significado.
8. **Mensajes con contexto.** "Fondos insuficientes: saldo 20.000, retiro 50.000, cuenta 123" en lugar de "Error".
9. **Registra o relanza, no ambas.** Registrar y relanzar en cada capa produce la misma traza cinco veces.
10. **Libera recursos con `try-with-resources`**, nunca a mano en un `finally`.
11. **No devuelvas `null` ni códigos de error** donde corresponde una excepción o un `Optional`.

## Código con problemas

<!-- file: ejemplos/antes/Main.java -->
```java
import java.util.HashMap;
import java.util.Map;

public class Main {

    public static void main(String[] args) {
        Cuentas cuentas = new Cuentas();
        System.out.println(cuentas.transferir("A", "B", 500_000));
        System.out.println(cuentas.transferir("A", "X", 10));
        System.out.println(cuentas.saldos);
    }
}

class Cuentas {

    final Map<String, Long> saldos = new HashMap<>(Map.of("A", 100_000L, "B", 0L));

    /** Devuelve 0 si todo va bien, -1 si hay error. */
    int transferir(String origen, String destino, long monto) {
        try {
            long saldoOrigen = saldos.get(origen);
            if (saldoOrigen < monto) {
                return -1;
            }
            saldos.put(origen, saldoOrigen - monto);
            long saldoDestino = saldos.get(destino);   // NullPointerException si no existe
            saldos.put(destino, saldoDestino + monto);
            return 0;
        } catch (Exception e) {
            return -1;
        }
    }
}
```

## Análisis

- **Códigos de error:** el llamador puede ignorar el `-1`, y no sabe **por qué** falló. Fondos insuficientes y cuenta inexistente son indistinguibles.
- **`catch (Exception e)` que traga todo:** oculta un `NullPointerException`, que es un error de programación.
- **Estado corrupto:** en la segunda llamada se descuenta el dinero de A, falla al buscar X, y el dinero desaparece. El `catch` impide que alguien se entere.
- **Sin contexto:** nada indica qué cuenta ni qué monto.

## Código mejorado

<!-- file: ejemplos/despues/Main.java -->
```java
import java.util.HashMap;
import java.util.Map;

public class Main {

    public static void main(String[] args) {
        Cuentas cuentas = new Cuentas();
        intentar(() -> cuentas.transferir("A", "B", 500_000));
        intentar(() -> cuentas.transferir("A", "X", 10));
        intentar(() -> cuentas.transferir("A", "B", 40_000));
        System.out.println(cuentas.saldos());
    }

    /** La frontera: aquí sí se sabe qué hacer con el error (informar). */
    static void intentar(Runnable operacion) {
        try {
            operacion.run();
            System.out.println("Transferencia realizada");
        } catch (ReglaDeNegocioException e) {
            System.out.println("Rechazada: " + e.getMessage());
        }
    }
}

/** Raíz de los errores de negocio: permite tratarlos como familia. */
class ReglaDeNegocioException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    ReglaDeNegocioException(String mensaje) { super(mensaje); }
}

class CuentaNoEncontradaException extends ReglaDeNegocioException {
    private static final long serialVersionUID = 1L;
    CuentaNoEncontradaException(String cuenta) { super("No existe la cuenta " + cuenta); }
}

class FondosInsuficientesException extends ReglaDeNegocioException {
    private static final long serialVersionUID = 1L;
    FondosInsuficientesException(String cuenta, long saldo, long monto) {
        super("Fondos insuficientes en " + cuenta + ": saldo " + saldo + ", solicitado " + monto);
    }
}

class Cuentas {

    private final Map<String, Long> saldos = new HashMap<>(Map.of("A", 100_000L, "B", 0L));

    void transferir(String origen, String destino, long monto) {
        if (monto <= 0) {
            throw new IllegalArgumentException("El monto debe ser positivo: " + monto);
        }
        if (origen.equals(destino)) {
            throw new IllegalArgumentException("El origen y el destino son la misma cuenta: " + origen);
        }
        // Validar todo antes de modificar nada: o se hace completo o no se hace.
        long saldoOrigen = saldoDe(origen);
        long saldoDestino = saldoDe(destino);
        if (saldoOrigen < monto) {
            throw new FondosInsuficientesException(origen, saldoOrigen, monto);
        }
        saldos.put(origen, saldoOrigen - monto);
        saldos.put(destino, saldoDestino + monto);
    }

    private long saldoDe(String cuenta) {
        Long saldo = saldos.get(cuenta);
        if (saldo == null) {
            throw new CuentaNoEncontradaException(cuenta);
        }
        return saldo;
    }

    Map<String, Long> saldos() {
        return Map.copyOf(saldos);
    }
}
```

Cada fallo tiene nombre y contexto. No se puede ignorar. Y el estado queda consistente porque se valida todo antes de modificar.

## Taller

<!-- file: taller/inicio/Main.java -->
```java
import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        System.out.println(new Importador().importar(List.of("Teclado;120000", "Mouse;abc", "", "Monitor")));
    }
}

class Importador {

    List<String> importar(List<String> lineas) {
        List<String> resultado = new ArrayList<>();
        for (String linea : lineas) {
            try {
                String[] campos = linea.split(";");
                long precio = Long.parseLong(campos[1]);
                resultado.add(campos[0] + "=" + precio);
            } catch (Exception e) {
                // línea mala, seguimos
            }
        }
        return resultado.isEmpty() ? null : resultado;
    }
}
```

1. Lista todo lo que este código oculta y qué problema real causaría cada cosa.
2. Decide la política: ¿una línea inválida detiene la importación o se reporta y se continúa? Implementa la que elijas de modo que **ningún** error se pierda.
3. El resultado debe distinguir líneas importadas de rechazadas, con el número de línea y el motivo de cada rechazo.
4. Elimina el `null` de retorno.
5. Diseña la jerarquía de excepciones, si es que la necesitas. Justifica si usaste excepciones o un objeto resultado.

Se revisará: que no haya `catch` genéricos, que los mensajes tengan contexto y que la política de errores sea explícita. Rama `taller/n4-07-excepciones`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## Aplicación profesional

Fragmentos compilados y ejecutados con Spring Boot 4.1.1 (el manejador devuelve 404 y 422 con Problem Details).

**Traducción centralizada a HTTP con `@RestControllerAdvice`:**

```java
@RestControllerAdvice
class ManejadorDeErrores {

    @ExceptionHandler(RecursoNoEncontradoException.class)
    ResponseEntity<ProblemDetail> noEncontrado(RecursoNoEncontradoException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage()));
    }

    @ExceptionHandler(ReglaDeNegocioException.class)
    ResponseEntity<ProblemDetail> regla(ReglaDeNegocioException e) {
        return ResponseEntity.unprocessableEntity()
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage()));
    }
}
```

Los controladores no tienen `try/catch`. El formato de error sigue el estándar *Problem Details* (RFC 9457).

- **`@Transactional` revierte por defecto solo con excepciones no comprobadas.** Una excepción comprobada no revierte salvo que se indique `rollbackFor`. Es una fuente clásica de datos a medias.
- **Capturar una excepción dentro de un método transaccional** y continuar puede dejar la transacción marcada para revertir, y fallar al confirmar con `UnexpectedRollbackException`.
- **Spring traduce `SQLException`** a la jerarquía `DataAccessException`: es el principio 7 aplicado por el framework.
- **Nunca expongas trazas ni mensajes internos** al cliente de la API: son información para un atacante. El detalle va al log, con un identificador de correlación.

## Preguntas senior

1. ¿Comprobadas o no comprobadas? Defiende una postura y da un caso en el que usarías la otra.
2. ¿Qué pasa con una transacción de Spring si lanzas una excepción comprobada? ¿Y si capturas una no comprobada dentro del método?
3. Diferencia entre un error de negocio, un error técnico recuperable y un error de programación. ¿Cómo responde tu API a cada uno?
4. ¿Cuándo prefieres un tipo resultado (`Optional`, `Either`, un `record` de resultado) a una excepción?
5. ¿Cómo evitas registrar la misma excepción cinco veces en cinco capas?
6. ¿Qué información debe llevar un error para poder diagnosticarlo en producción sin acceso al servidor?

## Cuándo no usar excepciones

Para resultados esperados (búsquedas sin resultado, validaciones de formularios con varios errores a la vez), o en bucles de alto rendimiento donde crear la traza de la pila es medible.

Siguiente: [08 — Código testeable](../08-codigo-testeable/).
