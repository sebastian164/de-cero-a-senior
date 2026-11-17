// Taller nivel-03/04, punto 5: dos componentes piden la misma consulta; en la red debe salir UNA petición.
import { expect, test } from '@playwright/test';
import { guardarMedicion } from './registro';

test('la misma consulta desde dos componentes sale una sola vez en la red', async ({ page }) => {
  const peticiones: string[] = [];
  page.on('request', (r) => { if (r.url().includes('/api/n3-04/pedidos') && r.method() === 'GET') peticiones.push(r.url()); });
  await page.goto('/n3/04-datos', { waitUntil: 'networkidle' });
  await expect(page.getByText('Pendientes:')).toBeVisible();                      // el segundo componente
  await expect(page.getByRole('button', { name: 'Cancelar #101' })).toBeVisible();  // el primero
  guardarMedicion('n3-04.json', 'peticionesDeLaMismaConsulta', { componentes: 2, peticionesEnLaRed: peticiones.length, urls: peticiones });
  expect(peticiones).toHaveLength(1);
});
