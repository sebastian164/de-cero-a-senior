import { connection } from 'next/server';
import { RESENAS } from '@/react-next/n5-01-servidor-cliente/contexto';

export async function GET(_: Request, { params }: { params: Promise<{ id: string }> }) {
  await connection();
  await new Promise((r) => setTimeout(r, 700));
  return Response.json(RESENAS[(await params).id] ?? []);
}
