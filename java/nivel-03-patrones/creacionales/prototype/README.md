# Prototype

Familia: creacional. Frecuencia en Java empresarial: baja como patrón; alta como necesidad ("copiar un objeto").

## El problema

Necesitas un objeto nuevo que sea casi igual a uno existente. Construirlo desde cero es costoso o exige conocer su clase concreta y todos sus datos. Copiar a mano campo por campo es frágil, y la copia ingenua comparte las referencias internas con el original: modificar una copia altera la otra.

## Cómo reconocer que lo necesitas

- "Duplicar" es una función del negocio: clonar una plantilla, repetir un pedido, copiar una configuración.
- Hay objetos base preconfigurados de los que se derivan variantes.
- Tienes una referencia de tipo general y necesitas una copia sin saber su clase concreta.

## Copia superficial frente a copia profunda

| | Qué copia | Riesgo |
|---|---|---|
| Superficial | Los campos; las referencias apuntan a los mismos objetos | Original y copia comparten estado mutable |
| Profunda | También los objetos referenciados | Más costosa; hay que decidir hasta dónde llegar |

## Antes

```java
Plantilla copia = original;                       // no es una copia: es la misma
Plantilla otra = new Plantilla(original.titulo(), original.secciones());  // comparte la lista
```

## Después

<!-- file: ejemplo/Main.java -->
```java
import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        Plantilla base = new Plantilla("Contrato de servicios");
        base.agregarSeccion("Objeto");
        base.agregarSeccion("Obligaciones");

        Plantilla paraCliente = base.copiar();
        paraCliente.agregarSeccion("Cláusula especial");

        System.out.println("Base:    " + base);
        System.out.println("Cliente: " + paraCliente);
    }
}

interface Copiable<T> {
    T copiar();
}

class Plantilla implements Copiable<Plantilla> {

    private final String titulo;
    private final List<String> secciones;

    Plantilla(String titulo) {
        this.titulo = titulo;
        this.secciones = new ArrayList<>();
    }

    /** Constructor de copia: copia profunda de lo mutable. */
    private Plantilla(Plantilla otra) {
        this.titulo = otra.titulo;
        this.secciones = new ArrayList<>(otra.secciones);
    }

    @Override
    public Plantilla copiar() {
        return new Plantilla(this);
    }

    void agregarSeccion(String seccion) {
        secciones.add(seccion);
    }

    @Override
    public String toString() {
        return titulo + " " + secciones;
    }
}
```

## Alternativas

- **Objetos inmutables**: si nada cambia, compartir es seguro y copiar es innecesario. Es la mejor alternativa.
- **`record` con métodos "with"** que devuelven una copia modificada.
- **Builder con `toBuilder()`**: parte de un objeto existente y cambia algunos campos.
- **Constructor de copia o método de fábrica estático** (`Plantilla.copiaDe(otra)`).

## En Spring y en Java

- El alcance `prototype` de Spring comparte el nombre pero no la idea: crea una instancia nueva cada vez, no copia una existente.
- **Evita `Object.clone()` y `Cloneable`**: el contrato es confuso, hace copia superficial por defecto y elude constructores. Usa constructores de copia.
- `BeanUtils.copyProperties` y los mapeadores (MapStruct) copian propiedades entre objetos; son copia superficial.
- Con JPA, copiar una entidad exige decidir qué pasa con el `id` y con las relaciones.

## Trade-offs

- A favor: evita construir desde cero; no hay que conocer la clase concreta; las plantillas son datos, no código.
- En contra: la copia profunda de grafos con ciclos es difícil; cada clase debe mantener su lógica de copia al agregar campos; fácil dejar una referencia compartida por error.

## Errores comunes

- Copia superficial donde se necesitaba profunda.
- Olvidar copiar un campo agregado después.
- Copiar el identificador o las fechas de auditoría de una entidad.
- Implementar `clone()`.

## Cuándo no usarlo

Cuando los objetos son inmutables; cuando construir uno nuevo es trivial; cuando copiar es raro y un constructor basta.

## Taller

Modela `Pedido` con cliente, dirección de envío (objeto mutable) y lista de ítems (cada uno con producto y cantidad). La tienda ofrece "repetir pedido".

1. Implementa una copia superficial y demuestra con un programa el error que causa.
2. Implementa la copia correcta. ¿Qué campos **no** deben copiarse (número, fecha, estado)?
3. Reescribe `Direccion` e `Item` como inmutables. ¿Cuánta lógica de copia desapareció?
4. Explica por qué `clone()` no es recomendable, con un ejemplo.

Entrega: rama `taller/n3-prototype`.
