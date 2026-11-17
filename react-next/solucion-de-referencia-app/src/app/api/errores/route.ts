// Colector de errores del navegador (sesiones nivel-03/05 y frontend-senior/02). Guarda en memoria, sin datos personales.
import { connection } from 'next/server';
import { registrarError, listarErrores } from '@talleres/frontend-senior/02-calidad-entrega-y-observabilidad/colector';

export async function POST(peticion: Request) {
  const texto = await peticion.text();
  try {
    registrarError(JSON.parse(texto));
  } catch {
    return new Response(null, { status: 400 });
  }
  return new Response(null, { status: 204 });
}

export async function GET() {
  await connection();
  return Response.json(listarErrores());
}
