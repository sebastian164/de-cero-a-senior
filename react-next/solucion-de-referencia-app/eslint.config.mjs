import { defineConfig, globalIgnores } from 'eslint/config';
import nextVitals from 'eslint-config-next/core-web-vitals';
import nextTs from 'eslint-config-next/typescript';

const ENTRADA_POR_INDEX = { group: ['**/funcionalidades/*/*', '!./**', '!../**'], message: 'Importa otra funcionalidad solo por su index.ts.' };
const SIN_FRAMEWORKS = { group: ['react', 'react-dom', 'next', 'next/*'], message: 'El dominio no depende de frameworks.' };
const SIN_DATOS_NI_INTERFAZ = { group: ['../datos/*', '../acciones/*', '../ui/*', '**/datos/**'], message: 'El dominio no importa datos, acciones ni interfaz.' };

export default defineConfig([
  ...nextVitals,
  ...nextTs,
  {
    rules: {
      // Taller nivel-04/01: reglas de hooks y de accesibilidad como error, sin silenciar nada.
      'react-hooks/rules-of-hooks': 'error',
      'react-hooks/exhaustive-deps': 'error',
      'jsx-a11y/click-events-have-key-events': 'error',
      'jsx-a11y/no-static-element-interactions': 'error',
      'jsx-a11y/label-has-associated-control': 'error',
      'jsx-a11y/no-autofocus': 'error',
    },
  },
  // Límites de arquitectura (nivel-05/03), sobre el código real: proyectos/funcionalidades/** y proyectos/nucleo/**.
  // Cada bloque repite los patrones que le tocan porque, en configuración plana, la última regla que coincide reemplaza a las anteriores.
  {
    // Una funcionalidad entra a otra solo por su index.ts (cualquier ruta interna de otra funcionalidad se rechaza).
    files: ['proyectos/funcionalidades/**'],
    rules: { 'no-restricted-imports': ['error', { patterns: [ENTRADA_POR_INDEX] }] },
  },
  {
    files: ['proyectos/funcionalidades/*/dominio/**'],
    rules: { 'no-restricted-imports': ['error', { patterns: [ENTRADA_POR_INDEX, SIN_FRAMEWORKS, SIN_DATOS_NI_INTERFAZ] }] },
  },
  {
    // La capa de aplicación (casos de uso) depende solo del dominio: ni React, ni Next, ni datos, ni acciones, ni interfaz.
    files: ['proyectos/funcionalidades/*/aplicacion/**'],
    rules: { 'no-restricted-imports': ['error', { patterns: [ENTRADA_POR_INDEX, SIN_FRAMEWORKS, SIN_DATOS_NI_INTERFAZ] }] },
  },
  {
    files: ['proyectos/nucleo/**'],
    rules: { 'no-restricted-imports': ['error', { patterns: [{ group: ['**/funcionalidades/**'], message: 'compartido no importa funcionalidades.' }] }] },
  },
  globalIgnores([
    // "Antes" de los talleres: mal a propósito. 'proyectos/' son copias literales de los README (se compilan y prueban, no se reescriben para el linter).
    '**/antes/**', 'proyectos/react-next/**', 'proyectos/pedidos/**','.next/**', '.next-*/**', 'node_modules/**', 'next-env.d.ts', 'talleres/**/fallo-*/**', 'playwright-report/**', 'test-results/**', 'capturas/**']),
]);
