import { afterNextRender, Directive, ElementRef, inject, input, output } from '@angular/core';

/** 1. Da el foco al elemento al aparecer. afterNextRender: solo en el navegador, después de pintar. */
@Directive({ selector: '[appAutofoco]' })
export class AutofocoDirective {
  readonly activo = input(true, { alias: 'appAutofoco', transform: (v: boolean | '') => v !== false });
  constructor() {
    const elemento = inject<ElementRef<HTMLElement>>(ElementRef);
    afterNextRender(() => { if (this.activo()) elemento.nativeElement.focus(); });
  }
}

/**
 * 1. Emite cuando se hace clic fuera del elemento.
 *    Escucha en el documento con `host: { '(document:click)' }` (sin addEventListener manual: Angular lo quita al destruir).
 */
@Directive({
  selector: '[appClicFuera]',
  host: { '(document:click)': 'alHacerClic($event)' },
})
export class ClicFueraDirective {
  readonly appClicFuera = output<MouseEvent>();
  private readonly elemento = inject<ElementRef<HTMLElement>>(ElementRef);
  protected alHacerClic(evento: MouseEvent) {
    if (!this.elemento.nativeElement.contains(evento.target as Node)) this.appClicFuera.emit(evento);
  }
}

/** Reemplazo del BaseComponent (taller 4): resaltar el anfitrión al pasar el mouse, como directiva. */
@Directive({
  selector: '[appResaltable]',
  host: { '[class.resaltado]': 'encima', '(mouseenter)': 'encima = true', '(mouseleave)': 'encima = false' },
})
export class ResaltableDirective { protected encima = false; }

