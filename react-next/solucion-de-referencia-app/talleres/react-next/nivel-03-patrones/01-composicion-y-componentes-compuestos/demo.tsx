'use client';
import { useState } from 'react';
import { Acordeon, AcordeonDeArreglo, Dialogo, Pestanas } from './compuestos';

export function DemoComposicion() {
  const [confirmado, setConfirmado] = useState(false);
  return (
    <div>
      <h3>Pestañas con teclado</h3>
      <Pestanas inicial="datos">
        <Pestanas.Lista etiqueta="Ficha del cliente">
          <Pestanas.Pestana id="datos">Datos</Pestanas.Pestana>
          <Pestanas.Pestana id="pedidos">Pedidos</Pestanas.Pestana>
          <Pestanas.Pestana id="facturas">Facturas</Pestanas.Pestana>
        </Pestanas.Lista>
        <Pestanas.Panel id="datos">Ana Gómez</Pestanas.Panel>
        <Pestanas.Panel id="pedidos">2 pedidos pendientes</Pestanas.Panel>
        <Pestanas.Panel id="facturas">Sin facturas vencidas</Pestanas.Panel>
      </Pestanas>
      <h3>Acordeón compuesto</h3>
      <Acordeon>
        <Acordeon.Item id="envio"><Acordeon.Encabezado>Envío</Acordeon.Encabezado><Acordeon.Panel>Llega en 2 días.</Acordeon.Panel></Acordeon.Item>
        <Acordeon.Item id="pago"><Acordeon.Encabezado>Pago <small>(con ícono 💳 y lo que quieras)</small></Acordeon.Encabezado><Acordeon.Panel>Tarjeta o PSE.</Acordeon.Panel></Acordeon.Item>
      </Acordeon>
      <h3>Acordeón con arreglo</h3>
      <AcordeonDeArreglo multiple secciones={[{ id: 'a', titulo: 'Garantía', contenido: 'Un año.' }, { id: 'b', titulo: 'Devoluciones', contenido: '30 días.' }]} />
      <h3>Diálogo nativo con huecos</h3>
      <Dialogo disparador="Eliminar cuenta" titulo="¿Eliminar la cuenta?"
        acciones={(cerrar) => (<><button type="button" onClick={cerrar}>Cancelar</button> <button type="button" className="btn-peligro" onClick={() => { setConfirmado(true); cerrar(); }}>Eliminar</button></>)}>
        <p>Esta acción no se puede deshacer.</p>
      </Dialogo>
      {confirmado && <p role="status">Cuenta eliminada (simulado)</p>}
    </div>
  );
}
