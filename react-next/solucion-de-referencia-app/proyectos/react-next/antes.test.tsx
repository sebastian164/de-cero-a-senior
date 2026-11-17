// Los bloques «Antes» de nivel-01, nivel-02, nivel-05/02 y nivel-06/03 compilan (tsc) y muestran lo que el README dice que hacen mal.
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Pedidos, expuesto as pedidosExpuesto } from './n1-01-fundamentos-de-react/antes/pedidos-antes';
import { Buscador, expuesto as buscadorExpuesto } from './n1-02-hooks-y-efectos/antes/buscador-antes';
import { Factura } from './n2-01-srp/antes/factura-antes';
import { Boton } from './n2-02-ocp/antes/boton-antes';
import { ContextoApp, InterruptorDeTema, conteo, type Carrito, type Tema, type Usuario } from './n2-04-isp/antes/contexto-antes';
import { BotonAgregar } from './n2-05-dip/antes/boton-agregar-antes';
import { obtenerUsuarioActual, apoyo, llamadas } from './n5-02-datos-cache/antes/usuario-actual-antes';
import { Campo } from './n6-03-accesibilidad/antes/campo-antes';

afterEach(() => { vi.unstubAllGlobals(); vi.restoreAllMocks(); localStorage.clear(); });

describe('nivel-01/01 · antes: estado mal usado', () => {
  it('agregar modifica el arreglo del estado en el sitio (misma referencia) y el total es estado aparte', () => {
    render(<Pedidos />);
    const antes = pedidosExpuesto.pedidos!;
    act(() => pedidosExpuesto.agregar!({ id: 1, cliente: 'Ana', valor: 100 }));
    expect(pedidosExpuesto.pedidos).toBe(antes);       // setPedidos(pedidos) recibió la misma referencia
    expect(antes).toHaveLength(1);                     // y aun así tiene el pedido: se mutó
    // Matiz comprobado: la fila aparece solo porque setTotal provoca otro pintado; setPedidos(pedidos) por sí solo no lo habría hecho.
    expect(screen.getByText('Ana - 100')).toBeInTheDocument();
    expect(screen.getByText('Total: 100')).toBeInTheDocument();
  });
  it('la fila es un div clicable: no es un botón para el teclado ni para lectores de pantalla', () => {
    render(<Pedidos />);
    act(() => pedidosExpuesto.agregar!({ id: 1, cliente: 'Ana', valor: 100 }));
    expect(screen.queryByRole('button')).toBeNull();
  });
});

describe('nivel-01/02 · antes: efectos con carreras y dependencias olvidadas', () => {
  beforeEach(() => { buscadorExpuesto.resultados = undefined; });
  function falsoFetch() {
    const pendientes: { url: string; resolver: (v: unknown) => void }[] = [];
    const f = vi.fn((url: string) => new Promise((resolver) => pendientes.push({ url, resolver: (v) => resolver({ json: async () => v }) })));
    vi.stubGlobal('fetch', f);
    return { f, pendientes };
  }
  it('una petición por tecla y una respuesta lenta pisa a la reciente', async () => {
    const { f, pendientes } = falsoFetch();
    render(<Buscador categoria="cafe" />);
    await userEvent.type(screen.getByRole('textbox'), 'ab');
    expect(f).toHaveBeenCalledTimes(3);                // al montar, tras "a" y tras "ab"
    await act(async () => pendientes[2]!.resolver([{ id: 2, nombre: 'respuesta de "ab"' }]));   // la reciente llega primero
    await act(async () => pendientes[1]!.resolver([{ id: 1, nombre: 'respuesta de "a"' }]));    // la lenta llega después y gana
    expect(buscadorExpuesto.resultados).toEqual([{ id: 1, nombre: 'respuesta de "a"' }]);
  });
  it('cambiar la categoría no vuelve a pedir: datos obsoletos', async () => {
    const { f } = falsoFetch();
    const { rerender } = render(<Buscador categoria="cafe" />);
    expect(f).toHaveBeenCalledTimes(1);
    rerender(<Buscador categoria="te" />);
    expect(f).toHaveBeenCalledTimes(1);                // falta 'categoria' en las dependencias
  });
  it('la cantidad es un estado derivado sincronizado con un efecto: un pintado de más', async () => {
    const { pendientes } = falsoFetch();
    render(<Buscador categoria="cafe" />);
    await act(async () => pendientes[0]!.resolver([{ id: 1, nombre: 'x' }, { id: 2, nombre: 'y' }]));
    expect(buscadorExpuesto.cantidad).toBe(2);
  });
});

describe('nivel-02/01 · antes: un componente con todo', () => {
  it('trae, convierte, aplica la regla tributaria y pinta, todo en el JSX; solo se prueba renderizando con fetch simulado', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => ({ json: async () => ({ numero: 'F-1', fecha_emision: '2026-03-05T12:00:00', lineas: [{ valor: 100000 }, { valor: 50000 }] }) })));
    render(<Factura id="1" />);
    expect(screen.getByText('Cargando…')).toBeInTheDocument();
    expect(await screen.findByText('Factura F-1')).toBeInTheDocument();
    expect(screen.getByText(`Total: ${150000 * 1.19}`)).toBeInTheDocument();   // 178500: el IVA del 19 % vive dentro del componente
    expect(screen.getByText('5/3/2026')).toBeInTheDocument();
  });
});

describe('nivel-02/02 · antes: banderas y un if por necesidad', () => {
  it('renderiza las variantes por banderas', () => {
    const { container } = render(<><Boton texto="Guardar" primario conIcono /><Boton texto="Borrar" peligro primario /><Boton texto="Ir" comoEnlace href="/x" peligro /></>);
    const [guardar, borrar] = screen.getAllByRole('button');
    expect(guardar!.firstElementChild).toHaveAttribute('aria-label', 'icono');
    expect(borrar).toHaveClass('btn', 'peligro');
    expect(container).toBeTruthy();
  });
  it('combinaciones sin sentido se aceptan en silencio: peligro y primario a la vez, o peligro como enlace', () => {
    render(<><Boton texto="Borrar" peligro primario /><Boton texto="Ir" comoEnlace href="/x" peligro /></>);
    expect(screen.getByRole('button')).not.toHaveClass('primario');          // peligro gana sobre primario sin avisar
    expect(screen.getByRole('link')).toHaveAttribute('class', 'btn');         // como enlace, "peligro" se ignora
  });
});

describe('nivel-02/04 · antes: interfaces anchas', () => {
  it('el interruptor de tema se vuelve a renderizar cuando solo cambia el carrito', async () => {
    const usuario = { nombres: 'Ana', apellidos: 'Gil', perfil: { fotoUrl: '/a.png' } } as Usuario;
    function Proveedor() {
      const [tema, setTema] = useState<Tema>('claro');
      const [carrito, setCarrito] = useState<Carrito>({ items: [] });
      const valor = { usuario, carrito, tema, avisos: [], setTema, agregarAlCarrito: () => setCarrito({ items: [...carrito.items, 'x'] }) };
      return <ContextoApp value={valor}><InterruptorDeTema /><button onClick={valor.agregarAlCarrito}>agregar</button></ContextoApp>;
    }
    render(<Proveedor />);
    const base = conteo.interruptor;
    await userEvent.click(screen.getByText('agregar'));
    expect(conteo.interruptor).toBe(base + 1);         // el tema no cambió y aun así se pintó de nuevo
  });
});

describe('nivel-02/05 · antes: el componente conoce navegador, reloj, SDK y red', () => {
  it('para probar el clic hay que simular localStorage, Date.now, window.gtag y fetch', () => {
    const gtag = vi.fn(); const f = vi.fn(); vi.stubGlobal('fetch', f);
    (window as unknown as { gtag: unknown }).gtag = gtag;
    vi.spyOn(Date, 'now').mockReturnValue(1234);
    render(<BotonAgregar producto={{ id: 'p1' }} />);
    fireEvent.click(screen.getByText('Agregar'));
    expect(JSON.parse(localStorage.getItem('carrito')!)).toEqual([{ id: 'p1', agregadoEn: 1234 }]);
    expect(gtag).toHaveBeenCalledWith('event', 'add_to_cart', { id: 'p1' });
    expect(f).toHaveBeenCalledWith('/api/carrito', expect.objectContaining({ method: 'POST' }));
  });
  it('sin el SDK de analítica el clic lanza un error y se pierde la llamada a la red', () => {
    const f = vi.fn(); vi.stubGlobal('fetch', f);
    delete (window as unknown as { gtag?: unknown }).gtag;
    render(<BotonAgregar producto={{ id: 'p1' }} />);
    const errores: unknown[] = [];
    const captar = (e: ErrorEvent) => { e.preventDefault(); errores.push(e.error); };
    window.addEventListener('error', captar);
    fireEvent.click(screen.getByText('Agregar'));
    window.removeEventListener('error', captar);
    expect(f).not.toHaveBeenCalled();
    expect(errores).toHaveLength(1);
    expect((errores[0] as Error).message).toMatch(/gtag is not a function/);
  });
});

describe('nivel-05/02 · bloque 1: obtenerUsuarioActual con cache', () => {
  it('devuelve el usuario de la sesión o null sin sesión', async () => {
    apoyo.usuarioId = null;
    expect(await obtenerUsuarioActual()).toBeNull();
    apoyo.usuarioId = 'u-1';
    expect(await obtenerUsuarioActual()).toEqual({ id: 'u-1', nombre: 'Ana' });
    expect(llamadas.buscar).toBeGreaterThan(0);
    // La deduplicación por petición de cache() solo existe dentro de un renderizado de servidor: se comprueba en e2e/n5-02.spec.ts ("cache() de React").
  });
});

describe('nivel-06/03 · bloque 1: Campo accesible', () => {
  it('la etiqueta se asocia al campo y el error se anuncia y se enlaza con aria-describedby', () => {
    render(<Campo etiqueta="Correo" error="Escribe un correo válido" />);
    const campo = screen.getByLabelText('Correo');
    expect(campo).toHaveAttribute('aria-invalid', 'true');
    const alerta = screen.getByRole('alert');
    expect(alerta).toHaveTextContent('Escribe un correo válido');
    expect(campo.getAttribute('aria-describedby')).toBe(alerta.id);
  });
  it('sin error no hay aria-invalid ni aria-describedby', () => {
    render(<Campo etiqueta="Correo" />);
    const campo = screen.getByLabelText('Correo');
    expect(campo).not.toHaveAttribute('aria-invalid');
    expect(campo).not.toHaveAttribute('aria-describedby');
  });
});
void waitFor;
