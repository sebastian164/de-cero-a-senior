// Hallazgo sobre el README: la prueba original renderizaba FormularioRegistro (acción de servidor, valida solo al
// enviar) y esperaba un error al salir del campo. Esta prueba conserva la evidencia: con ese formulario no aparece.
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, it, vi } from 'vitest';
import { FormularioRegistro } from '@/react-next/n4-02-formularios/registro/formulario';

vi.mock('@/react-next/n4-02-formularios/registro/acciones', () => ({ registrar: vi.fn(async () => ({})) }));

it('con FormularioRegistro (acción de servidor) no hay error al salir del campo: por eso el README usa la versión en vivo', async () => {
  const usuario = userEvent.setup();
  render(<FormularioRegistro />);
  await usuario.type(screen.getByLabelText('Correo'), 'no-es-correo');
  await usuario.tab();
  await new Promise((r) => setTimeout(r, 200));
  expect(screen.queryByRole('alert')).not.toBeInTheDocument();
});
