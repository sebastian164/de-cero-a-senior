# LSP — Principio de sustitución de Liskov

Requisito: [OCP](../02-ocp/) y la lección 03 del Nivel 1 (herencia).

## 1. Explicación sencilla

Pides un taxi por una aplicación. No sabes qué conductor llegará, y no te importa: cualquiera que llegue debe poder llevarte a tu destino. Si un día llega un "conductor" que dice "yo no manejo de noche" o "solo voy hasta la esquina", el servicio dejó de ser confiable: ya no basta pedir un taxi, hay que preguntar quién viene.

El principio dice: si algo se presenta como un tipo, debe comportarse como ese tipo promete, de modo que quien lo usa no tenga que averiguar cuál versión le tocó.

## 2. Definición técnica

Barbara Liskov, 1987: si `S` es un subtipo de `T`, los objetos de tipo `T` pueden reemplazarse por objetos de tipo `S` sin alterar la corrección del programa.

El compilador verifica que las firmas coincidan. LSP exige además que coincida el **comportamiento**. Un subtipo cumple el contrato de su supertipo cuando:

| Regla | En palabras |
|---|---|
| No fortalece las precondiciones | No exige más que el supertipo para funcionar. |
| No debilita las postcondiciones | No entrega menos de lo que el supertipo promete. |
| Preserva las invariantes | Lo que siempre es cierto en el supertipo sigue siéndolo. |
| No lanza excepciones nuevas e inesperadas | Quien usa el supertipo no está preparado para ellas. |

**"Es un" se decide por comportamiento, no por vocabulario.** En matemáticas un cuadrado es un rectángulo. En código, si `Rectangulo` permite cambiar ancho y alto por separado, un `Cuadrado` que los cambia juntos rompe lo que el cliente espera. La taxonomía del mundo real no es una guía fiable para la herencia.

### Señales de violación

- Un método sobrescrito que lanza `UnsupportedOperationException` o queda vacío.
- Código cliente con `instanceof` para tratar distinto a un subtipo.
- Un subtipo que devuelve `null` donde el supertipo nunca lo hace.
- Documentación del tipo "no llamar este método si el objeto es de tipo X".

## 3. Código que viola el principio

<!-- file: ejemplos/antes/Main.java -->
```java
import java.util.List;

public class Main {

    public static void main(String[] args) {
        List<Cuenta> cuentas = List.of(
                new Cuenta("Ahorros de Ana", 500_000),
                new CuentaPlazoFijo("CDT de Ana", 2_000_000));

        cobrarCuotaDeManejo(cuentas);
    }

    /** Escrito contra Cuenta. Debería funcionar con cualquier Cuenta. */
    static void cobrarCuotaDeManejo(List<Cuenta> cuentas) {
        for (Cuenta cuenta : cuentas) {
            try {
                cuenta.retirar(10_000);
                System.out.println(cuenta.nombre() + ": saldo " + cuenta.saldo());
            } catch (UnsupportedOperationException e) {
                System.out.println(cuenta.nombre() + ": FALLO - " + e.getMessage());
            }
        }
    }
}

class Cuenta {

    private final String nombre;
    protected long saldo;

    Cuenta(String nombre, long saldo) {
        this.nombre = nombre;
        this.saldo = saldo;
    }

    void retirar(long monto) {
        if (monto > saldo) {
            throw new IllegalStateException("Fondos insuficientes");
        }
        saldo -= monto;
    }

    String nombre() { return nombre; }
    long saldo() { return saldo; }
}

/** "Un CDT es una cuenta", así que hereda. Pero de un CDT no se retira. */
class CuentaPlazoFijo extends Cuenta {

    CuentaPlazoFijo(String nombre, long saldo) {
        super(nombre, saldo);
    }

    @Override
    void retirar(long monto) {
        throw new UnsupportedOperationException("No se puede retirar de un plazo fijo");
    }
}
```

## 4. Por qué es un problema

- **`cobrarCuotaDeManejo` es correcto y aun así falla.** Fue escrito contra el contrato de `Cuenta`, que promete que se puede retirar si hay fondos. `CuentaPlazoFijo` rompe esa promesa.
- **El error aparece en ejecución, lejos de su causa.** El compilador lo acepta todo.
- **La "solución" habitual empeora las cosas:** agregar `if (cuenta instanceof CuentaPlazoFijo)` en el cliente. Ahora cada método que reciba cuentas debe conocer la excepción, y cada subtipo problemático nuevo obliga a modificarlos todos. Violar LSP termina violando OCP.
- **El `try/catch` del ejemplo ya es un síntoma:** el cliente se defiende de su propia abstracción.
- **La jerarquía miente.** Dice que un plazo fijo puede usarse donde se espere una cuenta, y no es cierto.

## 5. Refactorización paso a paso

**Paso 1 — Preguntarse qué promete realmente el supertipo.** `Cuenta` promete nombre, saldo y retiros.

**Paso 2 — Separar las promesas que no todos cumplen.** Tener saldo es común. Permitir retiros no.

**Paso 3 — El cliente pide exactamente la capacidad que necesita.**

<!-- file: ejemplos/despues/Main.java -->
```java
import java.util.List;

public class Main {

    public static void main(String[] args) {
        CuentaAhorros ahorros = new CuentaAhorros("Ahorros de Ana", 500_000);
        CuentaPlazoFijo cdt = new CuentaPlazoFijo("CDT de Ana", 2_000_000);

        cobrarCuotaDeManejo(List.of(ahorros));
        // cobrarCuotaDeManejo(List.of(cdt));  // no compila: un CDT no es Retirable

        imprimirSaldos(List.of(ahorros, cdt));
    }

    static void cobrarCuotaDeManejo(List<? extends Retirable> cuentas) {
        for (Retirable cuenta : cuentas) {
            cuenta.retirar(10_000);
        }
    }

    static void imprimirSaldos(List<? extends Cuenta> cuentas) {
        for (Cuenta cuenta : cuentas) {
            System.out.println(cuenta.nombre() + ": saldo " + cuenta.saldo());
        }
    }
}

/** Lo que TODA cuenta cumple. */
interface Cuenta {
    String nombre();
    long saldo();
}

/** Una capacidad adicional que solo algunas cuentas tienen. */
interface Retirable {
    void retirar(long monto);
}

class CuentaAhorros implements Cuenta, Retirable {

    private final String nombre;
    private long saldo;

    CuentaAhorros(String nombre, long saldo) {
        this.nombre = nombre;
        this.saldo = saldo;
    }

    @Override
    public void retirar(long monto) {
        if (monto > saldo) {
            throw new IllegalStateException("Fondos insuficientes");
        }
        saldo -= monto;
    }

    @Override public String nombre() { return nombre; }
    @Override public long saldo() { return saldo; }
}

class CuentaPlazoFijo implements Cuenta {

    private final String nombre;
    private final long saldo;

    CuentaPlazoFijo(String nombre, long saldo) {
        this.nombre = nombre;
        this.saldo = saldo;
    }

    @Override public String nombre() { return nombre; }
    @Override public long saldo() { return saldo; }
}
```

### Qué se ganó

- El error pasó de ejecución a compilación: no se puede pedir un retiro a un plazo fijo.
- Ningún cliente necesita `instanceof` ni `try/catch` defensivo.
- Cada tipo promete solo lo que cumple.

### Qué se pagó

- Ya no hay código compartido por herencia: `nombre` y `saldo` están repetidos. Si esa duplicación creciera, se resolvería con composición o con una clase base **que no incluya `retirar`**.
- Hay más tipos y los métodos deben declarar qué capacidad necesitan.

Observa que la solución fue partir un contrato en dos más pequeños. Eso anticipa el siguiente principio, ISP.

## 6. Ejemplo empresarial con Spring Boot

Fragmentos ilustrativos: no forman parte del código del repo. Se compilaron y probaron con Spring Boot 4.1.1 y Java 21; donde aparece `{ ... }` o `// ...` se omitió código que no aporta a la idea.

**Implementaciones a medias de una interfaz.**

```java
public interface AlmacenamientoArchivos {
    void guardar(String ruta, byte[] contenido);
    byte[] leer(String ruta);
    void eliminar(String ruta);
}

@Component
@Profile("auditoria")
class AlmacenamientoInmutable implements AlmacenamientoArchivos {
    public void eliminar(String ruta) {
        throw new UnsupportedOperationException("Los archivos de auditoría no se eliminan");
    }
    // ...
}
```

El servicio que hace limpieza de temporales funciona con un perfil y explota con otro.

**Subclases de servicios que cambian la semántica.** Una subclase que sobrescribe un método `@Transactional` y abre transacciones distintas, o que deja de publicar un evento que la clase base publicaba, rompe a quienes dependían de ese efecto.

**Dobles de prueba que no respetan el contrato.** Un repositorio falso en memoria que devuelve `null` donde el real lanza excepción hace que las pruebas pasen y producción falle. Un doble de prueba también es un subtipo y también debe cumplir LSP.

**El JDK tiene su propio caso famoso:** `List.of(...)` y `Collections.unmodifiableList(...)` devuelven objetos de tipo `List` cuyo `add` lanza `UnsupportedOperationException`. La interfaz documenta esas operaciones como "opcionales", una concesión de diseño que se sigue discutiendo. En tu código, no asumas que una `List` recibida es modificable.

## 7. Taller práctico

**Parte A — El clásico.** Implementa `Rectangulo` con `setAncho`, `setAlto` y `area()`, y `Cuadrado extends Rectangulo` que mantenga los lados iguales. Escribe un método `void duplicarAncho(Rectangulo r)` y una comprobación de que el área se duplicó. Demuestra con código que la comprobación falla con un `Cuadrado`. Propón dos diseños alternativos y explica cuál elegirías.

**Parte B — El caso de negocio.**

<!-- file: taller/inicio/Main.java -->
```java
import java.util.List;

public class Main {

    public static void main(String[] args) {
        List<Documento> documentos = List.of(
                new Documento("contrato.docx", "Texto del contrato"),
                new DocumentoFirmado("acta.pdf", "Texto del acta"));
        for (Documento documento : documentos) {
            documento.editar(documento.contenido() + " (revisado)");
            System.out.println(documento.nombre() + ": " + documento.contenido());
        }
    }
}

class Documento {
    private final String nombre;
    protected String contenido;

    Documento(String nombre, String contenido) {
        this.nombre = nombre;
        this.contenido = contenido;
    }

    void editar(String nuevoContenido) { this.contenido = nuevoContenido; }
    String nombre() { return nombre; }
    String contenido() { return contenido; }
}

class DocumentoFirmado extends Documento {

    DocumentoFirmado(String nombre, String contenido) {
        super(nombre, contenido);
    }

    @Override
    void editar(String nuevoContenido) {
        // Un documento firmado no se puede cambiar: se ignora en silencio.
    }
}
```

1. ¿Qué regla de LSP viola `DocumentoFirmado`? ¿Es más o menos grave que lanzar una excepción? Argumenta.
2. Rediseña para que editar un documento firmado sea un error de compilación.
3. Un documento editable puede firmarse y pasar a ser inmutable. ¿Cómo modelas esa transición sin herencia?
4. Busca en un proyecto tuyo un método sobrescrito vacío o que lance `UnsupportedOperationException`. Describe el caso y cómo lo corregirías.

Entrega: rama `taller/03-lsp`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## 8. Preguntas de entrevista senior

1. ¿Por qué LSP no se puede verificar solo con el sistema de tipos? ¿Qué herramientas ayudan?
2. Explica precondición, postcondición e invariante con un ejemplo propio y muestra una violación de cada una.
3. ¿Un método que no hace nada es una violación más peligrosa que uno que lanza excepción? ¿Por qué?
4. ¿Cómo escribirías una misma batería de pruebas que deba pasar para todas las implementaciones de una interfaz?
5. ¿Qué relación hay entre LSP y los dobles de prueba (mocks, fakes)?
6. `java.sql.Date` hereda de `java.util.Date`. Investiga por qué se considera un error de diseño.
7. ¿LSP aplica a microservicios? Piensa en dos versiones de una misma API.

## 9. Cuándo aplicar LSP produce complejidad innecesaria

LSP es el principio con menos excepciones: violarlo casi siempre es un defecto. El exceso está en cómo se reacciona.

**Partir jerarquías que nadie usa polimórficamente.** Si ningún código trata a los subtipos a través del supertipo, no hay sustitución que proteger. Antes de rediseñar, comprueba que el problema existe.

**Crear una interfaz por cada capacidad imaginable.** Separar `Retirable` tiene sentido porque existe una cuenta que no admite retiros. Separar `ConNombre` y `ConSaldo` sin un tipo que carezca de ellos es ruido.

**Contratos con operaciones opcionales bien documentadas.** A veces es la decisión pragmática, como en las colecciones de Java: la alternativa era decenas de interfaces. Si eliges ese camino, que sea una decisión explícita y visible, no un accidente.

**La alternativa más simple suele ser no heredar.** Muchas violaciones desaparecen al reemplazar la herencia por composición, sin jerarquías nuevas.

## Comprobación antes de avanzar

- Enunciar LSP con tus palabras y con un ejemplo que no sea de esta lección.
- Explicar por qué "es un" en el mundo real no garantiza "es un" en el código.
- Reconocer las cuatro señales de violación.
- Explicar cómo una violación de LSP provoca violaciones de OCP.

Siguiente: [ISP — Principio de segregación de interfaces](../04-isp/).
