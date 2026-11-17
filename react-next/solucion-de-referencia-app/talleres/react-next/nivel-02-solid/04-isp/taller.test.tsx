import { memo } from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { AvatarAntes, FilaProducto, InsigniaRol, InsigniaRolAntes, Precio, PrecioAntes, type Producto, type Usuario } from './componentes';
import {
  BotonCarritoSeparado, BotonCarritoUnico, InterruptorSeparado, InterruptorUnico, ProveedoresSeparados, ProveedorUnico, renderizados,
} from './contextos';
import { Avatar, ProveedorDeTema, useCambiarTema, useTema } from '@/react-next/n2-04-isp/tema';

const usuario: Usuario = { id: 'u', nombres: 'Ana', apellidos: 'Gómez', correo: 'a@x.example', documento: '1', roles: ['admin'], perfil: { fotoUrl: '/a.png', biografia: '' } };
const producto: Producto = { sku: 'CAF', nombre: 'Café', precio: 18000, existencias: 3, costo: 9000, proveedor: { nit: '9', nombre: 'P' } };

describe('nivel-02/04 · punto 1: propiedades reducidas, mismo resultado', () => {
  it('los tres componentes pintan lo mismo con menos datos', () => {
    const antes = render(<><AvatarAntes usuario={usuario} /><PrecioAntes producto={producto} /><InsigniaRolAntes usuario={usuario} /></>);
    const htmlAntes = antes.container.innerHTML;
    antes.unmount();
    const despues = render(<><Avatar src={usuario.perfil.fotoUrl} alt="Ana Gómez" /><Precio nombre="Café" precio={18000} /><InsigniaRol esAdministrador /></>);
    expect(despues.container.innerHTML).toBe(htmlAntes);
  });
});

// Hijos memorizados: solo se renderizan si cambia un contexto que consumen.
const InterruptorUnicoMemo = memo(InterruptorUnico);
const InterruptorSeparadoMemo = memo(InterruptorSeparado);

describe('nivel-02/04 · punto 2: dividir el contexto elimina renderizados', () => {
  it('con un contexto único, el interruptor de tema se renderiza al cambiar el carrito', async () => {
    renderizados.interruptorUnico = 0;
    render(<ProveedorUnico><InterruptorUnicoMemo /><BotonCarritoUnico /></ProveedorUnico>);
    const inicial = renderizados.interruptorUnico;
    for (let i = 0; i < 3; i++) await userEvent.click(screen.getByRole('button', { name: /Carrito único/ }));
    expect(renderizados.interruptorUnico - inicial).toBe(3);
  });
  it('con contextos separados, no se renderiza', async () => {
    renderizados.interruptorSeparado = 0;
    render(<ProveedoresSeparados><InterruptorSeparadoMemo /><BotonCarritoSeparado /></ProveedoresSeparados>);
    const inicial = renderizados.interruptorSeparado;
    for (let i = 0; i < 3; i++) await userEvent.click(screen.getByRole('button', { name: /Carrito separado/ }));
    expect(screen.getByRole('button', { name: 'Carrito separado (3)' })).toBeInTheDocument();
    expect(renderizados.interruptorSeparado - inicial).toBe(0);
    await userEvent.click(screen.getByRole('button', { name: /Tema separado/ }));
    expect(renderizados.interruptorSeparado - inicial).toBe(1);
  });
  it('el proveedor de tema del README separa estado y acción', async () => {
    function Interruptor() {
      const tema = useTema(); const cambiar = useCambiarTema();
      return <button type="button" onClick={() => cambiar(tema === 'claro' ? 'oscuro' : 'claro')}>{tema}</button>;
    }
    render(<ProveedorDeTema><Interruptor /></ProveedorDeTema>);
    await userEvent.click(screen.getByRole('button', { name: 'claro' }));
    expect(screen.getByRole('button', { name: 'oscuro' })).toBeInTheDocument();
  });
});

describe('nivel-02/04 · punto 4: pasar el objeto completo cuando se usa y se entrega entero', () => {
  it('FilaProducto muestra cuatro campos y entrega el producto completo a quien lo elige', async () => {
    const alElegir = vi.fn();
    render(<ul><FilaProducto producto={producto} onElegir={alElegir} /></ul>);
    expect(screen.getByText(/CAF · 18000 · 3 en stock/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Café' }));
    expect(alElegir).toHaveBeenCalledWith(producto);
  });
});
