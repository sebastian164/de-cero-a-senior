import { CurrencyPipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { Routes } from '@angular/router';
import { AlmacenPedidos } from './almacen-pedidos';

@Component({
  imports: [CurrencyPipe],
  template: `
    @if (almacen.cargando()) { <p>Cargando…</p> }
    @if (almacen.error(); as e) { <p role="alert">{{ e }}</p> }
    <button type="button" (click)="almacen.filtrar('pendiente')">Solo pendientes</button>
    <button type="button" (click)="almacen.filtrar('todos')">Todos</button>
    <ul>@for (p of almacen.visibles(); track p.id) { <li>{{ p.cliente }} ({{ p.estado }}) <button type="button" (click)="almacen.cancelar(p.id)">Cancelar {{ p.cliente }}</button></li> }</ul>
    <p>Pendiente: {{ almacen.totalPendiente() | currency: 'COP' : 'symbol-narrow' : '1.0-0' }}</p>
  `,
})
class Demo { protected readonly almacen = inject(AlmacenPedidos); constructor() { this.almacen.cargar(); } }
export default [{ path: '', component: Demo }] satisfies Routes;
