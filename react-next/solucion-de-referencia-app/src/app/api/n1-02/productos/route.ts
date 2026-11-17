import type { NextRequest } from 'next/server';
import { buscarEnCatalogo, retardo } from '@talleres/react-next/nivel-01-fundamentos/02-hooks-y-efectos/catalogo';

export async function GET(peticion: NextRequest) {
  const q = peticion.nextUrl.searchParams.get('q') ?? '';
  const c = peticion.nextUrl.searchParams.get('c') ?? 'todas';
  await new Promise((r) => setTimeout(r, retardo(q)));
  return Response.json(buscarEnCatalogo(q, c));
}
