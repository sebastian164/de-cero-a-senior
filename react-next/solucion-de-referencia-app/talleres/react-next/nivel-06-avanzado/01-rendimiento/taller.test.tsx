// Solución del taller de nivel-06/01 (la parte que se prueba con Vitest; las mediciones en navegador están en e2e/n6-01.spec.ts).
import { Profiler, type ProfilerOnRenderCallback } from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { Pagina as PaginaAntesLibro } from '@/react-next/n6-01-rendimiento/bajar-estado-antes';
import { Pagina as PaginaDespuesLibro } from '@/react-next/n6-01-rendimiento/bajar-estado-despues';
import { PaginaAntes, PaginaDespuesChildren, PaginaDespuesEstado, contadores } from './estado-y-children';

async function duracionAlEscribir(ui: React.ReactElement, texto: string) {
  let total = 0; let montaje = 0; let primera = true;
  const alRenderizar: ProfilerOnRenderCallback = (_id, _fase, actual) => { if (primera) { montaje = actual; primera = false; } else total += actual; };
  render(<Profiler id="p" onRender={alRenderizar}>{ui}</Profiler>);
  await userEvent.setup().type(screen.getByRole('textbox'), texto);
  return { montaje, actualizaciones: total };
}

describe('punto 2 y 3: qué se renderiza de más, y bajar el estado', () => {
  it('"Antes" del README: cada tecla vuelve a renderizar la tabla pesada; "Después": no', async () => {
    const antes = await duracionAlEscribir(<PaginaAntesLibro />, 'abcdefgh');
    document.body.innerHTML = '';
    const despues = await duracionAlEscribir(<PaginaDespuesLibro />, 'abcdefgh');
    // Antes: 8 teclas × costo de la tabla. Después: solo cambia el campo.
    expect(antes.actualizaciones).toBeGreaterThan(despues.actualizaciones * 3);
  });

  it('con contadores: el padre con estado vuelve a renderizar al hijo; con el estado abajo, no', async () => {
    const u = userEvent.setup();
    contadores.pesadoAntes = contadores.pesadoDespuesEstado = contadores.pesadoDespuesChildren = 0;
    const a = render(<PaginaAntes />);
    await u.type(screen.getByRole('textbox'), 'hola');
    expect(contadores.pesadoAntes).toBe(1 + 4);            // montaje + una vez por tecla
    a.unmount();
    const b = render(<PaginaDespuesEstado />);
    await u.type(screen.getByRole('textbox'), 'hola');
    expect(contadores.pesadoDespuesEstado).toBe(1);        // solo el montaje
    b.unmount();
  });

  it('children: el contenedor con estado cambia y el contenido que recibió no se vuelve a renderizar', async () => {
    const u = userEvent.setup();
    contadores.pesadoDespuesChildren = 0;
    render(<PaginaDespuesChildren />);
    await u.click(screen.getByRole('button', { name: /Tic/ }));
    await u.click(screen.getByRole('button', { name: /Tic/ }));
    expect(screen.getByRole('button', { name: 'Tic 2' })).toBeInTheDocument();
    expect(contadores.pesadoDespuesChildren).toBe(1);
  });
});
