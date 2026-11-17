import { connection } from 'next/server';
import { listarLibros } from '@talleres/react-next/nivel-01-fundamentos/03-fundamentos-de-next/datos';

export async function GET() {
  await connection();
  return Response.json(await listarLibros());
}
