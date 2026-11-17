'use client';
// ---- react-next/nivel-03-patrones/01-composicion-y-componentes-compuestos/README.md (bloque 2), copiado tal cual entre el contexto agregado ----
import { Pestanas } from './pestanas';

// Contexto: lo que el uso del README da por supuesto.
const Insignia = ({ children }: { children: React.ReactNode }) => <span className="insignia" aria-label={`${children} pendientes`}>{children}</span>;
const DatosCliente = () => <p>Ana Gómez · ana@correo.example</p>;
const PedidosCliente = () => <ul><li>P-1 pendiente</li><li>P-2 pendiente</li></ul>;

export function UsoPestanas({ pendientes }: { pendientes: number }) {
  return (
<Pestanas inicial="datos">
  <Pestanas.Lista>
    <Pestanas.Pestana id="datos">Datos</Pestanas.Pestana>
    <Pestanas.Pestana id="pedidos">Pedidos <Insignia>{pendientes}</Insignia></Pestanas.Pestana>
  </Pestanas.Lista>
  <Pestanas.Panel id="datos"><DatosCliente /></Pestanas.Panel>
  <Pestanas.Panel id="pedidos"><PedidosCliente /></Pestanas.Panel>
</Pestanas>
  );
}
