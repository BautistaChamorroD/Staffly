import { DatePipe } from '@angular/common';
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { map } from 'rxjs/operators';

import { AdvanceService } from '../../features/advances/services/advance.service';
import { LeaveRequestService } from '../../features/leaves/services/leave-request.service';
import { Schedule } from '../../features/schedules/models/schedule';
import { ScheduleService } from '../../features/schedules/services/schedule.service';
import { CardComponent } from '../../shared/components/card/card.component';
import { Rol } from '../models/rol';
import { AuthService } from '../services/auth.service';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [RouterLink, CardComponent, DatePipe],
  templateUrl: './home.component.html',
})
export class HomeComponent implements OnInit {
  private authService = inject(AuthService);
  private router = inject(Router);
  private leaveService = inject(LeaveRequestService);
  private advanceService = inject(AdvanceService);
  private scheduleService = inject(ScheduleService);
  private destroyRef = inject(DestroyRef);

  readonly role: Rol | null = this.authService.getRole();

  loading = true;
  loadError = false;

  pendingLeavesCount = 0;
  pendingAdvancesCount = 0;

  upcomingShifts: Schedule[] = [];
  pendingOwnLeavesCount = 0;

  readonly employeeLinks = [
    { label: 'Mi horario', route: '/schedules' },
    { label: 'Disponibilidad', route: '/availability' },
    { label: 'Licencias', route: '/leaves' },
    { label: 'Mis recibos', route: '/payslips' },
  ];

  ngOnInit(): void {
    if (this.role === 'SUPER_ADMIN') {
      this.router.navigate(['/companies']);
      return;
    }

    if (this.role === 'EMPLOYEE') {
      this.loadEmployeeData();
    } else {
      this.loadManagementData();
    }
  }

  private loadManagementData(): void {
    const includesAdvances = this.role === 'ADMIN' || this.role === 'RRHH';

    forkJoin({
      pendingLeaves: this.leaveService.list({ estado: 'PENDIENTE' }).pipe(map((l) => l.length)),
      pendingAdvances: includesAdvances
        ? this.advanceService
            .list()
            .pipe(map((a) => a.filter((x) => x.estado === 'PENDIENTE').length))
        : of(0),
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ pendingLeaves, pendingAdvances }) => {
          this.pendingLeavesCount = pendingLeaves;
          this.pendingAdvancesCount = pendingAdvances;
          this.loading = false;
        },
        error: () => {
          this.loading = false;
          this.loadError = true;
        },
      });
  }

  private loadEmployeeData(): void {
    const today = new Date().toISOString().slice(0, 10);
    const in7Days = new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10);

    forkJoin({
      shifts: this.scheduleService.list({ desde: today, hasta: in7Days }),
      pendingLeaves: this.leaveService.list({ estado: 'PENDIENTE' }).pipe(map((l) => l.length)),
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ shifts, pendingLeaves }) => {
          this.upcomingShifts = shifts.slice(0, 3);
          this.pendingOwnLeavesCount = pendingLeaves;
          this.loading = false;
        },
        error: () => {
          this.loading = false;
          this.loadError = true;
        },
      });
  }
}
