// Punto 4 del taller: tamaño del paquete inicial con todo importado (antes) y con carga diferida (después).
import { spawnSync } from 'node:child_process';
import { rmSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const raiz = join(dirname(fileURLToPath(import.meta.url)), '../../..');
const medir = (variante) => {
  const r = spawnSync('npx', ['ng', 'build', `n5-01-${variante}`], { cwd: raiz, encoding: 'utf8' });
  const salida = `${r.stdout}${r.stderr}`.replace(/\x1b\[[0-9;]*m/g, '');
  if (r.status !== 0) { console.error(salida); process.exit(1); }
  const total = /Initial total\s*\|\s*([\d.]+) (k?B)\s*\|\s*([\d.]+) (k?B)/.exec(salida);
  const diferidos = [...salida.matchAll(/^(chunk-\S+\.js)\s*\|\s*(\S+)\s*\|\s*([\d.]+) kB/gm)].length;
  rmSync(join(raiz, `tmp/medicion-${variante}`), { recursive: true, force: true });
  return { variante, bruto: `${total[1]} ${total[2]}`, transferido: `${total[3]} ${total[4]}`, brutoNum: Number(total[1]), diferidos };
};
const antes = medir('eager');
const despues = medir('lazy');
console.log(`ANTES   (todo importado)     paquete inicial: ${antes.bruto} (transferido ${antes.transferido})`);
console.log(`DESPUÉS (carga diferida)     paquete inicial: ${despues.bruto} (transferido ${despues.transferido})`);
const ok = despues.brutoNum < antes.brutoNum;
console.log(`${ok ? 'OK   ' : 'FALLO'} el paquete inicial ${ok ? 'se reduce' : 'NO se reduce'} ${(100 * (1 - despues.brutoNum / antes.brutoNum)).toFixed(0)} %`);
process.exit(ok ? 0 : 1);
