// Solución del taller de frontend-senior/01: el documento DISENO.md y el prototipo ejecutable del problema elegido (chat).
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';
import { alReconectar, enviarOptimista, marcarFallido, recibir, reintentar, ServidorSimulado, vacio } from './chat';

const doc = readFileSync(join(__dirname, 'DISENO.md'), 'utf8');
const SECCIONES = [
  'Requisitos y fuera de alcance',
  'Arquitectura',
  'Modelo de datos y contrato',
  'Clasificación del estado',
  'Árbol de componentes',
  'Cinco decisiones',
  'Modos de fallo',
];

describe('DISENO.md: tres problemas, cada uno con todas las secciones pedidas', () => {
  const problemas = doc.split(/^# Problema /m).slice(1);
  it('hay tres problemas', () => expect(problemas).toHaveLength(3));
  it.each(SECCIONES)('cada problema tiene la sección "%s"', (s) => {
    for (const p of problemas) expect(p).toContain(`## ${s}`);
  });
  it('cada problema tiene un diagrama y cinco decisiones con alternativa', () => {
    for (const p of problemas) {
      expect(p).toMatch(/```mermaid/);
      const decisiones = p
        .split('## Cinco decisiones')[1]
        .split('\n## ')[0]
        .split('\n')
        .filter((l) => /^\| \d \|/.test(l));
      expect(decisiones).toHaveLength(5);
      for (const d of decisiones) expect(d.split('|').filter((c) => c.trim()).length).toBeGreaterThanOrEqual(4);
    }
  });
});

describe('chat: prototipo', () => {
  it('el envío optimista aparece al instante y se confirma con la secuencia del servidor', () => {
    let c = enviarOptimista(vacio(), 'a', 'hola');
    expect(c.mensajes[0]).toMatchObject({ estado: 'enviando', secuencia: null });
    c = recibir(c, { idCliente: 'a', secuencia: 1, texto: 'hola' });
    expect(c.mensajes).toEqual([{ idCliente: 'a', secuencia: 1, texto: 'hola', estado: 'enviado' }]);
  });
  it('los mensajes que llegan desordenados quedan ordenados por secuencia, y los repetidos no se duplican', () => {
    let c = vacio();
    for (const s of [3, 1, 2, 2, 3]) c = recibir(c, { idCliente: `m${s}`, secuencia: s, texto: `t${s}` });
    expect(c.mensajes.map((m) => m.secuencia)).toEqual([1, 2, 3]);
    expect(c.ultimaSecuencia).toBe(3);
  });
  it('un mensaje pendiente se queda al final hasta que llega su secuencia, aunque lleguen ajenos antes', () => {
    let c = enviarOptimista(vacio(), 'mio', 'yo');
    c = recibir(c, { idCliente: 'x', secuencia: 1, texto: 'otro' });
    expect(c.mensajes.map((m) => m.idCliente)).toEqual(['x', 'mio']);
    c = recibir(c, { idCliente: 'mio', secuencia: 2, texto: 'yo' });
    expect(c.mensajes.map((m) => m.idCliente)).toEqual(['x', 'mio']);
  });
  it('fallo, reintento y reconexión: se pide lo perdido, se reenvía lo pendiente y el servidor idempotente evita duplicados', () => {
    const srv = new ServidorSimulado();
    let c = vacio();
    c = recibir(c, srv.publicar('s1', 'uno'));
    // se cae la red: el usuario escribe dos mensajes que no llegan, y el servidor recibe otros ajenos
    c = enviarOptimista(c, 'p1', 'pendiente 1');
    c = enviarOptimista(c, 'p2', 'pendiente 2');
    c = marcarFallido(c, 'p1');
    srv.publicar('ajeno', 'dicho por otro');
    expect(c.mensajes.map((m) => m.estado)).toEqual(['enviado', 'fallido', 'enviando']);
    c = reintentar(c, 'p1');
    const { desde, reenviar } = alReconectar(c);
    expect(desde).toBe(1);
    expect(reenviar.map((r) => r.idCliente)).toEqual(['p1', 'p2']);
    for (const m of srv.desde(desde)) c = recibir(c, m);
    for (const r of reenviar) c = recibir(c, srv.publicar(r.idCliente, r.texto));
    for (const r of reenviar) srv.publicar(r.idCliente, r.texto); // un segundo reenvío no duplica
    expect(c.mensajes.map((m) => m.idCliente)).toEqual(['s1', 'ajeno', 'p1', 'p2']);
    expect(c.mensajes.every((m) => m.estado === 'enviado')).toBe(true);
    expect(srv.desde(0)).toHaveLength(4);
  });
});
