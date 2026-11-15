# Template Method

Familia: comportamiento. Frecuencia en Java empresarial: alta.

## El problema

Varios procesos siguen los mismos pasos en el mismo orden y solo difieren en cómo se ejecutan algunos. Si cada proceso se escribe completo, el esqueleto se duplica; cuando hay que cambiar el orden o agregar un paso común, hay que hacerlo en todos y alguno se olvida.

## Cómo reconocer que lo necesitas

- Dos o más clases con métodos casi idénticos que difieren en unas pocas líneas.
- Un procedimiento con pasos obligatorios que nadie debe saltarse (abrir, procesar, cerrar).
- Quieres que quien extiende llene huecos, no que reescriba el flujo.

## Antes

```java
class ImportadorClientes {
    void importar(String archivo) {
        List<String> lineas = leer(archivo);
        for (String linea : lineas) { if (linea.isBlank()) continue; /* validar y guardar cliente */ }
        System.out.println("Importados: " + lineas.size());
    }
}
class ImportadorProductos {
    void importar(String archivo) {
        List<String> lineas = leer(archivo);
        for (String linea : lineas) { /* validar y guardar producto */ }   // olvidó saltar las vacías
        System.out.println("Importados: " + lineas.size());
    }
}
```

## Después

<!-- file: ejemplo/Main.java -->
```java
import java.util.List;

public class Main {

    public static void main(String[] args) {
        List<String> lineas = List.of("Ana;ana@correo.example", "", "sin-correo", "Luis;luis@correo.example");
        new ImportadorClientes().importar(lineas);
        new ImportadorProductos().importar(List.of("Teclado;120000", "Mouse;-5"));
    }
}

abstract class Importador {

    /** El método plantilla: fija los pasos y su orden. Es final a propósito. */
    final void importar(List<String> lineas) {
        int importados = 0;
        int rechazados = 0;
        for (String linea : lineas) {
            if (linea.isBlank()) {
                continue;
            }
            String[] campos = linea.split(";");
            if (esValida(campos)) {
                guardar(campos);
                importados++;
            } else {
                rechazados++;
            }
        }
        alTerminar(importados, rechazados);
    }

    /** Pasos obligatorios: cada subclase debe definirlos. */
    protected abstract boolean esValida(String[] campos);

    protected abstract void guardar(String[] campos);

    /** Gancho (hook): paso opcional con comportamiento por defecto. */
    protected void alTerminar(int importados, int rechazados) {
        System.out.println(getClass().getSimpleName() + ": " + importados + " importados, "
                + rechazados + " rechazados");
    }
}

class ImportadorClientes extends Importador {

    @Override
    protected boolean esValida(String[] campos) {
        return campos.length == 2 && campos[1].contains("@");
    }

    @Override
    protected void guardar(String[] campos) {
        System.out.println("  cliente " + campos[0]);
    }
}

class ImportadorProductos extends Importador {

    @Override
    protected boolean esValida(String[] campos) {
        return campos.length == 2 && Long.parseLong(campos[1]) > 0;
    }

    @Override
    protected void guardar(String[] campos) {
        System.out.println("  producto " + campos[0]);
    }
}
```

La regla de saltar líneas vacías y el conteo ya no pueden olvidarse: están en un único lugar.

## Alternativas

- **Strategy** (o lambdas): pasar los pasos variables como objetos o funciones en lugar de heredar. Es la versión por composición y suele ser preferible: `importar(lineas, validador, guardador)`.
- **Funciones de orden superior**: `ejecutarEnTransaccion(() -> …)` es un Template Method sin herencia.
- **Duplicar**, si los procesos se parecen hoy pero van a divergir.

## En Spring

- `JdbcTemplate`, `RestTemplate`, `TransactionTemplate`: fijan el esqueleto (obtener recurso, ejecutar, manejar errores, liberar) y reciben el paso variable como *callback*. Es la variante moderna, por composición.
- `OncePerRequestFilter.doFilterInternal`, `AbstractHealthIndicator.doHealthCheck`, `AbstractRoutingDataSource.determineCurrentLookupKey`: variante clásica, por herencia.
- En el JDK: `AbstractList` (implementas `get` y `size`), `HttpServlet.service` que despacha a `doGet`/`doPost`.

## Ventajas, desventajas y trade-offs

| A favor | En contra |
|---|---|
| Elimina la duplicación del esqueleto | Usa herencia: acoplamiento fuerte a la clase base |
| Garantiza el orden y los pasos obligatorios | Cambiar la base afecta a todas las subclases |
| Las subclases son pequeñas | Difícil combinar variaciones de varios pasos |
| Invierte el control: la base llama a la subclase | El flujo se entiende solo leyendo la base y la subclase juntas |

## Errores comunes

- No declarar `final` el método plantilla, permitiendo que una subclase lo reescriba.
- Demasiados pasos abstractos: la subclase termina escribiendo casi todo.
- Subclases que necesitan llamar a `super.paso()` "sin olvidarlo": eso debe ser un gancho, no una convención.
- Usar herencia cuando un par de lambdas bastaba.

## Cuándo no usarlo

Cuando solo hay una variante; cuando las variaciones se combinan (herencia explota); cuando los procesos coinciden por casualidad y no por regla.

## Taller

Un sistema genera documentos: factura, cotización y nota crédito. Todos siguen: validar datos, calcular totales, numerar (consecutivo por tipo), renderizar a texto y registrar en auditoría. Solo cambian el cálculo de totales, el prefijo de numeración y el render.

1. Impleméntalo con Template Method. Decide qué pasos son abstractos, cuáles son ganchos y cuáles fijos.
2. Reescríbelo por composición, pasando los pasos variables como funciones u objetos. Compara las dos versiones: líneas, claridad, facilidad de prueba.
3. La nota crédito necesita un paso extra (referenciar la factura original). ¿Cómo lo acomoda cada versión?
4. ¿En cuál versión es más fácil probar el cálculo de totales de forma aislada?
5. Explica por qué `JdbcTemplate` eligió callbacks en lugar de obligarte a heredar.

Entrega: rama `taller/n3-template-method`.
