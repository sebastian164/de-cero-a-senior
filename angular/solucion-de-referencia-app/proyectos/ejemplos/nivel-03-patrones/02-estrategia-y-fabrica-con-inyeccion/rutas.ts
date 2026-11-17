import { Component, inject } from '@angular/core';
import { Routes } from '@angular/router';
import { CotizadorEnvio, provideEnvios } from './envios';
import { PASARELA } from './pasarela';

@Component({ template: `<p>Estándar 2 kg: {{ estandar }}</p><p>Express 2 kg: {{ express }}</p><p>Pasarela: {{ pasarela }}</p>` })
class Demo {
  private readonly cotizador = inject(CotizadorEnvio);
  protected readonly estandar = this.cotizador.cotizar('estandar', 2);
  protected readonly express = this.cotizador.cotizar('express', 2);
  protected readonly pasarela = inject(PASARELA).nombre;
}
// CotizadorEnvio es providedIn: 'root': las estrategias deben estar en el inyector donde se crea. Se vuelve a proveer en la ruta.
export default [{ path: '', component: Demo, providers: [provideEnvios(), CotizadorEnvio] }] satisfies Routes;
