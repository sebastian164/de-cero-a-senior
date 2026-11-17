// Contexto: '@/pedidos/datos' que importa el ejemplo del README (nivel-01/03).
import 'server-only';
import { connection } from 'next/server';
import { repositorio } from './servidor';

export async function listarPedidos() {
  await connection(); // dato vivo: se consulta en cada petición
  return repositorio.listar();
}
