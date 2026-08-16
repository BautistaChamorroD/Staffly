import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-forbidden',
  standalone: true,
  imports: [RouterLink],
  template: `
    <main class="flex min-h-[60vh] flex-col items-center justify-center gap-4 px-4 text-center">
      <p class="text-7xl font-bold text-brand-acc">403</p>
      <h1 class="font-heading text-2xl font-bold text-brand-ink">Sin permisos</h1>
      <p class="text-brand-muted">No tenés acceso a esta sección con tu rol actual.</p>
      <a
        routerLink="/"
        class="mt-2 rounded-lg bg-brand-acc px-3.5 py-2 text-sm font-semibold text-white hover:bg-brand-acc-dark transition-colors"
      >
        Ir al inicio
      </a>
    </main>
  `,
})
export class ForbiddenComponent {}
