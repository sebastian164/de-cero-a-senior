import { Component, signal } from '@angular/core';
import { Routes } from '@angular/router';
import { PedidoLimpioComponent, Usuario } from './limpio';

@Component({
  imports: [PedidoLimpioComponent],
  template: `
    <h1>Taller n4-01: pedido sin cálculos en la plantilla</h1>
    <app-pedido-limpio [pedidoId]="'1'" [usuario]="admin" (edicionSolicitada)="aviso.set('Edición solicitada del pedido ' + $event)" />
    @if (aviso()) { <p role="status">{{ aviso() }}</p> }
  `,
})
class Pagina { protected readonly admin: Usuario = { id: 'u1', rol: 'admin' }; protected readonly aviso = signal(''); }
export default [{ path: '', component: Pagina }] satisfies Routes;
