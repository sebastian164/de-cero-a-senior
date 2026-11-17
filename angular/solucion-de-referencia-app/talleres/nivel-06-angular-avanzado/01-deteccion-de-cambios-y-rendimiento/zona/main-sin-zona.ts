import { provideZonelessChangeDetection } from '@angular/core';
import { bootstrapApplication } from '@angular/platform-browser';
import { Raiz } from './escenario.component';

bootstrapApplication(Raiz, { providers: [provideZonelessChangeDetection()] }).catch((e) => console.error(e));
