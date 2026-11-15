# 03 — Funciones, inmutabilidad y asincronía

## Idea

Pides un café y te dan una ficha: sigues con lo tuyo y, cuando el café esté listo, te llaman. El navegador funciona así. Todo lo que tarda (una petición, un temporizador, un clic futuro) entrega una "ficha" y el programa continúa. Entender ese modelo evita la mitad de los errores de una aplicación web.

## Lo esencial

### Funciones puras

Mismo argumento, mismo resultado, sin efectos visibles (no modifica sus argumentos ni nada externo, no hace peticiones, no lee la hora). Son triviales de probar y de componer. La lógica de una aplicación debería vivir en funciones puras, con los efectos en el borde.

### Inmutabilidad

En frontend el estado se compara por referencia para saber si algo cambió. Modificar un objeto en el sitio (`lista.push(x)`) conserva la referencia y la interfaz puede no enterarse.

| En lugar de | Usa |
|---|---|
| `lista.push(x)` | `[...lista, x]` |
| `lista.splice(i, 1)` | `lista.filter((_, j) => j !== i)` o `toSpliced` |
| `lista.sort()` | `toSorted()` o `[...lista].sort()` |
| `objeto.campo = v` | `{ ...objeto, campo: v }` |

La copia con `...` es **superficial**: los objetos anidados se comparten.

### El bucle de eventos

JavaScript ejecuta en un solo hilo. Las tareas asíncronas se encolan y se atienden cuando la pila queda libre. Consecuencia: una operación larga y síncrona congela la interfaz.

### Promesas y `async`/`await`

Una promesa representa un valor futuro: pendiente, cumplida o rechazada.

- `await` espera sin bloquear el hilo.
- Los errores se capturan con `try/catch`.
- `Promise.all` ejecuta en paralelo y falla si una falla; `Promise.allSettled` espera a todas.
- Una promesa **no se puede cancelar** y entrega **un solo valor**. Por eso existen los observables y las bibliotecas de datos asíncronos, que se ven en cada ruta.

## Antes

```ts
function aplicarDescuento(pedido: Pedido, porcentaje: number) {
  pedido.total = pedido.total - (pedido.total * porcentaje) / 100;   // modifica el argumento
  pedido.items.sort((a, b) => a.precio - b.precio);                  // y reordena la lista de otro
  return pedido;
}

async function cargarTablero(id: string) {
  const cliente = await obtenerCliente(id);        // 300 ms
  const pedidos = await obtenerPedidos(id);        // 500 ms, y no dependía de la anterior
  const facturas = await obtenerFacturas(id);      // 400 ms
  return { cliente, pedidos, facturas };           // 1.200 ms en total, sin manejo de errores
}
```

## Después

```ts
function conDescuento(pedido: Pedido, porcentaje: number): Pedido {
  return {
    ...pedido,
    total: pedido.total - (pedido.total * porcentaje) / 100,
    items: pedido.items.toSorted((a, b) => a.precio - b.precio),
  };
}

async function cargarTablero(id: string): Promise<Tablero> {
  const [cliente, pedidos, facturas] = await Promise.all([
    obtenerCliente(id),
    obtenerPedidos(id),
    obtenerFacturas(id),
  ]);                                               // unos 500 ms: el más lento
  return { cliente, pedidos, facturas };
}

async function cargarTableroTolerante(id: string): Promise<Tablero> {
  const cliente = await obtenerCliente(id);         // crítico: si falla, que falle
  const [pedidos, facturas] = await Promise.allSettled([obtenerPedidos(id), obtenerFacturas(id)]);
  return {
    cliente,
    pedidos: pedidos.status === 'fulfilled' ? pedidos.value : [],
    facturas: facturas.status === 'fulfilled' ? facturas.value : [],
  };
}
```

## Errores comunes

- Modificar argumentos o estado compartido.
- `await` en serie para operaciones independientes.
- Olvidar `await`: se trabaja con una promesa creyendo que es el valor.
- `await` dentro de `forEach` (no espera); usa `for...of` o `Promise.all` con `map`.
- Promesas sin manejo de rechazo.
- **Condiciones de carrera:** dos peticiones salen, la primera llega después y pisa el resultado más reciente.
- Cálculos pesados en el hilo principal.
- Copia superficial creyendo que es profunda.

## Taller

1. Reescribe cinco funciones que modifican sus argumentos como funciones puras, y escribe para cada una una prueba que demuestre que el original no cambió.
2. Demuestra con código que la copia con `...` es superficial y corrígelo para un objeto con dos niveles.
3. Mide la diferencia entre tres `await` en serie y `Promise.all` con funciones simuladas con retardo.
4. Reproduce una condición de carrera: un buscador que dispara una petición por tecla, donde una respuesta lenta pisa a una rápida. Corrígela.
5. Explica en qué orden se imprime una secuencia con `console.log`, `setTimeout(…, 0)` y una promesa ya resuelta, y por qué.

Rama `taller/ts-03-funciones`.

## Preguntas senior

1. ¿Por qué la inmutabilidad importa para la detección de cambios?
2. Explica el bucle de eventos y por qué un cálculo largo congela la interfaz.
3. `Promise.all` frente a `Promise.allSettled`.
4. ¿Qué limitaciones de las promesas resuelven los observables?
5. ¿Cómo evitas una condición de carrera entre peticiones?
