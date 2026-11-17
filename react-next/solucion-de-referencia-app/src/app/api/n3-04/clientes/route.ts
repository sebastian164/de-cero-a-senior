import { connection } from 'next/server';
import { CLIENTES_INICIALES } from '@talleres/react-next/nivel-03-patrones/04-capa-de-datos-y-adaptadores/api-simulada';

export async function GET() {
  await connection();
  return Response.json(CLIENTES_INICIALES);
}
