import { CurrencyPipe } from '@angular/common';
import { Component } from '@angular/core';
import { Routes } from '@angular/router';
import { Notificacion, NotificacionComponent } from './notificaciones';
import { ColumnaDirective, TablaComponent } from './tabla';
import { TarjetaProductoComponent } from './tarjeta-producto';
import { VISTA_ENVIO } from './vista-envio';
import { VISTAS_BASE } from './vistas-base';

interface Empleado { nombre: string; cargo: string; salario: number; }
interface Producto { sku: string; existencias: number; }

@Component({
  imports: [TablaComponent, ColumnaDirective, NotificacionComponent, TarjetaProductoComponent, CurrencyPipe],
  template: `
    <h1>OCP</h1>
    <h2>Tabla con empleados</h2>
    <app-tabla [filas]="empleados">
      <ng-template appColumna="Nombre" let-e><strong>{{ e.nombre }}</strong></ng-template>
      <ng-template appColumna="Cargo" let-e>{{ e.cargo }}</ng-template>
      <ng-template appColumna="Salario" let-e>{{ e.salario | currency: 'COP' : 'symbol-narrow' : '1.0-0' }}</ng-template>
    </app-tabla>
    <h2>La misma tabla con productos</h2>
    <app-tabla [filas]="productos">
      <ng-template appColumna="SKU" let-p><code>{{ p.sku }}</code></ng-template>
      <ng-template appColumna="Existencias" let-p>{{ p.existencias === 0 ? 'Agotado' : p.existencias }}</ng-template>
    </app-tabla>
    <h2>Notificaciones</h2>
    @for (n of notificaciones; track $index) { <app-notificacion [n]="n" /> }
    <h2>Tarjetas por composición</h2>
    <app-tarjeta-producto>
      <img medio src="favicon.ico" alt="Teclado" width="40" height="40" />
      <span nombre>Teclado</span>
      <p>{{ 120000 | currency: 'COP' : 'symbol-narrow' : '1.0-0' }}</p>
      <button acciones type="button">Agregar</button>
    </app-tarjeta-producto>
    <app-tarjeta-producto><span nombre>Mouse (compacta)</span></app-tarjeta-producto>
  `,
})
export class PaginaOcp {
  protected readonly empleados: Empleado[] = [{ nombre: 'Ana', cargo: 'Desarrolladora', salario: 9_000_000 }, { nombre: 'Luis', cargo: 'Diseñador', salario: 7_000_000 }];
  protected readonly productos: Producto[] = [{ sku: 'T-1', existencias: 4 }, { sku: 'M-2', existencias: 0 }];
  protected readonly notificaciones: Notificacion[] = [
    { tipo: 'pedido', numero: 'A-17' }, { tipo: 'pago', valor: '$ 50.000' }, { tipo: 'alerta', mensaje: 'Stock bajo' },
    { tipo: 'envio', transportadora: 'Servientrega', guia: '123' },
  ];
}

export const rutasOcp: Routes = [{ path: '', component: PaginaOcp, providers: [...VISTAS_BASE, VISTA_ENVIO] }];
