import { connection } from 'next/server';
import { FACTURAS } from '@talleres/react-next/nivel-02-solid/01-srp/despues/facturas-servidor';

export async function GET(_: Request, { params }: { params: Promise<{ id: string }> }) {
  await connection();
  const factura = FACTURAS[(await params).id];
  return factura ? Response.json(factura) : Response.json({ error: 'no existe' }, { status: 404 });
}

export async function PUT(peticion: Request, { params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const actual = FACTURAS[id];
  if (!actual) return Response.json({ error: 'no existe' }, { status: 404 });
  const cuerpo = (await peticion.json()) as { lineas: { desc: string; valor_centavos: number }[] };
  FACTURAS[id] = { ...actual, lineas: cuerpo.lineas };
  return new Response(null, { status: 204 });
}
