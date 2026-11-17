import { Component, signal } from '@angular/core';
import { Routes } from '@angular/router';
import { ResumenComponent } from './resumen';

@Component({
  imports: [ResumenComponent],
  template: `
    <h1>Clean code: resumen del pedido</h1>
    <button type="button" (click)="id.set('1')">Pedido 1</button>
    <button type="button" (click)="id.set('99')">Pedido 99 (falla)</button>
    <app-resumen [pedidoId]="id()" />
  `,
})
class Demo { protected readonly id = signal('1'); }
export default [{ path: '', component: Demo }] satisfies Routes;
