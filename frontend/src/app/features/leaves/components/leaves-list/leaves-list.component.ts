import { Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { forkJoin } from 'rxjs';

import { AuthService } from '../../../../core/services/auth.service';
import { BadgeComponent, BadgeVariant } from '../../../../shared/components/badge/badge.component';
import { ButtonDirective } from '../../../../shared/components/button/button.directive';
import { InputComponent } from '../../../../shared/components/input/input.component';
import { ModalComponent } from '../../../../shared/components/modal/modal.component';
import { SelectComponent, SelectOption } from '../../../../shared/components/select/select.component';
import { Employee } from '../../../employees/models/employee';
import { EmployeeService } from '../../../employees/services/employee.service';
import { Schedule } from '../../../schedules/models/schedule';
import { ScheduleService } from '../../../schedules/services/schedule.service';
import { EstadoLicencia, LeaveRequest } from '../../models/leave-request';
import { LeaveType } from '../../models/leave-type';
import { LeaveRequestService } from '../../services/leave-request.service';
import { LeaveTypeService } from '../../services/leave-type.service';

const ESTADO_VARIANTS: Record<EstadoLicencia, BadgeVariant> = {
  PENDIENTE: 'warning',
  APROBADA: 'success',
  RECHAZADA: 'neutral',
  CANCELADA: 'neutral',
};

const ESTADO_LABELS: Record<EstadoLicencia, string> = {
  PENDIENTE: 'Pendiente',
  APROBADA: 'Aprobada',
  RECHAZADA: 'Rechazada',
  CANCELADA: 'Cancelada',
};

function toIsoDate(d: Date): string {
  return d.toISOString().substring(0, 10);
}

function todayPlusDays(n: number): string {
  const d = new Date();
  d.setDate(d.getDate() + n);
  return toIsoDate(d);
}

@Component({
  selector: 'app-leaves-list',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    BadgeComponent,
    ButtonDirective,
    InputComponent,
    ModalComponent,
    SelectComponent,
  ],
  templateUrl: './leaves-list.component.html',
})
export class LeavesListComponent implements OnInit {
  private leaveRequestService = inject(LeaveRequestService);
  private leaveTypeService = inject(LeaveTypeService);
  private employeeService = inject(EmployeeService);
  private scheduleService = inject(ScheduleService);
  private authService = inject(AuthService);
  private fb = inject(FormBuilder);

  readonly role = this.authService.getRole();
  readonly canApprove = this.role === 'ADMIN' || this.role === 'RRHH' || this.role === 'SUPERVISOR';
  readonly canRequest = this.role === 'EMPLOYEE' || this.role === 'ADMIN' || this.role === 'RRHH';
  readonly isEmployee = this.role === 'EMPLOYEE';

  readonly estadoOptions: SelectOption[] = [
    { value: 'PENDIENTE', label: 'Pendiente' },
    { value: 'APROBADA', label: 'Aprobada' },
    { value: 'RECHAZADA', label: 'Rechazada' },
    { value: 'CANCELADA', label: 'Cancelada' },
  ];

  leaveRequests: LeaveRequest[] = [];
  leaveTypes: LeaveType[] = [];
  employees: Employee[] = [];
  schedules: Schedule[] = []; // loaded to pre-check conflicts

  loading = true;
  loadError: string | null = null;

  filterForm = this.fb.group({ estado: [''] });

  // create modal
  formOpen = false;
  formError: string | null = null;
  createForm = this.fb.group({
    employeeId: [''],
    leaveTypeId: ['', Validators.required],
    fechaInicio: ['', Validators.required],
    fechaFin: ['', Validators.required],
    motivo: [''],
  });

  // reject modal
  rejectTarget: LeaveRequest | null = null;
  rejectError: string | null = null;
  rejectForm = this.fb.group({ motivo: ['', Validators.required] });

  // approve 409 conflict modal
  approveConflict: string[] | null = null;

  // ─── computed ─────────────────────────────────────────────────────────

  get leaveTypeOptions(): SelectOption[] {
    return this.leaveTypes.map((lt) => ({ value: lt.id, label: lt.nombre }));
  }

  get employeeOptions(): SelectOption[] {
    return this.employees.map((e) => ({ value: e.id, label: `${e.nombre} ${e.apellido}` }));
  }

  estadoVariant(estado: EstadoLicencia): BadgeVariant {
    return ESTADO_VARIANTS[estado];
  }

  estadoLabel(estado: EstadoLicencia): string {
    return ESTADO_LABELS[estado];
  }

  getEmployeeName(employeeId: string): string {
    const emp = this.employees.find((e) => e.id === employeeId);
    return emp ? `${emp.nombre} ${emp.apellido}` : '—';
  }

  /** True if a loaded schedule overlaps with this pending leave request. */
  hasScheduleConflict(lr: LeaveRequest): boolean {
    if (lr.estado !== 'PENDIENTE') return false;
    return this.schedules.some((s) => {
      if (s.employeeId !== lr.employeeId) return false;
      const scheduleDate = s.fechaHoraInicio.substring(0, 10);
      return scheduleDate >= lr.fechaInicio && scheduleDate <= lr.fechaFin;
    });
  }

  canCancel(lr: LeaveRequest): boolean {
    if (lr.estado === 'RECHAZADA' || lr.estado === 'CANCELADA') return false;
    if (this.isEmployee) return lr.estado === 'PENDIENTE' || lr.estado === 'APROBADA';
    return this.role === 'ADMIN' || this.role === 'RRHH';
  }

  // ─── lifecycle ────────────────────────────────────────────────────────

  ngOnInit(): void {
    const initial$ = this.canApprove
      ? forkJoin({
          leaveTypes: this.leaveTypeService.list(),
          employees: this.employeeService.list(),
          schedules: this.scheduleService.list({ desde: toIsoDate(new Date()), hasta: todayPlusDays(365) }),
        })
      : forkJoin({
          leaveTypes: this.leaveTypeService.list(),
          employees: this.employeeService.list(),
        });

    initial$.subscribe({
      next: (result) => {
        this.leaveTypes = (result as { leaveTypes: LeaveType[] }).leaveTypes;
        this.employees = (result as { employees: Employee[] }).employees;
        if ('schedules' in result) {
          this.schedules = (result as { schedules: Schedule[] }).schedules;
        }
        this.loadLeaveRequests();
      },
      error: () => {
        this.loading = false;
        this.loadError = 'No se pudieron cargar los datos iniciales.';
      },
    });

    this.filterForm.get('estado')!.valueChanges.subscribe(() => this.loadLeaveRequests());
  }

  loadLeaveRequests(): void {
    this.loading = true;
    this.loadError = null;
    const estado = this.filterForm.getRawValue().estado as EstadoLicencia | undefined;
    this.leaveRequestService.list(estado ? { estado } : {}).subscribe({
      next: (requests) => {
        this.leaveRequests = requests;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.loadError = 'No se pudieron cargar las licencias.';
      },
    });
  }

  // ─── create ───────────────────────────────────────────────────────────

  openCreateModal(): void {
    this.formOpen = true;
    this.formError = null;
    this.createForm.reset({ leaveTypeId: '', fechaInicio: '', fechaFin: '', motivo: '', employeeId: '' });
  }

  closeCreateModal(): void {
    this.formOpen = false;
  }

  handleCreateSubmit(): void {
    if (this.createForm.invalid) return;
    const raw = this.createForm.getRawValue();
    this.formError = null;

    const request = {
      leaveTypeId: raw.leaveTypeId!,
      fechaInicio: raw.fechaInicio!,
      fechaFin: raw.fechaFin!,
      motivo: raw.motivo || undefined,
      ...(!this.isEmployee && raw.employeeId ? { employeeId: raw.employeeId } : {}),
    };

    this.leaveRequestService.create(request).subscribe({
      next: () => {
        this.formOpen = false;
        this.loadLeaveRequests();
      },
      error: () => {
        this.formError = 'No se pudo crear la solicitud. Verificá las fechas.';
      },
    });
  }

  // ─── approve ──────────────────────────────────────────────────────────

  approve(lr: LeaveRequest): void {
    this.leaveRequestService.approve(lr.id).subscribe({
      next: () => this.loadLeaveRequests(),
      error: (err: HttpErrorResponse) => {
        if (err.status === 409 && err.error?.code === 'LEAVE_APPROVAL_CONFLICT') {
          this.approveConflict = err.error.turnosConflictivos as string[];
        } else {
          this.loadError = 'No se pudo aprobar la solicitud.';
        }
      },
    });
  }

  closeApproveConflict(): void {
    this.approveConflict = null;
  }

  // ─── reject ───────────────────────────────────────────────────────────

  openRejectModal(lr: LeaveRequest): void {
    this.rejectTarget = lr;
    this.rejectError = null;
    this.rejectForm.reset({ motivo: '' });
  }

  closeRejectModal(): void {
    this.rejectTarget = null;
  }

  confirmReject(): void {
    if (this.rejectForm.invalid || !this.rejectTarget) return;
    const motivo = this.rejectForm.getRawValue().motivo!;
    this.rejectError = null;
    this.leaveRequestService.reject(this.rejectTarget.id, { motivo }).subscribe({
      next: () => {
        this.rejectTarget = null;
        this.loadLeaveRequests();
      },
      error: () => {
        this.rejectError = 'No se pudo rechazar la solicitud.';
      },
    });
  }

  // ─── cancel ───────────────────────────────────────────────────────────

  cancel(lr: LeaveRequest): void {
    this.leaveRequestService.cancel(lr.id).subscribe({
      next: () => this.loadLeaveRequests(),
      error: () => {
        this.loadError = 'No se pudo cancelar la solicitud.';
      },
    });
  }
}
