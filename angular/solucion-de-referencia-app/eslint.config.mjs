// Reglas de límites (sesiones n5-01 y n5-03): una convención que no se comprueba automáticamente se erosiona.
import tseslint from 'typescript-eslint';

const restringir = (patterns) => ({ 'no-restricted-imports': ['error', { patterns }] });
const funcionalidades = ['catalogo', 'pedidos', 'admin'];

export default [
  { ignores: ['node_modules/**', 'dist/**', 'tmp/**', 'resultados-e2e/**', '.angular/**'] },
  // Reglas recomendadas de typescript-eslint (sin comprobación de tipos) sobre todo el código TypeScript.
  ...tseslint.configs.recommended.map((c) => ({ ...c, files: ['**/*.ts'] })),
  { files: ['**/*.ts'], languageOptions: { parser: tseslint.parser } },
  // Excepciones deliberadas: los fragmentos "antes" están mal a propósito; los ejemplos copian el código del README tal cual
  // (con importaciones que el README muestra aunque la prueba no las use); las pruebas acceden a internos (ɵcmp) y a campos privados.
  { files: ['**/antes.ts', '**/antes/**/*.ts', '**/proyectos/ejemplos/**/*.ts', '**/*.spec.ts'], rules: { '@typescript-eslint/no-explicit-any': 'off' } },
  { files: ['**/proyectos/ejemplos/**/*.ts'], rules: { '@typescript-eslint/no-unused-vars': 'off' } },

  // --- n5-01: la tienda por funcionalidades ---
  // En la configuración plana, un bloque posterior REEMPLAZA las opciones de la misma regla, no las suma:
  // por eso se genera un bloque por (funcionalidad × capa) con la lista completa de patrones.
  ...funcionalidades.flatMap((f) => ['dominio', 'ui', 'datos', 'estado', 'paginas', null].map((capa) => ({
    files: [capa ? `**/01-estructura-por-funcionalidades/tienda/${f}/${capa}/**/*.ts` : `**/01-estructura-por-funcionalidades/tienda/${f}/*.ts`],
    rules: restringir([
      ...funcionalidades.filter((o) => o !== f).map((o) => ({ group: [`**/${o}/*`], message: `Importa ${o} solo por su API pública (index.ts), no por su interior.` })),
      ...(capa === 'dominio' ? [
        { group: ['@angular/*', 'rxjs', 'rxjs/*'], message: 'El dominio no importa Angular ni RxJS.' },
        { group: ['**/datos/*', '**/ui/*', '**/estado/*', '**/paginas/*'], message: 'El dominio no depende de capas externas.' },
      ] : []),
      ...(capa === 'ui' ? [{ group: ['**/datos/*', '**/estado/*'], message: 'La interfaz de presentación no depende de datos ni de estado.' }] : []),
    ]),
  }))),
  {
    files: ['**/01-estructura-por-funcionalidades/tienda/compartido/**/*.ts'],
    rules: restringir([{ group: ['**/catalogo*', '**/pedidos*', '**/admin*', '**/nucleo*', '**/catalogo/**', '**/pedidos/**', '**/admin/**', '**/nucleo/**'], message: 'compartido no conoce ninguna funcionalidad ni el núcleo.' }]),
  },

  // --- n5-03: el dominio no importa Angular; solo los puertos (contratos) pueden nombrar `Observable` de RxJS ---
  {
    files: ['**/03-arquitectura-limpia-en-frontend/**/dominio/**/*.ts', '**/ejemplos/nivel-05-arquitectura/03-arquitectura-limpia-en-frontend/dominio/**/*.ts'],
    ignores: ['**/*.spec.ts', '**/puertos.ts'],
    rules: restringir([
      { group: ['@angular/*', 'rxjs', 'rxjs/*'], message: 'El dominio no importa Angular (ni RxJS, salvo en los puertos).' },
      { group: ['**/datos/*', '**/aplicacion/*', '**/ui/*'], message: 'El dominio no depende de capas externas.' },
    ]),
  },
  {
    files: ['**/03-arquitectura-limpia-en-frontend/**/dominio/puertos.ts', '**/ejemplos/nivel-05-arquitectura/03-arquitectura-limpia-en-frontend/dominio/puertos.ts'],
    rules: restringir([
      { group: ['@angular/*'], message: 'El dominio no importa Angular.' },
      { group: ['**/datos/*', '**/aplicacion/*', '**/ui/*'], message: 'El dominio no depende de capas externas.' },
    ]),
  },
  {
    files: ['**/03-arquitectura-limpia-en-frontend/**/aplicacion/**/*.ts'],
    ignores: ['**/*.spec.ts'],
    rules: restringir([{ group: ['**/datos/*', '**/ui/*'], message: 'La aplicación depende de puertos del dominio, no de los adaptadores ni de la interfaz.' }]),
  },
];
