import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of, throwError } from 'rxjs';

import { AuthService } from '../services/auth.service';
import { AdvanceService } from '../../features/advances/services/advance.service';
import { LeaveRequestService } from '../../features/leaves/services/leave-request.service';
import { ScheduleService } from '../../features/schedules/services/schedule.service';
import { HomeComponent } from './home.component';

function setup(role: string) {
  const routerStub = { navigate: vi.fn() };
  const authServiceStub = { getRole: () => role };
  const leaveServiceStub = {
    list: vi.fn().mockReturnValue(of([])),
  };
  const advanceServiceStub = {
    list: vi.fn().mockReturnValue(of([])),
  };
  const scheduleServiceStub = {
    list: vi.fn().mockReturnValue(of([])),
  };

  TestBed.configureTestingModule({
    imports: [HomeComponent],
    providers: [
      { provide: AuthService, useValue: authServiceStub },
      { provide: Router, useValue: routerStub },
      { provide: ActivatedRoute, useValue: { snapshot: {}, params: of({}) } },
      { provide: LeaveRequestService, useValue: leaveServiceStub },
      { provide: AdvanceService, useValue: advanceServiceStub },
      { provide: ScheduleService, useValue: scheduleServiceStub },
    ],
  });

  return { routerStub, leaveServiceStub, advanceServiceStub, scheduleServiceStub };
}

describe('HomeComponent', () => {
  it('SUPER_ADMIN sigue redirigiendo a /companies', () => {
    const { routerStub } = setup('SUPER_ADMIN');
    const fixture = TestBed.createComponent(HomeComponent);
    fixture.detectChanges();
    expect(routerStub.navigate).toHaveBeenCalledWith(['/companies']);
  });

  it('ADMIN no redirige', () => {
    const { routerStub } = setup('ADMIN');
    const fixture = TestBed.createComponent(HomeComponent);
    fixture.detectChanges();
    expect(routerStub.navigate).not.toHaveBeenCalled();
  });

  it('RRHH no redirige', () => {
    const { routerStub } = setup('RRHH');
    const fixture = TestBed.createComponent(HomeComponent);
    fixture.detectChanges();
    expect(routerStub.navigate).not.toHaveBeenCalled();
  });

  it('SUPERVISOR no redirige', () => {
    const { routerStub } = setup('SUPERVISOR');
    const fixture = TestBed.createComponent(HomeComponent);
    fixture.detectChanges();
    expect(routerStub.navigate).not.toHaveBeenCalled();
  });

  it('ADMIN carga licencias y adelantos en paralelo', () => {
    const { leaveServiceStub, advanceServiceStub } = setup('ADMIN');
    const fixture = TestBed.createComponent(HomeComponent);
    fixture.detectChanges();
    expect(leaveServiceStub.list).toHaveBeenCalledWith({ estado: 'PENDIENTE' });
    expect(advanceServiceStub.list).toHaveBeenCalled();
  });

  it('SUPERVISOR carga licencias pero NO adelantos', () => {
    const { leaveServiceStub, advanceServiceStub } = setup('SUPERVISOR');
    const fixture = TestBed.createComponent(HomeComponent);
    fixture.detectChanges();
    expect(leaveServiceStub.list).toHaveBeenCalledWith({ estado: 'PENDIENTE' });
    expect(advanceServiceStub.list).not.toHaveBeenCalled();
  });

  it('ADMIN: pendingLeavesCount y pendingAdvancesCount se calculan correctamente', () => {
    const { leaveServiceStub, advanceServiceStub } = setup('ADMIN');
    leaveServiceStub.list.mockReturnValue(
      of([{ id: '1', estado: 'PENDIENTE' }, { id: '2', estado: 'PENDIENTE' }])
    );
    advanceServiceStub.list.mockReturnValue(
      of([
        { id: 'a1', estado: 'PENDIENTE' },
        { id: 'a2', estado: 'DESCONTADO' },
        { id: 'a3', estado: 'PENDIENTE' },
      ])
    );
    const fixture = TestBed.createComponent(HomeComponent);
    fixture.detectChanges();
    const comp = fixture.componentInstance;
    expect(comp.pendingLeavesCount).toBe(2);
    expect(comp.pendingAdvancesCount).toBe(2);
  });

  it('error HTTP: setea loadError y apaga loading', () => {
    const { leaveServiceStub } = setup('ADMIN');
    leaveServiceStub.list.mockReturnValue(throwError(() => new Error('network')));
    const fixture = TestBed.createComponent(HomeComponent);
    fixture.detectChanges();
    const comp = fixture.componentInstance;
    expect(comp.loadError).toBe(true);
    expect(comp.loading).toBe(false);
  });
});
