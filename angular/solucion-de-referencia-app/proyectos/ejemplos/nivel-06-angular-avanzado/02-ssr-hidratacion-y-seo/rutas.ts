import { Component, inject } from '@angular/core';
import { Routes } from '@angular/router';
import { ALMACEN_LOCAL } from './almacen';
import { Ancho } from './ancho.component';

@Component({
  imports: [Ancho],
  template: `
    <div>
      <h1>SSR: código compatible con el servidor</h1>
      <app-ancho />
      <p>Valor del almacén: <output data-id="almacen">{{ valor }}</output></p>
    </div>
  `,
})
class Demo {
  private readonly almacen = inject(ALMACEN_LOCAL);
  protected valor = (this.almacen.escribir('k', 'guardado'), this.almacen.leer('k'));
}
export default [{ path: '', component: Demo }] satisfies Routes;
