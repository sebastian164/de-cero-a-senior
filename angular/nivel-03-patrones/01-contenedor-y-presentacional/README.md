# 01 — Componentes contenedores y de presentación

## El problema

Un componente que obtiene datos, guarda estado, decide reglas y además pinta no se puede reutilizar en otra pantalla ni probar sin simular servicios. Y un cambio visual obliga a tocar el mismo archivo que la lógica.

## El patrón

| | Presentación ("tonto") | Contenedor ("inteligente") |
|---|---|---|
| Sabe | Cómo se ve algo | Qué datos se necesitan y qué hacer con los eventos |
| Recibe datos por | `input()` | Servicios, almacén de estado, ruta |
| Comunica por | `output()` | Llamadas a servicios |
| Inyecta servicios | No (salvo utilidades de interfaz) | Sí |
| Reutilizable | Mucho | Poco |
| Se prueba | Dándole entradas y mirando el resultado | Con dobles de los servicios |

Regla práctica: la mayoría de los componentes deberían ser de presentación; uno por pantalla o por sección coordina.

## Cómo reconocer que lo necesitas

- Quieres usar la misma tarjeta en dos pantallas con datos de origen distinto.
- No puedes mostrar el componente en un catálogo (Storybook) sin un servidor.
- La prueba de "muestra el botón deshabilitado" exige simular HTTP.

## Ejemplo

```ts
// Presentación: una función de sus entradas
@Component({
  selector: 'app-lista-pedidos',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @for (pedido of pedidos(); track pedido.id) {
      <article [class.urgente]="pedido.urgente">
        <h3>{{ pedido.cliente }}</h3>
        <button type="button" [disabled]="soloLectura()" (click)="cancelar.emit(pedido.id)">Cancelar</button>
      </article>
    } @empty {
      <p>No hay pedidos.</p>
    }
  `,
})
export class ListaPedidosComponent {
  readonly pedidos = input.required<readonly PedidoVista[]>();
  readonly soloLectura = input(false);
  readonly cancelar = output<string>();
}

// Contenedor: conecta con el estado y los servicios
@Component({
  selector: 'app-pagina-pedidos',
  imports: [ListaPedidosComponent],
  template: `<app-lista-pedidos [pedidos]="almacen.pendientes()"
                                [soloLectura]="!sesion.puedeEditar()"
                                (cancelar)="almacen.cancelar($event)" />`,
})
export class PaginaPedidosComponent {
  protected readonly almacen = inject(AlmacenPedidos);
  protected readonly sesion = inject(Sesion);
}
```

## Alternativas y matices

- En componentes pequeños, separar es burocracia: un componente que carga y muestra una cosa puede quedarse entero.
- Con señales e `inject`, la frontera es menos rígida que antes. Sigue siendo útil como criterio: **¿este componente podría mostrarse solo con datos de ejemplo?**
- El exceso contrario es el **taladro de propiedades**: pasar un dato por cinco niveles de componentes de presentación. Soluciones: proyección de contenido, o un contenedor más abajo en el árbol.

## Errores comunes

- Componentes de presentación que inyectan el almacén "solo para una cosa".
- Componentes de presentación que modifican sus entradas.
- Un contenedor gigante con toda la plantilla adentro.
- Emitir eventos con objetos enormes cuando bastaba un identificador.
- Dividir tanto que nadie encuentra dónde está la lógica.

## Taller

1. Divide una pantalla tuya en un contenedor y al menos tres componentes de presentación.
2. Prueba los de presentación sin `TestBed` de servicios: solo entradas y eventos.
3. Monta uno en una página de ejemplos con datos fijos.
4. Encuentra un caso de taladro de propiedades y resuélvelo con proyección de contenido.

Rama `taller/ng-n3-01-contenedor`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## Preguntas senior

1. ¿Qué criterio usas para decidir si un componente inyecta servicios?
2. ¿Qué es el taladro de propiedades y cómo se evita sin acoplar al estado global?
3. ¿Cómo cambió este patrón con las señales?
