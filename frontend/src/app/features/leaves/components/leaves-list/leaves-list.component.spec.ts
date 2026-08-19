import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';

import { AuthService } from '../../../../core/services/auth.service';
import { EmployeeService } from '../../../employees/services/employee.service';
import { LeaveRequest } from '../../models/leave';
import { LeaveRequestService } from '../../services/leave-request.service';
import { LeaveTypeService } from '../../services/leave-type.service';
import { LeavesListComponent } from './leaves-list.component';

/** Mock con la forma REAL de LeaveRequestResponse (post AUD-14/AUD-18/AUD-20). */
function mockRequest(overrides: Partial<LeaveRequest> = {}): LeaveRequest {
  return {
    id: 'lr-1',
    employeeId: 'emp-1',
    employeeNombre: 'Juan',
    employeeApellido: 'Pérez',
    leaveTypeId: 'lt-1',
    leaveTypeNombre: 'Vacaciones',
    fechaInicio: '2026-09-01',
    fechaFin: '2026-09-05',
    motivo: null,
    estado: 'PENDIENTE',
    aprobadoPorId: null,
    tieneConflicto: false,
    ...overrides,
  };
}

function configureTestBed(role: 'ADMIN' | 'RRHH' | 'SUPERVISOR' | 'EMPLOYEE', requests: LeaveRequest[] = []) {
  const leaveRequestServiceStub = {
    list: vi.fn().mockReturnValue(of(requests)),
    approve: vi.fn(),
    reject: vi.fn(),
    cancel: vi.fn(),
    create: vi.fn(),
  };
  const leaveTypeServiceStub = {
    list: vi.fn().mockReturnValue(of([])),
    create: vi.fn(),
    update: vi.fn(),
  };
  const employeeServiceStub = { list: vi.fn().mockReturnValue(of([])) };
  const authServiceStub = { getRole: vi.fn().mockReturnValue(role) };
  const routerStub = { navigate: vi.fn() };

  TestBed.configureTestingModule({
    imports: [LeavesListComponent],
    providers: [
      { provide: LeaveRequestService, useValue: leaveRequestServiceStub },
      { provide: LeaveTypeService, useValue: leaveTypeServiceStub },
      { provide: EmployeeService, useValue: employeeServiceStub },
      { provide: AuthService, useValue: authServiceStub },
      { provide: Router, useValue: routerStub },
    ],
  });

  return { leaveRequestServiceStub, routerStub };
}

describe('LeavesListComponent', () => {
  afterEach(() => TestBed.resetTestingModule());

  describe('canCancel()', () => {
    it('permite cancelar una solicitud PENDIENTE que todavía no empezó (ADMIN)', () => {
      configureTestBed('ADMIN');
      const fixture = TestBed.createComponent(LeavesListComponent);
      const futureDate = new Date();
      futureDate.setDate(futureDate.getDate() + 5);
      const req = mockRequest({ estado: 'PENDIENTE', fechaInicio: futureDate.toISOString().split('T')[0] });

      expect(fixture.componentInstance.canCancel(req)).toBe(true);
    });

    it('permite cancelar una solicitud APROBADA que todavía no empezó', () => {
      configureTestBed('ADMIN');
      const fixture = TestBed.createComponent(LeavesListComponent);
      const futureDate = new Date();
      futureDate.setDate(futureDate.getDate() + 1);
      const req = mockRequest({ estado: 'APROBADA', fechaInicio: futureDate.toISOString().split('T')[0] });

      expect(fixture.componentInstance.canCancel(req)).toBe(true);
    });

    it('no permite cancelar una solicitud cuya fechaInicio es hoy', () => {
      configureTestBed('ADMIN');
      const fixture = TestBed.createComponent(LeavesListComponent);
      const now = new Date();
      const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
      const req = mockRequest({ estado: 'APROBADA', fechaInicio: today });

      expect(fixture.componentInstance.canCancel(req)).toBe(false);
    });

    it('no permite cancelar una solicitud que ya empezó', () => {
      configureTestBed('ADMIN');
      const fixture = TestBed.createComponent(LeavesListComponent);
      const req = mockRequest({ estado: 'APROBADA', fechaInicio: '2020-01-01' });

      expect(fixture.componentInstance.canCancel(req)).toBe(false);
    });

    it('no permite cancelar solicitudes RECHAZADA o CANCELADA', () => {
      configureTestBed('ADMIN');
      const fixture = TestBed.createComponent(LeavesListComponent);
      const futureDate = new Date();
      futureDate.setDate(futureDate.getDate() + 5);
      const fecha = futureDate.toISOString().split('T')[0];

      expect(fixture.componentInstance.canCancel(mockRequest({ estado: 'RECHAZADA', fechaInicio: fecha }))).toBe(false);
      expect(fixture.componentInstance.canCancel(mockRequest({ estado: 'CANCELADA', fechaInicio: fecha }))).toBe(false);
    });

    it('SUPERVISOR nunca puede cancelar, sin importar estado ni fecha', () => {
      configureTestBed('SUPERVISOR');
      const fixture = TestBed.createComponent(LeavesListComponent);
      const futureDate = new Date();
      futureDate.setDate(futureDate.getDate() + 5);
      const req = mockRequest({ estado: 'PENDIENTE', fechaInicio: futureDate.toISOString().split('T')[0] });

      expect(fixture.componentInstance.canCancel(req)).toBe(false);
    });
  });

  describe('render de la card contra el contrato real del backend', () => {
    it('muestra nombre, apellido y tipo de licencia para ADMIN/RRHH/SUPERVISOR', () => {
      const req = mockRequest();
      configureTestBed('ADMIN', [req]);
      const fixture = TestBed.createComponent(LeavesListComponent);
      fixture.detectChanges();

      const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
      expect(text).toContain('Juan');
      expect(text).toContain('Pérez');
      expect(text).toContain('Vacaciones');
    });

    it('muestra la advertencia de conflicto y el link cuando tieneConflicto es true', () => {
      const req = mockRequest({ tieneConflicto: true });
      configureTestBed('ADMIN', [req]);
      const fixture = TestBed.createComponent(LeavesListComponent);
      fixture.detectChanges();

      const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
      expect(text).toContain('Se superpone con un turno ya asignado');
      expect(text).toContain('Ver turno en conflicto');
    });

    it('no muestra la advertencia de conflicto cuando tieneConflicto es false', () => {
      const req = mockRequest({ tieneConflicto: false });
      configureTestBed('ADMIN', [req]);
      const fixture = TestBed.createComponent(LeavesListComponent);
      fixture.detectChanges();

      const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
      expect(text).not.toContain('Se superpone con un turno ya asignado');
    });
  });

  describe('verTurnoEnConflicto()', () => {
    it('navega a /schedules con la fecha de inicio de la licencia', () => {
      const { routerStub } = configureTestBed('ADMIN');
      const fixture = TestBed.createComponent(LeavesListComponent);
      const req = mockRequest({ fechaInicio: '2026-09-01' });

      fixture.componentInstance.verTurnoEnConflicto(req);

      expect(routerStub.navigate).toHaveBeenCalledWith(['/schedules'], {
        queryParams: { desde: '2026-09-01' },
      });
    });
  });
});
