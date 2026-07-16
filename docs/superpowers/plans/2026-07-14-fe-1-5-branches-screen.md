# FE-1.5 — `features/branches` Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the `/branches` screen (list, create, edit, activate/deactivate) against the already-merged BE-1.4 backend, with role-gated write actions (`ADMIN` only) inside a screen readable by `ADMIN`, `RRHH`, and `SUPERVISOR`.

**Architecture:** Same shape as FE-1.4's companies screen: a `BranchesListComponent` smart container composes a reusable `BranchFormComponent` (create/edit reactive form, both modes share the exact same fields this time) and a `BranchService` HTTP wrapper. No one-time-secret modal this time — `Branch` has no generated secret. Role gating happens inside `BranchesListComponent` by reading `AuthService.getRole()` once, hiding write UI (not just relying on the backend's 403).

**Tech Stack:** Angular 21.2 (standalone, decorator-based `@Input`/`@Output`, no signals), Reactive Forms, `@angular/common/http` + `provideHttpClientTesting`/`HttpTestingController` for service tests, Vitest globals (no import needed for `describe`/`it`/`expect`/`vi`), `zone.js` + `provideZoneChangeDetection()` (added in FE-1.4 — `.subscribe()` callbacks now trigger change detection automatically, no manual `markForCheck()` needed).

**Reference:** Spec at `docs/superpowers/specs/2026-07-14-fe-1-5-branches-screen-design.md`. Backend contract at `backend/src/main/java/com/staffly/backend/branch/` (already merged, do not modify).

## Global Constraints

- Standalone components only, no NgModules; no signals — local state is plain class properties (`angular-frontend` skill).
- `@Input()`/`@Output()` decorators, never `input()`/`output()` functions; `inject()` for DI inside the class body (`angular-frontend` skill).
- Control flow: `@if`/`@for` with `track`, never `*ngIf`/`*ngFor` (`angular-frontend` skill).
- Reactive Forms only (`FormBuilder`/`FormGroup`), never template-driven (`frontend/CLAUDE.md`).
- Styles: Tailwind utility classes only, referencing the FE-1.3 brand tokens (`brand-*`, `badge-*`). Zero `style=""` inline, zero custom CSS.
- Zero `any` without a justifying comment; every input has an associated `<label>`; max ~150 lines per file.
- Commits: Conventional Commits, messages in Spanish, lowercase, no trailing period. Allowed types: `feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:`.
- `company_id` is never read from the URL or a client-supplied parameter — the backend scopes every branch endpoint from the JWT automatically; this screen never sends a `company_id` anywhere.
- Write UI (create button, Editar, Activar/Desactivar) is hidden — not just disabled — for any role other than `ADMIN`. This mirrors the backend's own `@PreAuthorize("hasRole('ADMIN')")` on those three endpoints.
- `core/home/home.component.ts` is explicitly OUT of scope for this plan — no redirect changes for `ADMIN`/`RRHH`/`SUPERVISOR` (spec decision, deferred to FE-1.6).
- `horarioVisibleInicio`/`horarioVisibleFin` are optional `LocalTime` fields, represented as `HH:mm` strings on the frontend (native `<input type="time">` granularity) — always slice any `HH:mm:ss` value from the backend down to 5 characters before putting it in the form.
- Branch: `feature/branches-screen`, already created from `main` with the spec doc committed. This plan's commits land on top of it.

---

## File Structure

```
frontend/src/app/
├── app.routes.ts                                          [MODIFY] add /branches route
└── features/branches/
    ├── models/
    │   └── branch.ts                                      [CREATE] TS types mirroring the backend DTOs
    ├── services/
    │   ├── branch.service.ts                              [CREATE] list/create/update/updateStatus
    │   └── branch.service.spec.ts                         [CREATE]
    └── components/
        ├── branch-form/
        │   ├── branch-form.component.ts                   [CREATE]
        │   ├── branch-form.component.html                 [CREATE]
        │   └── branch-form.component.spec.ts               [CREATE]
        └── branches-list/
            ├── branches-list.component.ts                  [CREATE]
            ├── branches-list.component.html                 [CREATE]
            └── branches-list.component.spec.ts               [CREATE]
```

Task order: models+service (nothing depends on UI) → branch-form → branches-list (composes the previous two, plus role gating) → routing (depends on branches-list existing) → full verification.

---

### Task 1: `Branch` models + `BranchService`

**Files:**
- Create: `frontend/src/app/features/branches/models/branch.ts`
- Create: `frontend/src/app/features/branches/services/branch.service.ts`
- Test: `frontend/src/app/features/branches/services/branch.service.spec.ts`

**Interfaces:**
- Produces: `Branch`, `EstadoSucursal` (`'ACTIVA' | 'INACTIVA'`), `CreateBranchRequest`, `UpdateBranchRequest`, `UpdateBranchStatusRequest` (all from `models/branch.ts`); `BranchService` (`providedIn: 'root'`) with `list(): Observable<Branch[]>`, `create(request: CreateBranchRequest): Observable<Branch>`, `update(id: string, request: UpdateBranchRequest): Observable<Branch>`, `updateStatus(id: string, request: UpdateBranchStatusRequest): Observable<Branch>`. Later tasks (branch-form, branches-list) import from `models/branch.ts`; `branches-list` also injects `BranchService`.

- [ ] **Step 1: Write the models (no test — pure types)**

Create `frontend/src/app/features/branches/models/branch.ts`:

```typescript
export type EstadoSucursal = 'ACTIVA' | 'INACTIVA';

export interface Branch {
  id: string;
  nombre: string;
  direccion: string;
  zonaHoraria: string;
  estado: EstadoSucursal;
  horarioVisibleInicio: string | null;
  horarioVisibleFin: string | null;
}

export interface CreateBranchRequest {
  nombre: string;
  direccion: string;
  zonaHoraria: string;
  horarioVisibleInicio?: string;
  horarioVisibleFin?: string;
}

export interface UpdateBranchRequest {
  nombre?: string;
  direccion?: string;
  zonaHoraria?: string;
  horarioVisibleInicio?: string;
  horarioVisibleFin?: string;
}

export interface UpdateBranchStatusRequest {
  estado: EstadoSucursal;
}
```

- [ ] **Step 2: Write the failing test for `BranchService`**

Create `frontend/src/app/features/branches/services/branch.service.spec.ts`:

```typescript
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { environment } from '../../../../environments/environment';
import {
  Branch,
  CreateBranchRequest,
  UpdateBranchRequest,
  UpdateBranchStatusRequest,
} from '../models/branch';
import { BranchService } from './branch.service';

const baseUrl = `${environment.apiUrl}/branches`;

const mockBranch: Branch = {
  id: '1',
  nombre: 'Casa Central',
  direccion: 'Av. Colón 1240',
  zonaHoraria: 'America/Buenos_Aires',
  estado: 'ACTIVA',
  horarioVisibleInicio: '10:00:00',
  horarioVisibleFin: '02:00:00',
};

describe('BranchService', () => {
  let service: BranchService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(BranchService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('lists branches with a GET to /branches', () => {
    service.list().subscribe((branches) => expect(branches).toEqual([mockBranch]));
    const req = httpMock.expectOne(baseUrl);
    expect(req.request.method).toBe('GET');
    req.flush([mockBranch]);
  });

  it('creates a branch with a POST to /branches', () => {
    const request: CreateBranchRequest = {
      nombre: 'Casa Central',
      direccion: 'Av. Colón 1240',
      zonaHoraria: 'America/Buenos_Aires',
    };
    service.create(request).subscribe((branch) => expect(branch).toEqual(mockBranch));
    const req = httpMock.expectOne(baseUrl);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(mockBranch);
  });

  it('updates a branch with a PATCH to /branches/{id}', () => {
    const request: UpdateBranchRequest = { nombre: 'Nuevo nombre' };
    service.update('1', request).subscribe((branch) => expect(branch).toEqual(mockBranch));
    const req = httpMock.expectOne(`${baseUrl}/1`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual(request);
    req.flush(mockBranch);
  });

  it('updates a branch status with a PATCH to /branches/{id}/status', () => {
    const request: UpdateBranchStatusRequest = { estado: 'INACTIVA' };
    service.updateStatus('1', request).subscribe((branch) => expect(branch).toEqual(mockBranch));
    const req = httpMock.expectOne(`${baseUrl}/1/status`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual(request);
    req.flush(mockBranch);
  });
});
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd frontend && npx ng test --include='**/branch.service.spec.ts' --watch=false`
Expected: FAIL — `Cannot find module './branch.service'`.

- [ ] **Step 4: Write minimal implementation**

Create `frontend/src/app/features/branches/services/branch.service.ts`:

```typescript
import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../../environments/environment';
import {
  Branch,
  CreateBranchRequest,
  UpdateBranchRequest,
  UpdateBranchStatusRequest,
} from '../models/branch';

@Injectable({ providedIn: 'root' })
export class BranchService {
  private http = inject(HttpClient);
  private baseUrl = `${environment.apiUrl}/branches`;

  list(): Observable<Branch[]> {
    return this.http.get<Branch[]>(this.baseUrl);
  }

  create(request: CreateBranchRequest): Observable<Branch> {
    return this.http.post<Branch>(this.baseUrl, request);
  }

  update(id: string, request: UpdateBranchRequest): Observable<Branch> {
    return this.http.patch<Branch>(`${this.baseUrl}/${id}`, request);
  }

  updateStatus(id: string, request: UpdateBranchStatusRequest): Observable<Branch> {
    return this.http.patch<Branch>(`${this.baseUrl}/${id}/status`, request);
  }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd frontend && npx ng test --include='**/branch.service.spec.ts' --watch=false`
Expected: PASS, 4 tests.

- [ ] **Step 6: Commit**

```bash
cd frontend
git add src/app/features/branches/models src/app/features/branches/services
git commit -m "feat: agregar modelos y servicio http de branches"
```

---

### Task 2: `BranchFormComponent`

**Files:**
- Create: `frontend/src/app/features/branches/components/branch-form/branch-form.component.ts`
- Create: `frontend/src/app/features/branches/components/branch-form/branch-form.component.html`
- Test: `frontend/src/app/features/branches/components/branch-form/branch-form.component.spec.ts`

**Interfaces:**
- Consumes: `Branch` from `../../models/branch` (Task 1); `InputComponent` (selector `ui-input`, supports `type="time"` via its existing `type` `@Input`) and `ButtonDirective` (selector `button[ui-button]`) from the FE-1.3 kit at `frontend/src/app/shared/components/`.
- Produces: `BranchFormComponent` (selector `app-branch-form`), `@Input() mode: 'create' | 'edit' = 'create'`, `@Input() initialValue?: Branch`, `@Output() submitted = new EventEmitter<BranchFormValue>()`, exported type `BranchFormValue` (`{ nombre, direccion, zonaHoraria, horarioVisibleInicio, horarioVisibleFin }`, all `string` — the two horario fields are `''` when left blank, never `undefined`, since they come straight off a reactive form). Task 3 (`branches-list`) imports `BranchFormComponent` and `BranchFormValue`.
- Unlike `CompanyFormComponent` (FE-1.4), there is no field that only exists in one mode — `mode` only changes the submit button's label and (in the parent) the modal title.

- [ ] **Step 1: Write the failing test**

Create `frontend/src/app/features/branches/components/branch-form/branch-form.component.spec.ts`:

```typescript
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { Branch } from '../../models/branch';
import { BranchFormComponent, BranchFormValue } from './branch-form.component';

@Component({
  standalone: true,
  imports: [BranchFormComponent],
  template: `
    <app-branch-form
      [mode]="mode"
      [initialValue]="initialValue"
      (submitted)="onSubmitted($event)"
    ></app-branch-form>
  `,
})
class HostComponent {
  mode: 'create' | 'edit' = 'create';
  initialValue?: Branch;
  submittedValue?: BranchFormValue;

  onSubmitted(value: BranchFormValue): void {
    this.submittedValue = value;
  }
}

const mockBranch: Branch = {
  id: '1',
  nombre: 'Casa Central',
  direccion: 'Av. Colón 1240',
  zonaHoraria: 'America/Buenos_Aires',
  estado: 'ACTIVA',
  horarioVisibleInicio: '10:00:00',
  horarioVisibleFin: '02:00:00',
};

describe('BranchFormComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
  });

  it('marks fields touched and does not emit submitted when required fields are missing', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const form = fixture.nativeElement.querySelector('form') as HTMLFormElement;
    form.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
    expect(fixture.componentInstance.submittedValue).toBeUndefined();
    expect(fixture.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
  });

  it('prefills the form from initialValue, slicing horario fields down to HH:mm', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.componentInstance.mode = 'edit';
    fixture.componentInstance.initialValue = mockBranch;
    fixture.detectChanges();
    const inputs = fixture.nativeElement.querySelectorAll('input');
    expect((inputs[0] as HTMLInputElement).value).toBe('Casa Central');
    expect((inputs[3] as HTMLInputElement).value).toBe('10:00');
  });

  it('emits submitted with empty strings for the optional horario fields when left blank', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const component = fixture.debugElement.children[0].componentInstance as BranchFormComponent;
    component.form.setValue({
      nombre: 'Casa Central',
      direccion: 'Av. Colón 1240',
      zonaHoraria: 'America/Buenos_Aires',
      horarioVisibleInicio: '',
      horarioVisibleFin: '',
    });
    fixture.detectChanges();
    const form = fixture.nativeElement.querySelector('form') as HTMLFormElement;
    form.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
    expect(fixture.componentInstance.submittedValue).toEqual({
      nombre: 'Casa Central',
      direccion: 'Av. Colón 1240',
      zonaHoraria: 'America/Buenos_Aires',
      horarioVisibleInicio: '',
      horarioVisibleFin: '',
    });
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npx ng test --include='**/branch-form.component.spec.ts' --watch=false`
Expected: FAIL — `Cannot find module './branch-form.component'`.

- [ ] **Step 3: Write minimal implementation**

Create `frontend/src/app/features/branches/components/branch-form/branch-form.component.ts`:

```typescript
import { Component, EventEmitter, Input, OnInit, Output, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { ButtonDirective } from '../../../../shared/components/button/button.directive';
import { InputComponent } from '../../../../shared/components/input/input.component';
import { Branch } from '../../models/branch';

export interface BranchFormValue {
  nombre: string;
  direccion: string;
  zonaHoraria: string;
  horarioVisibleInicio: string;
  horarioVisibleFin: string;
}

@Component({
  selector: 'app-branch-form',
  standalone: true,
  imports: [ReactiveFormsModule, InputComponent, ButtonDirective],
  templateUrl: './branch-form.component.html',
})
export class BranchFormComponent implements OnInit {
  @Input() mode: 'create' | 'edit' = 'create';
  @Input() initialValue?: Branch;
  @Output() submitted = new EventEmitter<BranchFormValue>();

  private fb = inject(FormBuilder);

  form = this.fb.group({
    nombre: ['', Validators.required],
    direccion: ['', Validators.required],
    zonaHoraria: ['', Validators.required],
    horarioVisibleInicio: [''],
    horarioVisibleFin: [''],
  });

  get nombreCtrl() {
    return this.form.get('nombre')!;
  }
  get direccionCtrl() {
    return this.form.get('direccion')!;
  }
  get zonaHorariaCtrl() {
    return this.form.get('zonaHoraria')!;
  }

  ngOnInit(): void {
    if (this.initialValue) {
      this.form.patchValue({
        nombre: this.initialValue.nombre,
        direccion: this.initialValue.direccion,
        zonaHoraria: this.initialValue.zonaHoraria,
        horarioVisibleInicio: this.initialValue.horarioVisibleInicio?.slice(0, 5) ?? '',
        horarioVisibleFin: this.initialValue.horarioVisibleFin?.slice(0, 5) ?? '',
      });
    }
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitted.emit(this.form.getRawValue() as BranchFormValue);
  }
}
```

Create `frontend/src/app/features/branches/components/branch-form/branch-form.component.html`:

```html
<form [formGroup]="form" (ngSubmit)="onSubmit()" class="space-y-4">
  <ui-input
    label="Nombre"
    formControlName="nombre"
    [errorMessage]="nombreCtrl.invalid && nombreCtrl.touched ? 'El nombre es requerido.' : undefined"
  ></ui-input>
  <ui-input
    label="Dirección"
    formControlName="direccion"
    [errorMessage]="direccionCtrl.invalid && direccionCtrl.touched ? 'La dirección es requerida.' : undefined"
  ></ui-input>
  <ui-input
    label="Zona horaria"
    formControlName="zonaHoraria"
    [errorMessage]="zonaHorariaCtrl.invalid && zonaHorariaCtrl.touched ? 'La zona horaria es requerida.' : undefined"
  ></ui-input>
  <ui-input label="Horario visible desde (opcional)" type="time" formControlName="horarioVisibleInicio"></ui-input>
  <ui-input label="Horario visible hasta (opcional)" type="time" formControlName="horarioVisibleFin"></ui-input>
  <button ui-button type="submit" class="w-full">
    @if (mode === 'create') {
      Crear sucursal
    } @else {
      Guardar cambios
    }
  </button>
</form>
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npx ng test --include='**/branch-form.component.spec.ts' --watch=false`
Expected: PASS, 3 tests.

- [ ] **Step 5: Commit**

```bash
cd frontend
git add src/app/features/branches/components/branch-form
git commit -m "feat: agregar formulario reactivo de alta y edicion de branches"
```

---

### Task 3: `BranchesListComponent`

**Files:**
- Create: `frontend/src/app/features/branches/components/branches-list/branches-list.component.ts`
- Create: `frontend/src/app/features/branches/components/branches-list/branches-list.component.html`
- Test: `frontend/src/app/features/branches/components/branches-list/branches-list.component.spec.ts`

**Interfaces:**
- Consumes: `BranchService` (Task 1) — `list()`, `create()`, `update()`, `updateStatus()`; `Branch`, `EstadoSucursal`, `CreateBranchRequest`, `UpdateBranchRequest` (Task 1); `BranchFormComponent`/`BranchFormValue` (Task 2); `AuthService` (`frontend/src/app/core/services/auth.service.ts`, already exists — `getRole(): Rol | null`); `TableComponent` (`ui-table`), `BadgeComponent` (`ui-badge`), `ModalComponent` (`ui-modal`), `ButtonDirective` (`button[ui-button]`) from the FE-1.3 kit.
- Produces: `BranchesListComponent` (selector `app-branches-list`) with public properties `isAdmin: boolean` (computed once from `AuthService.getRole()`), `branches: Branch[]`, `loading: boolean`, `loadError: string | null`, `formMode: 'create' | 'edit' | null`, `formInitialValue?: Branch`, `formError: string | null`, `statusTarget: Branch | null`, `statusError: string | null`, and methods `openCreateModal()`, `openEditModal(branch: Branch)`, `closeFormModal()`, `handleFormSubmit(value: BranchFormValue)`, `openStatusConfirm(branch: Branch)`, `closeStatusConfirm()`, `confirmStatusChange()`. Task 4 (routing) lazy-loads this component by name.

- [ ] **Step 1: Write the failing test**

Create `frontend/src/app/features/branches/components/branches-list/branches-list.component.spec.ts`:

```typescript
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { AuthService } from '../../../../core/services/auth.service';
import { Branch } from '../../models/branch';
import { BranchService } from '../../services/branch.service';
import { BranchesListComponent } from './branches-list.component';

const mockBranch: Branch = {
  id: '1',
  nombre: 'Casa Central',
  direccion: 'Av. Colón 1240',
  zonaHoraria: 'America/Buenos_Aires',
  estado: 'ACTIVA',
  horarioVisibleInicio: '10:00:00',
  horarioVisibleFin: '02:00:00',
};

describe('BranchesListComponent', () => {
  let branchServiceStub: {
    list: ReturnType<typeof vi.fn>;
    create: ReturnType<typeof vi.fn>;
    update: ReturnType<typeof vi.fn>;
    updateStatus: ReturnType<typeof vi.fn>;
  };

  function configure(role: string): void {
    branchServiceStub = {
      list: vi.fn().mockReturnValue(of([mockBranch])),
      create: vi.fn(),
      update: vi.fn(),
      updateStatus: vi.fn(),
    };

    TestBed.configureTestingModule({
      imports: [BranchesListComponent],
      providers: [
        { provide: BranchService, useValue: branchServiceStub },
        { provide: AuthService, useValue: { getRole: () => role } },
      ],
    });
  }

  it('shows the loaded branches in the table', () => {
    configure('ADMIN');
    const fixture = TestBed.createComponent(BranchesListComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Casa Central');
  });

  it('shows a loading error banner when the list request fails', () => {
    configure('ADMIN');
    branchServiceStub.list.mockReturnValue(throwError(() => new Error('network error')));
    const fixture = TestBed.createComponent(BranchesListComponent);
    fixture.detectChanges();
    const alert = fixture.nativeElement.querySelector('[role="alert"]');
    expect(alert?.textContent).toContain('No se pudo cargar');
  });

  it('shows create/edit/status action buttons for an ADMIN', () => {
    configure('ADMIN');
    const fixture = TestBed.createComponent(BranchesListComponent);
    fixture.detectChanges();
    const buttons = Array.from(fixture.nativeElement.querySelectorAll('button')).map((b) =>
      (b as HTMLButtonElement).textContent?.trim(),
    );
    expect(buttons).toContain('+ Nueva sucursal');
    expect(buttons).toContain('Editar');
  });

  it('hides create/edit/status action buttons for a non-ADMIN role', () => {
    configure('RRHH');
    const fixture = TestBed.createComponent(BranchesListComponent);
    fixture.detectChanges();
    const buttons = Array.from(fixture.nativeElement.querySelectorAll('button')).map((b) =>
      (b as HTMLButtonElement).textContent?.trim(),
    );
    expect(buttons).not.toContain('+ Nueva sucursal');
    expect(buttons).not.toContain('Editar');
  });

  it('opens the create modal, and on successful submit refreshes the list', () => {
    configure('ADMIN');
    branchServiceStub.create.mockReturnValue(of(mockBranch));

    const fixture = TestBed.createComponent(BranchesListComponent);
    fixture.detectChanges();

    fixture.componentInstance.openCreateModal();
    fixture.detectChanges();
    expect(fixture.componentInstance.formMode).toBe('create');

    fixture.componentInstance.handleFormSubmit({
      nombre: 'Sucursal Nueva',
      direccion: 'Calle Falsa 123',
      zonaHoraria: 'America/Buenos_Aires',
      horarioVisibleInicio: '',
      horarioVisibleFin: '',
    });
    fixture.detectChanges();

    expect(branchServiceStub.create).toHaveBeenCalled();
    expect(fixture.componentInstance.formMode).toBeNull();
    expect(branchServiceStub.list).toHaveBeenCalledTimes(2);
  });

  it('shows a form error banner without closing the modal when create fails', () => {
    configure('ADMIN');
    branchServiceStub.create.mockReturnValue(throwError(() => new Error('validation error')));
    const fixture = TestBed.createComponent(BranchesListComponent);
    fixture.detectChanges();

    fixture.componentInstance.openCreateModal();
    fixture.detectChanges();
    fixture.componentInstance.handleFormSubmit({
      nombre: 'X',
      direccion: 'X',
      zonaHoraria: 'X',
      horarioVisibleInicio: '',
      horarioVisibleFin: '',
    });
    fixture.detectChanges();

    expect(fixture.componentInstance.formMode).toBe('create');
    expect(fixture.componentInstance.formError).toContain('No se pudo crear');
  });

  it('opens the edit modal prefilled with the selected branch', () => {
    configure('ADMIN');
    const fixture = TestBed.createComponent(BranchesListComponent);
    fixture.detectChanges();

    fixture.componentInstance.openEditModal(mockBranch);

    expect(fixture.componentInstance.formMode).toBe('edit');
    expect(fixture.componentInstance.formInitialValue).toEqual(mockBranch);
  });

  it('opens the status confirmation and applies the opposite estado on confirm', () => {
    configure('ADMIN');
    branchServiceStub.updateStatus.mockReturnValue(of({ ...mockBranch, estado: 'INACTIVA' }));
    const fixture = TestBed.createComponent(BranchesListComponent);
    fixture.detectChanges();

    fixture.componentInstance.openStatusConfirm(mockBranch);
    expect(fixture.componentInstance.statusTarget).toEqual(mockBranch);

    fixture.componentInstance.confirmStatusChange();

    expect(branchServiceStub.updateStatus).toHaveBeenCalledWith('1', { estado: 'INACTIVA' });
    expect(fixture.componentInstance.statusTarget).toBeNull();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npx ng test --include='**/branches-list.component.spec.ts' --watch=false`
Expected: FAIL — `Cannot find module './branches-list.component'`.

- [ ] **Step 3: Write minimal implementation**

Create `frontend/src/app/features/branches/components/branches-list/branches-list.component.ts`:

```typescript
import { Component, OnInit, inject } from '@angular/core';

import { AuthService } from '../../../../core/services/auth.service';
import { BadgeComponent } from '../../../../shared/components/badge/badge.component';
import { ButtonDirective } from '../../../../shared/components/button/button.directive';
import { ModalComponent } from '../../../../shared/components/modal/modal.component';
import { TableComponent } from '../../../../shared/components/table/table.component';
import {
  Branch,
  CreateBranchRequest,
  EstadoSucursal,
  UpdateBranchRequest,
} from '../../models/branch';
import { BranchService } from '../../services/branch.service';
import { BranchFormComponent, BranchFormValue } from '../branch-form/branch-form.component';

@Component({
  selector: 'app-branches-list',
  standalone: true,
  imports: [ButtonDirective, BadgeComponent, ModalComponent, TableComponent, BranchFormComponent],
  templateUrl: './branches-list.component.html',
})
export class BranchesListComponent implements OnInit {
  private branchService = inject(BranchService);
  private authService = inject(AuthService);

  readonly isAdmin = this.authService.getRole() === 'ADMIN';

  branches: Branch[] = [];
  loading = true;
  loadError: string | null = null;

  formMode: 'create' | 'edit' | null = null;
  formInitialValue?: Branch;
  formError: string | null = null;

  statusTarget: Branch | null = null;
  statusError: string | null = null;

  ngOnInit(): void {
    this.loadBranches();
  }

  loadBranches(): void {
    this.loading = true;
    this.loadError = null;
    this.branchService.list().subscribe({
      next: (branches) => {
        this.branches = branches;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.loadError = 'No se pudo cargar el listado de sucursales.';
      },
    });
  }

  openCreateModal(): void {
    this.formMode = 'create';
    this.formInitialValue = undefined;
    this.formError = null;
  }

  openEditModal(branch: Branch): void {
    this.formMode = 'edit';
    this.formInitialValue = branch;
    this.formError = null;
  }

  closeFormModal(): void {
    this.formMode = null;
  }

  handleFormSubmit(value: BranchFormValue): void {
    this.formError = null;

    if (this.formMode === 'create') {
      const request: CreateBranchRequest = {
        nombre: value.nombre,
        direccion: value.direccion,
        zonaHoraria: value.zonaHoraria,
        horarioVisibleInicio: value.horarioVisibleInicio || undefined,
        horarioVisibleFin: value.horarioVisibleFin || undefined,
      };
      this.branchService.create(request).subscribe({
        next: () => {
          this.formMode = null;
          this.loadBranches();
        },
        error: () => {
          this.formError = 'No se pudo crear la sucursal. Intentá de nuevo.';
        },
      });
      return;
    }

    const target = this.formInitialValue;
    if (this.formMode === 'edit' && target) {
      const request: UpdateBranchRequest = {
        nombre: value.nombre,
        direccion: value.direccion,
        zonaHoraria: value.zonaHoraria,
        horarioVisibleInicio: value.horarioVisibleInicio || undefined,
        horarioVisibleFin: value.horarioVisibleFin || undefined,
      };
      this.branchService.update(target.id, request).subscribe({
        next: () => {
          this.formMode = null;
          this.loadBranches();
        },
        error: () => {
          this.formError = 'No se pudo guardar los cambios. Intentá de nuevo.';
        },
      });
    }
  }

  openStatusConfirm(branch: Branch): void {
    this.statusTarget = branch;
    this.statusError = null;
  }

  closeStatusConfirm(): void {
    this.statusTarget = null;
  }

  confirmStatusChange(): void {
    const target = this.statusTarget;
    if (!target) {
      return;
    }
    const nuevoEstado: EstadoSucursal = target.estado === 'ACTIVA' ? 'INACTIVA' : 'ACTIVA';
    this.branchService.updateStatus(target.id, { estado: nuevoEstado }).subscribe({
      next: () => {
        this.statusTarget = null;
        this.loadBranches();
      },
      error: () => {
        this.statusError = 'No se pudo cambiar el estado de la sucursal.';
      },
    });
  }
}
```

Create `frontend/src/app/features/branches/components/branches-list/branches-list.component.html`:

```html
<main class="mx-auto max-w-5xl px-4 py-8">
  <div class="mb-4 flex items-center justify-between">
    <h1 class="font-heading text-2xl font-bold text-brand-ink">Sucursales</h1>
    @if (isAdmin) {
      <button ui-button type="button" (click)="openCreateModal()">+ Nueva sucursal</button>
    }
  </div>

  @if (loading) {
    <p class="text-sm text-brand-muted">Cargando sucursales...</p>
  } @else if (loadError) {
    <p class="rounded-lg bg-badge-error-bg p-3 text-sm text-badge-error-ink" role="alert">{{ loadError }}</p>
  } @else if (branches.length === 0) {
    <p class="text-sm text-brand-muted">Todavía no hay sucursales cargadas.</p>
  } @else {
    <ui-table>
      <thead>
        <tr>
          <th class="px-3 py-2 text-left text-xs font-semibold uppercase text-brand-muted">Nombre</th>
          <th class="px-3 py-2 text-left text-xs font-semibold uppercase text-brand-muted">Dirección</th>
          <th class="px-3 py-2 text-left text-xs font-semibold uppercase text-brand-muted">Zona horaria</th>
          <th class="px-3 py-2 text-left text-xs font-semibold uppercase text-brand-muted">Horario visible</th>
          <th class="px-3 py-2 text-left text-xs font-semibold uppercase text-brand-muted">Estado</th>
          @if (isAdmin) {
            <th class="px-3 py-2"></th>
          }
        </tr>
      </thead>
      <tbody>
        @for (branch of branches; track branch.id) {
          <tr class="border-b border-brand-line">
            <td class="px-3 py-2">{{ branch.nombre }}</td>
            <td class="px-3 py-2">{{ branch.direccion }}</td>
            <td class="px-3 py-2">{{ branch.zonaHoraria }}</td>
            <td class="px-3 py-2">
              @if (branch.horarioVisibleInicio && branch.horarioVisibleFin) {
                {{ branch.horarioVisibleInicio }} – {{ branch.horarioVisibleFin }}
              } @else {
                —
              }
            </td>
            <td class="px-3 py-2">
              <ui-badge [variant]="branch.estado === 'ACTIVA' ? 'success' : 'error'">{{ branch.estado }}</ui-badge>
            </td>
            @if (isAdmin) {
              <td class="px-3 py-2 text-right">
                <button ui-button variant="secondary" size="sm" type="button" (click)="openEditModal(branch)">
                  Editar
                </button>
                <button ui-button variant="secondary" size="sm" type="button" (click)="openStatusConfirm(branch)">
                  @if (branch.estado === 'ACTIVA') {
                    Desactivar
                  } @else {
                    Activar
                  }
                </button>
              </td>
            }
          </tr>
        }
      </tbody>
    </ui-table>
  }
</main>

@if (formMode) {
  <ui-modal [title]="formMode === 'create' ? 'Nueva sucursal' : 'Editar sucursal'" (closed)="closeFormModal()">
    @if (formError) {
      <p class="mb-4 rounded-lg bg-badge-error-bg p-3 text-sm text-badge-error-ink" role="alert">{{ formError }}</p>
    }
    <app-branch-form [mode]="formMode" [initialValue]="formInitialValue" (submitted)="handleFormSubmit($event)">
    </app-branch-form>
  </ui-modal>
}

@if (statusTarget) {
  <ui-modal title="Confirmar cambio de estado" (closed)="closeStatusConfirm()">
    @if (statusError) {
      <p class="mb-4 rounded-lg bg-badge-error-bg p-3 text-sm text-badge-error-ink" role="alert">{{ statusError }}</p>
    }
    <p class="text-sm text-brand-ink">
      @if (statusTarget.estado === 'ACTIVA') {
        ¿Confirmás desactivar <strong>{{ statusTarget.nombre }}</strong>?
      } @else {
        ¿Confirmás activar <strong>{{ statusTarget.nombre }}</strong>?
      }
    </p>
    <div class="mt-6 flex justify-end gap-2">
      <button ui-button variant="secondary" type="button" (click)="closeStatusConfirm()">Cancelar</button>
      <button ui-button type="button" (click)="confirmStatusChange()">Confirmar</button>
    </div>
  </ui-modal>
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npx ng test --include='**/branches-list.component.spec.ts' --watch=false`
Expected: PASS, 8 tests.

- [ ] **Step 5: Commit**

```bash
cd frontend
git add src/app/features/branches/components/branches-list
git commit -m "feat: agregar pantalla de listado de branches con alta, edicion y gating de rol"
```

---

### Task 4: Routing — `/branches` route

**Files:**
- Modify: `frontend/src/app/app.routes.ts`

**Interfaces:**
- Consumes: `BranchesListComponent` (Task 3, lazy-loaded by path); `roleGuard` from `frontend/src/app/core/guards/role.guard.ts` (already exists and already imported in this file since FE-1.4 — signature `roleGuard(rolesPermitidos: Rol[]): CanActivateFn`, self-sufficient, redirects to `/login` if not authenticated).

No new test file for this task — `roleGuard` already has its own behavior covered where it was introduced, and the route wiring itself is exercised by Task 5's manual verification (there is no unit-testable behavior in a one-line route table entry beyond what `roleGuard`'s existing tests already prove).

- [ ] **Step 1: Add the route**

Modify `frontend/src/app/app.routes.ts` — add this route object to the `routes` array, right after the existing `companies` route and before the `''` (home) route:

```typescript
  {
    path: 'branches',
    canActivate: [roleGuard(['ADMIN', 'RRHH', 'SUPERVISOR'])],
    loadComponent: () =>
      import('./features/branches/components/branches-list/branches-list.component').then(
        (m) => m.BranchesListComponent,
      ),
  },
```

The full file should read:

```typescript
import { Routes } from '@angular/router';

import { authGuard } from './core/guards/auth.guard';
import { roleGuard } from './core/guards/role.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./core/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'companies',
    canActivate: [roleGuard(['SUPER_ADMIN'])],
    loadComponent: () =>
      import('./features/companies/components/companies-list/companies-list.component').then(
        (m) => m.CompaniesListComponent,
      ),
  },
  {
    path: 'branches',
    canActivate: [roleGuard(['ADMIN', 'RRHH', 'SUPERVISOR'])],
    loadComponent: () =>
      import('./features/branches/components/branches-list/branches-list.component').then(
        (m) => m.BranchesListComponent,
      ),
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./core/home/home.component').then((m) => m.HomeComponent),
  },
  {
    path: '**',
    redirectTo: '',
  },
];
```

- [ ] **Step 2: Verify the app still builds**

Run: `cd frontend && npx ng build`
Expected: exit code 0, no TypeScript or template errors, and a `branches-list-component` entry appears among the lazy chunk files in the build output.

- [ ] **Step 3: Commit**

```bash
cd frontend
git add src/app/app.routes.ts
git commit -m "feat: agregar ruta branches"
```

---

### Task 5: Full suite, build, and manual verification

**Files:** none created — verification only.

- [ ] **Step 1: Run the full test suite**

Run: `cd frontend && npx ng test --watch=false`
Expected: PASS, all specs green — the pre-existing suite on `main` (12 files, 52 tests, after FE-1.4) plus this feature's 3 new spec files (`branch.service`: 4, `branch-form`: 3, `branches-list`: 8) = 15 new tests, 67 total across 15 files.

- [ ] **Step 2: Run a production build**

Run: `cd frontend && npx ng build`
Expected: exit code 0, no TypeScript or template errors.

- [ ] **Step 3: Verify against the real backend with both an ADMIN and a non-ADMIN role**

Start the backend (`cd backend && ./mvnw spring-boot:run`, `dev` profile, H2 in memory). This time you need real tenant data, not just a platform admin: insert one `company` row and two `app_user` rows (one `ADMIN`, one `RRHH` or `SUPERVISOR`) sharing that `company_id`, via `/h2-console`, same mechanism used for FE-1.4's Super Admin (BCrypt hash generated on the spot, `RANDOM_UUID()` for ids). Check `backend/src/main/resources/db/migration/V1__create_company_table.sql` and `V2__create_branch_employee_user_platformadmin.sql` for the exact `company`/`app_user` column lists before writing the inserts.

Then, with `npx ng serve` running:

1. Log in as the `ADMIN` user, navigate to `/branches`. Confirm the "+ Nueva sucursal" button and "Editar"/"Activar"/"Desactivar" actions are visible.
2. Create a branch with only the required fields (leave horario blank) — confirm it appears in the table with "—" under Horario visible.
3. Create a second branch WITH `horarioVisibleInicio`/`horarioVisibleFin` filled in — confirm the request succeeds (this is the step that validates the `HH:mm` → backend `LocalTime` format assumption from the spec; if the backend rejects it with a 400, the fix is contained to `branch-form.component.ts`'s submit mapping — append `:00` to non-empty horario values before sending).
4. Edit a branch's name — confirm the table reflects the change.
5. Desactivar a branch — confirm the confirmation modal, then the badge updates to `INACTIVA` and the row's action label flips to "Activar". Reactivate it.
6. Log out, log back in as the `RRHH`/`SUPERVISOR` user, navigate to `/branches` directly. Confirm the table renders read-only: no "+ Nueva sucursal" button, no "Editar"/"Activar"/"Desactivar" actions, no error.
7. Check the browser console and network tab for errors during the whole flow.

- [ ] **Step 4: Final commit if any fixes were needed**

If manual verification surfaced a bug (e.g. the `LocalTime` format issue from Step 3.3), fix it and commit:

```bash
cd frontend
git add -A
git commit -m "fix: corregir <lo que haya fallado en la verificacion manual>"
```

If nothing needed fixing, skip this commit — the branch is ready for review.

---

## Self-Review Notes

- **Spec coverage:** all 4 operations (listado, alta, edición, activar/desactivar) are covered — Task 3 wires all four to `BranchService`. Role gating inside the screen (Task 3) matches the spec's explicit requirement to hide, not just disable, write actions for non-`ADMIN` roles. Routing (Task 4) matches the spec exactly, including explicitly NOT touching `home.component.ts`. The `HH:mm` slicing decision from the spec's "Contrato del backend" section is implemented in Task 2's `ngOnInit` and flagged for empirical verification in Task 5.
- **Type consistency:** `BranchFormValue` (Task 2) is defined once and consumed as-is by Task 3's `handleFormSubmit`. `Branch`/`EstadoSucursal`/`CreateBranchRequest`/`UpdateBranchRequest`/`UpdateBranchStatusRequest` (Task 1) are referenced identically across every later task — field names match the backend DTOs verbatim. `BranchService`'s method names (`list`, `create`, `update`, `updateStatus`) match what Task 3's spec mocks and what Task 3's implementation calls — same names as `CompanyService` (FE-1.4), keeping the two features' services consistent with each other.
- **No placeholders:** every step has complete, runnable code; no "add tests for the above" or "TBD" in any task.
