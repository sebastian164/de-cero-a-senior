// Punto 3: lo que deja de actualizarse al pasar de Eager a OnPush, y cómo se corrige. Cada caso en pareja.
import { ChangeDetectionStrategy, Component, input, signal } from '@angular/core';

export interface Carrito { items: string[]; }

/** Caso 1: objeto mutado en el sitio. Eager lo ve (por casualidad); OnPush no: la referencia no cambió. */
@Component({ selector: 'app-c1-eager', changeDetection: ChangeDetectionStrategy.Eager, template: `<p data-id="c1-eager">{{ carrito().items.length }}</p>` })
export class C1Eager { readonly carrito = input.required<Carrito>(); }
@Component({ selector: 'app-c1-onpush', changeDetection: ChangeDetectionStrategy.OnPush, template: `<p data-id="c1-onpush">{{ carrito().items.length }}</p>` })
export class C1OnPush { readonly carrito = input.required<Carrito>(); }

/** Caso 2: campo normal que cambia fuera de la plantilla (temporizador, biblioteca externa). OnPush no lo ve; con señal, sí. */
@Component({ selector: 'app-c2-eager', changeDetection: ChangeDetectionStrategy.Eager, template: `<p data-id="c2-eager">{{ valor }}</p>` })
export class C2Eager { valor = 'inicial'; }
@Component({ selector: 'app-c2-onpush', changeDetection: ChangeDetectionStrategy.OnPush, template: `<p data-id="c2-onpush">{{ valor }}</p>` })
export class C2OnPush { valor = 'inicial'; }
@Component({ selector: 'app-c2-senal', changeDetection: ChangeDetectionStrategy.OnPush, template: `<p data-id="c2-senal">{{ valor() }}</p>` })
export class C2Senal { readonly valor = signal('inicial'); }
