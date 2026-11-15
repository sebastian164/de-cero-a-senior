# 05 — DIP en Angular

Explicación completa: [DIP en la ruta Java](../../../java/nivel-02-solid/05-dip/).

## Idea

La lógica de tu aplicación no debería saber si los datos vienen de una API REST, de `localStorage` o de un simulacro. Debería pedir "dame los pedidos" a un contrato, y que la configuración decida quién lo cumple.

## Lo esencial

Angular trae un contenedor de inyección de dependencias. Usarlo no garantiza cumplir DIP: inyectar `HttpClient` en un componente es inyección, pero el componente sigue dependiendo de un detalle.

### Qué se puede usar como contrato

| Contrato | Cómo se inyecta |
|---|---|
| Clase abstracta | `inject(RepositorioPedidos)` |
| `InjectionToken<Interfaz>` | `inject(REPOSITORIO_PEDIDOS)` |
| `InjectionToken` con función de fábrica | Para valores y configuración |

### Proveedores

| Forma | Uso |
|---|---|
| `{ provide: X, useClass: Y }` | Implementación concreta |
| `{ provide: X, useExisting: Y }` | Alias de un servicio ya registrado |
| `{ provide: X, useFactory: () => … }` | Decidir en ejecución |
| `{ provide: X, useValue: v }` | Un valor o un doble |

Se pueden registrar a nivel de aplicación, de ruta (cada funcionalidad con sus implementaciones) o de componente.

### Qué son "detalles" en el frontend

`HttpClient`, `localStorage`, `window`, `document`, `Date.now()`, el enrutador, bibliotecas de terceros (gráficas, mapas), el SDK de analítica.

## Antes

```ts
@Injectable({ providedIn: 'root' })
export class CarritoService {
  private readonly http = inject(HttpClient);

  agregar(producto: Producto) {
    const carrito = JSON.parse(localStorage.getItem('carrito') ?? '[]');   // detalle del navegador
    carrito.push({ ...producto, agregadoEn: Date.now() });                 // reloj del sistema
    localStorage.setItem('carrito', JSON.stringify(carrito));
    (window as any).gtag('event', 'add_to_cart', { id: producto.id });     // SDK de un tercero
    this.http.post('/api/carrito', producto).subscribe();
  }
}
```

No se puede probar sin un navegador, falla en renderizado del lado del servidor (no hay `window` ni `localStorage`) y cambiar de proveedor de analítica toca la lógica del carrito.

## Después

```ts
// Contratos, definidos por la lógica que los necesita
export abstract class AlmacenDeCarrito {
  abstract leer(): LineaCarrito[];
  abstract guardar(lineas: LineaCarrito[]): void;
}
export abstract class Analitica {
  abstract productoAgregado(productoId: string): void;
}
export const RELOJ = new InjectionToken<() => number>('reloj', { factory: () => () => Date.now() });

// Lógica: no conoce el navegador ni a los proveedores
@Injectable({ providedIn: 'root' })
export class CarritoService {
  private readonly almacen = inject(AlmacenDeCarrito);
  private readonly analitica = inject(Analitica);
  private readonly ahora = inject(RELOJ);

  agregar(producto: Producto): void {
    const lineas = [...this.almacen.leer(), { ...producto, agregadoEn: this.ahora() }];
    this.almacen.guardar(lineas);
    this.analitica.productoAgregado(producto.id);
  }
}

// Detalles
@Injectable() export class AlmacenEnLocalStorage extends AlmacenDeCarrito { /* ... */ }
@Injectable() export class AnaliticaGoogle extends Analitica { /* ... */ }

// Ensamblaje
export const appConfig: ApplicationConfig = {
  providers: [
    { provide: AlmacenDeCarrito, useClass: AlmacenEnLocalStorage },
    { provide: Analitica, useClass: AnaliticaGoogle },
  ],
};

// En una prueba
TestBed.configureTestingModule({
  providers: [
    { provide: AlmacenDeCarrito, useClass: AlmacenEnMemoria },
    { provide: Analitica, useValue: { productoAgregado: () => {} } },
    { provide: RELOJ, useValue: () => 1_700_000_000_000 },
  ],
});
```

## Cuándo no

`HttpClient` dentro de un servicio de datos es correcto: ese servicio **es** el detalle. No hace falta un contrato para cada servicio: abstrae en las fronteras con lo que es externo, lento, no determinista o que podría cambiar. Abstraer el enrutador o las tuberías de Angular casi nunca compensa.

## Errores comunes

- `HttpClient`, `localStorage` o `window` usados directamente en componentes y en lógica.
- Contratos que copian la API del proveedor.
- Una interfaz por cada servicio "por si acaso".
- `new Servicio()` dentro de un componente.
- Usar el localizador (`Injector.get`) repartido por el código en lugar de declarar las dependencias.
- No aprovechar los proveedores por ruta para aislar funcionalidades.

## Taller

1. Lista las dependencias ocultas de un servicio tuyo (navegador, reloj, terceros).
2. Aplica DIP y pruébalo sin navegador, con tiempo fijo.
3. Cambia de implementación (por ejemplo, de `localStorage` a memoria) tocando solo la configuración.
4. Registra una implementación distinta para una sola ruta.
5. Indica qué decidiste no abstraer y por qué.

Rama `taller/ng-n2-05-dip`.

## Preguntas senior

1. ¿Por qué usar inyección de dependencias no garantiza cumplir DIP?
2. Clase abstracta frente a `InjectionToken` como contrato.
3. ¿Cómo haces que un servicio funcione en el navegador y en el servidor?
4. ¿Qué abstraerías y qué no en una aplicación Angular?
5. ¿Qué permiten los proveedores a nivel de ruta?
