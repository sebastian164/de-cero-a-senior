import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { describe, expect, it } from 'vitest';
import { ComentarioComponent, VolverComponent } from './comentarios.component';

@Component({ template: '' }) class Vacia {}

describe('n6-03 ejemplo', () => {
  it('un comentario con HTML se muestra como texto y no crea elementos', async () => {
    const f = TestBed.createComponent(ComentarioComponent);
    f.componentRef.setInput('comentario', { autor: 'x', texto: '<img src=x onerror="window.__xss=1">' });
    await f.whenStable();
    expect(f.nativeElement.querySelector('img')).toBeNull();
    expect(f.nativeElement.textContent).toContain('<img src=x');
  });

  const destinoPara = async (volverA: string | null) => {
    TestBed.configureTestingModule({ providers: [provideRouter([{ path: 'pagina', component: VolverComponent }, { path: '**', component: Vacia }])] });
    const harness = await RouterTestingHarness.create();
    const f = await harness.navigateByUrl(`/pagina${volverA === null ? '' : `?volverA=${encodeURIComponent(volverA)}`}`, VolverComponent);
    void f; harness.routeNativeElement!.querySelector('button')!.click();
    await harness.fixture.whenStable();
    return TestBed.inject(Router).url;
  };
  it.each([['/pedidos/3', '/pedidos/3'], ['//evil.com', '/'], ['https://evil.com', '/'], ['javascript:alert(1)', '/'], [null, '/']])('volverA=%s → %s', async (entrada, esperado) => {
    expect(await destinoPara(entrada)).toBe(esperado);
  });
});
