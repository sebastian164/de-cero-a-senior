import { connection } from 'next/server';
import { estaActiva, fijarPorcentaje, porcentajeDe } from '@talleres/frontend-senior/02-calidad-entrega-y-observabilidad/bandera';

export async function GET(req: Request, { params }: { params: Promise<{ nombre: string }> }) {
  await connection();
  const { nombre } = await params;
  const usuario = new URL(req.url).searchParams.get('usuario') ?? '';
  return Response.json({ nombre, porcentaje: porcentajeDe(nombre), activa: estaActiva(nombre, usuario, porcentajeDe(nombre)) });
}
// Cambiar el porcentaje sin desplegar. (Solo para la demostración: un servicio real exigiría autenticación.)
export async function POST(req: Request, { params }: { params: Promise<{ nombre: string }> }) {
  await connection();
  const { porcentaje } = (await req.json()) as { porcentaje: number };
  try { fijarPorcentaje((await params).nombre, Number(porcentaje)); } catch { return new Response(null, { status: 404 }); }
  return new Response(null, { status: 204 });
}
