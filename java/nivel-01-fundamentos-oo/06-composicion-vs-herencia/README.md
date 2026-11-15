# 06 — Composición frente a herencia

## Paso 1 — Explicación para principiantes

Un carro **tiene** un motor. No **es** un motor. Si el motor falla, se cambia por otro sin fabricar un carro nuevo. Si mañana existe un motor eléctrico, se instala en la misma carrocería.

Eso es composición: armar algo a partir de piezas que se pueden cambiar. La herencia, en cambio, se parece a la genética: lo que recibes de tus padres viene completo y no se puede devolver.

## Paso 2 — Explicación técnica

| | Herencia | Composición |
|---|---|---|
| Relación | "es un" | "tiene un" / "usa un" |
| Qué se reutiliza | Todo lo heredable, se quiera o no | Solo lo que se decide delegar |
| Acoplamiento | A la implementación de la superclase | A la interfaz pública de la pieza |
| Cuándo se fija | Al compilar | Al construir el objeto; se puede cambiar al ejecutar |
| Varias dimensiones de variación | Una subclase por combinación | Una pieza por dimensión |
| Costo | Fragilidad | Más código de delegación |

**Delegación:** el objeto recibe un mensaje y se lo pasa a una pieza interna que sabe resolverlo.

"Prefiere composición sobre herencia" no significa "nunca heredes". Significa: cuando ambas sirven, la composición acopla menos. La herencia sigue siendo correcta cuando la relación "es un" es verdadera y estable, la superclase fue diseñada para extenderse y controlas ambas clases.

### Dos problemas que la herencia no resuelve bien

**Clase base frágil.** La subclase depende de cómo la superclase se llama a sí misma por dentro. Eso no está en ningún contrato y puede cambiar.

**Explosión de subclases.** Si algo varía en dos dimensiones (tipo de contrato y cargo, por ejemplo), la herencia obliga a una clase por combinación: 3 × 4 = 12 clases. Con composición son 3 + 4 = 7 piezas.

## Paso 3 — Código que funciona… casi

<!-- file: ejemplos/antes/Main.java -->
```java
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        ListaConContador lista = new ListaConContador();
        lista.add("a");
        lista.addAll(List.of("b", "c", "d"));
        System.out.println("Elementos reales: " + lista.size());
        System.out.println("Agregados según el contador: " + lista.totalAgregados());
    }
}

/** Una lista que cuenta cuántos elementos se le han agregado en toda su vida. */
@SuppressWarnings("serial")
class ListaConContador extends ArrayList<String> {

    private int agregados = 0;

    @Override
    public boolean add(String elemento) {
        agregados++;
        return super.add(elemento);
    }

    @Override
    public boolean addAll(Collection<? extends String> elementos) {
        agregados += elementos.size();
        return super.addAll(elementos);
    }

    int totalAgregados() {
        return agregados;
    }
}
```

Ejecútalo. Hoy el contador dice 4, que es correcto.

## Paso 4 — Análisis

El resultado es correcto **por casualidad**. Depende de un detalle interno de `ArrayList` que no aparece en su documentación como compromiso: que `addAll` no llama a `add` por cada elemento.

- Si heredaras igual de `HashSet`, cuyo `addAll` sí llama a `add` internamente, cada elemento de `addAll` se contaría dos veces. Mismo código, superclase distinta, resultado incorrecto.
- Si una versión futura cambia cómo `ArrayList` implementa `addAll`, el contador se rompe sin que nadie haya tocado tu clase.
- Para escribir la subclase bien, tuviste que **leer el código fuente de la superclase**. Eso es acoplamiento a la implementación.
- Además se heredan `add(int, E)`, `addAll(int, Collection)`, `replaceAll` y otros caminos para agregar que el contador no cubre. Una clase que promete contar no puede cumplir.

Este problema fue descrito por Joshua Bloch en *Effective Java* y es el argumento clásico a favor de la composición.

## Paso 5 — Código mejorado

<!-- file: ejemplos/despues/Main.java -->
```java
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        ListaConContador lista = new ListaConContador();
        lista.agregar("a");
        lista.agregarTodos(List.of("b", "c", "d"));
        System.out.println("Elementos reales: " + lista.tamano());
        System.out.println("Agregados según el contador: " + lista.totalAgregados());

        // Dos dimensiones de variación, combinadas sin crear una clase por combinación.
        Empleado ana = new Empleado("Ana", new ContratoFijo(5_000_000), new CargoGerente());
        Empleado luis = new Empleado("Luis", new ContratoPorHoras(30_000, 80), new CargoAnalista());
        System.out.println(ana.resumen());
        System.out.println(luis.resumen());
    }
}

/** TIENE una lista. Solo existen dos caminos para agregar, y ambos cuentan. */
class ListaConContador {

    private final List<String> elementos = new ArrayList<>();
    private int agregados = 0;

    void agregar(String elemento) {
        elementos.add(elemento);
        agregados++;
    }

    void agregarTodos(Collection<String> nuevos) {
        elementos.addAll(nuevos);
        agregados += nuevos.size();
    }

    int tamano() {
        return elementos.size();
    }

    int totalAgregados() {
        return agregados;
    }
}

interface Contrato {
    long pagoBase();
}

class ContratoFijo implements Contrato {
    private final long salario;
    ContratoFijo(long salario) { this.salario = salario; }
    @Override public long pagoBase() { return salario; }
}

class ContratoPorHoras implements Contrato {
    private final long valorHora;
    private final int horas;
    ContratoPorHoras(long valorHora, int horas) { this.valorHora = valorHora; this.horas = horas; }
    @Override public long pagoBase() { return valorHora * horas; }
}

interface Cargo {
    String nombre();
    long bono(long pagoBase);
}

class CargoGerente implements Cargo {
    @Override public String nombre() { return "Gerente"; }
    @Override public long bono(long pagoBase) { return pagoBase * 20 / 100; }
}

class CargoAnalista implements Cargo {
    @Override public String nombre() { return "Analista"; }
    @Override public long bono(long pagoBase) { return 0; }
}

class Empleado {

    private final String nombre;
    private final Contrato contrato;
    private Cargo cargo;

    Empleado(String nombre, Contrato contrato, Cargo cargo) {
        this.nombre = nombre;
        this.contrato = contrato;
        this.cargo = cargo;
    }

    /** Un ascenso cambia una pieza. Con herencia habría que crear otro objeto. */
    void ascender(Cargo nuevoCargo) {
        this.cargo = nuevoCargo;
    }

    long pagoMensual() {
        long base = contrato.pagoBase();
        return base + cargo.bono(base);
    }

    String resumen() {
        return nombre + " (" + cargo.nombre() + ") cobra $" + pagoMensual();
    }
}
```

Qué se ganó:

- `ListaConContador` no depende de cómo funciona `ArrayList` por dentro. Podría cambiarse por otra colección sin que nadie afuera lo note.
- No hay caminos ocultos para agregar: la clase expone exactamente lo que puede garantizar.
- `Empleado` combina dos dimensiones sin multiplicar clases, y un ascenso es cambiar una pieza.

Qué se pagó:

- `ListaConContador` ya no es una `List`: no sirve donde se espere una. Si eso fuera necesario, habría que implementar la interfaz `List` y delegar cada método, que es bastante código.
- Más tipos pequeños que leer.

## Paso 6 — Taller práctico

Un videojuego tiene estas clases, todas heredando de `Personaje`:

`GuerreroConEspada`, `GuerreroConArco`, `MagoConBaston`, `MagoConEspada`, `GuerreroConEspadaYArmadura`, `MagoConBastonYArmadura`.

Cada una calcula `atacar()` (daño según el arma), `defender(int dano)` (reduce el daño según la armadura) y `describir()`.

1. Implementa primero tres de esas clases con herencia para sentir la duplicación.
2. Calcula cuántas clases harían falta con 4 tipos de personaje, 5 armas y 3 armaduras.
3. Rediseña con composición. Un personaje debe poder cambiar de arma durante la partida.
4. ¿Queda alguna herencia en tu diseño? Justifica cada `extends`.
5. Señala qué código de delegación tuviste que escribir y si te parece un precio razonable.

Entrega: rama `taller/n1-06-composicion`.

## Paso 7 — Revisión técnica

- ¿Las dimensiones de variación son piezas independientes?
- ¿Se puede cambiar una pieza sin crear otro objeto?
- ¿Cada pieza se puede probar sola?
- ¿Algún `extends` existe solo para reutilizar código?

## Paso 8 — Aplicación profesional

- **Spring es composición de principio a fin.** Un servicio se arma recibiendo sus colaboradores por el constructor. Nadie hereda de `PedidoRepository` para usarlo.
- **`extends BaseService<T, ID>`** con operaciones CRUD genéricas es el equivalente empresarial de `ListaConContador`: todas las subclases reciben todas las operaciones, incluidas las que no deberían ofrecer (un `eliminar` en algo que nunca se elimina).
- **Para agregar comportamiento transversal** (transacciones, caché, seguridad), Spring no pide heredar: envuelve tu objeto en un proxy. Es composición aplicada por el framework.
- **Entidades:** prefiere `@Embedded` (un `Pedido` tiene una `Direccion`) antes que jerarquías de entidades con `@Inheritance`, que complican consultas y esquema.

## Paso 9 — Evaluación senior

1. ¿En qué casos concretos elegirías herencia sobre composición hoy?
2. ¿Por qué se dice que una clase debe diseñarse y documentarse para ser heredada, o prohibirlo con `final`?
3. La composición obliga a escribir delegación. ¿Qué alternativas ofrece Java o tu IDE y qué riesgo tienen?
4. Tienes una jerarquía de cuatro niveles en producción. ¿Cómo la migras a composición sin reescribir todo?
5. ¿Qué relación hay entre "prefiere composición" y la facilidad para escribir pruebas unitarias?

## Comprobación antes de avanzar

- Diferencia entre "es un" y "tiene un".
- Qué es delegación.
- Por qué la herencia acopla a la implementación.
- Qué es la explosión de subclases y cómo la evita la composición.

Siguiente: [07 — Acoplamiento y cohesión](../07-acoplamiento-y-cohesion/).
