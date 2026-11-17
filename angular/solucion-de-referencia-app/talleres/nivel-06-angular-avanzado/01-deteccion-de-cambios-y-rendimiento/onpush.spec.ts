import { Component, ViewChild, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { C1Eager, C1OnPush, C2Eager, C2OnPush, C2Senal, Carrito } from './onpush';

@Component({
  imports: [C1Eager, C1OnPush, C2Eager, C2OnPush, C2Senal],
  template: `
    <p data-id="tic">{{ tic() }}</p>
    <app-c1-eager [carrito]="carrito" /><app-c1-onpush [carrito]="carrito" /><app-c1-onpush [carrito]="inmutable()" data-fix />
    <app-c2-eager #e /><app-c2-onpush #o /><app-c2-senal #s />
  `,
})
class Anfitrion {
  readonly tic = signal(0);
  readonly carrito: Carrito = { items: [] };
  readonly inmutable = signal<Carrito>({ items: [] });
  @ViewChild('e') e!: C2Eager;
  @ViewChild('o') o!: C2OnPush;
  @ViewChild('s') s!: C2Senal;
}

const texto = (f: { nativeElement: HTMLElement }, id: string) => f.nativeElement.querySelector(`[data-id="${id}"]`)!.textContent;

describe('n6-01 punto 3: Eager frente a OnPush', async () => {
  it('objeto mutado en el sitio: Eager lo pinta, OnPush no; reemplazar la referencia lo corrige', async () => {
    const f = TestBed.createComponent(Anfitrion);
    await f.whenStable();
    f.componentInstance.carrito.items.push('a');            // mutación en el sitio
    f.componentInstance.tic.update((n) => n + 1);           // algo marca el anfitrión como sucio
    await f.whenStable();
    expect(texto(f, 'c1-eager')).toBe('1');
    expect(texto(f, 'c1-onpush')).toBe('0');                // se quedó atrás
    f.componentInstance.inmutable.set({ items: ['a'] });    // corrección: referencia nueva
    await f.whenStable();
    expect(f.nativeElement.querySelectorAll('[data-id="c1-onpush"]')[1].textContent).toBe('1');
  });

  it('campo normal cambiado fuera de la plantilla: Eager y OnPush no se enteran sin señal; con señal, sí', async () => {
    const f = TestBed.createComponent(Anfitrion);
    await f.whenStable();
    f.componentInstance.e.valor = 'nuevo'; f.componentInstance.o.valor = 'nuevo';
    f.componentInstance.s.valor.set('nuevo');
    f.componentInstance.tic.update((n) => n + 1);           // una revisión cualquiera del anfitrión
    await f.whenStable();
    expect(texto(f, 'c2-eager')).toBe('nuevo');             // Eager se revisa con el anfitrión
    expect(texto(f, 'c2-onpush')).toBe('inicial');          // OnPush no: nadie lo marcó
    expect(texto(f, 'c2-senal')).toBe('nuevo');             // la señal lo marca por sí sola
  });
});
