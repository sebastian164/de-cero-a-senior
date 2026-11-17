import { registerLocaleData } from '@angular/common';
import localeEsCo from '@angular/common/locales/es-CO';
import { LOCALE_ID, Provider } from '@angular/core';

// Las pruebas usan la misma configuración regional que la aplicación (es-CO).
registerLocaleData(localeEsCo);
const providers: Provider[] = [{ provide: LOCALE_ID, useValue: 'es-CO' }];
export default providers;
