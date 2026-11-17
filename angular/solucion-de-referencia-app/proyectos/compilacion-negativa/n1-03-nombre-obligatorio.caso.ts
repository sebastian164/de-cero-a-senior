// ESPERA: NG8008
// README n1-03: "nombre es obligatorio y el compilador lo exige".
import { Component } from '@angular/core';
import { SelectorCantidadComponent } from '../ejemplos/nivel-01-fundamentos/03-componentes-con-senales/selector-cantidad.component';

@Component({ selector: 'app-caso', imports: [SelectorCantidadComponent], template: `<app-selector-cantidad [maximo]="3" />` })
export class Caso {}
