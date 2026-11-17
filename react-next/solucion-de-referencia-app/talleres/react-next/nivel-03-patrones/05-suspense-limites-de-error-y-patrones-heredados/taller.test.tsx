import { createRoot } from 'react-dom/client';
import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { reportados } from '@/react-next/n3-05-suspense/reporte';
import { SeccionPedidos } from '@/react-next/n3-05-suspense/limite-de-error';
import { PaginaProtegida, Pagina } from '@/react-next/n3-05-suspense/hoc-y-hook';
import { ContextoSesion } from '@/react-next/n3-05-suspense/sesion';
import {
  BotonQueFallaConManejo, BotonQueFallaSinManejo, CoordenadasConHook, CoordenadasConRenderProp, Inventario, Recomendaciones, Seccion,
  conRegistro, repararRecomendaciones, tumbarRecomendaciones, useRegistro,
} from './cliente';

afterEach(() => { reportados.length = 0; vi.restoreAllMocks(); });

describe('nivel-03/05 · punto 3: límites de error por sección', () => {
  it('una sección falla, la otra sigue; se reporta y el reintento funciona', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => {}); // React registra el error atrapado
    tumbarRecomendaciones();
    render(<><Seccion nombre="recomendaciones" onReintentar={repararRecomendaciones}><Recomendaciones /></Seccion><Seccion nombre="inventario"><Inventario /></Seccion></>);
    expect(screen.getByRole('alert')).toHaveTextContent('No pudimos cargar recomendaciones.');
    expect(screen.getByText('Inventario: 42 unidades')).toBeInTheDocument();
    expect(reportados[0]).toBe('Servicio de recomendaciones caído');
    await userEvent.click(screen.getByRole('button', { name: 'Reintentar recomendaciones' }));
    expect(screen.getByText('Café de Huila')).toBeInTheDocument();
  });
  it('el bloque del README (ErrorBoundary + Suspense) muestra la alternativa, reporta y reintenta', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => {});
    // RTL envuelve render en act(), y React deja en la cola de act el reintento de un componente que suspendió con use():
    // ese reintento nunca llega. Aquí se monta con createRoot y sin entorno de act, como en el navegador.
    const g = globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean };
    g.IS_REACT_ACT_ENVIRONMENT = false;
    const contenedor = document.body.appendChild(document.createElement('div'));
    const raiz = createRoot(contenedor);
    raiz.render(<SeccionPedidos />);
    const esperar = (ms: number) => new Promise((r) => setTimeout(r, ms));
    await esperar(700);
    expect(screen.getByRole('alert')).toHaveTextContent('No pudimos cargar los pedidos.');
    expect(reportados).toEqual(['Fallo simulado al cargar pedidos']);
    screen.getByRole('button', { name: 'Reintentar' }).click();
    await esperar(700);
    expect(screen.getByText('P-2 pagado')).toBeInTheDocument();
    raiz.unmount();
    contenedor.remove();
    g.IS_REACT_ACT_ENVIRONMENT = true;
  });
});

describe('nivel-03/05 · punto 4: errores en manejadores de eventos', () => {
  it('el límite NO atrapa un error lanzado en un manejador', () => {
    const errores: unknown[] = [];
    const capturar = (e: ErrorEvent) => { errores.push(e.error); e.preventDefault(); };
    window.addEventListener('error', capturar);
    vi.spyOn(console, 'error').mockImplementation(() => {});
    render(<Seccion nombre="botón"><BotonQueFallaSinManejo /></Seccion>);
    fireEvent.click(screen.getByRole('button', { name: 'Guardar sin manejo' }));
    window.removeEventListener('error', capturar);
    expect(screen.queryByRole('alert')).not.toBeInTheDocument(); // la alternativa nunca apareció
    expect(errores).toHaveLength(1);
    expect((errores[0] as Error).message).toBe('Fallo dentro del manejador');
  });
  it('manejado donde ocurre: mensaje en su lugar y reporte', async () => {
    render(<BotonQueFallaConManejo accion={() => Promise.reject(new Error('timeout'))} />);
    await userEvent.click(screen.getByRole('button', { name: 'Guardar con manejo' }));
    expect(screen.getByRole('alert')).toHaveTextContent('No se pudo guardar. Intenta de nuevo.');
    expect(reportados).toEqual(['timeout']);
  });
});

describe('nivel-03/05 · punto 5: HOC y propiedad de renderizado a hooks', () => {
  it('render prop y hook dan el mismo resultado', () => {
    render(<><CoordenadasConRenderProp /><CoordenadasConHook /></>);
    fireEvent.mouseMove(window, { clientX: 10, clientY: 20 });
    expect(screen.getAllByText('10,20')).toHaveLength(2);
  });
  it('HOC de registro y hook de registro', async () => {
    const destinoA: string[] = []; const destinoB: string[] = [];
    const ConHoc = conRegistro(({ registrar }: { registrar: (e: string) => void }) => <button type="button" onClick={() => registrar('clic')}>A</button>, destinoA);
    function ConHook() { const registrar = useRegistro(destinoB); return <button type="button" onClick={() => registrar('clic')}>B</button>; }
    render(<><ConHoc /><ConHook /></>);
    await userEvent.click(screen.getByRole('button', { name: 'A' }));
    await userEvent.click(screen.getByRole('button', { name: 'B' }));
    expect(destinoA).toEqual(destinoB);
  });
  it('el HOC conSesion del README y el hook useSesion protegen igual', () => {
    const { rerender } = render(<><PaginaProtegida /><Pagina /></>);
    expect(screen.getAllByText('Inicia sesión para continuar.')).toHaveLength(2);
    rerender(<ContextoSesion value={{ nombre: 'Ana' }}><PaginaProtegida /><Pagina /></ContextoSesion>);
    expect(screen.getAllByText('Hola, Ana')).toHaveLength(2);
  });
});
