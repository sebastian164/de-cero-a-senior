import { Bloque } from '@app/bloque';
import { DemoOcp } from '@talleres/react-next/nivel-02-solid/02-ocp/usos';
import { VistaNotificacion } from '@/react-next/n2-02-ocp/notificaciones';

export const metadata = { title: 'nivel-02/02 · OCP' };

export default function Pagina() {
  return (
    <main>
      <h1>nivel-02/02 · OCP en React</h1>
      <Bloque titulo="Ejemplo del README: mapa de tipo a componente" origen="proyectos/react-next/n2-02-ocp/notificaciones.tsx">
        <VistaNotificacion notificacion={{ tipo: 'pago', id: '1', valor: 50000 }} />
      </Bloque>
      <Bloque titulo="Taller: Alerta, Tarjeta con huecos, Lista genérica y notificaciones ampliadas" origen="talleres/react-next/nivel-02-solid/02-ocp/">
        <DemoOcp />
      </Bloque>
    </main>
  );
}
