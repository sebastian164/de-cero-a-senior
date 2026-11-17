import { defer, of, throwError } from 'rxjs';
import { TestScheduler } from 'rxjs/testing';
import { autoguardado, busquedaConCatchErrorAdentro, busquedaConCatchErrorAfuera, cargarTablero, conEspera, envioProtegido, estadoTablero, sondeo } from './flujos';

const ts = () => new TestScheduler((a, b) => expect(a).toEqual(b));

describe('Taller n3-04', () => {
  it('1. autoguardado con una API que falla al azar: reintenta y, si se agota, informa error sin morir', () => {
    // Azar determinista: la secuencia de "fallas" la decide la prueba.
    const suerte = [true, true, false, true, true, true];   // 1.er guardado: falla, falla, ok. 2.º: falla ×3 → error
    ts().run(({ hot, expectObservable }) => {
      const guardar = () => defer(() => (suerte.shift() ? throwError(() => new Error('503')) : of({})));
      const cambios = hot('a 2000ms b', { a: { t: 'a' }, b: { t: 'b' } });
      expectObservable(autoguardado(guardar, { espera: 800, reintentos: 2, pausa: 100 })(cambios))
        .toBe('800ms g 199ms o 1800ms g 199ms e', { g: 'guardando', o: 'guardado', e: 'error' });
    });
  });

  it('2. el envío protegido ignora los clics durante el envío', () => {
    ts().run(({ cold, hot, expectObservable }) => {
      const clics = hot('aaa----a');
      expectObservable(clics.pipe(envioProtegido(() => cold('---(r|)', { r: 1 }))))
        .toBe('e--r---e--r', { e: 'enviando', r: 'enviado' });
    });
  });

  it('2b. tras un envío fallido, el flujo sigue vivo: el siguiente clic vuelve a enviar', () => {
    ts().run(({ cold, hot, expectObservable }) => {
      let intento = 0;
      const enviar = () => (intento++ === 0 ? cold('--#') : cold('--(r|)', { r: 1 }));
      expectObservable(hot('a----a').pipe(envioProtegido(enviar)))
        .toBe('e-x--e-r', { e: 'enviando', x: 'error', r: 'enviado' });
    });
  });

  it('3b. si fallan ventas o clientes (obligatorias) el tablero pasa a error, no se queda cargando', () => {
    ts().run(({ expectObservable }) => {
      const api = { ventas: () => throwError(() => new Error('500')), clientes: () => of(2), recomendaciones: () => of(['a']) };
      expectObservable(estadoTablero(api)).toBe('(cx|)', { c: { estado: 'cargando' }, x: { estado: 'error' } });
    });
  });

  it('3. el tablero espera las tres; si la opcional falla, se muestra sin ella', async () => {
    const t = await new Promise((res) => cargarTablero({
      ventas: () => of(10), clientes: () => of(2), recomendaciones: () => throwError(() => new Error('500')),
    }).subscribe(res));
    expect(t).toEqual({ ventas: 10, clientesNuevos: 2, recomendaciones: null });
  });

  it('4. catchError afuera: tras el primer fallo el buscador muere; adentro, sigue', () => {
    ts().run(({ cold, hot, expectObservable }) => {
      const buscar = (q: string) => (q === 'x' ? cold('#') : cold('(r|)', { r: q.toUpperCase() }));
      const textos = hot('a-x-b', { a: 'a', x: 'x', b: 'b' });
      expectObservable(busquedaConCatchErrorAfuera(buscar, '!')(textos)).toBe('a-(f|)', { a: 'A', f: '!' });
      expectObservable(busquedaConCatchErrorAdentro(buscar, '!')(textos)).toBe('a-f-b', { a: 'A', f: '!', b: 'B' });
    });
  });

  it('5. el sondeo consulta cada 10 s solo mientras la pestaña está visible', () => {
    ts().run(({ cold, hot, expectObservable }) => {
      const visible = hot('v 25s o 20s v', { v: true, o: false });
      expectObservable(sondeo(() => cold('(r|)', { r: 'ok' }), visible, 10_000), '^ 59s !')
        .toBe('r 9999ms r 9999ms r 25001ms r 9999ms r', { r: { estado: 'ok', valor: 'ok' } });
    });
  });

  it('5b. si una consulta falla el sondeo continúa con la siguiente', () => {
    ts().run(({ cold, hot, expectObservable }) => {
      let n = 0;
      const consultar = () => (n++ === 0 ? cold('#') : cold('(r|)', { r: 'ok' }));
      expectObservable(sondeo(consultar, hot('v', { v: true }), 10_000), '^ 25s !')
        .toBe('e 9999ms r 9999ms r', { e: { estado: 'error' }, r: { estado: 'ok', valor: 'ok' } });
    });
  });

  it('6. debounceTime con tiempo simulado', () => {
    ts().run(({ hot, expectObservable }) => {
      expectObservable(conEspera(300)(hot('a 100ms b 400ms c', { a: 'a', b: 'ab', c: 'abc' })))
        .toBe('401ms x 400ms y', { x: 'ab', y: 'abc' });
    });
  });
});
