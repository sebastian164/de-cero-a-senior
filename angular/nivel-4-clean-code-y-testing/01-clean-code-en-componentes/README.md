# 01 — Clean code en componentes y plantillas

Los principios generales (nombres, funciones pequeñas, olores, refactorización) están en el [Nivel 4 de Java](../../../java/nivel-4-clean-code-testing/). Esta sesión los aplica a lo que es propio de Angular.

## Idea

Una plantilla debería leerse como una descripción de la pantalla, y la clase del componente como la lista de lo que el usuario puede hacer en ella. Cuando la plantilla tiene cálculos y la clase tiene manipulación de cadenas, ninguna de las dos cuenta ya esa historia.

## Reglas

| Regla | Mal | Bien |
|---|---|---|
| La plantilla no calcula | `{{ items.filter(i => i.activo).length * 1.19 }}` | `{{ totalActivos() }}` |
| Sin llamadas a métodos costosos en la plantilla | `{{ calcular(pedido) }}` | `computed` o tubería pura |
| Nombres por intención, no por elemento | `(click)="onClick2()"` | `(click)="confirmarPedido()"` |
| Estado mínimo | `cantidad`, `total` y `hayItems` como campos | Solo `items`; lo demás, `computed` |
| Estados explícitos | `cargando`, `error` y `datos` como tres banderas sueltas | Un tipo unión: `'cargando' \| 'error' \| 'listo'` |
| Condiciones con nombre | `@if (u && u.rol === 'admin' && !p.cerrado && p.dueno === u.id)` | `@if (puedeEditar())` |
| Tipos, no `any` | `datos: any` | `datos = signal<Pedido[]>([])` |
| Miembros de la plantilla `protected`, entradas `readonly` | Todo público | La API del componente es solo `input` y `output` |
| Un componente, una tarea | 600 líneas | Extraer hijos de presentación |

### Estados imposibles

Con tres banderas booleanas hay ocho combinaciones y la mayoría no tienen sentido (`cargando` y `error` a la vez). Un tipo unión hace imposibles los estados inválidos:

```ts
type Carga<T> =
  | { estado: 'cargando' }
  | { estado: 'error'; mensaje: string }
  | { estado: 'listo'; datos: T };
```

### Estructura de un componente

Orden recomendado: dependencias inyectadas, entradas y salidas, estado, derivados, constructor y efectos, métodos de la plantilla, métodos privados. Y una convención de nombres de archivo consistente (la guía de estilo oficial cambió sus recomendaciones en versiones recientes: lo importante es que el equipo siga una sola).

## Antes y después

```ts
// Antes
export class ResumenComponent {
  d: any; l = false; e = false; f = '';
  get t() { return this.d ? this.d.items.filter((i: any) => i.nombre.includes(this.f)).reduce((s: number, i: any) => s + i.p * i.c, 0) : 0; }
}

// Después
export class ResumenComponent {
  private readonly api = inject(PedidosApi);

  readonly pedidoId = input.required<string>();
  protected readonly filtro = signal('');

  private readonly carga = toSignal(
    toObservable(this.pedidoId).pipe(switchMap((id) => this.api.obtener(id).pipe(
      map((datos): Carga<Pedido> => ({ estado: 'listo', datos })),
      startWith<Carga<Pedido>>({ estado: 'cargando' }),
      catchError(() => of<Carga<Pedido>>({ estado: 'error', mensaje: 'No se pudo cargar el pedido' })),
    ))),
    { initialValue: { estado: 'cargando' } as Carga<Pedido> },
  );

  protected readonly itemsVisibles = computed(() => {
    const carga = this.carga();
    return carga.estado === 'listo' ? filtrarPorNombre(carga.datos.items, this.filtro()) : [];
  });
  protected readonly total = computed(() => sumarImportes(this.itemsVisibles()));
}
```

## Herramientas

`strict` y `strictTemplates` activados, ESLint con las reglas de Angular, formateador automático, y la opción de `OnPush` por defecto en los esquemas de generación.

## Errores comunes

- *Getters* y métodos con cálculos en la plantilla.
- Banderas booleanas que se contradicen.
- Componentes y servicios llamados `Manager`, `Helper`, `Util`, `Common`.
- Suscripciones manuales donde servía `toSignal`.
- Comentarios en la plantilla explicando condiciones que deberían tener nombre.
- `$any()` y `!` para silenciar errores de tipos.
- Estilos globales que se filtran entre componentes.

## Taller

1. Toma un componente tuyo y elimina todo cálculo de su plantilla.
2. Reemplaza sus banderas de carga por un tipo unión y un `@switch`.
3. Reduce su estado al mínimo: lista qué campos eran derivados.
4. Renombra diez miembros por intención.
5. Activa `strictTemplates` y corrige lo que aparezca.

Rama `taller/ng-n4-01-clean-code`.

## Preguntas senior

1. ¿Por qué evitar llamadas a métodos en la plantilla?
2. ¿Qué son los estados imposibles y cómo los eliminas con tipos?
3. ¿Qué forma parte de la API pública de un componente?
4. ¿Qué reglas automatizarías para no discutirlas en la revisión?
