// ---- react-next/nivel-02-solid/04-isp/README.md (bloque 4), copiado tal cual entre el contexto agregado ----
import 'server-only';
import { connection } from 'next/server';

export interface Usuario {
  id: string; nombres: string; apellidos: string; correo: string; documento: string; roles: string[];
  perfil: { fotoUrl: string; biografia: string };
}
export async function obtenerUsuarioActual(): Promise<Usuario> {
  await connection();
  return { id: 'u-1', nombres: 'Ana', apellidos: 'Gómez', correo: 'ana@correo.example', documento: '1012345678', roles: ['admin'],
           perfil: { fotoUrl: '/avatar.svg', biografia: 'Texto largo que no debe viajar al navegador' } };
}

type ResumenUsuario = Pick<Usuario, 'id' | 'nombres'> & { fotoUrl: string };

export type { ResumenUsuario };
