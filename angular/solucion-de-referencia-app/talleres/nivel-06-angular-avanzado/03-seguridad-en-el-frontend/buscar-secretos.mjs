// Punto 4: busca secretos en el paquete de producción construido (dist/app/browser o la carpeta dada).
// Uso: node buscar-secretos.mjs [carpeta]. Sale con 1 si encuentra algo. `--autoprueba` comprueba que el detector detecta.
import { cpSync, mkdtempSync, readdirSync, readFileSync, rmSync, statSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const PATRONES = {
  'clave de AWS': /AKIA[0-9A-Z]{16}/,
  'clave secreta de Stripe': /sk_(live|test)_[0-9a-zA-Z]{16,}/,
  'token de GitHub': /gh[pousr]_[0-9A-Za-z]{30,}/,
  'clave privada PEM': /-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----/,
  'JWT': /eyJ[A-Za-z0-9_-]{10,}\.eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}/,
  'asignación sospechosa': /(api[_-]?key|secret|passw(or)?d|private[_-]?key)["']?\s*[:=]\s*["'][^"']{12,}["']/i,
};
const archivos = (d) => readdirSync(d).flatMap((n) => { const f = join(d, n); return statSync(f).isDirectory() ? archivos(f) : [f]; });
export function buscar(carpeta) {
  const hallazgos = [];
  for (const f of archivos(carpeta).filter((f) => /\.(js|mjs|css|html|json|map|txt)$/.test(f) && !f.endsWith('3rdpartylicenses.txt'))) {
    const texto = readFileSync(f, 'utf8');
    for (const [nombre, re] of Object.entries(PATRONES)) { const m = re.exec(texto); if (m) hallazgos.push({ archivo: f.replace(carpeta, ''), tipo: nombre, extracto: m[0].slice(0, 40) }); }
  }
  const mapas = archivos(carpeta).filter((f) => f.endsWith('.map'));
  return { hallazgos, mapas: mapas.length, archivos: archivos(carpeta).length };
}
const carpeta = process.argv.find((a, i) => i >= 2 && !a.startsWith('--')) ?? 'dist/app/browser';
if (process.argv.includes('--autoprueba')) {
  const copia = mkdtempSync(join(tmpdir(), 'secretos-'));
  cpSync(carpeta, copia, { recursive: true });
  // La clave falsa se arma al ejecutar: escrita literal, los escáneres de secretos del repositorio la bloquean.
  const claveFalsa = ['sk', 'live', 'EJEMPLOsinValor'.padEnd(24, '0')].join('_');
  writeFileSync(join(copia, 'plantado.js'), `export const cfg = { apiKey: "${claveFalsa}" };
`);
  const r = buscar(copia); rmSync(copia, { recursive: true });
  const ok = r.hallazgos.some((h) => h.tipo === 'clave secreta de Stripe');
  console.log(ok ? 'OK    la autoprueba detecta una clave plantada' : 'FALLO la autoprueba no detectó la clave plantada');
  process.exit(ok ? 0 : 1);
}
const r = buscar(carpeta);
console.log(`${r.archivos} archivos revisados en ${carpeta}; mapas de código: ${r.mapas}`);
for (const h of r.hallazgos) console.log(`HALLAZGO ${h.tipo} en ${h.archivo}: ${h.extracto}`);
process.exit(r.hallazgos.length || r.mapas ? 1 : 0);
