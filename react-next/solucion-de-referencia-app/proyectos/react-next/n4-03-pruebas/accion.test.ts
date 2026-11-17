// ---- react-next/nivel-04-clean-code-y-testing/03-pruebas/README.md (bloque 4), copiado tal cual entre el contexto agregado ----
import { expect, it, vi } from 'vitest';
import { registrar } from '../n4-02-formularios/registro/acciones';
import { existeCorreo } from '../n4-02-formularios/registro/usuarios';
import { aFormData } from './fabricas';

vi.mock('../n4-02-formularios/registro/usuarios', () => ({ existeCorreo: vi.fn(), crearUsuario: vi.fn() }));

it('rechaza un registro con correo ya usado', async () => {
  vi.mocked(existeCorreo).mockResolvedValue(true);
  const datos = aFormData({ correo: 'ana@correo.example', documento: '1234567', clave: 'x'.repeat(12), confirmacion: 'x'.repeat(12) });

  const estado = await registrar({}, datos);

  expect(estado.errores?.correo).toEqual(['Ese correo ya está registrado']);
});
