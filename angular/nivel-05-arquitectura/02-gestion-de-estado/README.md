# 02 — Gestión de estado

## Idea

El estado es todo lo que la aplicación "recuerda": qué usuario entró, qué hay en el carrito, qué pestaña está abierta, qué se está cargando. Los errores de interfaz más difíciles son de estado: dos partes de la pantalla que muestran cosas distintas del mismo dato.

## Lo esencial

### Clasificar antes de elegir herramienta

| Tipo de estado | Ejemplos | Dónde vive |
|---|---|---|
| De interfaz local | Un desplegable abierto, el texto de un campo | Señal en el componente |
| De URL | Filtros, página, pestaña, identificador | Parámetros de ruta y de consulta |
| De servidor | Pedidos, productos, perfil | Caché con política de actualización |
| De formulario | Valores, errores, tocado | El formulario |
| Compartido de cliente | Sesión, carrito, tema, avisos | Almacén |

La mayoría de las aplicaciones tienen poco estado compartido de cliente. Lo que suele parecer "estado global" es **estado de servidor** guardado a mano: una copia local de datos que viven en otro lugar, con todos los problemas de una caché (¿está al día?, ¿cuándo se vuelve a pedir?, ¿qué pasa si dos pantallas lo piden?).

### Principios

1. **Una sola fuente de verdad** por cada dato.
2. **Estado mínimo;** lo derivable se calcula.
3. **Inmutable.**
4. **El alcance más pequeño posible.**
5. **Flujo unidireccional:** la vista lee el estado y emite intenciones; solo el dueño del estado lo cambia.

### Escalera de soluciones

Sube un peldaño solo cuando el anterior duela:

| Peldaño | Cuándo |
|---|---|
| 1. Señales en el componente | Estado que solo usa ese componente |
| 2. Entradas y salidas | Padre e hijos cercanos |
| 3. Estado en la URL | Lo que debe sobrevivir a recargar o compartirse por enlace |
| 4. Servicio con señales (almacén por funcionalidad) | Varios componentes de una funcionalidad |
| 5. Biblioteca de estado (NgRx SignalStore) | Muchos almacenes con patrones repetidos; se quiere estructura común |
| 6. Almacén global con acciones y efectos (NgRx Store) | Aplicaciones grandes con flujos complejos, necesidad de trazabilidad y depuración con viaje en el tiempo |

### Almacén con acciones (Redux)

```
Componente ──acción──► Reductor (función pura) ──► Estado nuevo ──selector──► Componente
                 └──► Efecto (asíncrono) ──acción──┘
```

Da trazabilidad total (cada cambio es una acción registrada) y herramientas de depuración. Cuesta ceremonia: acciones, reductores, efectos y selectores para cada cosa. Es Command, Observer y Mediator combinados.

### Estado de servidor

Lo que hay que resolver: caché por clave, datos obsoletos y revalidación, deduplicación de peticiones, estados de carga y error, invalidación tras una mutación, y actualizaciones optimistas. Opciones: los recursos de Angular (`resource`, `httpResource`, estables desde la versión 22 y vistos en el [Nivel 1](../../nivel-01-fundamentos/04-datos-asincronos-con-resource/)), que resuelven carga, error y cancelación pero no son una caché compartida; almacenes propios con cuidado; o bibliotecas como TanStack Query para Angular, que sí traen caché, revalidación e invalidación.

## Ejemplo: el mismo almacén, a mano y con biblioteca

Un servicio con señales (peldaño 4):

```ts
@Injectable({ providedIn: 'root' })
export class Carrito {
  private readonly lineasInternas = signal<readonly Linea[]>([]);

  readonly lineas = this.lineasInternas.asReadonly();            // nadie de fuera puede escribir
  readonly total = computed(() => this.lineas().reduce((suma, l) => suma + l.precio * l.cantidad, 0));
  readonly cantidad = computed(() => this.lineas().reduce((suma, l) => suma + l.cantidad, 0));

  agregar(linea: Linea): void {
    this.lineasInternas.update((lineas) => [...lineas, linea]);
  }

  quitar(productoId: string): void {
    this.lineasInternas.update((lineas) => lineas.filter((l) => l.productoId !== productoId));
  }
}
```

Lo mismo con NgRx SignalStore (peldaño 5):

```ts
export const CarritoStore = signalStore(
  { providedIn: 'root' },
  withState<{ lineas: readonly Linea[] }>({ lineas: [] }),
  withComputed(({ lineas }) => ({
    total: computed(() => lineas().reduce((suma, l) => suma + l.precio * l.cantidad, 0)),
    cantidad: computed(() => lineas().reduce((suma, l) => suma + l.cantidad, 0)),
  })),
  withMethods((store) => ({
    agregar(linea: Linea): void {
      patchState(store, (estado) => ({ lineas: [...estado.lineas, linea] }));
    },
    quitar(productoId: string): void {
      patchState(store, (estado) => ({ lineas: estado.lineas.filter((l) => l.productoId !== productoId) }));
    },
  })),
);
```

Para un solo almacén, la biblioteca no aporta nada que el servicio no tenga. Empieza a pagar cuando hay veinte almacenes y se quiere que todos tengan la misma forma, con piezas reutilizables (carga de entidades, estados de petición, persistencia). Es una dependencia más y un vocabulario que el equipo tiene que aprender: esa es la cuenta que hay que hacer.

## Ejemplo: el filtro que debía estar en la URL

```ts
// Antes: el filtro vive en un servicio; al recargar o compartir el enlace, se pierde.

// Después: la URL es la fuente de verdad
@Component({ /* ... */ })
export class ListaPedidosPagina {
  private readonly router = inject(Router);
  private readonly api = inject(PedidosApi);

  readonly estado = input<EstadoPedido | 'todos'>('todos');   // enlazado al parámetro de consulta
  readonly pagina = input(1, { transform: numberAttribute });

  protected readonly pedidos = toSignal(
    combineLatest([toObservable(this.estado), toObservable(this.pagina)]).pipe(
      switchMap(([estado, pagina]) => this.api.listar({ estado, pagina }))),
    { initialValue: [] },
  );

  protected filtrar(estado: EstadoPedido | 'todos'): void {
    this.router.navigate([], { queryParams: { estado, pagina: 1 }, queryParamsHandling: 'merge' });
  }
}
// Requiere provideRouter(rutas, withComponentInputBinding())
```

El botón de atrás funciona, el enlace se puede compartir y no hay estado que sincronizar.

## Errores comunes

- Biblioteca de estado global desde el primer día "porque la aplicación va a crecer".
- Todo en el almacén global, incluido el estado de un desplegable.
- La misma información en el almacén y en la URL.
- Copias locales de datos del servidor que nunca se revalidan.
- Estado derivado guardado.
- Componentes que modifican el estado directamente.
- Almacenes que se llaman entre sí en cadena.

## Taller

1. Lista todo el estado de una aplicación tuya y clasifícalo en los cinco tipos.
2. Encuentra tres datos guardados en un servicio que deberían estar en la URL y muévelos.
3. Encuentra estado duplicado y unifícalo en una sola fuente.
4. Implementa la misma funcionalidad (carrito) con un servicio con señales y con una biblioteca de estado. Compara líneas, claridad y facilidad de prueba.
5. Para el estado de servidor de una pantalla, implementa caché con revalidación e invalidación tras una mutación.
6. Escribe el criterio que usará tu equipo para decidir en qué peldaño va cada estado.

Rama `taller/ng-n5-02-estado`.

## Preguntas senior

1. ¿Qué tipos de estado existen y dónde vive cada uno?
2. ¿Por qué el estado de servidor es un problema distinto?
3. ¿Cuándo justificas una biblioteca de estado?
4. ¿Qué ventajas y costos tiene el modelo de acciones y reductores?
5. ¿Cómo evitas que dos partes de la pantalla muestren datos distintos?
