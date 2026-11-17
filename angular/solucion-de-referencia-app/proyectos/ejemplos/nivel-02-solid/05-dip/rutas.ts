import { Component, inject } from '@angular/core';
import { Routes } from '@angular/router';
import { AlmacenDeCarrito, appConfig, CarritoService } from './carrito';

@Component({
  template: `<button type="button" (click)="agregar()">Agregar teclado</button><p>Líneas: {{ lineas }}</p>`,
})
class Demo {
  private readonly carrito = inject(CarritoService);
  private readonly almacen = inject(AlmacenDeCarrito);
  protected lineas = this.almacen.leer().length;
  protected agregar() { this.carrito.agregar({ id: 't', nombre: 'Teclado', precio: 1 }); this.lineas = this.almacen.leer().length; }
}
// CarritoService es providedIn: 'root' e inyecta los contratos desde el inyector raíz: el ensamblaje del README
// va en la configuración de la aplicación. Aquí se reproduce en la ruta junto con su propio CarritoService.
export default [{ path: '', component: Demo, providers: [...appConfig.providers, CarritoService] }] satisfies Routes;
