# 04 — Refactoring

## Idea sencilla

Reorganizar una cocina no cambia lo que se cocina: los platos salen iguales. Pero después cada cosa está a la mano y cocinar es más rápido. Refactorizar es reorganizar el código por dentro sin cambiar lo que hace por fuera.

## Explicación técnica

**Refactorizar** es cambiar la estructura interna del código sin modificar su comportamiento observable. Si cambia el comportamiento, es otra cosa: corregir un error o agregar una función.

### Las reglas del oficio

1. **Red de seguridad primero.** Sin pruebas que confirmen que el comportamiento no cambió, no es refactorizar: es editar con esperanza.
2. **Pasos pequeños.** Un cambio, ejecutar pruebas, confirmar. Si algo falla, se sabe exactamente qué lo rompió.
3. **Dos sombreros, nunca juntos.** O estás agregando comportamiento, o estás reestructurando. Mezclarlos en el mismo commit hace imposible la revisión.
4. **Usa las automatizaciones del IDE.** Renombrar, extraer método, mover, cambiar firma e *inline* son transformaciones seguras hechas por la herramienta.
5. **Commits frecuentes.** Cada paso verde es un punto de retorno.

### Cuándo refactorizar

- **Antes de agregar una función**, para que el cambio sea fácil. "Primero haz que el cambio sea fácil; luego haz el cambio fácil."
- **Al entender un código**, para dejar escrito lo que se entendió.
- **En la tercera repetición** (regla de tres).
- **No** como proyecto de meses separado de la entrega de valor.

### Catálogo mínimo

| Refactor | Cuándo |
|---|---|
| Renombrar | El nombre no dice la verdad |
| Extraer método / variable | Un fragmento merece nombre |
| *Inline* | La indirección no aporta |
| Mover método / campo | Está en la clase equivocada |
| Extraer clase | Una clase tiene dos responsabilidades |
| Introducir objeto parámetro | Parámetros que viajan juntos |
| Reemplazar condicional por polimorfismo | `switch` por tipo repetido |
| Reemplazar número mágico por constante | Valores sin nombre |
| Descomponer condicional | Condiciones largas |
| Reemplazar anidamiento por cláusulas de guarda | Flechas de `if` |
| Encapsular colección / campo | Estado expuesto |

### Código heredado sin pruebas

El círculo vicioso: no se puede refactorizar sin pruebas, y no se puede probar sin refactorizar. Se rompe así:

1. **Pruebas de caracterización:** no afirman lo que el código *debería* hacer, sino lo que *hace hoy*. Se ejecuta, se anota el resultado y se convierte en aserción.
2. **Costuras (*seams*):** puntos donde se puede cambiar el comportamiento sin editar el código, por ejemplo una dependencia que se pasa por parámetro.
3. **Cambios mínimos y mecánicos** (extraer método, introducir parámetro) hechos con el IDE para abrir esas costuras.

## Ejemplo paso a paso

Punto de partida:

<!-- file: ejemplos/antes/Main.java -->
```java
public class Main {

    public static void main(String[] args) {
        System.out.println(precio(3, 40_000, "VIP", true));
        System.out.println(precio(1, 40_000, "NORMAL", false));
    }

    static long precio(int cantidad, long unitario, String tipoCliente, boolean envioExpress) {
        long p = cantidad * unitario;
        if (tipoCliente.equals("VIP")) {
            p = p - p * 10 / 100;
        }
        if (p > 100_000) {
            p = p - 5_000;
        }
        if (envioExpress) {
            p = p + 15_000;
        } else {
            p = p + 8_000;
        }
        return p;
    }
}
```

**Paso 0 — Caracterizar.** Antes de tocar nada, se registra el comportamiento actual: `precio(3, 40000, "VIP", true)` da 118.000 y `precio(1, 40000, "NORMAL", false)` da 48.000. Esos valores son la red de seguridad.

**Paso 1 — Renombrar** `p` a `total`. Verificar.

**Paso 2 — Extraer variable** `subtotal`. Verificar.

**Paso 3 — Extraer métodos** para cada regla: descuento de cliente, descuento por volumen, costo de envío. Verificar después de cada uno.

**Paso 4 — Reemplazar números mágicos** por constantes. Verificar.

**Paso 5 — Reemplazar primitivos:** `String tipoCliente` por `enum`, `boolean envioExpress` por `enum`. Verificar.

Resultado:

<!-- file: ejemplos/despues/Main.java -->
```java
public class Main {

    public static void main(String[] args) {
        long vip = Precios.calcular(3, 40_000, TipoCliente.VIP, Envio.EXPRESS);
        long normal = Precios.calcular(1, 40_000, TipoCliente.NORMAL, Envio.ESTANDAR);
        System.out.println(vip);
        System.out.println(normal);

        // La red de seguridad: los valores caracterizados antes de empezar.
        comprobar(vip == 118_000, "caso VIP");
        comprobar(normal == 48_000, "caso normal");
    }

    static void comprobar(boolean condicion, String caso) {
        if (!condicion) {
            throw new AssertionError("Cambió el comportamiento en: " + caso);
        }
    }
}

enum TipoCliente {
    NORMAL(0), VIP(10);

    final int porcentajeDescuento;

    TipoCliente(int porcentajeDescuento) {
        this.porcentajeDescuento = porcentajeDescuento;
    }
}

enum Envio {
    ESTANDAR(8_000), EXPRESS(15_000);

    final long costo;

    Envio(long costo) {
        this.costo = costo;
    }
}

final class Precios {

    private static final long UMBRAL_DESCUENTO_VOLUMEN = 100_000;
    private static final long DESCUENTO_VOLUMEN = 5_000;

    private Precios() { }

    static long calcular(int cantidad, long unitario, TipoCliente cliente, Envio envio) {
        long subtotal = cantidad * unitario;
        long conDescuentoCliente = subtotal - descuentoCliente(subtotal, cliente);
        long conDescuentoVolumen = conDescuentoCliente - descuentoVolumen(conDescuentoCliente);
        return conDescuentoVolumen + envio.costo;
    }

    private static long descuentoCliente(long subtotal, TipoCliente cliente) {
        return subtotal * cliente.porcentajeDescuento / 100;
    }

    private static long descuentoVolumen(long valor) {
        return valor > UMBRAL_DESCUENTO_VOLUMEN ? DESCUENTO_VOLUMEN : 0;
    }
}
```

El refactor reveló una regla que estaba oculta: el descuento por volumen se evalúa **después** del descuento de cliente. Ahora se ve y se puede preguntar al negocio si es intencional.

## Taller

Usa el código de inicio del taller de la lección 02 (`liquidar`) o un método real de tu trabajo de más de 40 líneas.

1. Escribe pruebas de caracterización: al menos seis casos con sus resultados actuales.
2. Refactoriza en pasos pequeños. Registra cada paso como un commit separado con un mensaje que nombre el refactor aplicado.
3. Ningún commit debe cambiar comportamiento. Si encuentras un error, anótalo y corrígelo en un commit aparte al final.
4. Entrega el historial de commits como evidencia.

Se revisará: tamaño de los pasos, separación entre refactor y cambio de comportamiento, y que la red de seguridad exista antes del primer cambio. Rama `taller/n4-04-refactoring`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## Aplicación profesional

- **Pull requests separados:** uno de refactor puro (fácil de revisar: "no cambia comportamiento") y otro con la función nueva.
- **Estrangulamiento (*strangler fig*):** para módulos grandes, se construye lo nuevo al lado y se desvía el tráfico poco a poco, en lugar de reescribir de una vez.
- **Ramas por abstracción:** introducir una interfaz, tener dos implementaciones conviviendo y cambiar con un interruptor.
- **Negociar el tiempo:** el refactor no se pide como tarea aparte; forma parte de hacer bien la tarea que sí se pidió.

## Preguntas senior

1. ¿Por qué mezclar refactor y cambio funcional en un commit es un problema para quien revisa y para quien busca un error con `git bisect`?
2. ¿Cómo refactorizas una clase de 3.000 líneas sin pruebas, de la que depende la facturación?
3. ¿Reescribir desde cero o refactorizar? Da los criterios.
4. ¿Qué es una prueba de caracterización y en qué se diferencia de una prueba unitaria normal?
5. ¿Cómo justificas ante negocio el tiempo de refactorización sin usar la palabra "refactorizar"?

## Cuándo no refactorizar

Código que no se va a volver a tocar; código que se va a eliminar pronto; la víspera de una entrega crítica; cuando no existe ni se puede crear una red de seguridad y el riesgo es alto.

Siguiente: [05 — DRY, KISS y YAGNI](../05-dry-kiss-yagni/).
