import { Component, signal } from '@angular/core';
import { FiltroCategorias, ResumenSeleccion, TarjetaCurso } from './ui';

/** 3. Página de ejemplos (un "Storybook" mínimo): los componentes de presentación con datos fijos, sin servicios. */
@Component({
  imports: [FiltroCategorias, TarjetaCurso, ResumenSeleccion],
  template: `
    <h1>Catálogo de componentes</h1>
    <section><h2>Tarjeta con acciones</h2>
      <app-tarjeta-curso [curso]="{ id: 'x', nombre: 'Curso de muestra', categoria: 'Demo', precio: 99000 }">
        <button acciones type="button">Acción proyectada</button>
      </app-tarjeta-curso></section>
    <section><h2>Tarjeta sin acciones</h2>
      <app-tarjeta-curso [curso]="{ id: 'y', nombre: 'Solo lectura', categoria: 'Demo', precio: 0 }" /></section>
    <section><h2>Resumen vacío y con datos</h2>
      <app-resumen-seleccion [cantidad]="0" [total]="0" />
      <app-resumen-seleccion [cantidad]="2" [total]="450000" /></section>
    <section><h2>Filtro</h2>
      <app-filtro-categorias [categorias]="['A', 'B']" [(elegida)]="elegida" /> <p>Elegida: {{ elegida() }}</p></section>
  `,
})
export class PaginaEjemplos { protected readonly elegida = signal('A'); }
