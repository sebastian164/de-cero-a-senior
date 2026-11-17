// ---- react-next/nivel-03-patrones/04-capa-de-datos-y-adaptadores/README.md (bloque 3), copiado tal cual entre el contexto agregado ----
'use client';

export function usePedidos(filtro: FiltroPedidos) {
  return useQuery({
    queryKey: ['pedidos', filtro],
    queryFn: ({ signal }) => listarPedidos(filtro, signal),
    staleTime: 30_000,
  });
}

export function useCancelarPedido() {
  const cliente = useQueryClient();
  return useMutation({
    mutationFn: cancelarPedido,
    onMutate: async (id) => {                                   // optimista
      await cliente.cancelQueries({ queryKey: ['pedidos'] });
      const anterior = cliente.getQueriesData<Pedido[]>({ queryKey: ['pedidos'] });
      cliente.setQueriesData<Pedido[]>({ queryKey: ['pedidos'] }, (lista) => lista?.filter((p) => p.id !== id));
      return { anterior };
    },
    onError: (_error, _id, contexto) =>                          // reversa
      contexto?.anterior.forEach(([clave, datos]) => cliente.setQueryData(clave, datos)),
    onSettled: () => cliente.invalidateQueries({ queryKey: ['pedidos'] }),
  });
}

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { cancelarPedido, listarPedidos, type FiltroPedidos, type Pedido } from './datos/esquema';
