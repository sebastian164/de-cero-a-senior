'use client';
// ---- react-next/nivel-03-patrones/02-hooks-personalizados/README.md (bloque 2), copiado tal cual entre el contexto agregado ----
import { useQuery } from '@tanstack/react-query';
import { listarPedidos, type FiltroPedidos } from '../n3-04-datos/pedidos/datos/esquema';

// El componente no sabe qué biblioteca de datos hay debajo
export function usePedidos(filtro: FiltroPedidos) {
  const consulta = useQuery({
    queryKey: ['pedidos', filtro],
    queryFn: ({ signal }) => listarPedidos(filtro, signal),
  });
  return {
    pedidos: consulta.data ?? [],
    cargando: consulta.isPending,
    error: consulta.error ? 'No se pudieron cargar los pedidos' : null,
    recargar: consulta.refetch,
  };
}
