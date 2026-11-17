import { provideZonelessChangeDetection } from '@angular/core';
import { bootstrapApplication } from '@angular/platform-browser';
import { PantallaI18n } from './pantalla';

bootstrapApplication(PantallaI18n, { providers: [provideZonelessChangeDetection()] }).catch((e) => console.error(e));
