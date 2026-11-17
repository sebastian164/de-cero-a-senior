import { cookies } from 'next/headers';

// Sesión de demostración: fija la cookie que lee leerSesion().
export async function GET(req: Request) {
  const usuario = new URL(req.url).searchParams.get('usuario') ?? '';
  (await cookies()).set('usuario-demo', usuario, { httpOnly: true, sameSite: 'lax', path: '/' });
  return Response.json({ usuario });
}
