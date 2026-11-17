import { connection } from 'next/server';
import { PRODUCTOS } from '@/react-next/n5-01-servidor-cliente/contexto';

// El endpoint "solo para alimentar la página" del ejemplo Antes (nivel-05/01).
export async function GET(_: Request, { params }: { params: Promise<{ id: string }> }) {
  await connection();
  const { id } = await params;
  const p = PRODUCTOS.find((x) => x.id === id);
  if (!p) return Response.json({ error: 'no existe' }, { status: 404 });
  const { costoProveedor, notasInternas, ...publico } = p; void costoProveedor; void notasInternas;
  return Response.json(publico);
}
