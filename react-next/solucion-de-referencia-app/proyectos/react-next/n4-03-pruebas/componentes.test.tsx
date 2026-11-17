// ---- react-next/nivel-04-clean-code-y-testing/03-pruebas/README.md (bloque 2), copiado tal cual entre el contexto agregado ----
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, it, vi } from 'vitest';
import { ListaPedidos } from './lista-pedidos';
import { unPedido } from './fabricas';
import { FormularioRegistroEnVivo } from '../n4-02-formularios/registro/formulario-en-vivo';

vi.mock('../n4-02-formularios/registro/acciones-en-vivo', () => ({ registrarEnServidor: vi.fn(async () => ({})) }));

it('avisa cuando se cancela un pedido', async () => {
  const usuario = userEvent.setup();
  const alCancelar = vi.fn();
  render(<ListaPedidos pedidos={[unPedido({ id: 'p1', cliente: 'Ana' })]} onCancelar={alCancelar} />);

  await usuario.click(screen.getByRole('button', { name: /cancelar/i }));

  expect(alCancelar).toHaveBeenCalledWith('p1');
});

it('muestra el error junto al campo al salir de él', async () => {
  const usuario = userEvent.setup();
  render(<FormularioRegistroEnVivo />);          // la versión con biblioteca: valida al salir del campo

  await usuario.type(screen.getByLabelText('Correo'), 'no-es-correo');
  await usuario.tab();

  expect(await screen.findByRole('alert')).toHaveTextContent('Escribe un correo válido');
  expect(screen.getByLabelText('Correo')).toBeInvalid();
});
