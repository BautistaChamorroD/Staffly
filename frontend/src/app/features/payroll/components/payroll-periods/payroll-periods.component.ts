import { Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { AuthService } from '../../../../core/services/auth.service';
import { BadgeComponent, BadgeVariant } from '../../../../shared/components/badge/badge.component';
import { ButtonDirective } from '../../../../shared/components/button/button.directive';
import { InputComponent } from '../../../../shared/components/input/input.component';
import { ModalComponent } from '../../../../shared/components/modal/modal.component';
import { TableComponent } from '../../../../shared/components/table/table.component';
import { EstadoPeriodo, PayrollPeriod } from '../../models/payroll-period';
import { PayrollPeriodService } from '../../services/payroll-period.service';

@Component({
  selector: 'app-payroll-periods',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    InputComponent,
    ButtonDirective,
    BadgeComponent,
    ModalComponent,
    TableComponent,
  ],
  templateUrl: './payroll-periods.component.html',
})
export class PayrollPeriodsComponent implements OnInit {
  private service = inject(PayrollPeriodService);
  private authService = inject(AuthService);
  private fb = inject(FormBuilder);

  readonly isAdmin = this.authService.getRole() === 'ADMIN';
  readonly canClose = this.authService.getRole() === 'ADMIN' || this.authService.getRole() === 'RRHH';

  periods: PayrollPeriod[] = [];
  loading = true;
  loadError: string | null = null;

  showCreateModal = false;
  createError: string | null = null;
  creating = false;

  closeTarget: PayrollPeriod | null = null;
  closeError: string | null = null;
  closing = false;

  reopenTarget: PayrollPeriod | null = null;
  reopenError: string | null = null;
  reopening = false;

  createForm = this.fb.group({
    fechaInicio: ['', Validators.required],
    fechaFin: ['', Validators.required],
  });

  get fechaInicioCtrl() {
    return this.createForm.get('fechaInicio')!;
  }
  get fechaFinCtrl() {
    return this.createForm.get('fechaFin')!;
  }

  ngOnInit(): void {
    this.loadPeriods();
  }

  badgeVariant(estado: EstadoPeriodo): BadgeVariant {
    switch (estado) {
      case 'ABIERTO':
        return 'success';
      case 'CERRADO':
        return 'neutral';
      case 'REABIERTO':
        return 'warning';
      case 'PAGADO':
        return 'info';
    }
  }

  loadPeriods(): void {
    this.loading = true;
    this.loadError = null;
    this.service.list().subscribe({
      next: (periods) => {
        this.periods = periods;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.loadError = 'No se pudo cargar el listado de períodos.';
      },
    });
  }

  // ── Crear período ──────────────────────────────────────────────────────────

  openCreateModal(): void {
    this.createForm.reset();
    this.createError = null;
    this.showCreateModal = true;
  }

  closeCreateModal(): void {
    this.showCreateModal = false;
  }

  submitCreate(): void {
    if (this.createForm.invalid) {
      this.createForm.markAllAsTouched();
      return;
    }
    this.creating = true;
    this.createError = null;
    const { fechaInicio, fechaFin } = this.createForm.getRawValue();
    this.service.create({ fechaInicio: fechaInicio!, fechaFin: fechaFin! }).subscribe({
      next: () => {
        this.creating = false;
        this.showCreateModal = false;
        this.loadPeriods();
      },
      error: () => {
        this.creating = false;
        this.createError = 'No se pudo crear el período. Verificá las fechas e intentá de nuevo.';
      },
    });
  }

  // ── Cerrar período ─────────────────────────────────────────────────────────

  openCloseConfirm(period: PayrollPeriod): void {
    this.closeTarget = period;
    this.closeError = null;
  }

  cancelClose(): void {
    this.closeTarget = null;
  }

  confirmClose(): void {
    const target = this.closeTarget;
    if (!target) return;
    this.closing = true;
    this.closeError = null;
    this.service.close(target.id).subscribe({
      next: () => {
        this.closing = false;
        this.closeTarget = null;
        this.loadPeriods();
      },
      error: (err) => {
        this.closing = false;
        if (err?.status === 409) {
          this.closeError = 'El período ya está cerrado.';
        } else {
          this.closeError = 'No se pudo cerrar el período. Intentá de nuevo.';
        }
      },
    });
  }

  // ── Reabrir período ────────────────────────────────────────────────────────

  openReopenConfirm(period: PayrollPeriod): void {
    this.reopenTarget = period;
    this.reopenError = null;
  }

  cancelReopen(): void {
    this.reopenTarget = null;
  }

  confirmReopen(): void {
    const target = this.reopenTarget;
    if (!target) return;
    this.reopening = true;
    this.reopenError = null;
    this.service.reopen(target.id).subscribe({
      next: () => {
        this.reopening = false;
        this.reopenTarget = null;
        this.loadPeriods();
      },
      error: () => {
        this.reopening = false;
        this.reopenError = 'No se pudo reabrir el período. Intentá de nuevo.';
      },
    });
  }
}
