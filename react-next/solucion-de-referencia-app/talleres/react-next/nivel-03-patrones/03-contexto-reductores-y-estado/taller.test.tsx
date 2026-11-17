import { createContext, memo, Profiler, useContext, useReducer } from 'react';
import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { reductorCarrito, totalCarrito, type AccionCarrito, type EstadoCarrito } from '@/react-next/n3-03-carrito/reductor';
import { ProveedorDeCarrito as ProveedorReadme, useAccionesDeCarrito as useAccionesReadme, useCarrito as useCarritoReadme } from '@/react-next/n3-03-carrito/proveedor';
import { ContadorCarrito, useAlmacenCarrito } from '@/react-next/n3-03-carrito/almacen';
import { ProveedorDeCarrito, persistenciaLocal, type Persistencia } from './carrito';
import { BotonAgregar, CampoCupon, ResumenCarrito, renders } from './componentes';
import { descuentoCupon, reductorAmpliado, ESTADO_INICIAL } from './reductor-ampliado';
import { PaginaCompuesta, PaginaConTaladro } from './composicion';

const cafe = { id: 'cafe', nombre: 'Café', precio: 18000 };
const pan = { id: 'pan', nombre: 'Pan', precio: 3500 };

describe('nivel-03/03 · punto 1: el reductor del README, caso por caso, sin React', () => {
  const vacio: EstadoCarrito = { lineas: [] };
  it('agregado: nueva línea con cantidad 1', () => {
    expect(reductorCarrito(vacio, { tipo: 'agregado', producto: cafe }).lineas).toEqual([{ productoId: 'cafe', nombre: 'Café', precio: 18000, cantidad: 1 }]);
  });
  it('agregado de un producto existente: suma 1 sin tocar las demás líneas', () => {
    const e1 = reductorCarrito(reductorCarrito(vacio, { tipo: 'agregado', producto: cafe }), { tipo: 'agregado', producto: pan });
    const e2 = reductorCarrito(e1, { tipo: 'agregado', producto: cafe });
    expect(e2.lineas.map((l) => l.cantidad)).toEqual([2, 1]);
    expect(e2.lineas[1]).toBe(e1.lineas[1]);
  });
  it('cantidadCambiada: actualiza, y con 0 o menos quita la línea', () => {
    const e = reductorCarrito(vacio, { tipo: 'agregado', producto: cafe });
    expect(reductorCarrito(e, { tipo: 'cantidadCambiada', productoId: 'cafe', cantidad: 5 }).lineas[0]?.cantidad).toBe(5);
    expect(reductorCarrito(e, { tipo: 'cantidadCambiada', productoId: 'cafe', cantidad: 0 }).lineas).toEqual([]);
  });
  it('quitado y vaciado', () => {
    const e = [cafe, pan].reduce<EstadoCarrito>((s, p) => reductorCarrito(s, { tipo: 'agregado', producto: p }), vacio);
    expect(reductorCarrito(e, { tipo: 'quitado', productoId: 'cafe' }).lineas.map((l) => l.productoId)).toEqual(['pan']);
    expect(reductorCarrito(e, { tipo: 'vaciado' })).toEqual({ lineas: [] });
    expect(totalCarrito(e)).toBe(21500);
  });
  it('no modifica el estado recibido', () => {
    const e = Object.freeze({ lineas: Object.freeze([Object.freeze({ productoId: 'cafe', nombre: 'Café', precio: 1, cantidad: 1 })]) });
    expect(() => reductorCarrito(e, { tipo: 'agregado', producto: cafe })).not.toThrow();
    expect(e.lineas[0]?.cantidad).toBe(1);
  });
});

describe('nivel-03/03 · punto 2: persistencia y cupones sin tocar los componentes', () => {
  it('cupones: porcentaje, fijo con mínimo y código inválido', () => {
    let e = reductorAmpliado(ESTADO_INICIAL, { tipo: 'agregado', producto: cafe });
    e = reductorAmpliado(e, { tipo: 'cuponAplicado', codigo: 'nada' });
    expect(e.errorCupon).toBe('Cupón no válido');
    e = reductorAmpliado(e, { tipo: 'cuponAplicado', codigo: ' bienvenida10 ' });
    expect(e.cupon?.codigo).toBe('BIENVENIDA10');
    expect(descuentoCupon(e)).toBe(1800);                                       // 10 % de 18.000
  });
  it('cupón fijo MENOS5000: no aplica por debajo del mínimo y descuenta 5.000 por encima', () => {
    let e = reductorAmpliado(ESTADO_INICIAL, { tipo: 'agregado', producto: cafe });          // subtotal 18.000 < mínimo 30.000
    e = reductorAmpliado(e, { tipo: 'cuponAplicado', codigo: 'menos5000' });
    expect(e.cupon?.codigo).toBe('MENOS5000');
    expect(descuentoCupon(e)).toBe(0);
    e = reductorAmpliado(e, { tipo: 'agregado', producto: cafe });                           // subtotal 36.000 >= mínimo
    expect(descuentoCupon(e)).toBe(5000);
    e = reductorAmpliado(e, { tipo: 'cuponQuitado' });
    expect(descuentoCupon(e)).toBe(0);
  });
  it('el descuento fijo nunca supera el subtotal (tope Math.min(valor, subtotal))', () => {
    const barato = { productoId: 'pan', nombre: 'Pan', precio: 3500, cantidad: 1 };
    const e = { lineas: [barato], errorCupon: null, cupon: { codigo: 'X', tipo: 'fijo' as const, valor: 5000, minimo: 1000 } };
    expect(descuentoCupon(e)).toBe(3500);
  });
  it('lo leído de localStorage se valida: lo corrupto o con otra forma se descarta', () => {
    try {
      localStorage.setItem('carrito-n3', '{no es json');
      expect(persistenciaLocal.leer()).toBeNull();
      localStorage.setItem('carrito-n3', JSON.stringify([{ productoId: 'pan', nombre: 'Pan', precio: -5, cantidad: 2 }]));
      expect(persistenciaLocal.leer()).toBeNull();                              // precio negativo
      localStorage.setItem('carrito-n3', JSON.stringify({ lineas: [] }));
      expect(persistenciaLocal.leer()).toBeNull();                              // no es un arreglo
      const buena = [{ productoId: 'pan', nombre: 'Pan', precio: 3500, cantidad: 2 }];
      localStorage.setItem('carrito-n3', JSON.stringify(buena));
      expect(persistenciaLocal.leer()).toEqual(buena);
    } finally {
      localStorage.removeItem('carrito-n3');
    }
  });
  it('restaura lo guardado, guarda los cambios y aplica el cupón en el total', async () => {
    const u = userEvent.setup();
    const guardados: unknown[] = [];
    const persistencia: Persistencia = { leer: () => [{ productoId: 'pan', nombre: 'Pan', precio: 3500, cantidad: 2 }], guardar: (l) => guardados.push(l) };
    render(<ProveedorDeCarrito persistencia={persistencia}><BotonAgregar producto={cafe} /><ResumenCarrito /><CampoCupon /></ProveedorDeCarrito>);
    expect(await screen.findByText('Pan × 2', { exact: false })).toBeInTheDocument();
    await u.click(screen.getByRole('button', { name: 'Agregar Café' }));
    expect(screen.getByLabelText('total')).toHaveTextContent('$25.000');
    await u.type(screen.getByLabelText('Cupón'), 'BIENVENIDA10');
    await u.click(screen.getByRole('button', { name: 'Aplicar' }));
    expect(screen.getByLabelText('total')).toHaveTextContent('$22.500');
    expect(guardados.at(-1)).toEqual([{ productoId: 'pan', nombre: 'Pan', precio: 3500, cantidad: 2 }, { productoId: 'cafe', nombre: 'Café', precio: 18000, cantidad: 1 }]);
  });
});

// ---- Punto 3: qué se renderiza al agregar un producto, con un contexto único y con dos separados ----
const ContextoUnico = createContext<{ estado: EstadoCarrito; despachar: React.Dispatch<AccionCarrito> } | null>(null);
function ProveedorUnico({ children }: { children: React.ReactNode }) {
  const [estado, despachar] = useReducer(reductorCarrito, { lineas: [] });
  return <ContextoUnico value={{ estado, despachar }}>{children}</ContextoUnico>;
}
const rendersBoton = { unico: 0, separado: 0 };
const BotonUnico = memo(function BotonUnico() {
  const { despachar } = useContext(ContextoUnico)!;
  // eslint-disable-next-line react-hooks/immutability -- contador de renderizados: es justo lo que el taller mide (la alternativa sin mutar es <Profiler onRender>, ver nivel-03/03 punto 4)
  rendersBoton.unico++;
  return <button type="button" onClick={() => despachar({ tipo: 'agregado', producto: cafe })}>Agregar (único)</button>;
});
const BotonSeparado = memo(function BotonSeparado() {
  const { agregar } = useAccionesReadme();
  // eslint-disable-next-line react-hooks/immutability -- contador de renderizados: es justo lo que el taller mide (la alternativa sin mutar es <Profiler onRender>, ver nivel-03/03 punto 4)
  rendersBoton.separado++;
  return <button type="button" onClick={() => agregar(cafe)}>Agregar (separados)</button>;
});
function Cantidad() { const { cantidad, total } = useCarritoReadme(); return <p>{cantidad} · {total}</p>; }

describe('nivel-03/03 · punto 3: renderizados con contexto único frente a separados', () => {
  it('el botón se renderiza en cada agregado con el contexto único y nunca con los separados', async () => {
    const u = userEvent.setup();
    render(<>
      <ProveedorUnico><BotonUnico /></ProveedorUnico>
      <ProveedorReadme><BotonSeparado /><Cantidad /></ProveedorReadme>
    </>);
    const inicial = { ...rendersBoton };
    for (let i = 0; i < 3; i++) {
      await u.click(screen.getByRole('button', { name: 'Agregar (único)' }));
      await u.click(screen.getByRole('button', { name: 'Agregar (separados)' }));
    }
    expect(screen.getByText('1 · 54000')).toBeInTheDocument();
    expect({ unico: rendersBoton.unico - inicial.unico, separado: rendersBoton.separado - inicial.separado }).toEqual({ unico: 3, separado: 0 });
  });
  it('en el carrito ampliado, el botón tampoco se vuelve a renderizar', async () => {
    renders.boton = 0; renders.resumen = 0;
    render(<ProveedorDeCarrito persistencia={{ leer: () => null, guardar: () => {} }}><BotonAgregar producto={cafe} /><ResumenCarrito /></ProveedorDeCarrito>);
    const inicial = { ...renders };
    for (let i = 0; i < 3; i++) await userEvent.click(screen.getByRole('button', { name: 'Agregar Café' }));
    expect(renders.boton - inicial.boton).toBe(0);
    expect(renders.resumen - inicial.resumen).toBe(3);
  });
});

describe('nivel-03/03 · punto 4: almacén externo con selectores (README)', () => {
  it('el contador solo se renderiza cuando cambia el número de líneas', () => {
    let rendersContador = 0;
    render(<Profiler id="c" onRender={() => { rendersContador++; }}><ContadorCarrito /></Profiler>);
    const base = rendersContador;
    act(() => useAlmacenCarrito.getState().agregar(cafe));
    act(() => useAlmacenCarrito.getState().agregar(cafe)); // misma línea, más cantidad: el selector no cambia
    act(() => useAlmacenCarrito.getState().agregar(cafe));
    expect(screen.getByText('1')).toBeInTheDocument();
    expect(useAlmacenCarrito.getState().lineas[0]?.cantidad).toBe(3);
    expect(rendersContador - base).toBe(1);
    act(() => useAlmacenCarrito.getState().vaciar());
  });
});

describe('nivel-03/03 · punto 5: taladro de propiedades frente a composición', () => {
  it('pintan lo mismo', () => {
    const a = render(<PaginaConTaladro usuario={{ nombre: 'Ana' }} />).container.innerHTML;
    const b = render(<PaginaCompuesta usuario={{ nombre: 'Ana' }} />).container.innerHTML;
    expect(a).toBe(b);
  });
});
