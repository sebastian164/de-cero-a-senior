import { Component } from '@angular/core';
import { Routes } from '@angular/router';

// La solución de este taller es una aplicación con renderizado en servidor: proyectos/ssr (proyecto n6-02-ssr),
// construida y servida por su propio servidor Express. Esta pantalla solo resume lo que se comprobó.
@Component({
  template: `
    <div>
      <h1>SSR, hidratación y SEO</h1>
      <p>La aplicación está en <code>proyectos/ssr</code>; las comprobaciones, en <code>taller.e2e.ts</code> de esta carpeta.</p>
      <ol>@for (p of puntos; track p) { <li>{{ p }}</li> }</ol>
    </div>
  `,
})
class Resumen {
  protected readonly puntos = [
    'SSR en una aplicación con parte pública y parte privada',
    'Modos por ruta: prerenderizado (inicio), servidor (detalle), cliente (área privada)',
    'Desajuste de hidratación provocado (HTML inválido) y corregido',
    'Sin window ni localStorage directos: afterNextRender y un almacén inyectable',
    'Las peticiones del servidor no se repiten al hidratar (caché de transferencia)',
    'Título, descripción, vista previa y JSON-LD verificados con JavaScript desactivado',
    'LCP con y sin SSR medido con CPU 4x más lenta y 4G lenta',
  ];
}
export default [{ path: '', component: Resumen }] satisfies Routes;
