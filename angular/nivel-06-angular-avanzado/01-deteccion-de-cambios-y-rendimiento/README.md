# 01 — Detección de cambios y rendimiento

## Idea

Angular tiene que saber cuándo volver a pintar. Puede hacerlo revisando todo cada vez que pasa algo, o revisando solo lo que cambió. Entender ese mecanismo es la diferencia entre una aplicación fluida y una que se arrastra al llegar a mil filas.

## Lo esencial

### Cómo decide Angular cuándo actualizar

| Modelo | Cómo funciona |
|---|---|
| Con Zone.js (histórico) | Intercepta eventos, temporizadores y peticiones; tras cada uno, revisa el árbol de componentes |
| `OnPush` | Un componente solo se revisa si cambió la referencia de una entrada, emitió un evento propio, o cambió una señal que su plantilla lee |
| Sin zona (*zoneless*) | No hay Zone.js: Angular actualiza cuando las señales cambian o se le notifica explícitamente |

Consecuencia práctica: **señales + `OnPush` + datos inmutables** es la combinación que funciona en cualquier modelo. Si modificas un objeto en el sitio, `OnPush` no se entera.

### Qué es lo predeterminado hoy

| Versión | Cambio |
|---|---|
| Angular 21 | Las aplicaciones nuevas se crean sin Zone.js |
| Angular 22 | `OnPush` es la estrategia por defecto de un componente. La antigua estrategia por defecto pasa a llamarse `Eager` y hay que pedirla de forma explícita |

Dos consecuencias para quien mantiene código:

- Al actualizar un proyecto existente, la migración automática marca como `Eager` los componentes que no declaraban estrategia (y cambia `Default` por `Eager` en los que la pedían), para que nada cambie de comportamiento; los que ya eran `OnPush` no se tocan (comprobado actualizando un proyecto de Angular 21 a 22). Esos componentes son tu lista de trabajo pendiente, no un estado final.
- Un proyecto antiguo no se vuelve sin zona por actualizar la versión: se activa con `provideZonelessChangeDetection()` y se retira `zone.js`, después de comprobar que nada dependía de la revisión automática. El camino habitual es llevar primero todos los componentes a `OnPush` y luego quitar la zona.

Dos precisiones comprobadas: en un proyecto sin zona, un componente `Eager` tampoco se actualiza solo cuando cambia un campo normal (solo se pinta si otra cosa provoca una revisión del anfitrión); y, con zona, un componente raíz `OnPush` impide que la revisión baje a sus hijos aunque la zona la dispare.

Lo que se rompe al quitar la zona es siempre lo mismo: estado que cambia fuera de una señal, de una entrada o de un evento de la plantilla. Un `setTimeout` que asigna un campo normal, una biblioteca que llama a tu función desde fuera de Angular, un objeto modificado en el sitio.

### Fuentes habituales de lentitud

| Problema | Solución |
|---|---|
| Llamadas a funciones en la plantilla | `computed` o tubería pura |
| Listas largas | `track` por identificador estable; desplazamiento virtual; paginación |
| Paquete inicial grande | Carga diferida de rutas; `@defer`; revisar dependencias pesadas |
| Trabajo pesado en el hilo principal | *Web Worker*; dividir en trozos |
| Imágenes | `NgOptimizedImage`: dimensiones, carga diferida, prioridad |
| Peticiones repetidas o en cascada | Caché, paralelismo |
| Actualizaciones excesivas | Estado granular; no poner todo en una sola señal gigante |
| Fugas de memoria | Cancelar suscripciones y oyentes |

### `@defer`

```html
@defer (on viewport) {
  <app-grafica-de-ventas [datos]="ventas()" />
} @placeholder {
  <div class="hueco-grafica"></div>
} @loading (minimum 300ms) {
  <app-esqueleto />
}
```

El código del componente se descarga solo cuando hace falta: al entrar en pantalla, al interactuar, al quedar inactivo el navegador o por una condición.

### Métricas web esenciales

| Métrica | Mide | La empeoran |
|---|---|---|
| LCP | Cuándo aparece el contenido principal | Paquetes grandes, imágenes sin optimizar, servidor lento |
| INP | Qué tan rápido responde a la interacción | Tareas largas en el hilo principal |
| CLS | Cuánto salta el diseño | Imágenes sin dimensiones, contenido insertado tarde |

### Método

1. **Medir** en condiciones realistas (un teléfono de gama media con red lenta, no tu portátil).
2. **Encontrar** el cuello de botella con las herramientas del navegador y las de Angular.
3. **Corregir una cosa.**
4. **Volver a medir.**

Optimizar sin medir suele complicar el código sin mejorar nada.

## Ejemplo

```ts
// Antes: se ejecuta en cada revisión, por cada fila
template: `@for (p of pedidos; track $index) { <tr><td>{{ calcularTotal(p) }}</td></tr> }`

// Después
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CurrencyPipe],
  template: `@for (fila of filas(); track fila.id) { <tr><td>{{ fila.total | currency }}</td></tr> }`,
})
export class TablaPedidosComponent {
  readonly pedidos = input.required<readonly Pedido[]>();
  protected readonly filas = computed(() =>
    this.pedidos().map((p) => ({ id: p.id, total: total(p) })));   // se recalcula solo si cambia la lista
}
```

## Errores comunes

- Optimizar sin medir.
- `track $index` en listas que se reordenan o filtran.
- Modificar objetos en el sitio con `OnPush`.
- Una biblioteca de 300 KB para usar una función.
- Todo cargado al inicio.
- Tuberías impuras.
- `effect` que escribe señales en cadena.
- Probar el rendimiento solo en la máquina de desarrollo.

## Taller

1. Mide una aplicación tuya con Lighthouse en perfil móvil y anota LCP, INP y CLS.
2. Analiza el paquete (visualizador de mapas de código) y encuentra las tres dependencias más pesadas.
3. Lleva todos los componentes a `OnPush` (en un proyecto actualizado a Angular 22, busca los marcados como `Eager`) y corrige lo que deje de actualizarse; explica cada caso.
4. Pinta 10.000 filas, mide, y luego aplica desplazamiento virtual.
5. Difiere con `@defer` dos componentes pesados y mide el paquete inicial.
6. Toma una aplicación que todavía use Zone.js, actívale la detección sin zona y documenta qué se rompió y por qué. Si no tienes ninguna, crea una con zona a propósito e introduce tres actualizaciones de estado que dependan de ella.

Rama `taller/ng-n6-01-rendimiento`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## Preguntas senior

1. Explica cómo sabe Angular que debe actualizar la vista, con y sin Zone.js.
2. ¿Por qué `OnPush` exige inmutabilidad?
3. ¿Qué es INP y qué lo degrada?
4. ¿Cómo reduces el paquete inicial de una aplicación grande?
5. ¿Cómo abordas un "la aplicación está lenta" sin más detalle?
