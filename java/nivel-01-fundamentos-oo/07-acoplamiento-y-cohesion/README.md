# 07 — Acoplamiento y cohesión

## Paso 1 — Explicación para principiantes

**Cohesión.** En una cocina bien organizada, los cuchillos están con los cuchillos y las especias con las especias. En un cajón de sastre hay pilas, tijeras, un cargador y una llave que nadie sabe qué abre. Los dos guardan cosas; solo en uno encuentras lo que buscas.

**Acoplamiento.** Un televisor se conecta a la pared con un enchufe: lo desconectas y conectas otro aparato. Si en vez de enchufe estuviera soldado al cableado de la casa, cambiar el televisor implicaría romper la pared.

Buen diseño: cosas relacionadas juntas (alta cohesión) y conectadas con enchufes, no con soldadura (bajo acoplamiento).

## Paso 2 — Explicación técnica

**Cohesión:** grado en que los elementos de un módulo pertenecen juntos.

| Nivel | Qué une a los elementos | Ejemplo |
|---|---|---|
| Coincidental (la peor) | Nada | `Utilidades`, `Helper`, `Comun` |
| Lógica | Se parecen por categoría | `Validadores` con validaciones de módulos sin relación |
| Temporal | Se ejecutan en el mismo momento | `Inicializador` que arranca cosas inconexas |
| Funcional (la mejor) | Contribuyen a una sola tarea bien definida | `CalculadoraImpuestos` |

**Acoplamiento:** grado en que un módulo depende de otro. Lo que importa es **a qué** te acoplas:

| Te acoplas a… | Gravedad | Señal |
|---|---|---|
| Datos internos de otra clase | Alta | Campos públicos, cadenas de getters |
| Estado global | Alta | `static` mutable, singletons con estado |
| Una clase concreta | Media | `new` de un colaborador dentro de la lógica |
| El orden en que hay que llamar las cosas | Media | "primero `init()`, luego `cargar()`" |
| Un contrato estable | Baja | Dependes de una interfaz pequeña |

El acoplamiento cero no existe: un programa cuyas partes no se conocen no hace nada. El objetivo es acoplarse a lo estable y a lo mínimo.

### Síntomas observables

- **Cirugía de escopeta:** un cambio pequeño obliga a tocar muchos archivos. Cohesión baja: lo que cambia junto está separado.
- **Cambio divergente:** una misma clase cambia por motivos sin relación. Cohesión baja: lo que cambia separado está junto.
- **Efecto dominó:** tocar una clase rompe otras. Acoplamiento alto.
- **Trenes de llamadas:** `a.getB().getC().getD().hacer()`. Viola la Ley de Deméter.
- **No se puede probar una clase sin montar media aplicación.**

## Paso 3 — Código que funciona, pero está mal diseñado

<!-- file: ejemplos/antes/Main.java -->
```java
public class Main {

    public static void main(String[] args) {
        Cliente cliente = new Cliente();
        cliente.nombre = "Ana";
        cliente.direccion = new Direccion();
        cliente.direccion.ciudad = new Ciudad();
        cliente.direccion.ciudad.nombre = "Tunja";
        cliente.direccion.ciudad.zona = "CENTRO";

        Pedido pedido = new Pedido();
        pedido.cliente = cliente;
        pedido.valor = 80_000;

        Configuracion.ivaPorcentaje = 19;
        System.out.println("Envío: " + Utilidades.costoEnvio(pedido));
        System.out.println("IVA: " + Utilidades.iva(pedido.valor));
        System.out.println(Utilidades.mayusculas(cliente.nombre));
    }
}

class Ciudad { String nombre; String zona; }
class Direccion { Ciudad ciudad; }
class Cliente { String nombre; Direccion direccion; }
class Pedido { Cliente cliente; long valor; }

class Configuracion {
    static int ivaPorcentaje;
}

class Utilidades {

    static long costoEnvio(Pedido pedido) {
        String zona = pedido.cliente.direccion.ciudad.zona;
        if (zona.equals("CENTRO")) {
            return 5_000;
        }
        return 12_000;
    }

    static long iva(long valor) {
        return valor * Configuracion.ivaPorcentaje / 100;
    }

    static String mayusculas(String texto) {
        return texto.toUpperCase();
    }
}
```

## Paso 4 — Análisis

**Cohesión.** `Utilidades` junta el costo de envío, el IVA y un formato de texto. No tienen nada en común salvo que no se supo dónde ponerlos. Es cohesión coincidental. Estas clases solo crecen.

**Acoplamiento a datos internos.** `costoEnvio` recorre `pedido.cliente.direccion.ciudad.zona`. Conoce la estructura de cuatro clases. Si la zona pasa a depender del barrio y no de la ciudad, se rompe un método de `Utilidades` que nadie relaciona con direcciones.

**Acoplamiento a estado global.** `iva` depende de que alguien, antes, haya asignado `Configuracion.ivaPorcentaje`. Si no, calcula con cero y no avisa. El resultado de un método depende de algo que no está en sus parámetros.

**Acoplamiento temporal.** Hay un orden obligatorio (configurar y luego calcular) que no está escrito en ningún lado.

## Paso 5 — Código mejorado

<!-- file: ejemplos/despues/Main.java -->
```java
public class Main {

    public static void main(String[] args) {
        Cliente cliente = new Cliente("Ana", new Direccion(new Ciudad("Tunja", Zona.CENTRO)));
        Pedido pedido = new Pedido(cliente, 80_000);

        TarifaEnvio tarifa = new TarifaEnvio();
        CalculadoraIva iva = new CalculadoraIva(19);

        System.out.println("Envío: " + tarifa.costo(pedido.zonaDeEntrega()));
        System.out.println("IVA: " + iva.calcular(pedido.valor()));
        System.out.println(cliente.nombre().toUpperCase());
    }
}

enum Zona { CENTRO, PERIFERIA }

class Ciudad {
    private final String nombre;
    private final Zona zona;
    Ciudad(String nombre, Zona zona) { this.nombre = nombre; this.zona = zona; }
    String nombre() { return nombre; }
    Zona zona() { return zona; }
}

class Direccion {
    private final Ciudad ciudad;
    Direccion(Ciudad ciudad) { this.ciudad = ciudad; }
    Zona zona() { return ciudad.zona(); }
}

class Cliente {
    private final String nombre;
    private final Direccion direccion;
    Cliente(String nombre, Direccion direccion) { this.nombre = nombre; this.direccion = direccion; }
    String nombre() { return nombre; }
    Zona zonaDeEntrega() { return direccion.zona(); }
}

class Pedido {
    private final Cliente cliente;
    private final long valor;
    Pedido(Cliente cliente, long valor) { this.cliente = cliente; this.valor = valor; }
    long valor() { return valor; }
    Zona zonaDeEntrega() { return cliente.zonaDeEntrega(); }
}

/** Solo sabe de tarifas. No sabe qué es un pedido ni un cliente. */
class TarifaEnvio {
    long costo(Zona zona) {
        return zona == Zona.CENTRO ? 5_000 : 12_000;
    }
}

/** Lo que necesita para trabajar lo recibe al crearse. No hay orden oculto. */
class CalculadoraIva {
    private final int porcentaje;

    CalculadoraIva(int porcentaje) {
        if (porcentaje < 0 || porcentaje > 100) {
            throw new IllegalArgumentException("Porcentaje de IVA inválido");
        }
        this.porcentaje = porcentaje;
    }

    long calcular(long valor) {
        return valor * porcentaje / 100;
    }
}
```

Qué cambió:

1. **Se disolvió `Utilidades`.** Cada función fue a vivir con el concepto al que pertenece. `mayusculas` desapareció: ya existía en `String`.
2. **El tren de llamadas se reemplazó por una pregunta:** `pedido.zonaDeEntrega()`. Si la zona pasa a depender del barrio, cambia `Direccion` y nadie más.
3. **`TarifaEnvio` recibe una `Zona`,** el dato mínimo. Antes recibía un pedido entero para usar un solo campo.
4. **El estado global se convirtió en un parámetro del constructor.** Es imposible usar `CalculadoraIva` sin porcentaje.

Costo: métodos de delegación (`zonaDeEntrega` en tres clases). Si se abusa, todas las clases terminan con decenas de métodos que solo reenvían. La Ley de Deméter es una guía, no una ley: en objetos de datos puros, encadenar getters es aceptable.

## Paso 6 — Taller práctico

Toma un proyecto real en el que hayas trabajado (o uno de código abierto que conozcas) y haz una auditoría:

1. Encuentra una clase con cohesión baja. Indica qué grupos de métodos no tienen relación entre sí y cómo la dividirías.
2. Encuentra un tren de llamadas de tres o más niveles. Propón el método que lo reemplazaría y en qué clase iría.
3. Encuentra una dependencia de estado global (un `static` mutable, un singleton con estado, una variable de entorno leída en mitad de la lógica). Describe un error que podría causar.
4. Encuentra el archivo que más veces aparece en el historial de commits (`git log --format= --name-only | sort | uniq -c | sort -rn | head`). ¿Es por alta cohesión de una clase importante o por baja cohesión? Argumenta.
5. Con código propio, escribe un ejemplo mínimo de acoplamiento temporal y corrígelo de modo que el orden incorrecto no compile.

No copies código confidencial en la entrega: describe y anonimiza.

Entrega: rama `taller/n1-07-acoplamiento-cohesion`, solo `ANALISIS.md` y el código del punto 5.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## Paso 7 — Revisión técnica

- ¿Los problemas identificados son de acoplamiento o de cohesión? ¿Se distinguen bien?
- ¿Las soluciones propuestas reducen el problema o lo mueven de lugar?
- ¿Se reconoce el costo de cada cambio propuesto?

## Paso 8 — Aplicación profesional

- **La inyección por constructor hace visible el acoplamiento.** Un servicio con diez parámetros en el constructor está diciendo que hace demasiado. Con `@Autowired` en campos, ese síntoma queda oculto.
- **Paquetes por funcionalidad frente a paquetes por capa.** `controller/`, `service/`, `repository/` agrupa por parecido técnico (cohesión lógica). `pedidos/`, `clientes/`, `facturacion/` agrupa por lo que cambia junto (cohesión funcional). Lo segundo escala mejor.
- **Dependencias circulares entre beans** (`A` necesita `B` y `B` necesita `A`) son acoplamiento alto que Spring Boot rechaza por defecto al arrancar. La solución no es `@Lazy`: es encontrar la responsabilidad que está en el lugar equivocado.
- **`@Value` leído en mitad de la lógica** o `System.getenv` dentro de un método es estado global. Centralízalo en clases de configuración (`@ConfigurationProperties`) e inyéctalas.
- **Microservicios que se llaman en cadena** para completar una operación son trenes de llamadas a escala de red.

## Paso 9 — Evaluación senior

1. ¿Puede existir alta cohesión con alto acoplamiento? ¿Y baja cohesión con bajo acoplamiento? Da un ejemplo de cada caso.
2. ¿Cómo medirías el acoplamiento de un módulo en un proyecto que no conoces, en una hora?
3. Dos servicios comparten una clase `Utils` de fechas. ¿Es un problema? ¿De qué depende?
4. ¿Por qué eliminar duplicación puede aumentar el acoplamiento? Da un caso en el que preferirías duplicar.
5. ¿Qué acoplamiento introduce compartir una misma base de datos entre dos aplicaciones?

## Comprobación antes de avanzar

- Definir cohesión y acoplamiento sin usar ejemplos de esta lección.
- Reconocer cirugía de escopeta y cambio divergente.
- Explicar por qué el estado global es acoplamiento.
- Explicar la Ley de Deméter y cuándo no aplicarla.

Siguiente: [08 — Código funcional frente a código mantenible](../08-funcional-vs-mantenible/).
