import { Component } from '@angular/core';
import { render, screen, within } from '@testing-library/angular';
import { readFileSync } from 'node:fs';
import { NotificacionComponent } from './notificaciones';
import { PaginaOcp } from './pagina.component';
import { ColumnaDirective, TablaComponent } from './tabla';
import { VISTA_ENVIO } from './vista-envio';
import { VISTAS_BASE } from './vistas-base';

describe('Taller n2-02 — OCP', () => {
  it('1. la tabla pinta cualquier dato con las plantillas que le pasan', async () => {
    @Component({
      imports: [TablaComponent, ColumnaDirective],
      template: `<app-tabla [filas]="[{ a: 1 }, { a: 2 }]"><ng-template appColumna="Doble" let-f>{{ f.a * 2 }}</ng-template></app-tabla>`,
    })
    class Uso {}
    await render(Uso);
    expect(screen.getByRole('columnheader')).toHaveTextContent('Doble');
    expect(screen.getAllByRole('cell').map((c) => c.textContent?.trim())).toEqual(['2', '4']);
  });

  it('2. el cuarto tipo (envio) se pinta registrándolo, sin tocar NotificacionComponent', async () => {
    await render(NotificacionComponent, { inputs: { n: { tipo: 'envio', transportadora: 'TCC', guia: '9' } }, providers: [...VISTAS_BASE, VISTA_ENVIO] });
    expect(screen.getByText('Tu pedido salió con TCC (guía 9)')).toBeInTheDocument();
    // NotificacionComponent no menciona ningún tipo concreto.
    const fuente = readFileSync('talleres/nivel-02-solid/02-ocp/notificaciones.ts', 'utf8');
    expect(fuente).not.toMatch(/'pedido'|'pago'|'alerta'|'envio'/);
  });

  it('página completa: dos tablas, cuatro notificaciones y tarjetas compuestas', async () => {
    await render(PaginaOcp, { providers: [...VISTAS_BASE, VISTA_ENVIO] });
    const [empleados, productos] = screen.getAllByRole('table');
    expect(within(empleados).getAllByRole('row')).toHaveLength(3);
    expect(within(productos).getByText('Agotado')).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent('Stock bajo');
    expect(screen.getAllByRole('article')).toHaveLength(2);
  });
});
