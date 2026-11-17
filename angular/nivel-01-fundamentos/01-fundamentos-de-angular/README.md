# 01 — Fundamentos de Angular

Angular cambia con cada versión mayor. Esta ruta usa la forma moderna (componentes independientes, señales, control de flujo integrado, `inject`), tal como quedó en Angular 22. Confirma los detalles de tu versión en la documentación oficial (angular.dev).

## Idea

Una aplicación de Angular es un árbol de componentes. Cada componente es una pieza de pantalla con su plantilla, su estado y su comportamiento. Los datos bajan del padre al hijo y los eventos suben del hijo al padre. Lo que no es pantalla (hablar con el servidor, reglas, estado compartido) vive en servicios.

## Lo esencial

| Pieza | Qué es |
|---|---|
| **Componente** | Clase con `@Component`: plantilla, estilos y lógica de presentación |
| **Plantilla** | HTML con enlaces: `{{ valor }}`, `[propiedad]`, `(evento)`, `[(ngModel)]` |
| **Control de flujo** | `@if`, `@for` (con `track` obligatorio), `@switch` |
| **Entradas y salidas** | `input()` recibe del padre; `output()` emite hacia el padre; `model()` hace las dos cosas ([sesión 03](../03-componentes-con-senales/)) |
| **Servicio** | Clase con `@Injectable` que contiene lógica o estado. Desde Angular 22 existe también `@Service()`, equivalente a `@Injectable({ providedIn: 'root' })` |
| **Inyección de dependencias** | `inject(Servicio)`: Angular crea y entrega las instancias |
| **Enrutador** | Asocia rutas con componentes; admite carga diferida |
| **Tubería (*pipe*)** | Transforma un valor en la plantilla: `{{ precio \| currency }}` |
| **Directiva** | Agrega comportamiento a un elemento |

### Flujo de datos

```
        Padre
   [dato] ↓   ↑ (evento)
        Hijo
```

Unidireccional: el hijo no modifica lo que recibe; avisa, y el padre decide.

### Ciclo de vida

Con señales se necesita poco. Lo habitual: inicializar en el constructor o en campos, reaccionar con `computed` y `effect`, y limpiar con `DestroyRef`. Los ganchos clásicos (`ngOnInit`, `ngOnDestroy`, `ngOnChanges`) siguen existiendo.

## Antes

```ts
@Component({
  selector: 'app-pedidos',
  template: `
    <div *ngFor="let p of pedidos">
      {{ p.cliente }} - {{ p.items.reduce((s, i) => s + i.precio * i.cantidad, 0) * 1.19 }}
      <button (click)="http.delete('/api/pedidos/' + p.id).subscribe(); pedidos.splice(pedidos.indexOf(p), 1)">
        Eliminar
      </button>
    </div>
  `,
})
export class PedidosComponent {
  pedidos: any[] = [];
  constructor(public http: HttpClient) {
    this.http.get<any[]>('/api/pedidos').subscribe((r) => (this.pedidos = r));
  }
}
```

Cálculo de negocio en la plantilla, peticiones HTTP en la vista, `any`, sin estados de carga ni de error, y modificación del arreglo en el sitio.

## Después

```ts
// pedidos.service.ts
@Injectable({ providedIn: 'root' })
export class PedidosService {
  private readonly http = inject(HttpClient);

  listar(): Observable<Pedido[]> {
    return this.http.get<Pedido[]>('/api/pedidos');
  }

  eliminar(id: string): Observable<void> {
    return this.http.delete<void>(`/api/pedidos/${id}`);
  }
}

// tarjeta-pedido.component.ts — solo presenta
@Component({
  selector: 'app-tarjeta-pedido',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CurrencyPipe],
  template: `
    <article>
      <h3>{{ pedido().cliente }}</h3>
      <p>{{ total() | currency: 'COP' }}</p>
      <button type="button" (click)="eliminar.emit(pedido().id)">Eliminar</button>
    </article>
  `,
})
export class TarjetaPedidoComponent {
  readonly pedido = input.required<Pedido>();
  readonly eliminar = output<string>();
  protected readonly total = computed(() => totalConIva(this.pedido()));
}

// pedidos.component.ts — coordina
@Component({
  selector: 'app-pedidos',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TarjetaPedidoComponent],
  template: `
    @if (cargando()) {
      <p>Cargando…</p>
    } @else if (error()) {
      <p role="alert">No se pudieron cargar los pedidos.</p>
    } @else {
      @for (pedido of pedidos(); track pedido.id) {
        <app-tarjeta-pedido [pedido]="pedido" (eliminar)="eliminar($event)" />
      } @empty {
        <p>No hay pedidos.</p>
      }
    }
  `,
})
export class PedidosComponent {
  private readonly servicio = inject(PedidosService);

  protected readonly pedidos = signal<Pedido[]>([]);
  protected readonly cargando = signal(true);
  protected readonly error = signal(false);

  constructor() {
    this.servicio.listar().pipe(takeUntilDestroyed()).subscribe({
      next: (pedidos) => { this.pedidos.set(pedidos); this.cargando.set(false); },
      error: () => { this.error.set(true); this.cargando.set(false); },
    });
  }

  protected eliminar(id: string): void {
    this.servicio.eliminar(id).subscribe(() =>
      this.pedidos.update((lista) => lista.filter((p) => p.id !== id)));
  }
}

// pedido.ts — regla pura, probable sin Angular
export const totalConIva = (pedido: Pedido): number =>
  pedido.items.reduce((suma, i) => suma + i.precio * i.cantidad, 0) * 1.19;
```

Dos notas sobre este ejemplo:

- Desde Angular 22, `OnPush` es la estrategia por defecto de un componente y ya no hace falta declararla. Dejarla escrita no estorba y es necesaria en versiones anteriores.
- La carga con tres señales (`pedidos`, `cargando`, `error`) y una suscripción muestra el mecanismo. Para leer datos, la forma recomendada hoy es un recurso: lo verás en la [sesión 04](../04-datos-asincronos-con-resource/).

## Errores comunes

- Lógica de negocio y llamadas a funciones costosas en la plantilla.
- Componentes que hacen HTTP directamente.
- Olvidar `track` en `@for`, o usar el índice cuando la lista cambia.
- Suscripciones que nunca se cancelan.
- Modificar un `input` en el hijo.
- Un componente de 500 líneas que hace todo.
- No contemplar los estados de carga, vacío y error.

## Taller

Construye una lista de tareas:

1. Componentes: lista (coordina) y elemento (presenta), con `input()` y `output()`.
2. Un servicio con las tareas en memoria.
3. Agregar, marcar como hecha, eliminar y filtrar (todas, pendientes, hechas).
4. El contador de pendientes debe ser un valor derivado, no un campo que se actualiza a mano.
5. Dos rutas: la lista y el detalle de una tarea.
6. Identifica qué lógica quedó en funciones puras y pruébala sin Angular.

Rama `taller/ng-n1-01-angular`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## Preguntas senior

1. ¿Por qué el flujo de datos es unidireccional y qué problema evita?
2. ¿Qué debe ir en un componente y qué en un servicio?
3. ¿Para qué sirve `track` en `@for`?
4. ¿Qué gana un componente con `OnPush`?
5. ¿Cómo evitas fugas de memoria por suscripciones?
