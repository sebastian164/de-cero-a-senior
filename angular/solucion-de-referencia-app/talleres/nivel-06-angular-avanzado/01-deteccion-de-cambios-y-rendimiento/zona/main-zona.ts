import { provideZoneChangeDetection } from '@angular/core';
import { bootstrapApplication } from '@angular/platform-browser';
import { Raiz } from './escenario.component';

bootstrapApplication(Raiz, { providers: [provideZoneChangeDetection({ eventCoalescing: true })] }).catch((e) => console.error(e));
