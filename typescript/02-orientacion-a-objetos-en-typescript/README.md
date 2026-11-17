# 02 — Orientación a objetos en TypeScript

Los conceptos son los mismos de la [ruta Java](../../java/nivel-01-fundamentos-oo/): si quieres la explicación larga de encapsulamiento, herencia o composición, está allí. Esta sesión muestra cómo se expresan en TypeScript y qué cambia.

## Idea

Un objeto guarda sus datos, sabe qué hacer con ellos y protege sus propias reglas. En TypeScript eso se puede lograr con clases, y muchas veces también con funciones y objetos inmutables. Un buen desarrollador de frontend domina las dos formas y sabe cuándo usar cada una.

## Lo esencial

| Concepto | En TypeScript |
|---|---|
| Encapsulamiento | `private`, `protected`, `readonly`; o campos `#privados` de JavaScript, que sí son privados en ejecución |
| Constructor | Propiedades de parámetro: `constructor(private readonly repo: Repo) {}` |
| Interfaces | Estructurales: una clase cumple una interfaz si tiene su forma, aunque no escriba `implements` |
| Clases abstractas | Igual que en Java: base parcial con huecos |
| Herencia | `extends`, simple. Se abusa igual que en cualquier lenguaje |
| Composición | Recibir colaboradores en el constructor. Es la opción por defecto |
| Polimorfismo | Por interfaces, o por uniones discriminadas |

Diferencias que importan:

- `private` de TypeScript solo existe al compilar. En ejecución el campo es accesible.
- Las interfaces desaparecen al compilar: no se puede preguntar en ejecución si un objeto "implementa" una interfaz, y no sirven como identificador en ejecución (por eso Angular, por ejemplo, usa `InjectionToken` para inyectar).
- Funciones y objetos literales son ciudadanos de primera clase: muchas veces un tipo y un par de funciones reemplazan a una clase.

### Clase o datos con funciones

| Usa una clase cuando | Usa tipos y funciones cuando |
|---|---|
| Hay reglas que proteger y estado que cambia bajo control | Son datos que viajan (respuestas de API, estado, DTO) |
| Es un servicio con dependencias | Las operaciones son transformaciones puras |
| Hay varias implementaciones de un contrato | El objeto debe ser serializable y comparable |

En las aplicaciones de interfaz, el estado suele ser inmutable (objetos planos) y la lógica con dependencias se organiza en servicios o en funciones.

## Antes

```ts
class Carrito {
  items: { producto: string; precio: number; cantidad: number }[] = [];
  total = 0;
  pagado = false;
}

const carrito = new Carrito();
carrito.items.push({ producto: 'Café', precio: 18000, cantidad: -3 });
carrito.total = 1;
carrito.pagado = true;        // sin ítems válidos y con un total inventado
```

## Después

```ts
interface Linea {
  readonly producto: string;
  readonly precio: number;
  readonly cantidad: number;
}

class Carrito {
  readonly #lineas: Linea[] = [];
  #pagado = false;

  agregar(producto: string, precio: number, cantidad: number): void {
    this.#exigirAbierto();
    if (cantidad <= 0) throw new Error('La cantidad debe ser mayor que cero');
    if (precio < 0) throw new Error('El precio no puede ser negativo');
    this.#lineas.push({ producto, precio, cantidad });
  }

  pagar(): void {
    this.#exigirAbierto();
    if (this.#lineas.length === 0) throw new Error('No se puede pagar un carrito vacío');
    this.#pagado = true;
  }

  get total(): number {                       // calculado: no puede desincronizarse
    return this.#lineas.reduce((suma, l) => suma + l.precio * l.cantidad, 0);
  }

  get lineas(): readonly Linea[] {
    return this.#lineas;                      // solo lectura para quien la recibe
  }

  #exigirAbierto(): void {
    if (this.#pagado) throw new Error('El carrito ya fue pagado');
  }
}
```

La misma idea, en estilo inmutable, que es el más común para el estado de una interfaz:

```ts
interface EstadoCarrito {
  readonly lineas: readonly Linea[];
  readonly pagado: boolean;
}

function agregar(estado: EstadoCarrito, linea: Linea): EstadoCarrito {
  if (estado.pagado) throw new Error('El carrito ya fue pagado');
  if (linea.cantidad <= 0) throw new Error('La cantidad debe ser mayor que cero');
  return { ...estado, lineas: [...estado.lineas, linea] };   // devuelve uno nuevo
}

const total = (estado: EstadoCarrito) =>
  estado.lineas.reduce((suma, l) => suma + l.precio * l.cantidad, 0);
```

## Errores comunes

- Clases con todo público que son bolsas de datos.
- Confiar en `private` de TypeScript como si protegiera en ejecución.
- Jerarquías de componentes o servicios (`BaseComponent`, `BaseService`) para reutilizar código.
- Modificar objetos que vienen del estado o de un `input`.
- Devolver el arreglo interno sin marcarlo como `readonly`.
- Clases para datos que llegan de una API: lo que llega es un objeto plano, sin los métodos de la clase.

## Taller

1. Modela `Curso` con cupo máximo y lista de inscritos, protegiendo sus reglas, en dos versiones: clase con campos privados y estado inmutable con funciones.
2. Demuestra que `private` de TypeScript se puede saltar en ejecución y que `#privado` no.
3. Define una interfaz `Notificador` con tres implementaciones y una función que las use sin conocerlas.
4. Reemplaza una jerarquía de herencia (`Empleado` → por horas, asalariado) por composición.
5. Explica con código por qué una respuesta HTTP tipada como una clase no tiene los métodos de esa clase.

Rama `taller/ts-02-oo`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## Preguntas senior

1. ¿Qué diferencia hay entre `private` y `#privado`?
2. ¿Por qué una interfaz de TypeScript no puede usarse como identificador de inyección?
3. ¿Cuándo modelas con una clase y cuándo con tipos y funciones?
4. ¿Por qué en frontend se favorece el estado inmutable?
5. ¿Qué problema trae un `BaseComponent` del que heredan todos los componentes?
