# 04 — Datos asíncronos: resource y httpResource

`resource`, `rxResource` y `httpResource` son estables desde Angular 22 y son la forma recomendada de **leer** datos asíncronos. En versiones anteriores existían como API experimental con diferencias de nombres: confirma la tuya en angular.dev.

## Idea

La pantalla de salidas de un aeropuerto. Tú no llamas a la aerolínea cada vez que quieres saber de tu vuelo: miras la pantalla. Ella sabe sola cuándo consultar, muestra "embarcando", "retrasado" o "cancelado", y si cambias de vuelo deja de mostrar el anterior. Un recurso es esa pantalla: un valor que viene de fuera, con su estado, y que se actualiza cuando cambia aquello de lo que depende.

## Lo esencial

Un recurso es una señal cuyo valor llega de forma asíncrona. Declaras **de qué depende** y **cómo se obtiene**; Angular se encarga de pedirlo, de volver a pedirlo cuando cambian las dependencias y de cancelar la petición anterior.

| Función | Cuándo |
|---|---|
| `httpResource(() => url)` | Leer por HTTP. Usa `HttpClient` por debajo, así que pasan los interceptores |
| `resource({ params, loader })` | Cualquier origen que devuelva una promesa |
| `rxResource({ params, stream })` | Cuando el origen es un observable |

Lo que expone un recurso:

| Miembro | Qué dice |
|---|---|
| `value()` | El dato. Lanza un error si el recurso está en estado de error |
| `hasValue()` | Si hay un dato que se puede leer |
| `isLoading()` | Si hay una petición en curso |
| `error()` | El error, si lo hubo |
| `status()` | El estado exacto: inactivo, cargando, recargando, resuelto, error |
| `reload()` | Volver a pedir |

Tres reglas:

1. **Solo para leer.** Crear, modificar y eliminar siguen yendo por `HttpClient` en un servicio. Un recurso se dispara solo cuando cambian sus dependencias, y nadie quiere un `POST` que se dispare solo.
2. **Si la función de parámetros devuelve `undefined`, no hay petición.** Es la forma de decir "todavía no".
3. **Lee `value()` solo cuando `hasValue()` es verdadero.**

## Antes

```ts
@Component({ /* ... */ })
export class DetallePedidoComponent {
  private readonly api = inject(PedidosApi);
  readonly id = input.required<string>();

  protected readonly pedido = signal<Pedido | null>(null);
  protected readonly cargando = signal(false);
  protected readonly error = signal(false);

  constructor() {
    effect(() => {
      const id = this.id();
      this.cargando.set(true);
      this.error.set(false);
      this.api.obtener(id).subscribe({
        next: (pedido) => { this.pedido.set(pedido); this.cargando.set(false); },
        error: () => { this.error.set(true); this.cargando.set(false); },
      });
    });
  }
}
```

## Análisis

| Problema | Consecuencia |
|---|---|
| Tres señales para un solo hecho | Se pueden contradecir: `cargando` verdadero con `error` verdadero, o un pedido viejo visible mientras carga el nuevo |
| La suscripción no se cancela cuando cambia `id` | Si la respuesta del pedido anterior llega después, pisa a la del actual |
| Un `effect` que escribe señales | Es la forma de usar `effect` que la sesión 02 dijo evitar |
| El mismo bloque se copia en cada pantalla | Cada copia olvida un caso distinto |

## Después

```ts
@Component({
  selector: 'app-detalle-pedido',
  imports: [CurrencyPipe],
  template: `
    @if (pedido.isLoading()) {
      <p>Cargando…</p>
    } @else if (pedido.error()) {
      <p role="alert">No se pudo cargar el pedido.</p>
      <button type="button" (click)="pedido.reload()">Reintentar</button>
    } @else if (pedido.hasValue()) {
      <h2>Pedido {{ pedido.value().id }}</h2>
      <p>{{ pedido.value().total | currency: 'COP' }}</p>
    }
  `,
})
export class DetallePedidoComponent {
  readonly id = input.required<string>();

  protected readonly pedido = httpResource(
    () => `/api/pedidos/${this.id()}`,                       // se vuelve a pedir cuando cambia id
    { parse: (dto) => aPedido(dto as PedidoDto) },            // el adaptador, en el borde
  );
}
```

- Un solo objeto describe la petición: no hay estados que se contradigan.
- Cuando `id` cambia, la petición anterior se cancela.
- No hay `effect` ni suscripciones.
- `parse` convierte lo que llega al modelo de la aplicación. Es el lugar para validar la respuesta.

Con un origen que no es HTTP, o cuando la capa de datos ya existe como servicio:

```ts
protected readonly pedido = resource({
  params: () => ({ id: this.id() }),
  loader: ({ params, abortSignal }) => this.api.obtenerComoPromesa(params.id, abortSignal),
});

protected readonly historial = rxResource({
  params: () => ({ id: this.id() }),
  stream: ({ params }) => this.api.historial(params.id),      // devuelve un observable
});
```

El código es ilustrativo: no se compiló al preparar el curso.

## Dónde poner el recurso

En un componente de página está bien para una lectura que solo usa esa pantalla. Cuando varias pantallas necesitan el mismo dato, el recurso va en un servicio o almacén de la funcionalidad, y los componentes lo leen. La regla de siempre no cambia: los componentes de presentación no conocen direcciones.

## Errores comunes

- Usar un recurso para guardar o eliminar.
- Leer `value()` sin comprobar `hasValue()` y recibir una excepción en la plantilla.
- Diseñar solo el caso feliz: el recurso te da los cuatro estados; la plantilla tiene que usarlos.
- Construir la dirección con un valor aún vacío en lugar de devolver `undefined`.
- Esperar que un recurso sea una caché compartida: dos componentes con el mismo recurso hacen dos peticiones.
- Abandonar RxJS para todo: para eventos en el tiempo (escritura con espera, WebSocket, combinaciones) sigue siendo la herramienta.

## Taller

1. Reescribe con `httpResource` la carga de la lista de pedidos de la sesión 01. Compara las líneas y, sobre todo, los estados posibles.
2. Construye una pantalla de detalle que dependa del identificador de la ruta. Cambia de un pedido a otro con latencia simulada distinta y demuestra que no hay carrera.
3. Añade un buscador: el recurso no debe pedir nada hasta que el texto tenga dos caracteres.
4. La búsqueda debe esperar a que el usuario deje de escribir. Resuélvelo de dos maneras, una con RxJS y otra sin él, y compara.
5. Implementa "eliminar pedido": la escritura va por un servicio con `HttpClient` y, al terminar, la lista se recarga. Explica por qué no usaste un recurso para eliminar.
6. Diseña los cuatro estados (cargando, error con reintento, vacío, con datos) y prueba cada uno forzándolo.
7. Mueve el recurso de la lista a un servicio que usen dos componentes. ¿Cuántas peticiones salen ahora?

Rama `taller/ng-n1-04-resource`.

## Preguntas senior

1. ¿Qué problema de fondo resuelve un recurso frente a "tres señales y una suscripción"?
2. ¿Por qué los recursos son para leer y no para escribir?
3. ¿Cómo evita un recurso las condiciones de carrera?
4. ¿Cuándo sigues necesitando RxJS?
5. Un recurso no es una caché. ¿Qué le falta y cómo lo resolverías?
