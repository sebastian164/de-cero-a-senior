import { TestBed } from '@angular/core/testing';
import { render, screen } from '@testing-library/angular';
import { defer, of, throwError } from 'rxjs';
import { DocumentosApi, EditorComponent } from './editor.component';

describe('n3-04 autoguardado (ejemplo)', () => {
  afterEach(() => vi.useRealTimers());
  const escribir = (etiqueta: string, texto: string) => {
    const c = screen.getByLabelText(etiqueta) as HTMLInputElement;
    c.value = texto; c.dispatchEvent(new Event('input'));
  };

  it('espera 800 ms, guarda una vez, y tras dos reintentos fallidos muestra error sin matar el flujo', async () => {
    vi.useFakeTimers();
    let intentos = 0;
    let fallar = true;
    const guardar = vi.fn(() => defer(() => { intentos++; return fallar ? throwError(() => new Error('503')) : of({}); }));
    await render(EditorComponent, { providers: [{ provide: DocumentosApi, useValue: { guardar } }] });
    escribir('Título', 'H'); escribir('Título', 'Ho'); escribir('Título', 'Hola');
    await vi.advanceTimersByTimeAsync(799);
    expect(guardar).not.toHaveBeenCalled();
    await vi.advanceTimersByTimeAsync(1);
    expect(guardar).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(2000);                 // retry: dos reintentos con 1 s de espera
    expect(intentos).toBe(3);
    TestBed.tick();
    expect(screen.getByRole('status')).toHaveTextContent('error');
    fallar = false;                                          // el flujo sigue vivo: el siguiente cambio guarda
    escribir('Cuerpo', 'texto');
    await vi.advanceTimersByTimeAsync(800);
    TestBed.tick();
    expect(screen.getByRole('status')).toHaveTextContent('guardado');
  });
});
