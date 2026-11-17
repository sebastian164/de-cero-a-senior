import type { NextRequest } from 'next/server';
import { listarDto } from '@talleres/react-next/nivel-03-patrones/04-capa-de-datos-y-adaptadores/api-simulada';

export async function GET(peticion: NextRequest) {
  await new Promise((r) => setTimeout(r, 150));
  return Response.json(listarDto(peticion.nextUrl.searchParams));
}
