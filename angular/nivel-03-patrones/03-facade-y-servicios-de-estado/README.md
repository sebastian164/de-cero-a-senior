# 03 — Facade y servicios de estado

## El problema

Varios componentes necesitan los mismos datos y deben verse actualizados a la vez. Si cada uno hace su petición y guarda su copia, aparecen inconsistencias. Si cada uno conoce el servicio HTTP, el almacén, el caché y las reglas, el acoplamiento se dispara.

## El patrón

Un **servicio de estado** (almacén) es dueño de una porción del estado y la única vía para cambiarla. Hace de **fachada**: los componentes ven señales de solo lectura y métodos con nombre de intención; no ven HTTP, ni caché, ni cómo se guarda.

```
Componente ──lee señales──► Almacén ──► API
Componente ──llama método──►   │
                               └──► estado privado (signal)
```

Principios:

1. **Estado privado, lectura pública.** `private readonly _estado = signal(…)` y `asReadonly()` o `computed`.
2. **Una sola forma de cambiar el estado:** los métodos del almacén.
3. **Estado mínimo;** todo lo derivable, con `computed`.
4. **Actualizaciones inmutables.**
5. **El estado asíncrono es explícito:** cargando, error, datos.

## Ejemplo

```ts
interface EstadoPedidos {
  readonly pedidos: readonly Pedido[];
  readonly cargando: boolean;
  readonly error: string | null;
  readonly filtro: EstadoPedido | 'todos';
}

@Injectable({ providedIn: 'root' })
export class AlmacenPedidos {
  private readonly api = inject(PedidosApi);

  private readonly estado = signal<EstadoPedidos>({
    pedidos: [], cargando: false, error: null, filtro: 'todos',
  });

  // Lectura: selectores
  readonly cargando = computed(() => this.estado().cargando);
  readonly error = computed(() => this.estado().error);
  readonly visibles = computed(() => {
    const { pedidos, filtro } = this.estado();
    return filtro === 'todos' ? pedidos : pedidos.filter((p) => p.estado === filtro);
  });
  readonly totalPendiente = computed(() =>
    this.estado().pedidos.filter((p) => p.estado === 'pendiente').reduce((s, p) => s + p.total, 0));

  // Escritura: intenciones
  cargar(): void {
    this.estado.update((e) => ({ ...e, cargando: true, error: null }));
    this.api.listar().subscribe({
      next: (pedidos) => this.estado.update((e) => ({ ...e, pedidos, cargando: false })),
      error: () => this.estado.update((e) => ({ ...e, cargando: false, error: 'No se pudieron cargar los pedidos' })),
    });
  }

  filtrar(filtro: EstadoPedidos['filtro']): void {
    this.estado.update((e) => ({ ...e, filtro }));
  }

  cancelar(id: string): void {
    const anterior = this.estado().pedidos;
    this.estado.update((e) => ({ ...e, pedidos: e.pedidos.filter((p) => p.id !== id) }));   // optimista
    this.api.cancelar(id).subscribe({
      error: () => this.estado.update((e) => ({ ...e, pedidos: anterior, error: 'No se pudo cancelar' })),
    });
  }
}
```

Los componentes no saben que existe una API. Sustituir la implementación (caché, tiempo real, otra fuente) no los toca.

## Alcance del almacén

| Proveído en | Vive | Uso |
|---|---|---|
| `root` | Toda la aplicación | Sesión, carrito |
| La ruta | Mientras esa funcionalidad esté activa | Estado de una sección |
| El componente | Lo que viva el componente | Estado de un asistente o de un formulario complejo |

Prefiere el alcance más pequeño que funcione: el estado global es el equivalente frontend de una variable global.

## Alternativas

- **Estado local** en el componente, si nadie más lo necesita. Es el punto de partida.
- **Estado en la URL** (parámetros de ruta y de consulta) para filtros, paginación y pestañas: se puede compartir y sobrevive a recargar.
- **Bibliotecas** (NgRx SignalStore, NgRx Store): ver la sesión de gestión de estado del Nivel 5.

## Errores comunes

- Exponer la señal modificable: cualquiera puede cambiar el estado.
- Fachadas que solo reenvían llamadas a la API sin guardar ni derivar nada.
- Un almacén global con todo el estado de la aplicación.
- Guardar estado derivado.
- Duplicar en el almacén lo que ya está en la URL.
- Actualizaciones optimistas sin reversa.
- Lógica de presentación (textos, clases CSS) dentro del almacén.

## Taller

1. Implementa `AlmacenCarrito` con señales: agregar, quitar, cambiar cantidad, vaciar; y `subtotal`, `total` y `cantidad` derivados.
2. Dos componentes en lugares distintos de la pantalla (ícono con contador y página del carrito) deben reflejar los mismos datos.
3. Agrega actualización optimista con reversa ante fallo.
4. Prueba el almacén sin componentes, con un doble de la API.
5. Mueve un filtro del almacén a la URL y explica qué ganaste.

Rama `taller/ng-n3-03-facade-estado`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## Preguntas senior

1. ¿Qué oculta una fachada de estado y qué gana el componente?
2. ¿Cómo decides el alcance de un almacén?
3. ¿Qué estado pertenece a la URL?
4. ¿Qué es una actualización optimista y qué riesgos tiene?
