import { Component } from '@angular/core';
import { Routes } from '@angular/router';
import { ComentarioComponent, VolverComponent } from './comentarios.component';

@Component({
  imports: [ComentarioComponent, VolverComponent],
  template: `
    <div>
      <h1>Seguridad: texto, no HTML</h1>
      <app-comentario [comentario]="{ autor: 'mal', texto: peligroso }" />
      <app-volver />
    </div>
  `,
})
class Demo { protected readonly peligroso = '<img src=x onerror="window.__xss=1">'; }
export default [{ path: '', component: Demo }] satisfies Routes;
