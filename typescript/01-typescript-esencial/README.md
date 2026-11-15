# 01 — TypeScript esencial

## Idea

JavaScript te deja meter cualquier cosa en cualquier caja y te enteras del error cuando el usuario ya lo vio. TypeScript pone rótulos en las cajas y revisa antes de salir de casa: los errores aparecen en el editor, no en producción.

## Lo esencial

- TypeScript es JavaScript con tipos que se verifican al compilar y desaparecen al ejecutar. No valida datos en ejecución: lo que llega de una API hay que comprobarlo.
- **Tipado estructural:** dos tipos son compatibles si tienen la misma forma, no el mismo nombre. Es la diferencia más importante frente a Java.

| Herramienta | Para qué |
|---|---|
| `interface` / `type` | Describir la forma de un objeto. `type` además permite uniones e intersecciones |
| Unión `A \| B` | Un valor que puede ser de varios tipos |
| Tipos literales | `'pendiente' \| 'pagado'`: solo esos valores |
| Estrechamiento (*narrowing*) | El compilador deduce el tipo dentro de un `if` |
| Uniones discriminadas | Una propiedad común (`tipo`) distingue cada caso; `switch` exhaustivo |
| Genéricos `<T>` | Código reutilizable sin perder el tipo |
| `readonly`, `as const` | Impedir modificaciones |
| `unknown` | "No sé qué es": obliga a comprobar. Preferible a `any` |
| Utilitarios | `Partial`, `Pick`, `Omit`, `Record`, `Readonly`, `ReturnType` |

Activa siempre `"strict": true`. Sin él, `null` y `undefined` se cuelan en cualquier tipo y se pierde la mitad del valor.

## Antes

```ts
function total(items: any, cupon?: any) {
  let t = 0;
  for (const i of items) t += i.precio * i.cant;      // 'cant' o 'cantidad': nadie avisa
  if (cupon.tipo == 'porcentaje') t -= t * cupon.valor / 100;   // falla si no hay cupón
  return t;
}
```

`any` apaga el compilador: este código acepta cualquier cosa y falla al ejecutarse.

## Después

```ts
interface Item {
  readonly producto: string;
  readonly precio: number;
  readonly cantidad: number;
}

// Unión discriminada: cada caso tiene exactamente los datos que necesita
type Cupon =
  | { tipo: 'porcentaje'; porcentaje: number }
  | { tipo: 'fijo'; valor: number }
  | { tipo: 'envioGratis' };

function descuento(subtotal: number, cupon: Cupon): number {
  switch (cupon.tipo) {
    case 'porcentaje':
      return (subtotal * cupon.porcentaje) / 100;
    case 'fijo':
      return Math.min(cupon.valor, subtotal);
    case 'envioGratis':
      return 0;
    default: {
      const nunca: never = cupon;      // si se agrega un caso y se olvida aquí, no compila
      return nunca;
    }
  }
}

function total(items: readonly Item[], cupon?: Cupon): number {
  const subtotal = items.reduce((suma, item) => suma + item.precio * item.cantidad, 0);
  return cupon ? subtotal - descuento(subtotal, cupon) : subtotal;
}
```

La unión discriminada con comprobación `never` es el equivalente en TypeScript de una jerarquía sellada: el compilador obliga a atender todos los casos.

## Errores comunes

- `any` para "hacer que compile". Cada `any` es un agujero por el que se escapan los errores.
- Aserciones `as Tipo` para callar al compilador en lugar de comprobar.
- Confiar en que el tipo de la respuesta HTTP es verdad: `http.get<Pedido>()` no valida nada.
- `enum` numéricos; suelen bastar las uniones de literales.
- Tipos enormes y opcionales en todo (`nombre?: string`) que obligan a comprobar en cada uso.
- `!` (aserción de no nulo) por reflejo.
- `strict` desactivado.

## Taller

1. Modela con una unión discriminada los medios de pago (tarjeta, transferencia, efectivo, billetera), cada uno con sus datos, y una función que calcule la comisión con `switch` exhaustivo.
2. Agrega un medio de pago y anota qué deja de compilar.
3. Escribe `agruparPor<T, K extends keyof T>(lista: T[], clave: K)` con tipos correctos.
4. Escribe un validador que reciba `unknown` (una respuesta de API) y devuelva un `Pedido` tipado o un error. No uses `as`.
5. Convierte un archivo JavaScript de 50 líneas a TypeScript estricto sin `any`. Lista los errores reales que descubrió el compilador.

Rama `taller/ts-01-typescript`.

## Preguntas senior

1. ¿Qué es el tipado estructural y qué consecuencias tiene?
2. `any` frente a `unknown` frente a `never`.
3. `interface` frente a `type`: ¿cuándo cada uno?
4. ¿Por qué los tipos no protegen de datos incorrectos que llegan de una API y qué haces al respecto?
5. ¿Qué es una unión discriminada y qué patrón clásico reemplaza?
