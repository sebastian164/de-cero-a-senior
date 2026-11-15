# 02 — El dominio: las reglas, solas

## Idea

El reglamento del ajedrez. Dice cómo se mueve cada pieza y cuándo hay jaque mate. No dice si el tablero es de madera o está en una pantalla, ni si los jugadores se mandan las jugadas por correo. Por eso el mismo reglamento sirve para un torneo y para una aplicación. El dominio de tu sistema es ese reglamento: lo que seguiría siendo verdad si mañana cambiaras toda la tecnología.

## Lo esencial

### Qué va en el dominio

| Va | No va |
|---|---|
| Qué es una cuenta y cuándo puede enviar dinero | Cómo llega la orden |
| El tope diario y cómo se reinicia | Dónde se guardan los saldos |
| Que no se transfiere a la misma cuenta | Qué formato tiene la respuesta |
| Los nombres que usa el negocio | Anotaciones de un marco de trabajo |

La prueba práctica: **el dominio solo importa clases del propio lenguaje**, y ninguna de entrada y salida. Si aparece un `import` de HTTP, de SQL, de JSON o de Spring, algo se coló.

### Piezas

| Pieza | Qué es | En el proyecto |
|---|---|---|
| **Objeto de valor** | Un dato con reglas, sin identidad, inmutable | `Dinero`, `Movimiento` |
| **Entidad** | Algo con identidad que cambia en el tiempo | `Cuenta` |
| **Servicio de dominio** | Una regla que involucra a más de una entidad y no pertenece a ninguna | `Transferencia` |

### Dónde viven las reglas

En el objeto al que pertenecen, no en un servicio que lo manipula desde fuera.

| Regla | Dónde está | Por qué ahí |
|---|---|---|
| Un valor no puede ser negativo | Constructor de `Dinero` | No puede existir un `Dinero` inválido |
| Saldo suficiente | `Cuenta.enviar` | Solo la cuenta conoce su saldo |
| Tope diario, y su reinicio al cambiar el día | `Cuenta.enviar` | Solo la cuenta sabe cuánto ha enviado hoy |
| No se transfiere a la misma cuenta | `Transferencia.ejecutar` | Involucra a dos cuentas: no es de ninguna |

Cuando las reglas están dentro de los objetos, el resto del código deja de validar: si tiene una `Cuenta` en la mano, su saldo no es negativo. Es encapsulamiento, del [Nivel 1](../../java/nivel-01-fundamentos-oo/), haciendo su trabajo.

## El código

Los archivos están en [`../proyecto/src/banco/dominio/`](../proyecto/src/banco/dominio/). Mira los `import`: solo `java.time` y `java.util`.

<!-- file: ../proyecto/src/banco/dominio/ReglaDeNegocioViolada.java -->
```java
package banco.dominio;

/** El negocio dijo que no. No es un fallo técnico: es una respuesta válida del sistema. */
public class ReglaDeNegocioViolada extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ReglaDeNegocioViolada(String mensaje) {
        super(mensaje);
    }
}
```

<!-- file: ../proyecto/src/banco/dominio/Dinero.java -->
```java
package banco.dominio;

/** Un valor en centavos. Inmutable y nunca negativo. */
public record Dinero(long centavos) {

    public static final Dinero CERO = new Dinero(0);

    public Dinero {
        if (centavos < 0) {
            throw new ReglaDeNegocioViolada("un valor no puede ser negativo");
        }
    }

    public static Dinero pesos(long pesos) {
        return new Dinero(pesos * 100);
    }

    public Dinero mas(Dinero otro) {
        return new Dinero(centavos + otro.centavos);
    }

    public Dinero menos(Dinero otro) {
        return new Dinero(centavos - otro.centavos);            // si quedara negativo, el constructor lo rechaza
    }

    public boolean esMayorQue(Dinero otro) {
        return centavos > otro.centavos;
    }

    public boolean esCero() {
        return centavos == 0;
    }
}
```

<!-- file: ../proyecto/src/banco/dominio/Cuenta.java -->
```java
package banco.dominio;

import java.time.LocalDate;

/** Una cuenta. Solo ella decide si puede enviar dinero. */
public final class Cuenta {

    public static final Dinero TOPE_DIARIO = Dinero.pesos(800_000);

    private final String id;
    private final String titular;
    private Dinero saldo;
    private Dinero enviadoHoy;
    private LocalDate diaDelTope;
    private final long version;                                  // para detectar cambios simultáneos (sesión 05)

    private Cuenta(String id, String titular, Dinero saldo, Dinero enviadoHoy, LocalDate diaDelTope, long version) {
        this.id = id;
        this.titular = titular;
        this.saldo = saldo;
        this.enviadoHoy = enviadoHoy;
        this.diaDelTope = diaDelTope;
        this.version = version;
    }

    public static Cuenta abrir(String id, String titular, Dinero saldoInicial, LocalDate hoy) {
        if (titular == null || titular.isBlank()) {
            throw new ReglaDeNegocioViolada("una cuenta necesita un titular");
        }
        return new Cuenta(id, titular, saldoInicial, Dinero.CERO, hoy, 0);
    }

    /** Reconstruye una cuenta que ya existía. Lo usan los adaptadores que la leen de donde esté guardada. */
    public static Cuenta existente(String id, String titular, Dinero saldo, Dinero enviadoHoy, LocalDate diaDelTope, long version) {
        return new Cuenta(id, titular, saldo, enviadoHoy, diaDelTope, version);
    }

    public void depositar(Dinero valor) {
        exigirPositivo(valor);
        saldo = saldo.mas(valor);
    }

    /** Sale dinero hacia otra cuenta. La fecha llega de fuera: la cuenta no sabe qué día es. */
    public void enviar(Dinero valor, LocalDate hoy) {
        exigirPositivo(valor);
        if (valor.esMayorQue(saldo)) {
            throw new ReglaDeNegocioViolada("saldo insuficiente en " + id);
        }
        Dinero enviado = hoy.equals(diaDelTope) ? enviadoHoy : Dinero.CERO;       // otro día: se empieza de cero
        if (enviado.mas(valor).esMayorQue(TOPE_DIARIO)) {
            throw new ReglaDeNegocioViolada("supera el tope diario de " + id);
        }
        saldo = saldo.menos(valor);
        enviadoHoy = enviado.mas(valor);
        diaDelTope = hoy;
    }

    private static void exigirPositivo(Dinero valor) {
        if (valor.esCero()) {
            throw new ReglaDeNegocioViolada("el valor debe ser mayor que cero");
        }
    }

    public String id() {
        return id;
    }

    public String titular() {
        return titular;
    }

    public Dinero saldo() {
        return saldo;
    }

    public Dinero enviadoHoy() {
        return enviadoHoy;
    }

    public LocalDate diaDelTope() {
        return diaDelTope;
    }

    public long version() {
        return version;
    }
}
```

<!-- file: ../proyecto/src/banco/dominio/Transferencia.java -->
```java
package banco.dominio;

import java.time.LocalDate;

/** Mover dinero entre dos cuentas. La regla no es de ninguna de las dos: por eso vive aquí. */
public final class Transferencia {

    private Transferencia() {
    }

    public static void ejecutar(Cuenta origen, Cuenta destino, Dinero valor, LocalDate hoy) {
        if (origen.id().equals(destino.id())) {
            throw new ReglaDeNegocioViolada("no se transfiere a la misma cuenta");
        }
        origen.enviar(valor, hoy);
        destino.depositar(valor);
    }
}
```

<!-- file: ../proyecto/src/banco/dominio/Movimiento.java -->
```java
package banco.dominio;

import java.time.Instant;

/** Un renglón del extracto. Una vez escrito, no cambia. */
public record Movimiento(String cuentaId, Tipo tipo, Dinero valor, Instant fecha, String referencia) {

    public enum Tipo {
        DEPOSITO,
        TRANSFERENCIA_ENVIADA,
        TRANSFERENCIA_RECIBIDA
    }
}
```

Compara con el "antes" de la sesión 01:

| En el "antes" | Aquí |
|---|---|
| `long` sueltos que hay que recordar que son centavos | `Dinero`, que no puede ser negativo |
| `if (saldos.get(origen) < valor)` en mitad de leer texto | `Cuenta.enviar` |
| El número `80_000_000L` sin nombre | `Cuenta.TOPE_DIARIO` |
| `LocalDate.now()` y un mapa global que se limpia | La fecha **llega como parámetro** y la cuenta decide si es otro día |
| Restar de un mapa y sumar en otro, con un archivo en medio | `Transferencia.ejecutar`: dos líneas, sin entrada ni salida entre ellas |

Dos cosas que este dominio **no** hace, a propósito: no guarda nada y no garantiza que el débito y el crédito queden registrados juntos. Eso último es una transacción, y no es responsabilidad del dominio: es de la sesión 04.

## Las pruebas

Sin servidor, sin archivos, sin marco de pruebas. Para que el proyecto corra solo con el JDK usa un comprobador mínimo; en un proyecto real sería JUnit.

<!-- file: ../proyecto/pruebas/banco/Comprobar.java -->
```java
package banco;

import java.util.Objects;

/** Un comprobador mínimo para no depender de nada. En un proyecto real, esto es JUnit. */
public final class Comprobar {

    private static int total = 0;
    private static int fallos = 0;

    private Comprobar() {
    }

    public static void igual(String nombre, Object esperado, Object obtenido) {
        total++;
        if (Objects.equals(esperado, obtenido)) {
            System.out.println("  ok    " + nombre);
        } else {
            fallos++;
            System.out.println("  FALLA " + nombre + ": esperaba " + esperado + " y obtuve " + obtenido);
        }
    }

    public static void lanza(String nombre, Class<? extends RuntimeException> tipo, Runnable accion) {
        total++;
        try {
            accion.run();
            fallos++;
            System.out.println("  FALLA " + nombre + ": no lanzó " + tipo.getSimpleName());
        } catch (RuntimeException error) {
            if (tipo.isInstance(error)) {
                System.out.println("  ok    " + nombre);
            } else {
                fallos++;
                System.out.println("  FALLA " + nombre + ": lanzó " + error);
            }
        }
    }

    public static int total() {
        return total;
    }

    public static int fallos() {
        return fallos;
    }
}
```

<!-- file: ../proyecto/pruebas/banco/PruebasDelDominio.java -->
```java
package banco;

import banco.dominio.Cuenta;
import banco.dominio.Dinero;
import banco.dominio.ReglaDeNegocioViolada;
import banco.dominio.Transferencia;
import java.time.LocalDate;

/** Las reglas, probadas sin nada alrededor. */
public final class PruebasDelDominio {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 15);
    private static final LocalDate MANANA = HOY.plusDays(1);

    private PruebasDelDominio() {
    }

    public static void ejecutar() {
        System.out.println("Dominio:");

        Comprobar.lanza("un valor no puede ser negativo", ReglaDeNegocioViolada.class, () -> new Dinero(-1));

        Cuenta ana = Cuenta.abrir("c-1", "Ana", Dinero.pesos(1_000_000), HOY);
        Cuenta luis = Cuenta.abrir("c-2", "Luis", Dinero.pesos(200_000), HOY);

        Transferencia.ejecutar(ana, luis, Dinero.pesos(300_000), HOY);
        Comprobar.igual("transferir resta del origen", Dinero.pesos(700_000), ana.saldo());
        Comprobar.igual("transferir suma al destino", Dinero.pesos(500_000), luis.saldo());
        Comprobar.igual("el dinero total no cambia", Dinero.pesos(1_200_000), ana.saldo().mas(luis.saldo()));

        Comprobar.lanza("no se transfiere más de lo que hay", ReglaDeNegocioViolada.class,
                () -> Transferencia.ejecutar(luis, ana, Dinero.pesos(500_001), HOY));
        Comprobar.lanza("no se transfiere a la misma cuenta", ReglaDeNegocioViolada.class,
                () -> Transferencia.ejecutar(ana, ana, Dinero.pesos(1_000), HOY));
        Comprobar.lanza("no se transfiere cero", ReglaDeNegocioViolada.class,
                () -> Transferencia.ejecutar(ana, luis, Dinero.CERO, HOY));

        Transferencia.ejecutar(ana, luis, Dinero.pesos(500_000), HOY);
        Comprobar.igual("se puede llegar exactamente al tope diario", Cuenta.TOPE_DIARIO, ana.enviadoHoy());
        Comprobar.lanza("un centavo por encima del tope se rechaza", ReglaDeNegocioViolada.class,
                () -> Transferencia.ejecutar(ana, luis, new Dinero(1), HOY));
        Comprobar.igual("una transferencia rechazada no mueve nada", Dinero.pesos(200_000), ana.saldo());

        Transferencia.ejecutar(ana, luis, Dinero.pesos(100_000), MANANA);
        Comprobar.igual("al día siguiente el tope empieza de cero", Dinero.pesos(100_000), ana.enviadoHoy());

        Comprobar.igual("transferir todo el saldo deja la cuenta en cero", Dinero.CERO, transferirTodo());
    }

    private static Dinero transferirTodo() {
        Cuenta origen = Cuenta.abrir("c-3", "Marta", Dinero.pesos(50_000), HOY);
        Cuenta destino = Cuenta.abrir("c-4", "Pedro", Dinero.CERO, HOY);
        Transferencia.ejecutar(origen, destino, Dinero.pesos(50_000), HOY);
        return origen.saldo();
    }
}
```

Doce pruebas que corren en milisegundos, incluidos los límites exactos y el cambio de día, que en el "antes" exigía esperar a mañana.

## Errores comunes

- **Dominio anémico:** clases con solo campos y métodos de acceso, y las reglas en un "servicio". Es el "antes" con más archivos.
- Anotaciones de persistencia o de serialización en las clases del dominio.
- Tipos primitivos para todo: un `long` que a veces son centavos y a veces pesos. Y nunca `double` para dinero.
- Métodos `set` públicos que permiten dejar el objeto en un estado inválido.
- Preguntar la hora, un identificador aleatorio o una configuración desde dentro del dominio.
- Excepciones técnicas (`SQLException`, `IOException`) atravesando el dominio.
- Sobrediseñar: un objeto de valor para cada texto que no tiene ninguna regla.

## Taller

**Objetivo:** extraer el dominio de los pedidos, con sus reglas dentro, y probarlo sin nada alrededor.

Sobre tu análisis de la sesión 01. Crea un proyecto con la organización de [`../proyecto/`](../proyecto/): una carpeta `src` y una `pruebas`.

1. Decide qué conceptos del negocio hay. Como mínimo: dinero, una línea de pedido, un pedido y su estado. ¿Cuáles son objetos de valor y cuáles entidades? Justifica.
2. Escribe el objeto de valor para el dinero. ¿Qué operaciones necesita de verdad un pedido?
3. Escribe la línea y el pedido. Las reglas (cantidad mínima, al menos una línea, cliente obligatorio, tope) deben vivir **dentro** de ellos: no debe poder existir un pedido inválido.
4. El pedido tiene estado: pendiente, confirmado, cancelado. ¿Qué transiciones permite el negocio? Solo el pedido cambia su propio estado.
5. Las existencias: ¿pertenecen al pedido? Decide a qué objeto pertenece la regla "no se vende más de lo que hay" y escríbela.
6. Ningún archivo del dominio importa nada que no sea `java.*`, y nada de entrada y salida. La fecha de creación llega de fuera.
7. Escribe las pruebas de cada regla, con los límites exactos: el pedido justo en el tope, un centavo por encima, la última unidad de existencias, una de más.
8. Escribe una prueba para el defecto que encontraste en la sesión 01 (existencias descontadas de un pedido que luego se rechaza). Con tu dominio, ¿todavía puede ocurrir? Si puede, el descuento está en el sitio equivocado.
9. Busca un método `set` o un campo modificable desde fuera. Por cada uno: ¿puede dejar un objeto inválido?

**Criterios de aceptación**

| Criterio | Cómo se comprueba |
|---|---|
| El dominio solo importa `java.*`, sin entrada y salida | Búsqueda de `import` |
| Las reglas están dentro de los objetos | No hay una clase "servicio" con los `if` |
| Los límites exactos están probados | Las pruebas |
| No puede construirse un pedido inválido | Una prueba por cada intento |

**Entrega:** rama `taller/hexagonal-02`, con el proyecto en `taller/solucion/` y `ANALISIS.md`.

## En el trabajo real

En un proyecto con Spring y JPA, la tentación es que la entidad de la base de datos **sea** el dominio. A veces es un compromiso razonable; la sesión 07 muestra las dos opciones. Saber escribir un dominio limpio te permite decidir cuánto de esa pureza quieres pagar.

## Preguntas senior

1. ¿Qué es un dominio anémico y por qué es un problema?
2. Diferencia entre objeto de valor, entidad y servicio de dominio.
3. ¿Por qué el dominio no debe preguntar la hora?
4. ¿Dónde pones una regla que involucra dos entidades?
5. ¿Por qué no se usa `double` para dinero?
