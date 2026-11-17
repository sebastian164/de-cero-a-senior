import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { agregar, alternar, contarPendientes, eliminar, filtrar, type Tarea } from './tareas';
import { PaginaTareas } from './lista-tareas';
import { ListaConNotas } from './clave-indice';
import { PaginaPedidos, sumar } from '@/react-next/n1-01-fundamentos-de-react/pedidos';

const base: readonly Tarea[] = [
  { id: '1', titulo: 'Leer', hecha: false },
  { id: '2', titulo: 'Correr', hecha: true },
];

describe('nivel-01/01 · lógica pura, sin React', () => {
  it('agrega sin modificar el original e ignora títulos vacíos', () => {
    const nuevas = agregar(base, '3', '  Cocinar ');
    expect(nuevas.at(-1)).toEqual({ id: '3', titulo: 'Cocinar', hecha: false });
    expect(base).toHaveLength(2);
    expect(agregar(base, '4', '   ')).toBe(base);
  });
  it('alterna, elimina, filtra y cuenta', () => {
    expect(alternar(base, '1')[0]?.hecha).toBe(true);
    expect(eliminar(base, '1').map((t) => t.id)).toEqual(['2']);
    expect(filtrar(base, 'pendientes').map((t) => t.id)).toEqual(['1']);
    expect(filtrar(base, 'hechas').map((t) => t.id)).toEqual(['2']);
    expect(filtrar(base, 'todas')).toBe(base);
    expect(contarPendientes(base)).toBe(1);
  });
});

describe('nivel-01/01 · componentes', () => {
  it('agrega, completa, filtra y elimina como usuario', async () => {
    const u = userEvent.setup();
    render(<PaginaTareas />);
    await u.type(screen.getByLabelText('Nueva tarea'), 'Leer');
    await u.click(screen.getByRole('button', { name: 'Agregar' }));
    await u.type(screen.getByLabelText('Nueva tarea'), 'Correr{Enter}');
    expect(screen.getByRole('status')).toHaveTextContent('2 pendientes');
    await u.click(screen.getByRole('checkbox', { name: 'Leer' }));
    expect(screen.getByRole('status')).toHaveTextContent('1 pendientes');
    await u.click(screen.getByRole('radio', { name: 'hechas' }));
    expect(screen.getAllByRole('listitem')).toHaveLength(1);
    await u.click(screen.getByRole('button', { name: 'Eliminar Leer' }));
    expect(screen.getByText('No hay tareas.')).toBeInTheDocument();
  });

  it('con el índice como clave, la nota escrita queda en la fila equivocada', async () => {
    const u = userEvent.setup();
    render(<><ListaConNotas claveIndice /><ListaConNotas claveIndice={false} /></>);
    const [mal, bien] = screen.getAllByRole('list');
    for (const lista of [mal!, bien!]) {
      await u.type(within(lista).getByLabelText('Nota para Ana'), 'nota de Ana');
      await u.click(within(lista).getByRole('button', { name: 'Quitar a Ana' }));
    }
    // Con índice: React reutiliza el <input> de la fila 0 para Luis, que hereda el texto de Ana.
    expect(within(mal!).getByLabelText('Nota para Luis')).toHaveValue('nota de Ana');
    // Con id: el input de Ana se desmonta con su fila.
    expect(within(bien!).getByLabelText('Nota para Luis')).toHaveValue('');
  });

  it('el ejemplo "Después" del README renderiza y suma', () => {
    render(<PaginaPedidos />);
    expect(screen.getByText('No hay pedidos.')).toBeInTheDocument();
    expect(sumar([{ id: 'a', cliente: 'Ana', valor: 1000 }, { id: 'b', cliente: 'Luis', valor: 500 }])).toBe(1500);
  });
});
