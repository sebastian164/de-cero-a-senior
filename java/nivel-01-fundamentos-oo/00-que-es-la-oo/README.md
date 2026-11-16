# Clase 0 — ¿Qué es realmente la orientación a objetos?

La pregunta de esta clase: qué es la orientación a objetos, cómo se relaciona con SOLID y por qué importa en el trabajo profesional con Java y Spring Boot.

Es la clase de mapa. Los temas que aquí aparecen en un párrafo (encapsulamiento, herencia, polimorfismo, acoplamiento, cohesión) tienen después su propia lección en este nivel.

## Paso 1 — Explicación para principiantes

Piensa en un restaurante. Hay un mesero, un cocinero y un cajero.

- Cada uno **sabe cosas**: el mesero sabe qué mesas atiende, el cocinero sabe las recetas, el cajero sabe cuánto hay en la caja.
- Cada uno **sabe hacer cosas**: tomar un pedido, preparar un plato, cobrar.
- Tú no entras a la cocina a prender la estufa. Le **pides** algo al mesero y él se encarga de que ocurra.

Ahora imagina el restaurante sin esa organización: una sola libreta enorme con todos los datos y cualquier persona escribiendo en ella lo que quiera. Funciona mientras el restaurante es pequeño y nadie se equivoca. El día que alguien anota mal un pago, nadie sabe quién fue ni dónde se rompió.

La orientación a objetos es organizar un programa como el restaurante que sí funciona: piezas que guardan sus propios datos, saben qué hacer con ellos y colaboran pidiéndose cosas entre sí. Cada pieza es un **objeto**.

## Paso 2 — Explicación técnica

### Qué es un objeto

Un objeto combina tres cosas:

- **Estado**: los datos que guarda (sus atributos).
- **Comportamiento**: lo que sabe hacer (sus métodos).
- **Identidad**: dos cuentas con el mismo saldo siguen siendo dos cuentas distintas.

Una **clase** es la definición a partir de la cual se crean objetos. La clase `Cuenta` describe cómo es cualquier cuenta; la cuenta de Ana es un objeto, una instancia de esa clase.

Un programa orientado a objetos es un conjunto de objetos que colaboran **enviándose mensajes**: uno llama a un método de otro y le delega una parte del trabajo.

### Los cuatro pilares y para qué sirve cada uno

| Pilar | Qué es | Para qué sirve |
|---|---|---|
| Encapsulamiento | El objeto oculta su estado y solo permite cambiarlo a través de sus métodos. | Para que las reglas sobre esos datos vivan en un único lugar y no se puedan saltar. |
| Abstracción | Mostrar qué hace algo sin exponer cómo lo hace. | Para poder usar una pieza sin conocer sus detalles, y cambiar esos detalles sin romper a quien la usa. |
| Herencia | Una clase toma estado y comportamiento de otra y los extiende. | Para modelar relaciones "es un" y reutilizar comportamiento. Es el pilar del que más se abusa. |
| Polimorfismo | Un mismo mensaje produce comportamientos distintos según el objeto que lo recibe. | Para que quien envía el mensaje no necesite saber con qué tipo concreto está hablando. |

### La idea que casi nadie cuenta

Usar clases no es hacer orientación a objetos. Un programa puede tener cien clases y ser procedural: clases que solo guardan datos por un lado y funciones que manipulan esos datos por el otro.

Lo que la orientación a objetos aporta de verdad es **control sobre las dependencias**. Gracias al polimorfismo, un módulo puede usar a otro sin depender de su código concreto: depende de un contrato (una interfaz) y cualquier implementación que cumpla el contrato sirve. Eso permite cambiar o añadir piezas sin tocar las que ya existen.

### Los dos indicadores de un buen diseño

- **Cohesión**: qué tan relacionadas están entre sí las cosas que hay dentro de un módulo. Se busca **alta**: una clase que hace cosas que tienen que ver unas con otras.
- **Acoplamiento**: cuánto depende un módulo de los detalles de otro. Se busca **bajo**: poder cambiar una clase sin que se rompan diez.

Todo lo que viene después en este curso (SOLID, patrones, arquitectura) son técnicas para subir la cohesión y bajar el acoplamiento. Si recuerdas una sola cosa de esta clase, que sea esa.

## Paso 3 — Código que funciona, pero está mal diseñado

El código completo está en [`ejemplos/antes`](ejemplos/antes).

```java
public class Cuenta {
    public String titular;
    public String tipo;   // "AHORROS" o "CORRIENTE"
    public long saldo;    // en pesos
}
```

```java
static void retirar(Cuenta cuenta, long monto) {
    long comision;
    if (cuenta.tipo.equals("AHORROS")) {
        comision = 0;
    } else if (cuenta.tipo.equals("CORRIENTE")) {
        comision = monto * 4 / 1000;
    } else {
        throw new IllegalArgumentException("Tipo de cuenta desconocido: " + cuenta.tipo);
    }

    if (cuenta.saldo >= monto + comision) {
        cuenta.saldo -= monto + comision;
    } else {
        System.out.println("Fondos insuficientes");
    }
}
```

Compila, corre y hace el retiro. Tiene una clase. Y no es orientado a objetos.

## Paso 4 — Análisis

Antes de leer, intenta encontrar los problemas tú. Después compara.

**1. El estado no está protegido.** La regla "el saldo nunca es negativo" vive dentro de `retirar()`, no dentro de la cuenta. Cualquier línea del programa puede escribir `cuenta.saldo = -500_000` y nada lo impide. En términos técnicos: la clase no protege sus **invariantes**, las condiciones que siempre deberían cumplirse.

**2. Los datos y las reglas están separados.** `Cuenta` tiene los datos y `Main` tiene las reglas. Si mañana hay otra pantalla que también retira dinero, alguien copiará la validación o, peor, la olvidará. La cohesión es baja: lo que debería estar junto está repartido.

**3. Todos conocen los detalles de la cuenta.** Cualquier código que use `Cuenta` depende de que exista un campo `saldo` de tipo `long`. Si se cambia la forma de guardar el saldo, hay que revisar todo el programa. El acoplamiento es alto.

**4. El tipo es un texto y la lógica se bifurca con `if`.** Cada tipo nuevo de cuenta obliga a abrir `retirar()` y agregar una rama. Y no solo ahí: en cada método que se comporte distinto según el tipo. Un error de escritura como `"AHORRO"` no lo detecta el compilador; aparece en producción.

**5. El fallo es silencioso.** Si no hay fondos, el método imprime un mensaje y sigue. Quien lo llamó no se entera de que el retiro no ocurrió.

Ninguno de estos problemas se nota hoy. Se notan cuando el programa crece, y para entonces arreglarlo es caro. Esa es la diferencia entre código que funciona y código mantenible.

## Paso 5 — Código mejorado

El código completo está en [`ejemplos/despues`](ejemplos/despues). La refactorización tiene dos movimientos.

### Movimiento 1: la cuenta se hace dueña de su estado

El saldo pasa a ser privado y las operaciones se mudan a la clase.

```java
public class Cuenta {

    private final String titular;
    private long saldo;

    public void retirar(long monto) {
        validarMontoPositivo(monto);
        long total = monto + politicaComision.calcular(monto); // la comisión se explica en el movimiento 2
        if (total > saldo) {
            throw new IllegalStateException("Fondos insuficientes");
        }
        saldo -= total;
    }
}
```

Qué cambió:

- Ya no existe forma de dejar el saldo en negativo. La regla está en un único lugar y es imposible saltársela.
- El constructor rechaza cuentas inválidas. Un objeto `Cuenta` que existe es un objeto válido.
- El fallo dejó de ser silencioso: si no hay fondos, se lanza una excepción.
- Desde afuera ya no se le hacen cosas a la cuenta: se le pide que las haga. Este estilo se conoce como *Tell, Don't Ask*.

Esto es **encapsulamiento**, y su efecto es subir la cohesión.

### Movimiento 2: el `if` por tipo se reemplaza por polimorfismo

En lugar de un texto `"AHORROS"` y una cadena de `if`, la cuenta recibe un objeto que sabe calcular la comisión.

```java
public interface PoliticaComision {
    long calcular(long monto);
}

public class SinComision implements PoliticaComision {
    public long calcular(long monto) { return 0; }
}

public class ComisionPorMil implements PoliticaComision {
    private final long porMil;
    public long calcular(long monto) { return monto * porMil / 1000; }
}
```

```java
Cuenta ahorros = new Cuenta("Ana", 100_000, new SinComision());
Cuenta corriente = new Cuenta("Luis", 100_000, new ComisionPorMil(4));
```

Qué cambió:

- `Cuenta` no sabe cuántos tipos de comisión existen ni cómo se calculan. Solo envía el mensaje `calcular`.
- Una comisión nueva es una clase nueva. `Cuenta` no se toca.
- El error de escritura dejó de ser posible: no hay textos, hay tipos que el compilador verifica.

Esto es **abstracción** más **polimorfismo**, y su efecto es bajar el acoplamiento.

### Lo que este cambio cuesta

Hay tres archivos más. Para un programa de veinte líneas que nadie va a tocar, la primera versión era suficiente. El diseño no es gratis: se paga en indirección y se cobra en facilidad de cambio. Decidir cuándo vale la pena es precisamente el criterio que este curso busca entrenar.

## Cómo se relaciona con SOLID

La orientación a objetos da las **herramientas**. SOLID da **criterios para usarlas bien**. Son cinco principios que responden a la pregunta: ¿cómo reparto las responsabilidades y las dependencias entre mis clases para que el cambio sea barato?

| Principio | Pregunta que responde | Herramienta de la OO en la que se apoya |
|---|---|---|
| **S** — Responsabilidad única | ¿Qué debe ir junto en una clase? | Cohesión, encapsulamiento |
| **O** — Abierto/cerrado | ¿Cómo agrego comportamiento sin modificar lo que ya funciona? | Abstracción, polimorfismo |
| **L** — Sustitución de Liskov | ¿Cuándo es correcto heredar? | Herencia, polimorfismo |
| **I** — Segregación de interfaces | ¿Qué tan grande debe ser un contrato? | Abstracción, interfaces |
| **D** — Inversión de dependencias | ¿De qué debe depender mi código importante? | Polimorfismo, interfaces |

Fíjate en que el ejemplo de esta clase ya los rozó sin nombrarlos. El movimiento 1 juntó lo que cambia por la misma razón (anticipa SRP). El movimiento 2 permitió agregar comisiones sin modificar `Cuenta` (anticipa OCP) e hizo que `Cuenta` dependiera de una interfaz y no de clases concretas (anticipa DIP).

Una advertencia que se repetirá en cada lección: SOLID son heurísticas, no leyes. Aplicados sin criterio producen código con más clases, más interfaces y más confusión que el problema que querían evitar.

## Paso 6 — Taller práctico

El enunciado está en [`taller/`](taller/). No hay solución publicada: primero se intenta.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## Paso 7 — Revisión técnica

La revisión ocurre en el pull request de la entrega. Se evalúa:

- ¿Se puede dejar un objeto en un estado inválido desde afuera?
- ¿Cada regla de negocio vive en un único lugar?
- ¿Qué hay que modificar para agregar un caso nuevo?
- ¿Las decisiones están justificadas, incluidas las de no hacer algo?

## Paso 8 — Aplicación profesional: Java y Spring Boot

**Por qué importa en el trabajo real.** El código se escribe una vez y se lee y se modifica durante años, por personas que no lo escribieron. Casi todo el costo de un sistema está en cambiarlo, no en crearlo. Un diseño con baja cohesión y alto acoplamiento no falla el primer día: hace que cada cambio sea más lento y más arriesgado que el anterior.

**Spring es orientación a objetos convertida en framework.** Cuando un `@Service` recibe un repositorio por el constructor y Spring decide qué implementación entregarle, está pasando exactamente lo del movimiento 2: una clase depende de un contrato y alguien más le entrega el objeto concreto. La inyección de dependencias no es magia de Spring; es polimorfismo con un contenedor que hace el cableado.

**El modelo anémico.** El error del paso 3 es el más común en proyectos Spring Boot, solo que disfrazado:

```java
@Entity
@Data // genera getters y setters públicos para todo
public class Cuenta {
    @Id @GeneratedValue
    private Long id;
    private String tipo;
    private BigDecimal saldo;
}

@Service
@RequiredArgsConstructor
public class CuentaService {
    private final CuentaRepository repositorio;

    public void retirar(Long id, BigDecimal monto) {
        Cuenta cuenta = repositorio.findById(id).orElseThrow();
        if (cuenta.getSaldo().compareTo(monto) >= 0) {
            cuenta.setSaldo(cuenta.getSaldo().subtract(monto));
        }
        // ...
    }
}
```

Los campos son privados, pero con un `setSaldo` público el efecto es el mismo que tener el campo público: cualquier servicio puede dejar el saldo en cualquier valor. Es el mismo programa procedural, con anotaciones.

Esto no significa que toda entidad con setters esté mal. En un CRUD sin reglas de negocio, un modelo anémico es una decisión razonable. El problema aparece cuando hay reglas y esas reglas quedan repartidas por los servicios.

## Paso 9 — Evaluación senior

Estas preguntas no se responden con una definición. Respóndelas por escrito en tu entrega.

1. Un colega dice: "nuestro proyecto es orientado a objetos porque todo está en clases". ¿Qué le preguntarías para comprobarlo?
2. Una clase tiene todos sus campos privados y un getter y un setter público para cada uno. ¿Está encapsulada? Justifica.
3. En el ejemplo, ¿qué se ganó y qué se perdió al reemplazar el `if` por `PoliticaComision`? ¿En qué situación habrías dejado el `if`?
4. ¿Por qué decimos que el polimorfismo sirve para controlar dependencias y no solo para reutilizar código?
5. Describe una entidad de un proyecto real en el que hayas trabajado que sea un modelo anémico. ¿Qué regla de negocio vive fuera de ella y qué riesgo concreto genera eso?

## Comprobación antes de avanzar

No pases a la siguiente lección hasta poder explicar, sin mirar:

- La diferencia entre clase y objeto.
- Qué es una invariante y quién debe protegerla.
- Qué son cohesión y acoplamiento y hacia dónde se busca mover cada uno.
- Por qué usar clases no garantiza un diseño orientado a objetos.
- Qué relación hay entre los pilares de la OO y los principios SOLID.

Siguiente: [SRP — Principio de responsabilidad única](../../nivel-02-solid/01-srp/).
