import { timingSafeEqual } from 'node:crypto';
import { revalidateTag } from 'next/cache';

// Manejador de ruta (webhook): aquí sí va revalidateTag con perfil, porque quien llama no espera ver su propio cambio.
// Quien llama debe presentar el secreto compartido (cabecera x-secreto-webhook); en producción sería una firma HMAC del cuerpo.
const iguales = (a: string, b: string) => a.length === b.length && timingSafeEqual(Buffer.from(a), Buffer.from(b));

export async function POST(req: Request) {
  const esperado = process.env.SECRETO_WEBHOOK;
  const recibido = req.headers.get('x-secreto-webhook') ?? '';
  if (!esperado || !iguales(recibido, esperado)) return Response.json({ error: 'No autorizado' }, { status: 401 });
  const { etiqueta } = await req.json() as { etiqueta: string };
  revalidateTag(etiqueta, 'max');
  return Response.json({ revalidada: etiqueta });
}
