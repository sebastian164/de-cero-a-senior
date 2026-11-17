import { Component, input } from '@angular/core';

// 3. Antes: seis entradas booleanas que se combinan en 64 variantes, la mayoría sin probar.
@Component({
  selector: 'app-tarjeta-producto-configurable',
  template: `
    <article [class.borde]="conBorde()" [class.compacta]="modoCompacto()" [class.destacada]="destacado()">
      @if (mostrarImagen() && !modoCompacto()) { <img [src]="imagen()" [alt]="nombre()" width="80" height="80" /> }
      <h3>{{ nombre() }}</h3>
      @if (mostrarPrecio()) { <p>{{ precio() }}</p> }
      @if (mostrarDescripcion() && !modoCompacto()) { <p>{{ descripcion() }}</p> }
      @if (mostrarBoton()) { <button type="button">Agregar</button> }
    </article>
  `,
})
export class TarjetaProductoConfigurableComponent {
  readonly nombre = input.required<string>();
  readonly precio = input('');
  readonly imagen = input('');
  readonly descripcion = input('');
  readonly mostrarImagen = input(true);
  readonly mostrarPrecio = input(true);
  readonly mostrarDescripcion = input(true);
  readonly mostrarBoton = input(true);
  readonly conBorde = input(false);
  readonly modoCompacto = input(false);
  readonly destacado = input(false);
}

// Después: la tarjeta da la estructura; quien la usa decide qué va en cada hueco.
@Component({
  selector: 'app-tarjeta-producto',
  template: `
    <article class="tarjeta-producto">
      <ng-content select="[medio]" />
      <h3><ng-content select="[nombre]" /></h3>
      <ng-content />
      <div class="acciones"><ng-content select="[acciones]" /></div>
    </article>
  `,
})
export class TarjetaProductoComponent {}
