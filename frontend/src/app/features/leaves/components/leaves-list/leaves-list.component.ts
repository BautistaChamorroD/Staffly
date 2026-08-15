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
import { EstadoLicencia, LeaveRequest, LeaveType } from '../../models/leave';
import { LeaveRequestService } from '../../services/leave-request.service';
import { LeaveTypeService } from '../../services/leave-type.service';

@Component({
  selector: 'app-leaves-list',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    FormsModule,
    ButtonDirective,
    InputComponent,
    SelectComponent,
    BadgeComponent,
    ModalComponent,
  ],
  templateUrl: './leaves-list.component.html',
})
export class LeavesListComponent implements OnInit {
  private leaveRequestService = inject(LeaveRequestService);
  private leaveTypeService = inject(LeaveTypeService);
  private employeeService = inject(EmployeeService);
  private authService = inject(AuthService);
  private fb = inject(FormBuilder);

  readonly role = this.authService.getRole();
  readonly canManage = this.role === 'ADMIN' || this.role === 'RRHH' || this.role === 'SUPERVISOR';
  readonly isAdmin = this.role === 'ADMIN';

  requests: LeaveRequest[] = [];
  leaveTypes: LeaveType[] = [];
  employees: Employee[] = [];

  loading = true;
  loadError: string | null = null;

  filterEmployeeId = '';
  filterEstado = '';

  // Create modal
  showCreateModal = false;
  createError: string | null = null;
  creating = false;

  createForm = this.fb.group({
    leaveTypeId: ['', Validators.required],
    fechaInicio: ['', Validators.required],
    fechaFin: ['', Validators.required],
    motivo: [''],
  });

  // Reject modal
  rejectTarget: LeaveRequest | null = null;
  rejectError: string | null = null;
  rejecting = false;

  rejectForm = this.fb.group({
    motivo: ['', [Validators.required, Validators.maxLength(500)]],
  });

  // Cancel modal
  cancelTarget: LeaveRequest | null = null;
  cancelError: string | null = null;
  cancelling = false;

  // Approve state (inline, no modal)
  approvingId: string | null = null;
  approveErrors: Record<string, string> = {};

  get leaveTypeIdCtrl() { return this.createForm.get('leaveTypeId')!; }
  get fechaInicioCtrl() { return this.createForm.get('fechaInicio')!; }
  get fechaFinCtrl() { return this.createForm.get('fechaFin')!; }

  get leaveTypeOptions(): SelectOption[] {
    return this.leaveTypes.map((t) => ({ value: t.id, label: t.nombre }));
  }

  get employeeFilterOptions(): SelectOption[] {
    return [
      { value: '', label: 'Todos los empleados' },
      ...this.employees.map((e) => ({ value: e.id, label: `${e.nombre} ${e.apellido}` })),
    ];
  }

  get estadoFilterOptions(): SelectOption[] {
    return [
      { value: '', label: 'Todos los estados' },
      { value: 'PENDIENTE', label: 'Pendiente' },
      { value: 'APROBADA', label: 'Aprobada' },
      { value: 'RECHAZADA', label: 'Rechazada' },
      { value: 'CANCELADA', label: 'Cancelada' },
    ];
  }

  get filteredRequests(): LeaveRequest[] {
    return this.requests.filter((r) => {
      if (this.filterEmployeeId && r.employeeId !== this.filterEmployeeId) return false;
      if (this.filterEstado && r.estado !== this.filterEstado) return false;
      return true;
    });
  }

  estadoBadge(estado: EstadoLicencia): BadgeVariant {
    switch (estado) {
      case 'PENDIENTE':  return 'warning';
      case 'APROBADA':   return 'success';
      case 'RECHAZADA':  return 'neutral';
      case 'CANCELADA':  return 'neutral';
    }
  }

  ngOnInit(): void {
    if (this.canManage) {
      forkJoin({
        requests: this.leaveRequestService.list(),
        leaveTypes: this.leaveTypeService.list(),
        employees: this.employeeService.list(),
      }).subscribe({
        next: ({ requests, leaveTypes, employees }) => {
          this.requests = requests;
          this.leaveTypes = leaveTypes;
          this.employees = employees;
          this.loading = false;
        },
        error: () => {
          this.loading = false;
          this.loadError = 'No se pudo cargar la información. Intentá de nuevo.';
        },
      });
    } else {
      forkJoin({
        requests: this.leaveRequestService.list(),
        leaveTypes: this.leaveTypeService.list(),
      }).subscribe({
        next: ({ requests, leaveTypes }) => {
          this.requests = requests;
          this.leaveTypes = leaveTypes;
          this.loading = false;
        },
        error: () => {
          this.loading = false;
          this.loadError = 'No se pudieron cargar tus solicitudes.';
        },
      });
    }
  }

  // ── Crear ──────────────────────────────────────────────────────────────────

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
    const raw = this.createForm.getRawValue();
    this.leaveRequestService.create({
      leaveTypeId: raw.leaveTypeId!,
      fechaInicio: raw.fechaInicio!,
      fechaFin: raw.fechaFin!,
      motivo: raw.motivo || undefined,
    }).subscribe({
      next: (req) => {
        this.creating = false;
        this.showCreateModal = false;
        this.requests = [req, ...this.requests];
      },
      error: () => {
        this.creating = false;
        this.createError = 'No se pudo enviar la solicitud. Revisá las fechas e intentá de nuevo.';
      },
    });
  }

  // ── Aprobar ────────────────────────────────────────────────────────────────

  approve(req: LeaveRequest): void {
    this.approvingId = req.id;
    delete this.approveErrors[req.id];
    this.leaveRequestService.approve(req.id).subscribe({
      next: (updated) => {
        this.approvingId = null;
        const idx = this.requests.findIndex((r) => r.id === req.id);
        if (idx !== -1) this.requests[idx] = updated;
        this.requests = [...this.requests];
      },
      error: (err) => {
        this.approvingId = null;
        if (err?.status === 409) {
          this.approveErrors[req.id] = 'Conflicto: el empleado tiene un turno asignado en esas fechas.';
        } else {
          this.approveErrors[req.id] = 'No se pudo aprobar la solicitud. Intentá de nuevo.';
        }
      },
    });
  }

  // ── Rechazar ───────────────────────────────────────────────────────────────

  openRejectModal(req: LeaveRequest): void {
    this.rejectTarget = req;
    this.rejectError = null;
    this.rejectForm.reset();
  }

  closeRejectModal(): void {
    this.rejectTarget = null;
  }

  confirmReject(): void {
    const target = this.rejectTarget;
    if (!target || this.rejectForm.invalid) {
      this.rejectForm.markAllAsTouched();
      return;
    }
    this.rejecting = true;
    this.rejectError = null;
    const motivo = this.rejectForm.getRawValue().motivo!;
    this.leaveRequestService.reject(target.id, { motivo }).subscribe({
      next: (updated) => {
        this.rejecting = false;
        this.rejectTarget = null;
        const idx = this.requests.findIndex((r) => r.id === target.id);
        if (idx !== -1) this.requests[idx] = updated;
        this.requests = [...this.requests];
      },
      error: () => {
        this.rejecting = false;
        this.rejectError = 'No se pudo rechazar la solicitud. Intentá de nuevo.';
      },
    });
  }

  // ── Cancelar ───────────────────────────────────────────────────────────────

  openCancelModal(req: LeaveRequest): void {
    this.cancelTarget = req;
    this.cancelError = null;
  }

  closeCancelModal(): void {
    this.cancelTarget = null;
  }

  confirmCancel(): void {
    const target = this.cancelTarget;
    if (!target) return;
    this.cancelling = true;
    this.cancelError = null;
    this.leaveRequestService.cancel(target.id).subscribe({
      next: (updated) => {
        this.cancelling = false;
        this.cancelTarget = null;
        const idx = this.requests.findIndex((r) => r.id === target.id);
        if (idx !== -1) this.requests[idx] = updated;
        this.requests = [...this.requests];
      },
      error: () => {
        this.cancelling = false;
        this.cancelError = 'No se pudo cancelar la solicitud. Intentá de nuevo.';
      },
    });
  }

  canCancel(req: LeaveRequest): boolean {
    return req.estado === 'PENDIENTE' || req.estado === 'APROBADA';
  }
}
