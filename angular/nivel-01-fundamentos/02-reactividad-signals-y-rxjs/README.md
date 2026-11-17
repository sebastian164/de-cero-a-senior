# 02 — Reactividad: señales y RxJS

## Idea

Una hoja de cálculo es reactiva: cambias una celda y todas las fórmulas que dependen de ella se recalculan solas. Nadie les avisa una por una. Angular ofrece dos herramientas para eso: las señales, para el **estado** (el valor que algo tiene ahora), y los observables de RxJS, para los **eventos en el tiempo** (lo que va ocurriendo).

## Lo esencial

### Señales

| Primitiva | Qué es |
|---|---|
| `signal(valor)` | Un valor que avisa cuando cambia. Se lee llamándola: `contador()`. Se cambia con `set` y `update` |
| `computed(() => …)` | Valor derivado. Se recalcula solo cuando cambian sus dependencias, y se guarda en memoria |
| `effect(() => …)` | Efecto secundario que se ejecuta al cambiar lo que lee. Para sincronizar con algo externo (almacenamiento local, una biblioteca), **no** para derivar estado |
| `linkedSignal` | Estado editable que se reinicia cuando cambia su origen |

Reglas: deriva con `computed`, no con `effect`; mantén el estado mínimo y calcula lo demás; actualiza de forma inmutable.

### Observables (RxJS)

Un observable es un flujo de cero o más valores en el tiempo, que además se puede **cancelar** y **componer** con operadores.

| Operador | Para qué |
|---|---|
| `map`, `filter` | Transformar y filtrar |
| `debounceTime`, `distinctUntilChanged` | Controlar la frecuencia |
| `switchMap` | Cambiar a un flujo nuevo cancelando el anterior (búsquedas) |
| `concatMap` | Uno tras otro, en orden (guardados) |
| `mergeMap` | En paralelo |
| `exhaustMap` | Ignorar mientras hay uno en curso (evitar doble envío) |
| `catchError`, `retry` | Manejo de errores |
| `combineLatest`, `forkJoin` | Combinar flujos |

### Cuándo usar cada uno

| Necesito | Uso |
|---|---|
| Estado de un componente o de la aplicación | Señal |
| Valor derivado | `computed` |
| Leer datos del servidor y mostrarlos | Un recurso (`httpResource`, [sesión 04](../04-datos-asincronos-con-resource/)) |
| Escrituras por HTTP, eventos, temporizadores, WebSocket | Observable |
| Cancelar, esperar, reintentar, combinar en el tiempo | Operadores de RxJS |
| Mostrar un flujo en la plantilla | `toSignal(observable)` |
| Reaccionar a una señal con operadores | `toObservable(señal)` |

En la práctica: **RxJS en el borde asíncrono, señales para el estado y la vista.**

## Antes

```ts
export class BuscadorComponent {
  texto = '';
  resultados: Producto[] = [];
  total = 0;                                   // estado derivado guardado a mano

  buscar() {                                   // se llama en cada tecla
    this.http.get<Producto[]>(`/api/productos?q=${this.texto}`).subscribe((r) => {
      this.resultados = r;                     // una respuesta lenta puede pisar a una reciente
      this.total = r.length;                   // y hay que acordarse de actualizarlo siempre
    });
  }
}
```

## Después

```ts
@Component({
  selector: 'app-buscador',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <label>Buscar <input [value]="texto()" (input)="texto.set($any($event.target).value)" /></label>
    <p>{{ total() }} resultados</p>
    @for (producto of resultados(); track producto.id) {
      <p>{{ producto.nombre }}</p>
    }
  `,
})
export class BuscadorComponent {
  private readonly productos = inject(ProductosService);

  protected readonly texto = signal('');

  protected readonly resultados = toSignal(
    toObservable(this.texto).pipe(
      debounceTime(300),                       // espera a que deje de escribir
      distinctUntilChanged(),
      switchMap((texto) =>                     // cancela la petición anterior
        texto.length < 2 ? of([]) : this.productos.buscar(texto).pipe(catchError(() => of([])))),
    ),
    { initialValue: [] as Producto[] },
  );

  protected readonly total = computed(() => this.resultados().length);   // siempre al día
}
```

Menos estado, sin carrera entre peticiones, sin suscripciones manuales y sin riesgo de olvidar actualizar el total.

## Errores comunes

- `effect` para copiar una señal en otra: eso es un `computed`.
- Guardar estado derivado en señales independientes.
- `subscribe` dentro de `subscribe`: usa operadores de aplanamiento.
- `mergeMap` donde hacía falta `switchMap` (carreras) o `concatMap` (orden).
- Suscripciones sin cancelar: usa `toSignal`, la tubería `async` o `takeUntilDestroyed`.
- `catchError` en el lugar equivocado: en el flujo exterior lo termina; va dentro del flujo interno.
- Modificar el contenido de una señal en el sitio (`señal().push(x)`): no notifica.
- Convertir todo a señales, incluidos los eventos, o todo a observables, incluido el estado.

## Taller

1. Implementa un carrito con señales: `lineas` como estado, y `subtotal`, `iva`, `total` y `cantidadDeItems` como `computed`.
2. Persiste el carrito en `localStorage` con un `effect`. Justifica por qué aquí sí corresponde un efecto.
3. Implementa el buscador con `debounceTime` y `switchMap`. Simula latencia variable y demuestra que no hay carrera.
4. Implementa un botón "guardar" que no permita doble envío, con `exhaustMap`.
5. Cambia `switchMap` por `mergeMap` en el buscador y reproduce el error.
6. Explica con tus palabras por qué `computed` es preferible a `effect` para derivar.

Rama `taller/ng-n1-02-reactividad`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## Preguntas senior

1. ¿Qué diferencia conceptual hay entre una señal y un observable?
2. `switchMap`, `concatMap`, `mergeMap` y `exhaustMap`: un caso de uso para cada uno.
3. ¿Por qué no usar `effect` para derivar estado?
4. ¿Cómo conviven señales y RxJS en una aplicación?
5. ¿Qué es el estado derivado y por qué no debe almacenarse?
