// ---- react-next/nivel-02-solid/04-isp/README.md (bloque 3), copiado tal cual entre el contexto agregado ----
import { obtenerUsuarioActual } from './usuarios';
import { MenuDeUsuario } from './menu-de-usuario';

// Componente de servidor
export default async function Encabezado() {
  const usuario = await obtenerUsuarioActual();            // incluye correo, documento, roles…
  return <MenuDeUsuario nombre={usuario.nombres} fotoUrl={usuario.perfil.fotoUrl} />;   // solo dos campos cruzan
}
