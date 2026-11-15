# 01 — SRP en Angular

La explicación completa del principio está en la [ruta Java](../../../java/nivel-02-solid/01-srp/). Aquí se aplica a componentes y servicios.

## Idea

Un componente debería cambiar por una sola razón: porque cambia lo que se muestra o cómo se interactúa con ello. Si además cambia cuando cambia la API, una regla de negocio o el formato de una fecha, tiene demasiados dueños.

## Lo esencial

Responsabilidades que suelen amontonarse en un componente:

| Responsabilidad | Dónde debería vivir |
|---|---|
| Presentar y capturar eventos | Componente de presentación |
| Coordinar una pantalla | Componente contenedor |
| Hablar con el servidor | Servicio de datos |
| Reglas de negocio y cálculos | Funciones puras o servicios de dominio |
| Estado compartido | Servicio de estado (almacén) |
| Transformar para mostrar | Tuberías o `computed` |
| Traducir entre el formato de la API y el modelo | Adaptadores o *mappers* |

Señales de que un componente viola SRP: más de 200 o 300 líneas, cuatro o más servicios inyectados, plantilla con lógica, pruebas que necesitan simular medio mundo.

## Antes

```ts
@Component({ selector: 'app-factura', templateUrl: './factura.html' })
export class FacturaComponent {
  private http = inject(HttpClient);
  factura?: any;

  ngOnInit() {
    this.http.get(`/api/facturas/${this.id}`).subscribe((dto: any) => {
      this.factura = { ...dto, fecha: new Date(dto.fecha_emision), total: 0 };
      this.factura.total = dto.lineas.reduce((s: number, l: any) => s + l.valor, 0) * 1.19;   // regla tributaria
    });
  }

  exportarCsv() { /* arma el CSV y dispara la descarga */ }
  enviarPorCorreo() { this.http.post('/api/correos', { ... }).subscribe(); }
  formatearFecha(f: Date) { return `${f.getDate()}/${f.getMonth() + 1}/${f.getFullYear()}`; }
}
```

## Después

```ts
// dominio/factura.ts — reglas puras
export const subtotal = (f: Factura) => f.lineas.reduce((s, l) => s + l.valor, 0);
export const iva = (f: Factura) => subtotal(f) * TARIFA_IVA;
export const total = (f: Factura) => subtotal(f) + iva(f);

// datos/facturas.api.ts — habla con el servidor y traduce
@Injectable({ providedIn: 'root' })
export class FacturasApi {
  private readonly http = inject(HttpClient);
  obtener(id: string): Observable<Factura> {
    return this.http.get<FacturaDto>(`/api/facturas/${id}`).pipe(map(aFactura));
  }
}

// exportacion/exportador-csv.ts — una sola tarea
@Injectable({ providedIn: 'root' })
export class ExportadorCsv {
  exportar(factura: Factura): Blob { /* ... */ }
}

// factura.component.ts — coordina y presenta
@Component({ selector: 'app-factura', changeDetection: ChangeDetectionStrategy.OnPush, /* ... */ })
export class FacturaComponent {
  readonly id = input.required<string>();
  private readonly api = inject(FacturasApi);
  private readonly exportador = inject(ExportadorCsv);

  protected readonly factura = toSignal(toObservable(this.id).pipe(switchMap((id) => this.api.obtener(id))));
  protected readonly total = computed(() => { const f = this.factura(); return f ? total(f) : 0; });
}
```

La fecha se formatea con la tubería `date` en la plantilla. Cambiar la tarifa del IVA toca un archivo que no conoce a Angular.

## Cuándo no dividir

Un componente pequeño que hace una petición y muestra el resultado no necesita cinco archivos. Divide cuando aparezca el segundo motivo de cambio, no antes.

## Errores comunes

- El componente dios de pantalla completa.
- Servicios "utilitarios" que acumulan funciones sin relación.
- Un servicio que es a la vez cliente HTTP, almacén de estado y calculadora.
- Fragmentar en decenas de componentes diminutos sin motivo.
- Lógica de negocio en tuberías o directivas.

## Taller

1. Toma un componente tuyo de más de 200 líneas y lista sus actores: quién pediría cada cambio.
2. Sepáralo en funciones de dominio, servicio de datos y componentes.
3. Escribe una prueba de una regla de negocio que no importe nada de Angular.
4. Indica qué decidiste dejar junto y por qué.
5. Describe tres cambios posibles y qué archivos tocaría cada uno antes y después.

Rama `taller/ng-n2-01-srp`.

## Preguntas senior

1. ¿Qué responsabilidades puede tener legítimamente un componente?
2. ¿Cómo decides entre una función pura, un servicio y un componente?
3. ¿Cuándo dividir un componente empeora el diseño?
4. ¿Qué te dice sobre un componente que inyecte ocho servicios?
