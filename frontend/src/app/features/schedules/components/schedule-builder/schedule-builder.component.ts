import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { distinctUntilChanged, forkJoin } from 'rxjs';

import { AuthService } from '../../../../core/services/auth.service';
import { ButtonDirective } from '../../../../shared/components/button/button.directive';
import { InputComponent } from '../../../../shared/components/input/input.component';
import { ModalComponent } from '../../../../shared/components/modal/modal.component';
import { SelectComponent, SelectOption } from '../../../../shared/components/select/select.component';
import { Branch } from '../../../branches/models/branch';
import { BranchService } from '../../../branches/services/branch.service';
import { Employee } from '../../../employees/models/employee';
import { EmployeeService } from '../../../employees/services/employee.service';
import { LeaveRequest } from '../../../leaves/models/leave-request';
import { LeaveRequestService } from '../../../leaves/services/leave-request.service';
import { CreateScheduleRequest, Schedule, TipoTurno } from '../../models/schedule';
import { ScheduleService } from '../../services/schedule.service';

const MONTHS_SHORT = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];
const DAY_NAMES = ['Dom', 'Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb'];

interface ConflictDetail {
  fecha: string;
  turnoExistenteId: string;
}

function getMonday(d: Date): Date {
  const copy = new Date(d);
  const day = copy.getDay();
  copy.setDate(copy.getDate() - (day === 0 ? 6 : day - 1));
  copy.setHours(0, 0, 0, 0);
  return copy;
}

function isSameDay(a: Date, b: Date): boolean {
  return (
    a.getFullYear() === b.getFullYear() &&
    a.getMonth() === b.getMonth() &&
    a.getDate() === b.getDate()
  );
}

function toIsoDate(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

function pad2(n: number): string {
  return String(n).padStart(2, '0');
}

@Component({
  selector: 'app-schedule-builder',
  standalone: true,
  imports: [ReactiveFormsModule, ButtonDirective, InputComponent, ModalComponent, SelectComponent],
  templateUrl: './schedule-builder.component.html',
})
export class ScheduleBuilderComponent implements OnInit {
  private scheduleService = inject(ScheduleService);
  private employeeService = inject(EmployeeService);
  private branchService = inject(BranchService);
  private leaveRequestService = inject(LeaveRequestService);
  private authService = inject(AuthService);
  private fb = inject(FormBuilder);
  private destroyRef = inject(DestroyRef);

  readonly role = this.authService.getRole();
  readonly canWrite = this.role === 'ADMIN' || this.role === 'RRHH' || this.role === 'SUPERVISOR';
  readonly PIXELS_PER_HOUR = 60;

  readonly tipoTurnoOptions: SelectOption[] = [
    { value: 'FIJO', label: 'Fijo' },
    { value: 'ROTATIVO', label: 'Rotativo' },
  ];

  branches: Branch[] = [];
  employees: Employee[] = [];
  schedules: Schedule[] = [];
  leaveRequests: LeaveRequest[] = [];

  loading = false;
  loadError: string | null = null;
  branchesError: string | null = null;

  currentWeekStart: Date = getMonday(new Date());

  filterForm = this.fb.group({ branchId: [''] });

  formOpen = false;
  formDay: Date | null = null;
  formError: string | null = null;
  slotForm = this.fb.group({
    employeeId: ['', Validators.required],
    fechaHoraInicio: ['', Validators.required],
    fechaHoraFin: ['', Validators.required],
    tipoTurno: ['FIJO', Validators.required],
  });

  selectedSchedule: Schedule | null = null;
  deleteError: string | null = null;

  conflictDetails: ConflictDetail[] | null = null;

  // ─── computed getters ────────────────────────────────────────────────────

  get selectedBranchId(): string | null {
    return this.filterForm.getRawValue().branchId || null;
  }

  get selectedBranch(): Branch | null {
    return this.branches.find((b) => b.id === this.selectedBranchId) ?? null;
  }

  get visibleStartHour(): number {
    const t = this.selectedBranch?.horarioVisibleInicio;
    return t ? parseInt(t.split(':')[0], 10) : 6;
  }

  get visibleEndHour(): number {
    const t = this.selectedBranch?.horarioVisibleFin;
    return t ? parseInt(t.split(':')[0], 10) : 23;
  }

  get gridHeightPx(): number {
    return (this.visibleEndHour - this.visibleStartHour) * this.PIXELS_PER_HOUR;
  }

  get hourTicks(): number[] {
    const count = this.visibleEndHour - this.visibleStartHour;
    return Array.from({ length: count }, (_, i) => this.visibleStartHour + i);
  }

  get weekDays(): Date[] {
    return Array.from({ length: 7 }, (_, i) => {
      const d = new Date(this.currentWeekStart);
      d.setDate(d.getDate() + i);
      return d;
    });
  }

  get weekLabel(): string {
    const start = this.currentWeekStart;
    const end = this.weekDays[6];
    return `${start.getDate()} ${MONTHS_SHORT[start.getMonth()]} — ${end.getDate()} ${MONTHS_SHORT[end.getMonth()]} ${end.getFullYear()}`;
  }

  get branchOptions(): SelectOption[] {
    return this.branches.map((b) => ({ value: b.id, label: b.nombre }));
  }

  get employeeOptions(): SelectOption[] {
    return this.employees.map((e) => ({ value: e.id, label: `${e.nombre} ${e.apellido}` }));
  }

  // ─── lifecycle ───────────────────────────────────────────────────────────

  ngOnInit(): void {
    forkJoin({
      branches: this.branchService.list(),
      employees: this.employeeService.list(),
    }).subscribe({
      next: ({ branches, employees }) => {
        this.branches = branches;
        this.employees = employees;
        if (branches.length > 0) {
          this.filterForm.get('branchId')!.setValue(branches[0].id, { emitEvent: false });
          this.loadSchedules();
        }
      },
      error: () => {
        this.branchesError = 'No se pudieron cargar los datos iniciales.';
      },
    });

    this.filterForm
      .get('branchId')!
      .valueChanges.pipe(distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.loadSchedules());
  }

  // ─── data loading ────────────────────────────────────────────────────────

  loadSchedules(): void {
    if (!this.selectedBranchId) return;
    this.loading = true;
    this.loadError = null;
    const desde = toIsoDate(this.currentWeekStart);
    const hasta = toIsoDate(this.weekDays[6]);

    forkJoin({
      schedules: this.scheduleService.list({ branchId: this.selectedBranchId, desde, hasta }),
      leaves: this.leaveRequestService.list({ estado: 'APROBADA' }),
    }).subscribe({
      next: ({ schedules, leaves }) => {
        this.schedules = schedules;
        this.leaveRequests = leaves;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.loadError = 'No se pudieron cargar los turnos.';
      },
    });
  }

  prevWeek(): void {
    const d = new Date(this.currentWeekStart);
    d.setDate(d.getDate() - 7);
    this.currentWeekStart = d;
    this.loadSchedules();
  }

  nextWeek(): void {
    const d = new Date(this.currentWeekStart);
    d.setDate(d.getDate() + 7);
    this.currentWeekStart = d;
    this.loadSchedules();
  }

  // ─── grid helpers ────────────────────────────────────────────────────────

  schedulesForDay(day: Date): Schedule[] {
    return this.schedules.filter((s) => isSameDay(new Date(s.fechaHoraInicio), day));
  }

  /** Schedules that started on a previous day and continue into this day. */
  continuationSchedulesForDay(day: Date): Schedule[] {
    return this.schedules.filter((s) => {
      const start = new Date(s.fechaHoraInicio);
      const end = new Date(s.fechaHoraFin);
      return !isSameDay(start, day) && end > day && isSameDay(end, day);
    });
  }

  /** Approved leaves that cover this day. */
  leavesForDay(day: Date): LeaveRequest[] {
    const dayStr = toIsoDate(day);
    return this.leaveRequests.filter(
      (lr) => lr.fechaInicio <= dayStr && lr.fechaFin >= dayStr,
    );
  }

  blockTopPx(s: Schedule): number {
    const start = new Date(s.fechaHoraInicio);
    const startMinutes = start.getHours() * 60 + start.getMinutes();
    return Math.max(0, startMinutes - this.visibleStartHour * 60);
  }

  blockHeightPx(s: Schedule): number {
    const start = new Date(s.fechaHoraInicio);
    const end = new Date(s.fechaHoraFin);

    const startMinutes = start.getHours() * 60 + start.getMinutes();
    const endMinutes =
      end.getDate() !== start.getDate() ? 24 * 60 : end.getHours() * 60 + end.getMinutes();

    const visibleStartMin = this.visibleStartHour * 60;
    const visibleEndMin = this.visibleEndHour * 60;
    const clampedStart = Math.max(startMinutes, visibleStartMin);
    const clampedEnd = Math.min(endMinutes, visibleEndMin);

    return Math.max(30, clampedEnd - clampedStart);
  }

  continuationBlockHeightPx(s: Schedule): number {
    const end = new Date(s.fechaHoraFin);
    const endMinutes = Math.min(end.getHours() * 60 + end.getMinutes(), this.visibleEndHour * 60);
    return Math.max(30, endMinutes - this.visibleStartHour * 60);
  }

  continuationLabel(s: Schedule): string {
    const start = new Date(s.fechaHoraInicio);
    return `↪ de ${DAY_NAMES[start.getDay()]} ${pad2(start.getHours())}:${pad2(start.getMinutes())}`;
  }

  crossesMidnight(s: Schedule): boolean {
    const start = new Date(s.fechaHoraInicio);
    const end = new Date(s.fechaHoraFin);
    return end.getDate() !== start.getDate();
  }

  getEmployeeName(employeeId: string): string {
    const emp = this.employees.find((e) => e.id === employeeId);
    return emp ? `${emp.nombre} ${emp.apellido}` : '—';
  }

  formatTimeRange(s: Schedule): string {
    const start = new Date(s.fechaHoraInicio);
    const end = new Date(s.fechaHoraFin);
    return `${pad2(start.getHours())}:${pad2(start.getMinutes())} — ${pad2(end.getHours())}:${pad2(end.getMinutes())}`;
  }

  isWeekend(day: Date): boolean {
    const dow = day.getDay();
    return dow === 0 || dow === 6;
  }

  formatDayHeader(day: Date): string {
    return `${DAY_NAMES[day.getDay()]} ${day.getDate()}`;
  }

  hasOutOfAvailabilityWarning(s: Schedule): boolean {
    return s.warning === 'OUT_OF_AVAILABILITY';
  }

  // ─── create modal ────────────────────────────────────────────────────────

  openCreateModal(day?: Date): void {
    this.formOpen = true;
    this.formDay = day ?? this.currentWeekStart;
    this.formError = null;
    const dayStr = toIsoDate(this.formDay);
    this.slotForm.reset({
      employeeId: '',
      fechaHoraInicio: `${dayStr}T09:00`,
      fechaHoraFin: `${dayStr}T17:00`,
      tipoTurno: 'FIJO',
    });
  }

  closeCreateModal(): void {
    this.formOpen = false;
  }

  handleCreateSubmit(): void {
    if (this.slotForm.invalid || !this.selectedBranchId) return;
    const raw = this.slotForm.getRawValue();
    this.formError = null;

    const request: CreateScheduleRequest = {
      employeeId: raw.employeeId!,
      branchId: this.selectedBranchId,
      fechaHoraInicio: raw.fechaHoraInicio!,
      fechaHoraFin: raw.fechaHoraFin!,
      tipoTurno: raw.tipoTurno as TipoTurno,
    };

    this.scheduleService.create(request).subscribe({
      next: () => {
        this.formOpen = false;
        this.loadSchedules();
      },
      error: (err: HttpErrorResponse) => {
        if (err.status === 409 && err.error?.code === 'SCHEDULE_OVERLAP_BATCH') {
          this.formOpen = false;
          this.conflictDetails = err.error.conflictos as ConflictDetail[];
        } else {
          this.formError = 'No se pudo crear el turno. Intentá de nuevo.';
        }
      },
    });
  }

  closeConflictModal(): void {
    this.conflictDetails = null;
  }

  // ─── block detail / delete ───────────────────────────────────────────────

  openBlockDetail(schedule: Schedule): void {
    this.selectedSchedule = schedule;
    this.deleteError = null;
  }

  closeBlockDetail(): void {
    this.selectedSchedule = null;
  }

  confirmDelete(): void {
    if (!this.selectedSchedule) return;
    this.scheduleService.delete(this.selectedSchedule.id).subscribe({
      next: () => {
        this.selectedSchedule = null;
        this.loadSchedules();
      },
      error: () => {
        this.deleteError = 'No se pudo eliminar el turno. Intentá de nuevo.';
      },
    });
  }
}
