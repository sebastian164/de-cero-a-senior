import { Component, inject } from '@angular/core';
import { Routes } from '@angular/router';
import { Avatar, ResumenCompras, Saludo } from './componentes';
import { ContadorPedidosComponent } from './contador.component';
import { usuarioCompleto } from './modelo';
import { proveedoresDeUsuarios, Sesion } from './servicios';

@Component({
  imports: [Avatar, Saludo, ResumenCompras, ContadorPedidosComponent],
  template: `
    <h1>ISP</h1>
    <app-avatar [fotoUrl]="u.perfil.fotoUrl" [nombre]="u.nombres + ' ' + u.apellidos" />
    <app-saludo [datos]="u" />
    <app-resumen-compras [totales]="totales" />
    <app-contador-pedidos />
  `,
})
class Pagina {
  protected readonly u = usuarioCompleto();
  protected readonly totales = this.u.pedidos.map((p) => p.total);
  constructor() { inject(Sesion).iniciar('ana@correo.co', 'x'); }
}
export default [{ path: '', component: Pagina, providers: proveedoresDeUsuarios }] satisfies Routes;
