import { ApplicationConfig, inject, Injectable, InjectionToken } from '@angular/core';

// Tipos que el README usa sin declarar.
export interface Producto { id: string; nombre: string; precio: number; }
export interface LineaCarrito extends Producto { agregadoEn: number; }

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

// Detalles (los cuerpos "/* ... */" del README, escritos)
@Injectable() export class AlmacenEnLocalStorage extends AlmacenDeCarrito {
  leer(): LineaCarrito[] { return JSON.parse(localStorage.getItem('carrito') ?? '[]'); }
  guardar(lineas: LineaCarrito[]): void { localStorage.setItem('carrito', JSON.stringify(lineas)); }
}
@Injectable() export class AnaliticaGoogle extends Analitica {
  productoAgregado(productoId: string): void {
    (window as unknown as { gtag?: (...a: unknown[]) => void }).gtag?.('event', 'add_to_cart', { id: productoId });
  }
}

// Ensamblaje
export const appConfig: ApplicationConfig = {
  providers: [
    { provide: AlmacenDeCarrito, useClass: AlmacenEnLocalStorage },
    { provide: Analitica, useClass: AnaliticaGoogle },
  ],
};

export class AlmacenEnMemoria extends AlmacenDeCarrito {
  private lineas: LineaCarrito[] = [];
  leer() { return [...this.lineas]; }
  guardar(lineas: LineaCarrito[]) { this.lineas = [...lineas]; }
}
