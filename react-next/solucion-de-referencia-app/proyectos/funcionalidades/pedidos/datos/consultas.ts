// Contexto (nivel-05/03): listarPedidosDelUsuario, la función de acceso que usa la página delgada del README.
import 'server-only';
import { exigirUsuario } from '../../../nucleo/sesion';
import { listarDePostgres } from './repositorio-postgres';

const ESTADOS = ['pendiente', 'pagado', 'cancelado'] as const;

export async function listarPedidosDelUsuario({ estado }: { estado?: string }) {
  const usuario = await exigirUsuario();
  const valido = ESTADOS.find((e) => e === estado);           // lo que llega por la URL se valida
  return listarDePostgres(usuario.id, valido);
}
