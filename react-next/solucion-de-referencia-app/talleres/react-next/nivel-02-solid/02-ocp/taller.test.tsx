import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { createRef } from 'react';
import { DemoOcp } from './usos';
import { Alerta } from './alerta';
import { AlertaAntes } from './alerta-antes';
import { Boton } from '@/react-next/n2-02-ocp/boton';
import { VistaNotificacion } from '@/react-next/n2-02-ocp/notificaciones';

describe('nivel-02/02 · OCP', () => {
  it('la lista genérica sirve para dos tipos y la tarjeta acepta huecos', () => {
    render(<DemoOcp />);
    expect(screen.getByRole('heading', { name: 'Usuarios' })).toBeInTheDocument();
    expect(screen.getByText('Ana <ana@correo.example>')).toBeInTheDocument();
    expect(screen.getByText('Café: 18000')).toBeInTheDocument();
    expect(screen.getByText('Sin productos agotados.')).toBeInTheDocument();
    expect(screen.getByText('2 usuarios')).toBeInTheDocument();
  });
  it('el tipo de notificación agregado tiene su vista', () => {
    render(<DemoOcp />);
    expect(screen.getByText('Envío GU-778 con Servientrega')).toBeInTheDocument();
    expect(screen.getByText('Nuevo pedido P-10')).toBeInTheDocument();
  });
  it('la alerta se extiende por huecos (acción) sin propiedades booleanas', async () => {
    render(<DemoOcp />);
    expect(screen.getByRole('status')).toHaveTextContent('Pedido guardado');
    await userEvent.click(screen.getByRole('button', { name: 'Descartar' }));
    expect(screen.queryByText('Pedido guardado')).not.toBeInTheDocument();
  });
  it('la alerta de error se anuncia como alert y reenvía atributos nativos', () => {
    render(<Alerta variante="error" id="a1" className="extra">Falló</Alerta>);
    const alerta = screen.getByRole('alert');
    expect(alerta).toHaveAttribute('id', 'a1');
    expect(alerta).toHaveClass('alerta', 'alerta-error', 'extra');
  });
  it('la versión de partida permitía estados imposibles', () => {
    const { container } = render(<AlertaAntes texto="x" exito error />);
    expect(container.firstElementChild).toHaveClass('error'); // "éxito" y "error" a la vez: gana uno en silencio
  });
  it('Boton del README reenvía disabled, type, aria-* y ref', async () => {
    const alClic = vi.fn();
    const ref = createRef<HTMLButtonElement>();
    render(<Boton ref={ref} variante="primario" disabled aria-describedby="ayuda" onClick={alClic} className="x">Guardar</Boton>);
    const boton = screen.getByRole('button', { name: 'Guardar' });
    expect(boton).toBeDisabled();
    expect(boton).toHaveAttribute('type', 'button');
    expect(boton).toHaveClass('btn', 'btn-primario', 'x');
    expect(ref.current).toBe(boton);
    await userEvent.click(boton);
    expect(alClic).not.toHaveBeenCalled();
  });
  it('VistaNotificacion del README elige el componente por tipo', () => {
    render(<VistaNotificacion notificacion={{ tipo: 'alerta', id: '1', texto: 'Stock bajo' }} />);
    expect(screen.getByRole('status')).toHaveTextContent('Alerta: Stock bajo');
  });
});
