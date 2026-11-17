/** Índice de las 28 sesiones de la ruta Angular: ruta del README y prefijo corto. */
export interface Sesion {
  readonly id: string;          // n1-01
  readonly carpeta: string;     // nivel-01-fundamentos/01-fundamentos-de-angular
  readonly titulo: string;
  readonly ejemplo: boolean;    // tiene pantalla del ejemplo "después"
}

export const SESIONES: readonly Sesion[] = [
  { id: 'n1-01', carpeta: 'nivel-01-fundamentos/01-fundamentos-de-angular', titulo: 'Fundamentos de Angular', ejemplo: true },
  { id: 'n1-02', carpeta: 'nivel-01-fundamentos/02-reactividad-signals-y-rxjs', titulo: 'Reactividad: señales y RxJS', ejemplo: true },
  { id: 'n1-03', carpeta: 'nivel-01-fundamentos/03-componentes-con-senales', titulo: 'Componentes con señales', ejemplo: true },
  { id: 'n1-04', carpeta: 'nivel-01-fundamentos/04-datos-asincronos-con-resource', titulo: 'Datos asíncronos: resource', ejemplo: true },
  { id: 'n2-01', carpeta: 'nivel-02-solid/01-srp', titulo: 'SRP', ejemplo: true },
  { id: 'n2-02', carpeta: 'nivel-02-solid/02-ocp', titulo: 'OCP', ejemplo: true },
  { id: 'n2-03', carpeta: 'nivel-02-solid/03-lsp', titulo: 'LSP', ejemplo: true },
  { id: 'n2-04', carpeta: 'nivel-02-solid/04-isp', titulo: 'ISP', ejemplo: true },
  { id: 'n2-05', carpeta: 'nivel-02-solid/05-dip', titulo: 'DIP', ejemplo: true },
  { id: 'n3-01', carpeta: 'nivel-03-patrones/01-contenedor-y-presentacional', titulo: 'Contenedor y presentación', ejemplo: true },
  { id: 'n3-02', carpeta: 'nivel-03-patrones/02-estrategia-y-fabrica-con-inyeccion', titulo: 'Strategy y Factory', ejemplo: true },
  { id: 'n3-03', carpeta: 'nivel-03-patrones/03-facade-y-servicios-de-estado', titulo: 'Facade y estado', ejemplo: true },
  { id: 'n3-04', carpeta: 'nivel-03-patrones/04-observer-con-rxjs', titulo: 'Observer con RxJS', ejemplo: true },
  { id: 'n3-05', carpeta: 'nivel-03-patrones/05-adapter-interceptores-y-guardas', titulo: 'Adapter, interceptores y guardas', ejemplo: true },
  { id: 'n3-06', carpeta: 'nivel-03-patrones/06-composicion-directivas-y-proyeccion', titulo: 'Composición', ejemplo: true },
  { id: 'n4-01', carpeta: 'nivel-04-clean-code-y-testing/01-clean-code-en-componentes', titulo: 'Clean code', ejemplo: true },
  { id: 'n4-02', carpeta: 'nivel-04-clean-code-y-testing/02-formularios-y-validacion', titulo: 'Formularios', ejemplo: true },
  { id: 'n4-03', carpeta: 'nivel-04-clean-code-y-testing/03-pruebas-unitarias-y-de-componentes', titulo: 'Pruebas unitarias', ejemplo: true },
  { id: 'n4-04', carpeta: 'nivel-04-clean-code-y-testing/04-pruebas-e2e-y-estrategia', titulo: 'Pruebas E2E', ejemplo: true },
  { id: 'n5-01', carpeta: 'nivel-05-arquitectura/01-estructura-por-funcionalidades', titulo: 'Estructura por funcionalidades', ejemplo: true },
  { id: 'n5-02', carpeta: 'nivel-05-arquitectura/02-gestion-de-estado', titulo: 'Gestión de estado', ejemplo: true },
  { id: 'n5-03', carpeta: 'nivel-05-arquitectura/03-arquitectura-limpia-en-frontend', titulo: 'Arquitectura limpia', ejemplo: true },
  { id: 'n5-04', carpeta: 'nivel-05-arquitectura/04-comunicacion-con-el-backend', titulo: 'Comunicación con el backend', ejemplo: true },
  { id: 'n5-05', carpeta: 'nivel-05-arquitectura/05-monorepos-y-microfrontends', titulo: 'Monorepos y microfrontends', ejemplo: false },
  { id: 'n6-01', carpeta: 'nivel-06-angular-avanzado/01-deteccion-de-cambios-y-rendimiento', titulo: 'Rendimiento', ejemplo: true },
  { id: 'n6-02', carpeta: 'nivel-06-angular-avanzado/02-ssr-hidratacion-y-seo', titulo: 'SSR e hidratación', ejemplo: true },
  { id: 'n6-03', carpeta: 'nivel-06-angular-avanzado/03-seguridad-en-el-frontend', titulo: 'Seguridad', ejemplo: true },
  { id: 'n6-04', carpeta: 'nivel-06-angular-avanzado/04-accesibilidad-e-internacionalizacion', titulo: 'Accesibilidad e i18n', ejemplo: true },
];
