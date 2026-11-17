// Punto 4 del taller: una regla de lint verifica que `dominio` no importa Angular.
import { spawnSync } from 'node:child_process';
import { rmSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const aqui = dirname(fileURLToPath(import.meta.url));
const raiz = join(aqui, '../../..');
const lint = (...r) => spawnSync('npx', ['eslint', '--format', 'json', ...r], { cwd: raiz, encoding: 'utf8' });
const mensajes = (r) => JSON.parse(r.stdout).flatMap((f) => f.messages.map((m) => `${f.filePath.split('/').slice(-2).join('/')}: ${m.message}`));
let fallos = 0;
const bien = lint(join(aqui, 'dominio'), join(aqui, 'aplicacion'), join(aqui, '../../../proyectos/ejemplos/nivel-05-arquitectura/03-arquitectura-limpia-en-frontend'));
console.log(`${bien.status === 0 ? 'OK   ' : 'FALLO'} dominio, aplicación y el ejemplo cumplen las reglas (${mensajes(bien).length} problemas)`);
if (bien.status !== 0) { fallos++; console.log(mensajes(bien).join('\n')); }
const malos = {
  'dominio/mala-angular.ts': "import { signal } from '@angular/core';\nexport const a = signal(1);\n",
  'dominio/mala-rxjs.ts': "import { of } from 'rxjs';\nexport const b = of(1);\n",
  'dominio/mala-datos.ts': "import { SolicitudesHttp } from '../datos/solicitudes-http';\nexport const c = SolicitudesHttp;\n",
  'aplicacion/mala-adaptador.ts': "import { SolicitudesHttp } from '../datos/solicitudes-http';\nexport const d = SolicitudesHttp;\n",
};
try {
  for (const [r, c] of Object.entries(malos)) writeFileSync(join(aqui, r), c);
  const salida = mensajes(lint(join(aqui, 'dominio'), join(aqui, 'aplicacion')));
  for (const r of Object.keys(malos)) {
    const f = salida.find((m) => m.startsWith(r.split('/').slice(-2).join('/')));
    console.log(`${f ? 'OK   ' : 'FALLO'} ${r.split('/').pop()} ${f ? 'falla: ' + f.split(': ')[1] : 'NO se detectó'}`);
    if (!f) fallos++;
  }
} finally { for (const r of Object.keys(malos)) rmSync(join(aqui, r), { force: true }); }
process.exit(fallos ? 1 : 0);
