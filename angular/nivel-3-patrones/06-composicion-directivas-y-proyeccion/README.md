# 06 — Composición: directivas, proyección y plantillas

En Angular, "composición sobre herencia" tiene herramientas propias. Aquí aparecen Decorator, Composite y Template Method con forma de plantilla.

## El problema

Hay que reutilizar comportamiento (un *tooltip*, permisos, arrastrar y soltar) o estructura (una tarjeta, un diálogo, una tabla) entre muchos componentes. Las salidas malas: un `BaseComponent` del que todos heredan, o un componente con decenas de opciones.

## Las herramientas

| Herramienta | Patrón que recuerda | Reutiliza |
|---|---|---|
| **Directiva de atributo** | Decorator | Comportamiento añadido a cualquier elemento |
| **`hostDirectives`** | Composición | Directivas aplicadas a un componente desde su definición |
| **Proyección de contenido** (`ng-content`) | Composite | Estructura con huecos |
| **Plantillas** (`ng-template`, `ngTemplateOutlet`) | Template Method | Un esqueleto con partes que define quien lo usa |
| **Directiva estructural** | — | Lógica de mostrar u ocultar |
| **Funciones con `inject()`** | — | Lógica reactiva compartida entre componentes |

## Ejemplo 1: directiva como decorador

```ts
@Directive({ selector: '[appSiTienePermiso]' })
export class SiTienePermisoDirective {
  readonly permiso = input.required<Permiso>({ alias: 'appSiTienePermiso' });

  constructor() {
    const sesion = inject(Sesion);
    const plantilla = inject(TemplateRef);
    const contenedor = inject(ViewContainerRef);
    effect(() => {
      contenedor.clear();
      if (sesion.tienePermiso(this.permiso())) contenedor.createEmbeddedView(plantilla);
    });
  }
}
```

```html
<button *appSiTienePermiso="'pedidos.cancelar'" type="button">Cancelar pedido</button>
```

La regla de permisos está en un lugar y se aplica a cualquier elemento, sin que los componentes sepan de permisos.

## Ejemplo 2: esqueleto con plantillas

```ts
@Component({
  selector: 'app-lista-con-estados',
  imports: [NgTemplateOutlet],
  template: `
    @if (cargando()) {
      <ng-container [ngTemplateOutlet]="plantillaCargando() ?? porDefectoCargando" />
    } @else if (elementos().length === 0) {
      <ng-content select="[vacio]" />
    } @else {
      <ul>
        @for (elemento of elementos(); track identificar()(elemento)) {
          <li><ng-container [ngTemplateOutlet]="plantillaElemento()" [ngTemplateOutletContext]="{ $implicit: elemento }" /></li>
        }
      </ul>
    }
    <ng-template #porDefectoCargando><p>Cargando…</p></ng-template>
  `,
})
export class ListaConEstadosComponent<T> {
  readonly elementos = input.required<readonly T[]>();
  readonly cargando = input(false);
  readonly identificar = input.required<(elemento: T) => unknown>();
  readonly plantillaElemento = contentChild.required<TemplateRef<{ $implicit: T }>>('elemento');
  readonly plantillaCargando = contentChild<TemplateRef<unknown>>('cargando');
}
```

```html
<app-lista-con-estados [elementos]="pedidos()" [cargando]="cargando()" [identificar]="porId">
  <ng-template #elemento let-pedido>{{ pedido.cliente }} — {{ pedido.total | currency }}</ng-template>
  <p vacio>No hay pedidos todavía.</p>
</app-lista-con-estados>
```

El componente fija el algoritmo (cargando, vacío, lista) y quien lo usa define cómo se ve cada parte.

## Ejemplo 3: lógica compartida con una función

```ts
/** Reutilizable en cualquier componente; debe llamarse en un contexto de inyección. */
export function usarParametroDeRuta(nombre: string): Signal<string | null> {
  const ruta = inject(ActivatedRoute);
  return toSignal(ruta.paramMap.pipe(map((p) => p.get(nombre))), { initialValue: null });
}

export class DetallePedidoComponent {
  protected readonly id = usarParametroDeRuta('id');
}
```

Es el equivalente en Angular de un *hook* personalizado: comportamiento compartido sin clase base.

## Cuándo no

- Si el comportamiento se usa en un solo lugar, déjalo en el componente.
- Las plantillas pasadas como parámetro dan flexibilidad y cuestan legibilidad: para dos variantes, un `input` es más claro.
- Evita directivas con efectos sorprendentes sobre el elemento anfitrión.

## Errores comunes

- `BaseComponent` con utilidades que todos heredan.
- Componentes con veinte `input` de configuración en lugar de proyección.
- Directivas que manipulan el DOM directamente (`nativeElement`) cuando había un enlace de plantilla; rompe el renderizado en servidor.
- Proyección de contenido con lógica condicional compleja.
- Directivas estructurales propias que duplican `@if` y `@for`.

## Taller

1. Escribe una directiva `appAutofoco` y una `appClicFuera` (emite cuando se hace clic fuera del elemento).
2. Construye un componente de diálogo con proyección de título, cuerpo y acciones.
3. Construye la lista con estados del ejemplo y úsala con dos tipos de datos distintos.
4. Elimina un `BaseComponent` de un proyecto tuyo (o uno inventado con tres hijos), reemplazándolo por funciones con `inject` y directivas.
5. Aplica una directiva propia a un componente mediante `hostDirectives`.

Rama `taller/ng-n3-06-composicion`.

## Preguntas senior

1. ¿Qué alternativas a la herencia ofrece Angular para compartir comportamiento?
2. Proyección de contenido frente a plantillas como parámetro.
3. ¿Por qué evitar el acceso directo al DOM?
4. ¿Qué reglas debe cumplir una función que usa `inject()`?
