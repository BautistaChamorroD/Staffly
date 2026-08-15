import { DecimalPipe } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { forkJoin } from 'rxjs';

import { AuthService } from '../../../../core/services/auth.service';
import { BadgeComponent, BadgeVariant } from '../../../../shared/components/badge/badge.component';
import { ButtonDirective } from '../../../../shared/components/button/button.directive';
import { InputComponent } from '../../../../shared/components/input/input.component';
import { ModalComponent } from '../../../../shared/components/modal/modal.component';
import { SelectComponent, SelectOption } from '../../../../shared/components/select/select.component';
import { Employee } from '../../../employees/models/employee';
import { EmployeeService } from '../../../employees/services/employee.service';
import { PayrollPeriod } from '../../../payroll/models/payroll-period';
import { PayrollPeriodService } from '../../../payroll/services/payroll-period.service';
import { EstadoRecibo, Payslip } from '../../models/payslip';
import { PayslipService } from '../../services/payslip.service';

@Component({
  selector: 'app-payslips-list',
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
  ],
  templateUrl: './payslips-list.component.html',
})
export class PayslipsListComponent implements OnInit {
  private payslipService = inject(PayslipService);
  private employeeService = inject(EmployeeService);
  private periodService = inject(PayrollPeriodService);
  private authService = inject(AuthService);
  private fb = inject(FormBuilder);

  readonly role = this.authService.getRole();
  readonly isAdmin = this.role === 'ADMIN';
  readonly canManage = this.role === 'ADMIN' || this.role === 'RRHH';

  payslips: Payslip[] = [];
  employees: Employee[] = [];
  periods: PayrollPeriod[] = [];

  loading = true;
  loadError: string | null = null;

  filterEmployeeId = '';
  filterPeriodId = '';

  downloadingIds = new Set<string>();
  pdfErrors: Record<string, string> = {};

  voidTarget: Payslip | null = null;
  voidError: string | null = null;
  voiding = false;

  voidForm = this.fb.group({
    motivo: ['', Validators.maxLength(1000)],
  });

  get employeeOptions(): SelectOption[] {
    return [
      { value: '', label: 'Todos los empleados' },
      ...this.employees.map((e) => ({ value: e.id, label: `${e.nombre} ${e.apellido}` })),
    ];
  }

  get periodOptions(): SelectOption[] {
    return [
      { value: '', label: 'Todos los períodos' },
      ...this.periods.map((p) => ({ value: p.id, label: `${p.fechaInicio} — ${p.fechaFin}` })),
    ];
  }

  get filteredPayslips(): Payslip[] {
    return this.payslips.filter((p) => {
      if (this.filterEmployeeId && p.employeeId !== this.filterEmployeeId) return false;
      if (this.filterPeriodId && p.payrollPeriodId !== this.filterPeriodId) return false;
      return true;
    });
  }

  estadoBadge(estado: EstadoRecibo): BadgeVariant {
    switch (estado) {
      case 'GENERADO': return 'warning';
      case 'PAGADO':   return 'success';
      case 'ANULADO':  return 'neutral';
      case 'AJUSTE':   return 'info';
    }
  }

  ngOnInit(): void {
    if (this.canManage) {
      forkJoin({
        payslips: this.payslipService.list(),
        employees: this.employeeService.list(),
        periods: this.periodService.list(),
      }).subscribe({
        next: ({ payslips, employees, periods }) => {
          this.payslips = payslips;
          this.employees = employees;
          this.periods = periods;
          this.loading = false;
        },
        error: () => {
          this.loading = false;
          this.loadError = 'No se pudo cargar la información. Intentá de nuevo.';
        },
      });
    } else {
      this.payslipService.list().subscribe({
        next: (payslips) => {
          this.payslips = payslips;
          this.loading = false;
        },
        error: () => {
          this.loading = false;
          this.loadError = 'No se pudieron cargar tus recibos.';
        },
      });
    }
  }

  downloadPdf(payslip: Payslip): void {
    this.downloadingIds.add(payslip.id);
    delete this.pdfErrors[payslip.id];
    this.payslipService.downloadPdf(payslip.id).subscribe({
      next: (blob) => {
        this.downloadingIds.delete(payslip.id);
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `recibo-${payslip.periodoInicio}_${payslip.periodoFin}-${payslip.employeeApellido}.pdf`;
        a.click();
        URL.revokeObjectURL(url);
      },
      error: () => {
        this.downloadingIds.delete(payslip.id);
        this.pdfErrors[payslip.id] = 'No se pudo descargar el PDF. Intentá de nuevo.';
      },
    });
  }

  openVoidModal(payslip: Payslip): void {
    this.voidTarget = payslip;
    this.voidError = null;
    this.voidForm.reset();
  }

  closeVoidModal(): void {
    this.voidTarget = null;
  }

  confirmVoid(): void {
    const target = this.voidTarget;
    if (!target) return;
    this.voiding = true;
    this.voidError = null;
    const motivo = this.voidForm.getRawValue().motivo || undefined;
    this.payslipService.voidAndAdjust(target.id, { motivo }).subscribe({
      next: (adjustment) => {
        this.voiding = false;
        this.voidTarget = null;
        const idx = this.payslips.findIndex((p) => p.id === target.id);
        if (idx !== -1) {
          this.payslips[idx] = { ...this.payslips[idx], estado: 'ANULADO' };
        }
        this.payslips = [adjustment, ...this.payslips];
      },
      error: () => {
        this.voiding = false;
        this.voidError = 'No se pudo anular el recibo. Intentá de nuevo.';
      },
    });
  }
}
