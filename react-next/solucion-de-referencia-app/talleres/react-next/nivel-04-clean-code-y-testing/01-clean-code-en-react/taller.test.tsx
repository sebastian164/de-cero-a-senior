import { readFileSync } from 'node:fs';
import { registrarMedicion } from '../../../../scripts/medicion';
import { Profiler } from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ESLint } from 'eslint';
import { describe, expect, it } from 'vitest';
import { PanelPedidosAntes } from './antes/panel-pedidos';
import { PanelPedidos } from './despues/panel-pedidos';
import { cobrarPendientes, puedeCobrar, sumarTotal, type Pedido } from './despues/dominio';
import { PanelConReloj } from './memorizacion';
import { ResumenPedido } from '@/react-next/n4-01-clean-code/resumen-pedido';

const PEDIDOS: Pedido[] = [
  { id: 'a', cliente: 'Ana', total: 100, estado: 'pendiente' },
  { id: 'b', cliente: 'Luis', total: 50, estado: 'pagado' },
];
const cargar = () => Promise.resolve(PEDIDOS);

describe('nivel-04/01 · reglas puras', () => {
  it('total, permiso y cobro', () => {
    expect(sumarTotal(PEDIDOS)).toBe(150);
    expect(puedeCobrar({ rol: 'admin' }, PEDIDOS.slice(0, 1))).toBe(true);
    expect(puedeCobrar(null, PEDIDOS)).toBe(false);
    expect(cobrarPendientes(PEDIDOS).every((p) => p.estado === 'pagado')).toBe(true);
  });
});

describe('nivel-04/01 · el panel limpio hace lo mismo que el original', () => {
  it('después: carga, filtra y cobra con un botón real', async () => {
    const u = userEvent.setup();
    render(<PanelPedidos cargar={cargar} usuario={{ rol: 'admin' }} />);
    expect(await screen.findByText('Total: 150')).toBeInTheDocument();
    expect(screen.getByText('Pendientes por cobrar')).toBeInTheDocument();
    await u.type(screen.getByLabelText('Cliente'), 'Lu');
    expect(screen.getAllByRole('listitem')).toHaveLength(1);
    await u.click(screen.getByRole('button', { name: 'Cobrar pendientes' }));
    expect(screen.queryByText('Pendientes por cobrar')).not.toBeInTheDocument();
  });
  it('antes: con la lista sin pendientes, `pendientes.length && …` pinta un 0 suelto', async () => {
    const { container } = render(<PanelPedidosAntes cargar={() => Promise.resolve([PEDIDOS[1]!])} usuario={null} />);
    await screen.findByText('Total: 50');
    expect(container.querySelector('ul')?.previousSibling?.textContent).toBe('0');
  });
  it('después: sin pendientes no pinta nada extra', async () => {
    const { container } = render(<PanelPedidos cargar={() => Promise.resolve([PEDIDOS[1]!])} usuario={null} />);
    await screen.findByText('Total: 50');
    expect(container.textContent).not.toMatch(/^0|>0</);
    expect(container.querySelector('ul')?.previousSibling?.nodeName).toBe('LABEL');
  });
  it('antes: el cobro es un div clicable, sin rol de botón', async () => {
    render(<PanelPedidosAntes cargar={cargar} usuario={{ rol: 'admin' }} />);
    await screen.findByText('Total: 150');
    expect(screen.queryByRole('button', { name: 'Cobrar pendientes' })).not.toBeInTheDocument();
    expect(screen.getByText('Cobrar pendientes').tagName).toBe('DIV');
  });
  it('error: mensaje anunciado', async () => {
    render(<PanelPedidos cargar={() => Promise.reject(new Error('x'))} usuario={null} />);
    expect(await screen.findByRole('alert')).toHaveTextContent('No se pudieron cargar');
  });
  it('ResumenPedido del README: anatomía y cancelación con confirmación', async () => {
    const u = userEvent.setup();
    const cancelados: string[] = [];
    render(<ResumenPedido pedido={{ id: 'p1', numero: 'P-1', estado: 'pendiente', creadoEn: new Date(), lineas: [{ producto: 'Café', precio: 18000, cantidad: 2 }] }} onCancelar={(id) => cancelados.push(id)} />);
    expect(screen.getByRole('heading', { name: 'Pedido P-1' })).toBeInTheDocument();
    await u.click(screen.getByRole('button', { name: 'Cancelar' }));
    await u.click(screen.getByRole('button', { name: 'Sí, cancelar' }));
    expect(cancelados).toEqual(['p1']);
  });
});

describe('nivel-04/01 · punto 6: medir antes de memorizar', () => {
  async function medir(memorizar: boolean) {
    const duraciones: number[] = [];
    render(<Profiler id="lista" onRender={(_, fase, duracion) => { if (fase !== 'mount') duraciones.push(duracion); }}><PanelConReloj memorizar={memorizar} /></Profiler>);
    const u = userEvent.setup();
    for (let i = 0; i < 5; i++) await u.click(screen.getByRole('button', { name: /^Tic/ }));
    return duraciones.reduce((a, b) => a + b, 0) / duraciones.length;
  }
  it('memo evita volver a renderizar la lista cuando solo cambia el reloj', async () => {
    const sinMemo = await medir(false);
    document.body.innerHTML = '';
    const conMemo = await medir(true);
    registrarMedicion('nivel-04/01 memo en lista costosa (jsdom, modo desarrollo)', { sinMemoMs: Number(sinMemo.toFixed(2)), conMemoMs: Number(conMemo.toFixed(2)) });
    expect(conMemo).toBeLessThan(sinMemo / 3);
  });
});

describe('nivel-04/01 · punto 5: las reglas de hooks y accesibilidad del linter, sin silenciar nada', () => {
  const eslint = new ESLint({ cwd: process.cwd(), ignore: false });   // `antes/` está fuera del lint normal: aquí se lintea a propósito
  const reglas = async (ruta: string) => [...new Set((await eslint.lintFiles([ruta]))[0]!.messages.map((m) => m.ruleId))].sort();
  it('el panel de partida dispara reglas reales', async () => {
    const r = await reglas('talleres/react-next/nivel-04-clean-code-y-testing/01-clean-code-en-react/antes/panel-pedidos.tsx');
    expect(r).toEqual(expect.arrayContaining(['react-hooks/exhaustive-deps', 'react-hooks/set-state-in-effect']));
  });
  it('el panel limpio no dispara ninguna y no silencia nada con eslint-disable', async () => {
    const ruta = 'talleres/react-next/nivel-04-clean-code-y-testing/01-clean-code-en-react/despues/panel-pedidos.tsx';
    expect(await reglas(ruta)).toEqual([]);
    expect(readFileSync(ruta, 'utf8')).not.toMatch(/eslint-disable/);
  });
});
