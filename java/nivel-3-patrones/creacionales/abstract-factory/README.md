# Abstract Factory

Familia: creacional. Frecuencia en Java empresarial: media.

## El problema

Hay que crear **familias** de objetos relacionados que deben usarse juntos, y existen varias familias intercambiables. Si el código crea cada objeto por separado, puede mezclar piezas de familias distintas (el conector de un proveedor con el formateador de otro), y cambiar de familia obliga a tocar cada punto de creación.

## Cómo reconocer que lo necesitas

- Varios objetos "van en combo" y mezclar combos es un error.
- El mismo `if (proveedor == X)` decide la creación de varios objetos distintos.
- Quieres cambiar el combo completo con una sola decisión (por país, por proveedor, por ambiente).

## Antes

```java
Cobrador cobrador = pais.equals("CO") ? new CobradorPse() : new CobradorSpei();
Facturador facturador = pais.equals("CO") ? new FacturadorDian() : new FacturadorSat();
// nada impide combinar CobradorPse con FacturadorSat
```

## Después

<!-- file: ejemplo/Main.java -->
```java
public class Main {

    public static void main(String[] args) {
        procesarVenta(new FabricaColombia(), 100_000);
        procesarVenta(new FabricaMexico(), 100_000);
    }

    /** El cliente trabaja con la familia completa sin saber cuál es. */
    static void procesarVenta(FabricaPais fabrica, long valor) {
        Cobrador cobrador = fabrica.crearCobrador();
        Facturador facturador = fabrica.crearFacturador();
        System.out.println(cobrador.cobrar(valor) + " | " + facturador.facturar(valor));
    }
}

interface Cobrador { String cobrar(long valor); }
interface Facturador { String facturar(long valor); }

/** La fábrica abstracta: un método de creación por cada miembro de la familia. */
interface FabricaPais {
    Cobrador crearCobrador();
    Facturador crearFacturador();
}

class FabricaColombia implements FabricaPais {
    @Override public Cobrador crearCobrador() { return valor -> "PSE cobra $" + valor + " COP"; }
    @Override public Facturador crearFacturador() { return valor -> "Factura DIAN con IVA 19%"; }
}

class FabricaMexico implements FabricaPais {
    @Override public Cobrador crearCobrador() { return valor -> "SPEI cobra $" + valor + " MXN"; }
    @Override public Facturador crearFacturador() { return valor -> "CFDI SAT con IVA 16%"; }
}
```

La coherencia de la familia la garantiza la fábrica: es imposible combinar piezas de dos países.

## Alternativas

- **Factory Method o fábrica simple**, si solo hay un tipo de producto.
- **Inyección de dependencias con perfiles o configuración condicional**: el contenedor entrega la familia correcta.
- **Un objeto de configuración** que agrupe las estrategias, si no hace falta crear instancias nuevas cada vez.

## En Spring

- Clases `@Configuration` con `@Profile` o `@ConditionalOnProperty` que declaran un conjunto coherente de beans: es la forma idiomática.
- La autoconfiguración de Spring Boot elige familias completas (por ejemplo, el servidor embebido y sus componentes) según lo que haya en el classpath.
- En el JDK: `DocumentBuilderFactory`, `javax.sql.DataSource` como origen de conexiones y sentencias compatibles entre sí.

## Trade-offs

- A favor: garantiza coherencia entre productos; cambiar de familia es cambiar un objeto; el cliente no conoce clases concretas.
- En contra: agregar un **producto** nuevo obliga a modificar la interfaz y todas las fábricas; muchas interfaces y clases; excesivo si solo hay una familia.

## Errores comunes

- Usarlo con un solo producto (eso es Factory Method).
- Fábricas que además contienen lógica de negocio.
- Interfaces de fábrica que crecen sin control porque "todo se crea ahí".

## Cuándo no usarlo

Cuando no hay familias sino productos sueltos; cuando solo existe una familia y la segunda es hipotética; cuando el contenedor ya resuelve la selección.

## Taller

Una aplicación de reportes debe generar documentos con tres piezas coherentes: encabezado, tabla y pie. Hay dos familias: HTML y texto plano.

1. Implementa la fábrica abstracta y las dos familias.
2. Agrega una tercera familia (Markdown). ¿Qué clases existentes cambiaron?
3. Agrega un cuarto producto (gráfico). ¿Qué cambió ahora? Compara el costo de ambos tipos de cambio.
4. Reescribe la selección de familia como lo harías con perfiles de Spring (en papel).

Entrega: rama `taller/n3-abstract-factory`.
