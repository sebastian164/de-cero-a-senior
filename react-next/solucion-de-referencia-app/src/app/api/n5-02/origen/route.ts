import { connection } from 'next/server';
import { lecturas, modificar, reiniciar } from '@app/origen-cache';

export async function GET() { await connection(); return Response.json({ lecturas: lecturas() }); }
export async function POST(req: Request) {
  await connection();
  const cuerpo = await req.json() as { id?: string; precio?: number; reiniciar?: boolean };
  if (cuerpo.reiniciar) { reiniciar(); return Response.json({ ok: true }); }
  modificar(String(cuerpo.id), Number(cuerpo.precio));
  return Response.json({ ok: true });
}
