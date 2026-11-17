# 02 — OCP en Angular

Explicación completa: [OCP en la ruta Java](../../../java/nivel-02-solid/02-ocp/).

## Idea

Agregar una variante (un tipo de tarjeta, una columna, un método de pago) debería consistir en añadir una pieza, no en abrir un componente lleno de `@if` y agregar otro caso.

## Lo esencial

Mecanismos de extensión que ofrece Angular:

| Mecanismo | Extiende |
|---|---|
| **Proyección de contenido** (`<ng-content>`) | El contenido de un componente, sin que este lo conozca |
| **Plantillas** (`ng-template` + `ngTemplateOutlet`) | Cómo se pinta cada elemento de una lista o tabla |
| **Inyección con `multi: true`** | Un conjunto de implementaciones registradas |
| **`InjectionToken` con estrategias** | Qué implementación se usa, por configuración |
| **Componentes dinámicos** (`ngComponentOutlet`) | Qué componente se muestra según un dato |
| **Directivas y `hostDirectives`** | Comportamiento añadido a un componente existente |
| **Interceptores y guardas** | El tratamiento de peticiones y de la navegación |

## Antes

```ts
@Component({
  selector: 'app-notificacion',
  template: `
    @if (n().tipo === 'pedido') {
      <div class="azul">📦 Pedido {{ n().numero }} confirmado</div>
    } @else if (n().tipo === 'pago') {
      <div class="verde">💳 Pago de {{ n().valor }} recibido</div>
    } @else if (n().tipo === 'alerta') {
      <div class="rojo">⚠️ {{ n().mensaje }}</div>
    }
  `,
})
export class NotificacionComponent { n = input.required<any>(); }
```

Cada tipo nuevo modifica este componente, su plantilla, sus estilos y sus pruebas.

## Después

```ts
// El punto de extensión: un registro de qué componente pinta cada tipo
export interface VistaDeNotificacion { tipo: string; componente: Type<unknown>; }
export const VISTAS_DE_NOTIFICACION = new InjectionToken<VistaDeNotificacion[]>('vistas de notificación');

// Cada tipo es una pieza independiente
@Component({ selector: 'app-notificacion-pedido', template: `<div class="azul">Pedido {{ datos().numero }} confirmado</div>` })
export class NotificacionPedidoComponent { datos = input.required<NotificacionPedido>(); }

// Registro, en la configuración de la aplicación o de la funcionalidad
providers: [
  { provide: VISTAS_DE_NOTIFICACION, multi: true, useValue: { tipo: 'pedido', componente: NotificacionPedidoComponent } },
  { provide: VISTAS_DE_NOTIFICACION, multi: true, useValue: { tipo: 'pago', componente: NotificacionPagoComponent } },
]

// Cerrado: no cambia cuando aparece un tipo nuevo
@Component({
  selector: 'app-notificacion',
  imports: [NgComponentOutlet],
  template: `
    @if (vista(); as v) {
      <ng-container *ngComponentOutlet="v.componente; inputs: { datos: n() }" />
    }
  `,
})
export class NotificacionComponent {
  readonly n = input.required<Notificacion>();
  private readonly vistas = inject(VISTAS_DE_NOTIFICACION);
  protected readonly vista = computed(() => this.vistas.find((v) => v.tipo === this.n().tipo));
}
```

Otra forma, más sencilla y muy frecuente, es la proyección de contenido: una tarjeta que no sabe qué lleva dentro.

```ts
@Component({
  selector: 'app-tarjeta',
  template: `
    <section class="tarjeta">
      <header><ng-content select="[titulo]" /></header>
      <ng-content />
      <footer><ng-content select="[acciones]" /></footer>
    </section>
  `,
})
export class TarjetaComponent {}
```

## Cuándo no

Con dos o tres variantes estables, un `@switch` es más claro que un registro de componentes dinámicos. El mecanismo se justifica cuando las variantes crecen, las aportan equipos distintos o llegan de módulos cargados de forma diferida.

## Errores comunes

- Componentes "configurables" con veinte `input` booleanos (`mostrarBoton`, `conBorde`, `modoCompacto`): es un `switch` disfrazado. Prefiere composición y proyección.
- Abstraer antes de tener la segunda variante.
- Componentes dinámicos donde bastaba proyección.
- Heredar de un componente para modificarlo.

## Taller

1. Construye una tabla genérica: recibe las filas y, por cada columna, una plantilla que define cómo se pinta la celda. La tabla no debe conocer los datos.
2. Construye el registro de notificaciones del ejemplo y agrega un cuarto tipo sin modificar `NotificacionComponent`.
3. Toma un componente tuyo con más de cinco `input` booleanos y rediseñalo con proyección de contenido.
4. Indica en qué caso de los anteriores un `@switch` habría sido mejor.

Rama `taller/ng-n2-02-ocp`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## Preguntas senior

1. ¿Qué mecanismos de extensión ofrece Angular y cuándo usar cada uno?
2. ¿Por qué muchos `input` booleanos son una señal de mal diseño?
3. Proyección de contenido frente a plantillas frente a componentes dinámicos.
4. ¿Qué hace `multi: true`?
