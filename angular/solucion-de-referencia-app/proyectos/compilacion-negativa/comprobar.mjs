// Compila cada *.caso.ts por separado con ngc y comprueba que falla con el código de error esperado.
// Estos casos verifican afirmaciones del curso del tipo "esto no compila".
import { execFileSync } from 'node:child_process';
import { readdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const aqui = dirname(fileURLToPath(import.meta.url));
let fallos = 0;
for (const caso of readdirSync(aqui).filter((a) => a.endsWith('.caso.ts')).sort()) {
  const esperado = readFileSync(join(aqui, caso), 'utf8').match(/ESPERA: (\S+)/)[1];
  const tsconfig = join(aqui, `tsconfig.${caso}.json`);
  writeFileSync(tsconfig, JSON.stringify({ extends: '../../tsconfig.json', compilerOptions: { noEmit: true, types: [] }, files: [caso], angularCompilerOptions: { strictTemplates: true } }));
  let salida = '';
  try { execFileSync('npx', ['ngc', '-p', tsconfig], { cwd: join(aqui, '../..'), encoding: 'utf8', stdio: 'pipe' }); }
  catch (e) { salida = `${e.stdout}${e.stderr}`; }
  rmSync(tsconfig);
  const codigo = esperado.startsWith('NG') ? `NG${esperado.slice(2)}` : esperado;
  const ok = salida.includes(`error ${codigo}`) || salida.includes(`error ${codigo.replace('NG', 'NG')}`) || salida.includes(codigo);
  console.log(`${ok ? 'OK  ' : 'FALLO'} ${caso}: se esperaba ${esperado}${ok ? '' : `\n${salida || '(compiló sin errores)'}`}`);
  if (!ok) fallos++;
}
process.exit(fallos ? 1 : 0);
