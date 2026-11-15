# 03 — Componentes con señales: entradas, modelos y consultas

Esta sesión usa las API de componentes basadas en señales, que son la forma recomendada en Angular actual. Los decoradores `@Input`, `@Output` y `@ViewChild` siguen funcionando y los verás en mucho código existente.

## Idea

Un componente es un electrodoméstico. Tiene enchufes por donde le entra corriente (entradas), botones que avisan cuando alguien los pulsa (salidas) y, a veces, un mando que se puede girar desde dentro y desde fuera, como el volumen de un equipo que también se controla con el control remoto (modelo). Cuanto más claros son esos puntos de contacto, más fácil es cambiar el aparato por otro.

## Lo esencial

| API | Qué es | Dirección |
|---|---|---|
| `input()` | Entrada opcional con valor por defecto. Es una señal de solo lectura | Padre → hijo |
| `input.required<T>()` | Entrada obligatoria: no pasarla es un error de compilación | Padre → hijo |
| `input(valor, { transform })` | Entrada que convierte lo que recibe (`numberAttribute`, `booleanAttribute`) | Padre → hijo |
| `output<T>()` | Evento que el hijo emite | Hijo → padre |
| `model()` | Entrada que el hijo también puede escribir. Habilita `[(nombre)]` | En los dos sentidos |
| `viewChild()` / `viewChildren()` | Referencia, como señal, a elementos de la propia plantilla | Hacia dentro |
| `contentChild()` / `contentChildren()` | Referencia a lo que el padre proyectó dentro del componente | Hacia dentro |

Por qué importa que sean señales:

- Una entrada es una señal, así que cualquier valor que dependa de ella se escribe con `computed`. Desaparece `ngOnChanges` como lugar donde "recalcular cosas".
- Una consulta es una señal, así que no hay que esperar a un gancho del ciclo de vida para saber si el elemento ya existe.
- El compilador conoce los tipos: una entrada obligatoria que falta se detecta al compilar, no al ejecutar.

### `model()`: con cuidado

Un `model` rompe a propósito el flujo unidireccional: el hijo cambia un valor que le pertenece al padre. Es correcto para controles (un selector de cantidad, un interruptor, una paginación), donde ese valor *es* el propósito del componente. No es correcto para pasar un objeto del dominio y que el hijo lo edite a su gusto.

### Variables de plantilla con `@let`

```html
@let total = subtotal() + envio();
<p>Total: {{ total | currency }}</p>
@if (total > limite()) { <p role="status">Supera tu cupo.</p> }
```

Evita repetir una expresión. No es un sustituto de `computed`: si el valor tiene lógica, va en la clase, donde se puede probar.

### Estado que depende de una entrada: `linkedSignal`

Cuando el hijo necesita un valor editable que arranca de una entrada y debe reiniciarse si la entrada cambia:

```ts
readonly opciones = input.required<readonly string[]>();
protected readonly elegida = linkedSignal(() => this.opciones()[0]);   // editable; vuelve a la primera si cambian las opciones
```

## Antes

```ts
@Component({
  selector: 'app-selector-cantidad',
  template: `
    <button (click)="restar()">−</button>
    <input #campo type="number" [value]="cantidad" />
    <button (click)="sumar()">+</button>
    <p>{{ etiqueta }}</p>
  `,
})
export class SelectorCantidadComponent implements OnChanges, AfterViewInit {
  @Input() cantidad = 1;
  @Output() cantidadChange = new EventEmitter<number>();
  @Input() maximo: any = 10;
  @ViewChild('campo') campo!: ElementRef<HTMLInputElement>;
  etiqueta = '';

  ngOnChanges(cambios: SimpleChanges) {
    if (cambios['cantidad'] || cambios['maximo']) {
      this.etiqueta = `${this.cantidad} de ${this.maximo}`;     // estado derivado mantenido a mano
    }
  }

  ngAfterViewInit() {
    this.campo.nativeElement.focus();
  }

  sumar() {
    this.cantidad++;                                            // modifica una entrada
    this.cantidadChange.emit(this.cantidad);                    // y hay que acordarse de avisar
  }

  restar() {
    this.cantidad--;                                            // puede bajar de 1 y pasar de maximo
    this.cantidadChange.emit(this.cantidad);
  }
}
```

## Análisis

| Problema | Consecuencia |
|---|---|
| `etiqueta` se recalcula en `ngOnChanges` | Si alguien cambia `cantidad` desde dentro (como hace `sumar`), `ngOnChanges` no se dispara y la etiqueta queda desactualizada |
| `maximo: any` | Llega como texto cuando se escribe `maximo="5"` en la plantilla, y `"5" > 10` da resultados raros |
| `campo!` | El signo de admiración le promete al compilador que existe. Antes de `ngAfterViewInit`, no existe |
| Emitir a mano tras cada cambio | El día que se añade una tercera forma de cambiar la cantidad, se olvida |
| Sin límites | El componente permite estados que no tienen sentido |

## Después

```ts
@Component({
  selector: 'app-selector-cantidad',
  template: `
    @let enElLimite = cantidad() >= maximo();
    <button type="button" (click)="fijar(cantidad() - 1)" [disabled]="cantidad() <= 1">−</button>
    <input #campo type="number" [attr.aria-label]="nombre()"
           [value]="cantidad()" (change)="fijar($any($event.target).valueAsNumber)" />
    <button type="button" (click)="fijar(cantidad() + 1)" [disabled]="enElLimite">+</button>
    <p>{{ etiqueta() }}</p>
    @if (enElLimite) { <p role="status">Llegaste al máximo.</p> }
  `,
})
export class SelectorCantidadComponent {
  readonly cantidad = model(1);                                          // se usa con [(cantidad)]
  readonly maximo = input(10, { transform: numberAttribute });           // "5" llega como 5
  readonly nombre = input.required<string>();                            // sin él no compila

  private readonly campo = viewChild.required<ElementRef<HTMLInputElement>>('campo');
  protected readonly etiqueta = computed(() => `${this.cantidad()} de ${this.maximo()}`);

  constructor() {
    afterNextRender(() => this.campo().nativeElement.focus());           // cuando el elemento ya está en pantalla
  }

  protected fijar(valor: number): void {
    this.cantidad.set(Math.min(this.maximo(), Math.max(1, valor)));      // un solo lugar decide el valor
  }
}
```

En el padre:

```ts
protected readonly unidades = signal(1);
```

```html
<app-selector-cantidad [(cantidad)]="unidades" [maximo]="existencias()" nombre="Cantidad" />
<p>Vas a comprar {{ unidades() }}</p>
```

- `etiqueta` es un `computed`: no puede quedar desactualizada.
- `maximo` se convierte al entrar.
- `nombre` es obligatorio y el compilador lo exige.
- Un solo método cambia el valor y respeta los límites; `model` se encarga de avisar al padre.
- No hay ganchos del ciclo de vida.

El código es ilustrativo: no se compiló al preparar el curso.

## Errores comunes

- Usar `model()` para todo y perder el flujo unidireccional.
- Modificar el contenido de un objeto recibido por `input()`: la señal no cambia y nadie se entera.
- Leer una consulta en el constructor, antes de que exista la vista. `viewChild()` devuelve `undefined` hasta entonces; `viewChild.required()` lanza un error.
- Usar `effect` para copiar una entrada a una señal local, cuando lo que corresponde es `computed` o `linkedSignal`.
- Mezclar en un mismo componente decoradores y funciones de señal sin necesidad.
- Meter lógica en `@let`.

## Taller

Construye un componente de paginación y úsalo en una lista.

1. Entradas: total de elementos (obligatoria), tamaño de página con valor por defecto y conversión a número. Modelo: la página actual.
2. Todo lo derivable es `computed`: número de páginas, si hay anterior y siguiente, el texto "Página 2 de 7".
3. Si el total cambia y la página actual deja de existir, el componente debe corregirse solo. Decide con qué herramienta y justifícalo.
4. Un botón "ir a la primera" que además lleve el foco al primer elemento de la lista, usando una consulta.
5. Usa `@let` donde quite repetición y explica un sitio donde decidiste no usarlo.
6. Escribe la misma interfaz pública con decoradores (`@Input`, `@Output`, `@ViewChild`). Compara: ¿qué errores permite una versión que la otra impide?
7. Migra a señales un componente tuyo que use `ngOnChanges`. Angular trae esquemas de migración: averigua cuáles y revisa a mano lo que produzcan.

Rama `taller/ng-n1-03-componentes`.

## Preguntas senior

1. ¿Qué gana un componente cuando sus entradas son señales?
2. ¿Cuándo es legítimo `model()` y cuándo es una fuga del flujo unidireccional?
3. ¿Por qué `ngOnChanges` es una fuente de errores para el estado derivado?
4. Diferencia entre `computed`, `linkedSignal` y `effect` para reaccionar a una entrada.
5. ¿Cómo diseñas la interfaz pública de un componente reutilizable para que sea difícil usarlo mal?
