import { DecimalPipe } from '@angular/common';
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { forkJoin } from 'rxjs';

import { AuthService } from '../../../../core/services/auth.service';
import { BadgeComponent, BadgeVariant } from '../../../../shared/components/badge/badge.component';
import { ButtonDirective } from '../../../../shared/components/button/button.directive';
import { InputComponent } from '../../../../shared/components/input/input.component';
import { ModalComponent } from '../../../../shared/components/modal/modal.component';
import { SelectComponent, SelectOption } from '../../../../shared/components/select/select.component';
import { TableComponent } from '../../../../shared/components/table/table.component';
import { Employee } from '../../../employees/models/employee';
import { EmployeeService } from '../../../employees/services/employee.service';
import { PayrollPeriod } from '../../../payroll/models/payroll-period';
import { PayrollPeriodService } from '../../../payroll/services/payroll-period.service';
import { ESTADO_ADELANTO_LABELS } from '../../../../core/i18n/strings';
import { Advance, EstadoAdelanto } from '../../models/advance';
import { AdvanceService } from '../../services/advance.service';

@Component({
  selector: 'app-advances-list',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    FormsModule,
    DecimalPipe,
    InputComponent,
    SelectComponent,
    ButtonDirective,
    BadgeComponent,
    ModalComponent,
    TableComponent,
  ],
  templateUrl: './advances-list.component.html',
})
export class AdvancesListComponent implements OnInit {
  private advanceService = inject(AdvanceService);
  private employeeService = inject(EmployeeService);
  private periodService = inject(PayrollPeriodService);
  private authService = inject(AuthService);
  private fb = inject(FormBuilder);
  private destroyRef = inject(DestroyRef);

  protected readonly ESTADO_ADELANTO_LABELS = ESTADO_ADELANTO_LABELS;

  readonly role = this.authService.getRole();
  readonly canManage = this.role === 'ADMIN' || this.role === 'RRHH';

  advances: Advance[] = [];
  employees: Employee[] = [];
  periods: PayrollPeriod[] = [];

  loading = true;
  loadError: string | null = null;

  filterEmployeeId = '';
  filterPeriodId = '';

  showCreateModal = false;
  createError: string | null = null;
  creating = false;

  deleteTarget: Advance | null = null;
  deleteError: string | null = null;
  deleting = false;

  createForm = this.fb.group({
    employeeId: ['', Validators.required],
    periodId: [''],
    fecha: ['', Validators.required],
    monto: ['', [Validators.required, Validators.min(0.01)]],
    motivo: [''],
  });

  get employeeIdCtrl() { return this.createForm.get('employeeId')!; }
  get fechaCtrl()       { return this.createForm.get('fecha')!; }
  get montoCtrl()       { return this.createForm.get('monto')!; }

  get employeeOptions(): SelectOption[] {
    return this.employees.map((e) => ({
      value: e.id,
      label: `${e.nombre} ${e.apellido}`,
    }));
  }

  get filterEmployeeOptions(): SelectOption[] {
    return [{ value: '', label: 'Todos los empleados' }, ...this.employeeOptions];
  }

  get openPeriodOptions(): SelectOption[] {
    return [
      { value: '', label: 'Sin período' },
      ...this.periods
        .filter((p) => p.estado === 'ABIERTO' || p.estado === 'REABIERTO')
        .map((p) => ({ value: p.id, label: `${p.fechaInicio} — ${p.fechaFin} (${p.estado})` })),
    ];
  }

  get filterPeriodOptions(): SelectOption[] {
    return [
      { value: '', label: 'Todos los períodos' },
      ...this.periods.map((p) => ({
        value: p.id,
        label: `${p.fechaInicio} — ${p.fechaFin}`,
      })),
    ];
  }

  get filteredAdvances(): Advance[] {
    let result = this.advances;
    if (this.filterEmployeeId) {
      result = result.filter((a) => a.employeeId === this.filterEmployeeId);
    }
    if (this.filterPeriodId) {
      const period = this.periods.find((p) => p.id === this.filterPeriodId);
      if (period) {
        result = result.filter(
          (a) => a.fecha >= period.fechaInicio && a.fecha <= period.fechaFin,
        );
      }
    }
    return result;
  }

  ngOnInit(): void {
    this.reload();

    this.createForm.get('periodId')!.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((periodId) => {
        if (!this.showCreateModal || !periodId) return;
        const period = this.periods.find((p) => p.id === periodId);
        if (period) {
          this.createForm.patchValue({ fecha: period.fechaInicio }, { emitEvent: false });
        }
      });
  }

  reload(): void {
    if (!this.canManage) {
      this.loading = false;
      return;
    }
    this.loading = true;
    this.loadError = null;
    forkJoin({
      advances: this.advanceService.list(),
      employees: this.employeeService.list(),
      periods: this.periodService.list(),
    }).subscribe({
      next: ({ advances, employees, periods }) => {
        this.advances = advances;
        this.employees = employees;
        this.periods = periods;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.loadError = 'No se pudo cargar la información. Intentá de nuevo.';
      },
    });
  }

  badgeVariant(estado: EstadoAdelanto): BadgeVariant {
    switch (estado) {
      case 'PENDIENTE':  return 'warning';
      case 'DESCONTADO': return 'success';
      case 'CANCELADO':  return 'neutral';
    }
  }

  // ── Crear adelanto ─────────────────────────────────────────────────────────

  openCreateModal(): void {
    this.createForm.reset({ periodId: '', motivo: '' });
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
    const raw = this.createForm.getRawValue();
    this.advanceService
      .create({
        employeeId: raw.employeeId!,
        fecha: raw.fecha!,
        monto: Number(raw.monto),
        motivo: raw.motivo || undefined,
      })
      .subscribe({
        next: (advance) => {
          this.creating = false;
          this.showCreateModal = false;
          this.advances = [advance, ...this.advances];
        },
        error: () => {
          this.creating = false;
          this.createError = 'No se pudo registrar el adelanto. Revisá los datos e intentá de nuevo.';
        },
      });
  }

  // ── Eliminar adelanto ──────────────────────────────────────────────────────

  openDeleteConfirm(advance: Advance): void {
    this.deleteTarget = advance;
    this.deleteError = null;
  }

  cancelDelete(): void {
    this.deleteTarget = null;
  }

  confirmDelete(): void {
    const target = this.deleteTarget;
    if (!target) return;
    this.deleting = true;
    this.deleteError = null;
    this.advanceService.delete(target.id).subscribe({
      next: () => {
        this.deleting = false;
        this.deleteTarget = null;
        this.advances = this.advances.filter((a) => a.id !== target.id);
      },
      error: (err) => {
        this.deleting = false;
        if (err?.status === 409 || err?.status === 422) {
          this.deleteError =
            'Este adelanto ya fue descontado en una liquidación y no puede eliminarse.';
        } else {
          this.deleteError = 'No se pudo eliminar el adelanto. Intentá de nuevo.';
        }
      },
    });
  }
}
