import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { distinctUntilChanged } from 'rxjs';

import { AuthService } from '../../../../core/services/auth.service';
import { ButtonDirective } from '../../../../shared/components/button/button.directive';
import { InputComponent } from '../../../../shared/components/input/input.component';
import { ModalComponent } from '../../../../shared/components/modal/modal.component';
import { SelectComponent, SelectOption } from '../../../../shared/components/select/select.component';
import { Branch } from '../../../branches/models/branch';
import { BranchService } from '../../../branches/services/branch.service';
import { Employee } from '../../../employees/models/employee';
import { EmployeeService } from '../../../employees/services/employee.service';
import { CreateScheduleRequest, Schedule, TipoTurno } from '../../models/schedule';
import { ScheduleService } from '../../services/schedule.service';

const MONTHS_SHORT = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];
const DAY_NAMES = ['Dom', 'Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb'];

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

  myEmployeeId: string | null = null;

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

  ngOnInit(): void {
    if (this.role === 'EMPLOYEE') {
      this.employeeService.me().subscribe({
        next: (emp) => {
          this.myEmployeeId = emp.id;
          this.loadSchedules();
        },
        error: () => {
          this.loadError = 'No se pudo obtener tu información de empleado.';
        },
      });
      return;
    }

    this.branchService.list().subscribe({
      next: (branches) => {
        this.branches = branches;
        if (branches.length > 0) {
          this.filterForm.get('branchId')!.setValue(branches[0].id, { emitEvent: false });
          this.loadSchedules();
        }
      },
      error: () => {
        this.branchesError = 'No se pudieron cargar las sucursales.';
      },
    });

    this.employeeService.list().subscribe({
      next: (employees) => { this.employees = employees; },
      error: () => {},
    });

    this.filterForm
      .get('branchId')!
      .valueChanges.pipe(distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.loadSchedules());
  }

  loadSchedules(): void {
    const desde = toIsoDate(this.currentWeekStart);
    const hasta = toIsoDate(this.weekDays[6]);

    if (this.role === 'EMPLOYEE') {
      if (!this.myEmployeeId) return;
      this.loading = true;
      this.loadError = null;
      this.scheduleService.list({ employeeId: this.myEmployeeId, desde, hasta }).subscribe({
        next: (schedules) => { this.schedules = schedules; this.loading = false; },
        error: () => { this.loading = false; this.loadError = 'No se pudieron cargar tus turnos.'; },
      });
      return;
    }

    if (!this.selectedBranchId) return;
    this.loading = true;
    this.loadError = null;
    this.scheduleService.list({ branchId: this.selectedBranchId, desde, hasta }).subscribe({
      next: (schedules) => { this.schedules = schedules; this.loading = false; },
      error: () => { this.loading = false; this.loadError = 'No se pudieron cargar los turnos.'; },
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

  isContinuation(s: Schedule, day: Date): boolean {
    const start = new Date(s.fechaHoraInicio);
    const end = new Date(s.fechaHoraFin);
    const prev = new Date(day);
    prev.setDate(prev.getDate() - 1);
    return isSameDay(start, prev) && isSameDay(end, day);
  }

  schedulesForDay(day: Date): Schedule[] {
    return this.schedules.filter((s) => {
      const start = new Date(s.fechaHoraInicio);
      if (isSameDay(start, day)) return true;
      return this.isContinuation(s, day);
    });
  }

  blockTopPx(s: Schedule, day: Date): number {
    if (this.isContinuation(s, day)) return 0;
    const start = new Date(s.fechaHoraInicio);
    const startMinutes = start.getHours() * 60 + start.getMinutes();
    return Math.max(0, startMinutes - this.visibleStartHour * 60);
  }

  blockHeightPx(s: Schedule, day: Date): number {
    const end = new Date(s.fechaHoraFin);
    const endMinutes = end.getHours() * 60 + end.getMinutes();
    if (this.isContinuation(s, day)) {
      return Math.max(30, endMinutes - this.visibleStartHour * 60);
    }
    const start = new Date(s.fechaHoraInicio);
    const startMinutes = start.getHours() * 60 + start.getMinutes();
    const effectiveEnd = end.getDate() !== start.getDate() ? 24 * 60 : endMinutes;
    const visibleStartMin = this.visibleStartHour * 60;
    const visibleEndMin = this.visibleEndHour * 60;
    const clampedStart = Math.max(startMinutes, visibleStartMin);
    const clampedEnd = Math.min(effectiveEnd, visibleEndMin);
    return Math.max(30, clampedEnd - clampedStart);
  }

  crossesMidnight(s: Schedule): boolean {
    const start = new Date(s.fechaHoraInicio);
    const end = new Date(s.fechaHoraFin);
    return end.getDate() !== start.getDate();
  }

  continuationFromTime(s: Schedule): string {
    const start = new Date(s.fechaHoraInicio);
    return `${pad2(start.getHours())}:${pad2(start.getMinutes())}`;
  }

  formatEndTime(s: Schedule): string {
    const end = new Date(s.fechaHoraFin);
    return `${pad2(end.getHours())}:${pad2(end.getMinutes())}`;
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
      error: () => {
        this.formError = 'No se pudo crear el turno. Verificá que no haya solapamiento con otro turno del empleado.';
      },
    });
  }

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
