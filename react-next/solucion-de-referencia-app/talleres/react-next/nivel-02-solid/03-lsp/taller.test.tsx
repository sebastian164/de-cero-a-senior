import { createRef } from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse, delay } from 'msw';
import { setupServer } from 'msw/node';
import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import { Boton, Campo, Enlace } from './envoltorios';
import { ErrorDeRed, pedidosEnMemoriaConFallo, pedidosHttp } from './repositorio';
import { type Pedido, type RepositorioPedidos } from '@/react-next/n2-03-lsp/repositorio';
import { Campo as CampoReadme } from '@/react-next/n2-03-lsp/campo';
import { CampoAntes } from '@/react-next/n2-03-lsp/campo-antes';

describe('nivel-02/03 · punto 1: envoltorios con el contrato nativo', () => {
  it('Boton: disabled, foco, teclado y ref', async () => {
    const u = userEvent.setup();
    const alClic = vi.fn();
    const ref = createRef<HTMLButtonElement>();
    const { rerender } = render(<Boton ref={ref} onClick={alClic} aria-label="Guardar pedido">💾</Boton>);
    await u.tab();
    expect(screen.getByRole('button', { name: 'Guardar pedido' })).toHaveFocus();
    await u.keyboard('{Enter}');
    await u.keyboard(' ');
    expect(alClic).toHaveBeenCalledTimes(2);
    expect(ref.current?.tagName).toBe('BUTTON');
    rerender(<Boton ref={ref} onClick={alClic} aria-label="Guardar pedido" disabled>💾</Boton>);
    await u.click(screen.getByRole('button'));
    expect(alClic).toHaveBeenCalledTimes(2);
    expect(screen.getByRole('button')).toHaveAttribute('type', 'button');
  });
  it('Boton: type="submit" envía su formulario', async () => {
    const enviar = vi.fn((e: React.FormEvent) => e.preventDefault());
    render(<form onSubmit={enviar}><Boton type="submit">Enviar</Boton></form>);
    await userEvent.click(screen.getByRole('button', { name: 'Enviar' }));
    expect(enviar).toHaveBeenCalledOnce();
  });
  it('Campo: etiqueta asociada, required, error descrito, onBlur, ref y aria-describedby propio', async () => {
    const u = userEvent.setup();
    const alSalir = vi.fn();
    const ref = createRef<HTMLInputElement>();
    render(<><p id="externa">pista</p><Campo ref={ref} etiqueta="Correo" type="email" required name="correo" error="Correo inválido" ayuda="Lo usamos para avisarte" aria-describedby="externa" onBlur={alSalir} /></>);
    const campo = screen.getByLabelText('Correo');
    expect(campo).toBeRequired();
    expect(campo).toBeInvalid();
    expect(campo).toHaveAccessibleDescription('pista Lo usamos para avisarte Correo inválido');
    await u.click(campo);
    await u.tab();
    expect(alSalir).toHaveBeenCalledOnce();
    expect(ref.current).toBe(campo);
  });
  it('Campo del README: se usa donde iría un <input>, incluida la ref', () => {
    const ref = createRef<HTMLInputElement>();
    render(<form><CampoReadme ref={ref} etiqueta="Nombre" name="nombre" defaultValue="Ana" disabled /></form>);
    expect(screen.getByLabelText('Nombre')).toBeDisabled();
    expect(ref.current?.value).toBe('Ana');
  });
  it('Enlace: interno y externo son <a> enfocables con su contrato', async () => {
    render(<><Enlace href="/n1/01-react" aria-current="page">Interno</Enlace><Enlace externo href="https://react.dev" rel="me">Externo</Enlace></>);
    expect(screen.getByRole('link', { name: 'Interno' })).toHaveAttribute('aria-current', 'page');
    const externo = screen.getByRole('link', { name: /Externo/ });
    expect(externo).toHaveAttribute('target', '_blank');
    expect(externo).toHaveAttribute('rel', 'me noopener noreferrer');
    await userEvent.tab();
    expect(screen.getByRole('link', { name: 'Interno' })).toHaveFocus();
  });
});

// ---------- Punto 2: una batería de contrato para todas las implementaciones ----------
type Escenario = 'con-datos' | 'vacio' | 'falla';
function contratoRepositorioPedidos(nombre: string, crear: (e: Escenario) => RepositorioPedidos) {
  describe(`contrato RepositorioPedidos · ${nombre}`, () => {
    it('devuelve los pedidos', async () => {
      const lista = await crear('con-datos').listar();
      expect(lista).toEqual([{ id: '1', cliente: 'Ana', total: 1000 }]);
    });
    it('devuelve [] cuando no hay, nunca null', async () => {
      expect(await crear('vacio').listar()).toEqual([]);
    });
    it('lanza ErrorDeRed cuando no se puede consultar', async () => {
      await expect(crear('falla').listar()).rejects.toBeInstanceOf(ErrorDeRed);
    });
    it('devuelve una copia: modificarla no altera la siguiente consulta', async () => {
      const repo = crear('con-datos');
      const primera = (await repo.listar()) as Pedido[];
      primera.push({ id: 'x', cliente: 'Intruso', total: 0 });
      expect(await repo.listar()).toHaveLength(1);
    });
  });
}

const servidor = setupServer();
beforeAll(() => servidor.listen({ onUnhandledRequest: 'error' }));
afterEach(() => servidor.resetHandlers());
afterAll(() => servidor.close());

contratoRepositorioPedidos('HTTP con red simulada (MSW)', (escenario) => {
  servidor.use(http.get('http://api.test/pedidos', async () => {
    await delay(30); // el simulacro también tarda, como el servidor real
    if (escenario === 'falla') return new HttpResponse(null, { status: 503 });
    if (escenario === 'vacio') return HttpResponse.json(null);
    return HttpResponse.json([{ id: 1, cliente: 'Ana', total: 1000 }]);
  }));
  return pedidosHttp('http://api.test');
});

contratoRepositorioPedidos('en memoria (README)', (escenario) => {
  const semilla: Pedido[] = escenario === 'vacio' ? [] : [{ id: '1', cliente: 'Ana', total: 1000 }];
  return pedidosEnMemoriaConFallo(semilla, { falla: escenario === 'falla' });   // el fallo lo produce la implementación, no la prueba
});

describe('nivel-02/03 · punto 3: un componente que ignora lo que debería aceptar (el Campo «Antes» del README)', () => {
  it('no declara disabled, required, name ni ref, y la etiqueta no está asociada al campo', () => {
    // @ts-expect-error `disabled` no existe en CampoProps: el compilador lo rechaza
    const conDisabled = <CampoAntes etiqueta="Correo" valor="" alCambiar={() => {}} disabled />;
    render(conDisabled);
    expect(screen.getByRole('textbox')).not.toBeDisabled();                  // aunque se fuerce, se descarta en silencio
    expect(screen.queryByLabelText('Correo')).toBeNull();                     // <span>, no <label>: el lector de pantalla no lo anuncia
  });
  it('el Campo «Después» sí lo respeta: disabled, etiqueta asociada y ref', () => {
    const ref = createRef<HTMLInputElement>();
    render(<CampoReadme etiqueta="Correo" disabled ref={ref} />);
    expect(screen.getByLabelText('Correo')).toBeDisabled();
    expect(ref.current).toBe(screen.getByLabelText('Correo'));
  });
});
