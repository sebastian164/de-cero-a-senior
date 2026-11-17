// ---- react-next/nivel-04-clean-code-y-testing/03-pruebas/README.md (bloque 3), copiado tal cual entre el contexto agregado ----
import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { afterAll, afterEach, beforeAll, expect, it } from 'vitest';
import { PaginaPedidos } from './pagina-pedidos';
import { renderConProveedores } from './fabricas';

const servidor = setupServer(
  http.get('/api/pedidos', () => HttpResponse.json([{ id: 1, estado: 1, valor_centavos: 12000, fecha_creacion: '2026-04-01T10:00:00Z', cliente: { nombre_completo: 'Ana Gómez' } }])),
);
beforeAll(() => servidor.listen({ onUnhandledRequest: 'error' }));
afterEach(() => servidor.resetHandlers());
afterAll(() => servidor.close());

it('muestra un mensaje y permite reintentar cuando el servidor falla', async () => {
  servidor.use(http.get('/api/pedidos', () => new HttpResponse(null, { status: 500 })));
  renderConProveedores(<PaginaPedidos />);

  expect(await screen.findByRole('alert')).toHaveTextContent(/no se pudieron cargar/i);
  expect(screen.getByRole('button', { name: /reintentar/i })).toBeEnabled();
});

it('muestra los pedidos cuando el servidor responde (handler base del README)', async () => {
  renderConProveedores(<PaginaPedidos />);
  expect(await screen.findByText(/Ana Gómez/)).toBeInTheDocument();
});
