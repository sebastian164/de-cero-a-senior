// Taller nivel-05/03, punto 4: la composición elige el adaptador sin tocar el dominio ni el caso de uso.
import { crearCancelarPedido } from '@/funcionalidades/pedidos/aplicacion/cancelar-pedido';
import type { RepositorioPedidos } from '@/funcionalidades/pedidos/dominio/puertos';
import { crearRepositorioMemoria } from './repositorio-memoria';

export function componer(adaptador: 'memoria' | 'postgres', repositorioPostgres: RepositorioPedidos, reloj: () => Date = () => new Date()) {
  const repositorio = adaptador === 'memoria' ? crearRepositorioMemoria() : repositorioPostgres;
  return { repositorio, cancelarPedidoCasoDeUso: crearCancelarPedido(repositorio, reloj) };
}
