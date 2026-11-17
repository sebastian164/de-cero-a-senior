import { connection } from 'next/server';
import { listarMetricas, registrarMetrica } from '@talleres/frontend-senior/02-calidad-entrega-y-observabilidad/colector';

export async function POST(peticion: Request) {
  try { registrarMetrica(JSON.parse(await peticion.text())); } catch { return new Response(null, { status: 400 }); }
  return new Response(null, { status: 204 });
}
export async function GET() { await connection(); return Response.json(listarMetricas()); }
