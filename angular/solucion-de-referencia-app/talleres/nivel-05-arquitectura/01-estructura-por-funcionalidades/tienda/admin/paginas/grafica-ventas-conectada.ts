import { Component } from '@angular/core';
import { SERIE_HISTORICA } from '../datos/serie-historica';
import { GraficaVentas } from '../ui/grafica-ventas';

/** Contenedor: junta los datos pesados con la presentación. Es lo que se difiere con @defer. */
@Component({ selector: 'app-grafica-ventas-conectada', imports: [GraficaVentas], template: `<app-grafica-ventas [serie]="serie" />` })
export class GraficaVentasConectada { protected readonly serie = SERIE_HISTORICA; }
