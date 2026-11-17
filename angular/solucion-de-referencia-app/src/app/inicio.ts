import { Component } from '@angular/core';
import { SESIONES } from './sesiones';

@Component({
  selector: 'app-inicio',
  template: `
    <h1>Soluciones de referencia de la ruta Angular</h1>
    <p>Cada sesión tiene el ejemplo "después" del README, copiado tal cual, y la solución del taller.</p>
    <p>{{ total }} sesiones.</p>
  `,
})
export default class Inicio {
  protected readonly total = SESIONES.length;
}
