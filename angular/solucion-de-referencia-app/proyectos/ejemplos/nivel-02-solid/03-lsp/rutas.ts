import { Component, inject } from '@angular/core';
import { Routes } from '@angular/router';
import { Almacenamiento, AlmacenamientoLocal, ConfiguracionFija, Lector } from './almacenamiento';

@Component({
  template: `<p>Tema (Lector): {{ tema }}</p><p>Visitas (Almacenamiento): {{ visitas }}</p>`,
})
class Demo {
  private readonly lector = inject(Lector);
  private readonly almacen = inject(Almacenamiento);
  protected readonly tema = this.lector.leer('tema');
  protected readonly visitas: string;
  constructor() {
    this.almacen.guardar('lsp-visitas', String(Number(this.almacen.leer('lsp-visitas') ?? 0) + 1));
    this.visitas = this.almacen.leer('lsp-visitas')!;
  }
}
export default [{
  path: '', component: Demo,
  providers: [{ provide: Lector, useClass: ConfiguracionFija }, { provide: Almacenamiento, useClass: AlmacenamientoLocal }],
}] satisfies Routes;
