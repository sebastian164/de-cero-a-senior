# 05 — Abstracción, interfaces y clases abstractas

## Paso 1 — Explicación para principiantes

La carta de un restaurante dice qué puedes pedir. No dice cómo se cocina, quién lo prepara ni de qué proveedor es el arroz. Mientras el plato llegue como lo promete la carta, la cocina puede cambiar de cocinero o de receta sin que tú te enteres.

La carta es una **abstracción**: muestra el qué y oculta el cómo. Y es también un **contrato**: lo que aparece en ella, el restaurante se compromete a servirlo.

## Paso 2 — Explicación técnica

**Abstraer** es quedarse con lo esencial de algo para un propósito y descartar los detalles. En código, se concreta separando **qué hace** un componente (su contrato) de **cómo lo hace** (su implementación).

Java ofrece dos mecanismos:

| | Interfaz | Clase abstracta |
|---|---|---|
| Qué expresa | Un contrato: "sabe hacer esto" | Una base común: "es un tipo de esto, parcialmente hecho" |
| Estado (atributos de instancia) | No | Sí |
| Constructores | No | Sí |
| Métodos con implementación | Solo `default` y `static` | Sí |
| Cuántas se pueden usar | Varias (`implements A, B`) | Una (`extends`) |
| Acoplamiento que genera | Bajo: solo al contrato | Alto: es herencia |

### Cómo elegir

- ¿Solo necesitas un contrato? **Interfaz.** Es la opción por defecto.
- ¿Varias implementaciones comparten estado y pasos, y la relación "es un" es real? **Clase abstracta**, normalmente implementando una interfaz.
- Ante la duda, interfaz: no consume la única herencia disponible.

### Tres advertencias

**Una abstracción buena es más pequeña que lo que oculta.** Si la interfaz expone todos los detalles de una implementación, no abstrae nada.

**Abstracción con fugas.** Si para usar `Almacenamiento` hay que saber que por debajo es un archivo (rutas, permisos), el detalle se escapó.

**Una interfaz no es gratis.** Es un archivo más y un salto más al leer. Crearla "por si acaso" para una clase que solo tendrá una implementación es un costo sin beneficio.

## Paso 3 — Código que funciona, pero está mal diseñado

<!-- file: ejemplos/antes/Main.java -->
```java
import java.util.List;

public class Main {

    public static void main(String[] args) {
        List<String[]> ventas = List.of(
                new String[] {"Teclado", "120000"},
                new String[] {"Mouse", "60000"});

        ExportadorCsv csv = new ExportadorCsv();
        System.out.println(csv.exportarCsv(ventas));

        ExportadorTexto texto = new ExportadorTexto();
        System.out.println(texto.generarTextoPlano(ventas));
    }
}

class ExportadorCsv {

    String exportarCsv(List<String[]> filas) {
        if (filas.isEmpty()) {
            throw new IllegalArgumentException("No hay datos para exportar");
        }
        StringBuilder salida = new StringBuilder();
        salida.append("producto;valor\n");
        long total = 0;
        for (String[] fila : filas) {
            salida.append(fila[0]).append(";").append(fila[1]).append("\n");
            total += Long.parseLong(fila[1]);
        }
        salida.append("TOTAL;").append(total).append("\n");
        return salida.toString();
    }
}

class ExportadorTexto {

    String generarTextoPlano(List<String[]> filas) {
        if (filas.isEmpty()) {
            throw new IllegalArgumentException("No hay datos para exportar");
        }
        StringBuilder salida = new StringBuilder();
        salida.append("REPORTE DE VENTAS\n");
        long total = 0;
        for (String[] fila : filas) {
            salida.append("- ").append(fila[0]).append(": $").append(fila[1]).append("\n");
            total += Long.parseLong(fila[1]);
        }
        salida.append("Total: $").append(total).append("\n");
        return salida.toString();
    }
}
```

## Paso 4 — Análisis

- **No hay contrato común.** Los métodos se llaman distinto. Quien usa los exportadores debe conocer cada clase y cada nombre; no puede tratarlos como "un exportador".
- **El esqueleto está duplicado:** validar, encabezado, recorrer filas sumando, total. Solo cambia el formato de cada parte. Un error en la suma hay que corregirlo dos veces.
- **Un tercer formato** se hará copiando y pegando, con el riesgo de que alguien olvide la validación.

## Paso 5 — Código mejorado

<!-- file: ejemplos/despues/Main.java -->
```java
import java.util.List;

public class Main {

    public static void main(String[] args) {
        List<String[]> ventas = List.of(
                new String[] {"Teclado", "120000"},
                new String[] {"Mouse", "60000"});

        List<Exportador> exportadores = List.of(new ExportadorCsv(), new ExportadorTexto());
        for (Exportador exportador : exportadores) {
            System.out.println(exportador.exportar(ventas));
        }
    }
}

/** El contrato: lo único que el resto del sistema necesita conocer. */
interface Exportador {
    String exportar(List<String[]> filas);
}

/** La base común: los pasos que todos comparten y los huecos que cada uno llena. */
abstract class ExportadorBase implements Exportador {

    @Override
    public final String exportar(List<String[]> filas) {
        if (filas.isEmpty()) {
            throw new IllegalArgumentException("No hay datos para exportar");
        }
        StringBuilder salida = new StringBuilder(encabezado());
        long total = 0;
        for (String[] fila : filas) {
            salida.append(linea(fila[0], fila[1]));
            total += Long.parseLong(fila[1]);
        }
        return salida.append(pie(total)).toString();
    }

    protected abstract String encabezado();

    protected abstract String linea(String producto, String valor);

    protected abstract String pie(long total);
}

class ExportadorCsv extends ExportadorBase {

    @Override
    protected String encabezado() {
        return "producto;valor\n";
    }

    @Override
    protected String linea(String producto, String valor) {
        return producto + ";" + valor + "\n";
    }

    @Override
    protected String pie(long total) {
        return "TOTAL;" + total + "\n";
    }
}

class ExportadorTexto extends ExportadorBase {

    @Override
    protected String encabezado() {
        return "REPORTE DE VENTAS\n";
    }

    @Override
    protected String linea(String producto, String valor) {
        return "- " + producto + ": $" + valor + "\n";
    }

    @Override
    protected String pie(long total) {
        return "Total: $" + total + "\n";
    }
}
```

Dos decisiones separadas:

1. **La interfaz `Exportador`** da el contrato. El código cliente depende solo de ella. Un exportador futuro que no comparta nada con los actuales (por ejemplo, uno que llame a un servicio externo) puede implementarla directamente.
2. **La clase abstracta `ExportadorBase`** elimina la duplicación entre los que sí comparten el esqueleto. `exportar` es `final`: las subclases llenan huecos, no cambian el algoritmo.

Este esquema (algoritmo fijo en la base, pasos variables en las subclases) es el patrón Template Method, que se estudia en el Nivel 3.

## Paso 6 — Taller práctico

Diseña los medios de pago de una tienda:

- Tarjeta de crédito y tarjeta débito: ambas validan el número de tarjeta con las mismas reglas (16 dígitos) y registran los últimos cuatro dígitos. La de crédito cobra 2 % de comisión; la débito, nada.
- Efectivo: no tiene datos que validar; calcula el cambio a devolver.
- Transferencia: valida que el banco esté en una lista de bancos permitidos.

Todos deben poder `pagar(monto)` y devolver un comprobante con el valor cobrado y una descripción.

1. Antes de programar, dibuja o describe: qué será interfaz, qué será clase abstracta y qué será clase concreta. Justifica cada decisión.
2. Impleméntalo.
3. Escribe un método `cobrar(MedioDePago medio, long monto)` que funcione con todos sin conocerlos.
4. ¿Pusiste `calcularCambio` en el contrato común? Explica por qué sí o por qué no y qué problema aparece con la otra opción.
5. Agrega un medio nuevo (billetera digital) y cuenta cuántas clases existentes modificaste.

Entrega: rama `taller/n1-05-abstraccion`.

## Paso 7 — Revisión técnica

- ¿El contrato tiene métodos que alguna implementación no puede cumplir con sentido?
- ¿La clase abstracta existe porque hay algo real que compartir?
- ¿El código cliente menciona alguna clase concreta?
- ¿Algún detalle de implementación se filtra por la interfaz?

## Paso 8 — Aplicación profesional

- **Spring Data:** declaras `interface PedidoRepository extends JpaRepository<Pedido, Long>` y nunca escribes la implementación. Tu servicio depende de un contrato; el framework fabrica el objeto.
- **Clases abstractas del framework:** `OncePerRequestFilter` fija el algoritmo (ejecutar una vez por petición) y te deja implementar `doFilterInternal`. Es el mismo diseño que `ExportadorBase`.
- **Puertos en arquitectura hexagonal** (Nivel 5): son interfaces que el dominio define y la infraestructura implementa.
- **El par `UsuarioService` / `UsuarioServiceImpl`** con una sola implementación para siempre es una convención heredada, no una necesidad. Spring puede inyectar clases concretas. La interfaz se justifica cuando hay varias implementaciones, cuando marca una frontera entre módulos o cuando se necesita sustituir la implementación.

## Paso 9 — Evaluación senior

1. Desde Java 8 las interfaces tienen métodos `default`. ¿Qué diferencia real queda frente a una clase abstracta?
2. ¿Cuándo una interfaz con una sola implementación está justificada?
3. Describe una abstracción con fugas que hayas sufrido. ¿Qué detalle se escapaba?
4. ¿Qué es mejor: una interfaz de diez métodos o cinco de dos? ¿De qué depende?
5. ¿Qué riesgo tiene agregar un método a una interfaz pública ya publicada y cómo lo mitigas?

## Comprobación antes de avanzar

- Qué es abstraer y qué es un contrato.
- Diferencias entre interfaz y clase abstracta y criterio para elegir.
- Por qué el cliente debe depender del contrato.
- Cuándo una interfaz sobra.

Siguiente: [06 — Composición frente a herencia](../06-composicion-vs-herencia/).
