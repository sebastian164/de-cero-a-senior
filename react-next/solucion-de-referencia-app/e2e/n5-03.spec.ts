// Taller nivel-05/03: la funcionalidad de pedidos contra PostgreSQL real, con el filtro en la URL.
import { expect, test } from '@playwright/test';
import { Pool } from 'pg';

const BD = process.env.PEDIDOS_BD_URL ?? 'postgres://verificacion:verificacion@127.0.0.1:49540/pedidos';

test.beforeAll(async () => {
  const pool = new Pool({ connectionString: BD });
  await pool.query("CREATE TABLE IF NOT EXISTS pedidos (id text PRIMARY KEY, cliente_id text NOT NULL, estado text NOT NULL, creado_en timestamptz NOT NULL, total integer NOT NULL)");
  await pool.query("DELETE FROM pedidos WHERE id LIKE 'P-%'");
  await pool.query(`INSERT INTO pedidos VALUES
    ('P-100','u-1','pendiente', now() - interval '1 hour', 32000), ('P-101','u-1','pagado', now() - interval '3 hours', 9000),
    ('P-102','u-1','pagado', now() - interval '30 hours', 85000), ('P-200','u-2','pendiente', now() - interval '2 hours', 64000)`);
  await pool.end();
});

test('sin sesión, la ruta delgada redirige al ingreso', async ({ page }) => {
  await page.goto('/n5/03-arquitectura/pedidos');
  await expect(page).toHaveURL(/\/n5\/03-arquitectura\?ingreso=requerido/);
});

test('el usuario ve solo lo suyo, el filtro vive en la URL y cancelar respeta el plazo', async ({ page }) => {
  await page.goto('/n5/03-arquitectura', { waitUntil: 'networkidle' });
  await page.getByRole('button', { name: 'Ingresar como Ana (u-1)' }).click();
  await expect(page.getByText(/^Sesión:/)).toHaveText('Sesión: u-1');
  await page.getByRole('link', { name: /Pedidos del usuario/ }).click();
  await expect(page.getByText(/^P-100 · pendiente/)).toBeVisible();
  await expect(page.getByText(/P-200/)).toHaveCount(0);                    // el pedido de Luis no aparece

  await page.getByLabel('Estado').selectOption('pagado');
  await expect(page).toHaveURL(/\?estado=pagado$/);                        // estado en la URL
  await expect(page.getByText(/P-100/)).toHaveCount(0);
  await expect(page.getByText(/^P-101 · pagado/)).toBeVisible();
  await page.reload({ waitUntil: 'networkidle' });                         // recargar conserva el filtro
  await expect(page.getByLabel('Estado')).toHaveValue('pagado');

  await page.getByRole('button', { name: 'Cancelar P-102' }).click();     // hace 30 h: fuera de plazo
  await expect(page.getByText('El pedido ya no se puede cancelar')).toBeVisible();
  await page.getByRole('button', { name: 'Cancelar P-101' }).click();     // dentro de plazo
  await expect(page.getByText('Pedido P-101 cancelado')).toBeVisible();
  await expect(page.getByText(/^P-101 ·/)).toHaveCount(0);                // con el filtro "pagado", la lista ya no lo incluye: se refrescó
  await page.getByLabel('Estado').selectOption('cancelado');
  await expect(page.getByText(/^P-101 · cancelado/)).toBeVisible();       // y PostgreSQL lo tiene cancelado
});
