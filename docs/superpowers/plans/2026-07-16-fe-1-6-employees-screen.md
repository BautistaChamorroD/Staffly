# FE-1.6 — `features/employees` Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the `/employees` screen (server-side filtered/searchable list, create, edit, employment-status change) against the already-merged BE-1.6 backend, adding a `ui-select` component to the FE-1.3 kit along the way, and resolving the home-redirect decision deferred from FE-1.5.

**Architecture:** Same shape as FE-1.4/FE-1.5: an `EmployeesListComponent` smart container composes a reusable `EmployeeFormComponent` (create/edit reactive form, both modes share the same fields) and an `EmployeeService` HTTP wrapper. New this time: a `ui-select` kit component (needed for `tipoContrato` and the 4-value `estadoLaboral` status change), server-side filters (search/estado/sucursal) wired through the backend's existing query params, and a write-gate (`canWrite`) generalized from `branches`' `isAdmin` pattern to `ADMIN || RRHH` (the two roles the backend actually allows to write).

**Tech Stack:** Angular 21.2 (standalone, decorator-based `@Input`/`@Output`, no signals), Reactive Forms, `@angular/common/http` + `provideHttpClientTesting`/`HttpTestingController`, RxJS (`debounceTime` for the search filter), Vitest's own fake timers (`vi.useFakeTimers()`/`vi.advanceTimersByTime()`) for the debounce test — **not** Angular's `fakeAsync`/`tick`, which this project's test builder (`@angular/build:unit-test`) does not support out of the box (`Expected to be running in 'ProxyZone', but it was not found` — verified empirically; `zone.js/testing` + a `setupFiles` entry did not fix it either). Vitest's fake timers patch the global `setTimeout` that RxJS's `debounceTime` uses internally, so they work transparently without needing Angular's zone-testing infrastructure. Vitest globals, `zone.js` + `provideZoneChangeDetection()` (FE-1.4 fix — `.subscribe()` callbacks trigger change detection automatically).

**Reference:** Spec at `docs/superpowers/specs/2026-07-16-fe-1-6-employees-screen-design.md`. Backend contract at `backend/src/main/java/com/staffly/backend/employee/` (already merged, do not modify).

## Global Constraints

- Standalone components only, no NgModules; no signals — local state is plain class properties (`angular-frontend` skill).
- `@Input()`/`@Output()` decorators, never `input()`/`output()` functions; `inject()` for DI inside the class body (`angular-frontend` skill).
- Control flow: `@if`/`@for` with `track`, never `*ngIf`/`*ngFor` (`angular-frontend` skill).
- Reactive Forms only (`FormBuilder`/`FormGroup`), never template-driven / `ngModel` — this includes the status-change modal's select, which uses a one-field `FormGroup`, not `[(ngModel)]` (`frontend/CLAUDE.md`).
- Styles: Tailwind utility classes only, referencing the FE-1.3 brand tokens. Zero `style=""` inline, zero custom CSS.
- Zero `any` without a justifying comment; every input has an associated `<label>`; max ~150 lines per file (the smart container, `EmployeesListComponent`, is a deliberate, documented exception — see its task).
- Commits: Conventional Commits, messages in Spanish, lowercase, no trailing period. Allowed types: `feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:`.
- `company_id` is never read from the URL or a client-supplied parameter — every employee endpoint is scoped from the JWT automatically.
- All filtering (search, `estadoLaboral`, `branchId`) is server-side via the backend's existing query params — no client-side array filtering of the loaded list.
- Write UI (create button, "Editar", "Cambiar estado") is hidden — not just disabled — for any role without backend write access. `canWrite = rol === 'ADMIN' || rol === 'RRHH'` (generalizes `branches`' `isAdmin` — here `SUPERVISOR` can read but not write, matching `@PreAuthorize` on `POST`/`PATCH /employees/**`).
- `branchIds` (an employee can belong to 1..N branches) is a checkbox list against the already-loaded `Branch[]`, not a custom multi-select widget (spec decision, YAGNI).
- Branch: `feature/employees-screen`, already created from `main` with the spec doc committed. This plan's commits land on top of it.

---

## File Structure

```
frontend/src/app/
├── app.routes.ts                                           [MODIFY] add /employees route
├── core/home/
│   ├── home.component.ts                                   [MODIFY] redirect ADMIN/RRHH/SUPERVISOR to /employees
│   └── home.component.spec.ts                               [MODIFY] cover the 3 newly-redirected roles + EMPLOYEE (no redirect)
├── shared/components/
│   ├── select/
│   │   ├── select.component.ts                              [CREATE]
│   │   ├── select.component.html                             [CREATE]
│   │   └── select.component.spec.ts                           [CREATE]
│   └── input/
│       ├── input.component.ts                                [MODIFY] add `step` input
│       ├── input.component.html                               [MODIFY] bind `[attr.step]`
│       └── input.component.spec.ts                             [MODIFY] add a step-forwarding test
└── features/employees/
    ├── models/
    │   └── employee.ts                                        [CREATE] TS types mirroring the backend DTOs
    ├── services/
    │   ├── employee.service.ts                                [CREATE] list(filters)/create/update/updateStatus
    │   └── employee.service.spec.ts                             [CREATE]
    └── components/
        ├── employee-form/
        │   ├── employee-form.component.ts                      [CREATE]
        │   ├── employee-form.component.html                     [CREATE]
        │   └── employee-form.component.spec.ts                   [CREATE]
        └── employees-list/
            ├── employees-list.component.ts                      [CREATE]
            ├── employees-list.component.html                     [CREATE]
            └── employees-list.component.spec.ts                   [CREATE]
```

Task order: kit prep (`ui-select` + `ui-input` step) → models+service → employee-form → employees-list (composes the previous three, plus `BranchService` from FE-1.5) → routing + home redirect → full verification.

---

### Task 1: `ui-select` kit component + `ui-input` `step` input

**Files:**
- Create: `frontend/src/app/shared/components/select/select.component.ts`
- Create: `frontend/src/app/shared/components/select/select.component.html`
- Test: `frontend/src/app/shared/components/select/select.component.spec.ts`
- Modify: `frontend/src/app/shared/components/input/input.component.ts`
- Modify: `frontend/src/app/shared/components/input/input.component.html`
- Modify: `frontend/src/app/shared/components/input/input.component.spec.ts`

**Interfaces:**
- Produces: `SelectComponent` (selector `ui-select`), implements `ControlValueAccessor`, `@Input() label = ''`, `@Input() options: SelectOption[] = []`, `@Input() placeholder?: string`, `@Input() errorMessage?: string`; exported type `SelectOption` (`{ value: string; label: string }`). Also: `InputComponent` gains `@Input() step?: string`, forwarded via `[attr.step]`. Task 3 (`employee-form`) and Task 4 (`employees-list`) both import `SelectComponent`/`SelectOption`; Task 3 uses `InputComponent`'s new `step` input for `sueldoBase`.

- [ ] **Step 1: Write the failing test for `ui-select`**

Create `frontend/src/app/shared/components/select/select.component.spec.ts`:

```typescript
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';

import { SelectComponent, SelectOption } from './select.component';

const testOptions: SelectOption[] = [
  { value: 'A', label: 'Opción A' },
  { value: 'B', label: 'Opción B' },
];

@Component({
  standalone: true,
  imports: [ReactiveFormsModule, SelectComponent],
  template: `
    <form [formGroup]="form">
      <ui-select
        formControlName="choice"
        label="Elegí"
        [options]="options"
        [placeholder]="placeholder"
        [errorMessage]="error"
      ></ui-select>
    </form>
  `,
})
class HostComponent {
  form = new FormGroup({ choice: new FormControl('') });
  options = testOptions;
  placeholder?: string;
  error?: string;
}

describe('SelectComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
  });

  it('renders the given options', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const optionEls = fixture.nativeElement.querySelectorAll('option');
    const labels = Array.from(optionEls).map((o) => (o as HTMLOptionElement).textContent);
    expect(labels).toEqual(['Opción A', 'Opción B']);
  });

  it("reflects the form control's initial value into the native select", () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.componentInstance.form.get('choice')!.setValue('B');
    fixture.detectChanges();
    const select = fixture.nativeElement.querySelector('select') as HTMLSelectElement;
    expect(select.value).toBe('B');
  });

  it('propagates a selected option back to the form control', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const select = fixture.nativeElement.querySelector('select') as HTMLSelectElement;
    select.value = 'A';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(fixture.componentInstance.form.get('choice')!.value).toBe('A');
  });

  it('renders the placeholder as a disabled first option when set', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.componentInstance.placeholder = 'Seleccionar...';
    fixture.detectChanges();
    const firstOption = fixture.nativeElement.querySelector('option') as HTMLOptionElement;
    expect(firstOption.textContent).toBe('Seleccionar...');
    expect(firstOption.disabled).toBe(true);
  });

  it('shows the error message with role alert when errorMessage is set', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.componentInstance.error = 'Campo requerido.';
    fixture.detectChanges();
    const alert = fixture.nativeElement.querySelector('[role="alert"]') as HTMLElement;
    expect(alert.textContent?.trim()).toBe('Campo requerido.');
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npx ng test --include='**/select.component.spec.ts' --watch=false`
Expected: FAIL — `Cannot find module './select.component'`.

- [ ] **Step 3: Write the `ui-select` implementation**

Create `frontend/src/app/shared/components/select/select.component.ts`:

```typescript
import { Component, Input, forwardRef } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

export interface SelectOption {
  value: string;
  label: string;
}

let nextSelectId = 0;

@Component({
  selector: 'ui-select',
  standalone: true,
  templateUrl: './select.component.html',
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => SelectComponent),
      multi: true,
    },
  ],
})
export class SelectComponent implements ControlValueAccessor {
  @Input() label = '';
  @Input() options: SelectOption[] = [];
  @Input() placeholder?: string;
  @Input() errorMessage?: string;

  readonly id = `ui-select-${nextSelectId++}`;
  value = '';
  disabled = false;

  private onChange: (value: string) => void = () => {};
  private onTouched: () => void = () => {};

  writeValue(value: string): void {
    this.value = value ?? '';
  }

  registerOnChange(fn: (value: string) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
  }

  handleChange(value: string): void {
    this.value = value;
    this.onChange(value);
  }

  handleBlur(): void {
    this.onTouched();
  }
}
```

Create `frontend/src/app/shared/components/select/select.component.html`:

```html
<div>
  <label [for]="id" class="mb-1 block text-sm font-medium text-brand-ink">{{ label }}</label>
  <select
    [id]="id"
    [value]="value"
    [disabled]="disabled"
    (change)="handleChange($any($event.target).value)"
    (blur)="handleBlur()"
    [attr.aria-describedby]="errorMessage ? id + '-error' : null"
    class="w-full rounded-lg border bg-brand-card px-3 py-2 text-sm text-brand-ink"
    [class.border-brand-line]="!errorMessage"
    [class.border-badge-error-ink]="errorMessage"
  >
    @if (placeholder) {
      <option value="" disabled>{{ placeholder }}</option>
    }
    @for (option of options; track option.value) {
      <option [value]="option.value">{{ option.label }}</option>
    }
  </select>
  @if (errorMessage) {
    <p [id]="id + '-error'" class="mt-1 text-sm text-badge-error-ink" role="alert">{{ errorMessage }}</p>
  }
</div>
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npx ng test --include='**/select.component.spec.ts' --watch=false`
Expected: PASS, 5 tests.

- [ ] **Step 5: Write the failing test for `ui-input`'s new `step` input**

Modify `frontend/src/app/shared/components/input/input.component.spec.ts` — add `[step]="step"` to the host template and a `step?: string` field to `HostComponent`, and append one test. Full file:

```typescript
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';

import { InputComponent } from './input.component';

@Component({
  standalone: true,
  imports: [ReactiveFormsModule, InputComponent],
  template: `
    <form [formGroup]="form">
      <ui-input
        formControlName="email"
        label="Email"
        [errorMessage]="error"
        [autocomplete]="autocomplete"
        [step]="step"
      ></ui-input>
    </form>
  `,
})
class HostComponent {
  form = new FormGroup({ email: new FormControl('') });
  error?: string;
  autocomplete?: string;
  step?: string;
}

describe('InputComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
  });

  it('reflects the form control\'s initial value into the native input', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.componentInstance.form.get('email')!.setValue('ana@staffly.com');
    fixture.detectChanges();
    const input = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    expect(input.value).toBe('ana@staffly.com');
  });

  it('propagates typed input back to the form control', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const input = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    input.value = 'nuevo@staffly.com';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(fixture.componentInstance.form.get('email')!.value).toBe('nuevo@staffly.com');
  });

  it('associates the label with the input via for/id', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const label = fixture.nativeElement.querySelector('label') as HTMLLabelElement;
    const input = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    expect(label.htmlFor).toBe(input.id);
    expect(input.id).toBeTruthy();
  });

  it('shows the error message with role alert when errorMessage is set', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.componentInstance.error = 'El email es requerido.';
    fixture.detectChanges();
    const alert = fixture.nativeElement.querySelector('[role="alert"]') as HTMLElement;
    expect(alert.textContent?.trim()).toBe('El email es requerido.');
  });

  it('renders no error element when errorMessage is not set', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]')).toBeNull();
  });

  it('disables the native input when the form control is disabled', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    fixture.componentInstance.form.get('email')!.disable();
    fixture.detectChanges();
    const input = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    expect(input.disabled).toBe(true);
  });

  it('marks the form control as touched when the native input is blurred', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const input = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    input.dispatchEvent(new Event('blur'));
    fixture.detectChanges();
    expect(fixture.componentInstance.form.get('email')!.touched).toBe(true);
  });

  it('forwards the autocomplete input to the native input attribute', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.componentInstance.autocomplete = 'username';
    fixture.detectChanges();
    const input = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    expect(input.getAttribute('autocomplete')).toBe('username');
  });

  it('forwards the step input to the native input attribute', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.componentInstance.step = '0.01';
    fixture.detectChanges();
    const input = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    expect(input.getAttribute('step')).toBe('0.01');
  });
});
```

- [ ] **Step 6: Run test to verify the new one fails**

Run: `cd frontend && npx ng test --include='**/input.component.spec.ts' --watch=false`
Expected: FAIL — the whole file fails to compile: `Property 'step' does not exist on type 'InputComponent'` (Angular's strict template checking rejects the `[step]="step"` binding in the host template before `InputComponent` declares that input — this is expected at this point, not a bug).

- [ ] **Step 7: Add the `step` input to `ui-input`**

Modify `frontend/src/app/shared/components/input/input.component.ts` — add one line to the `@Input()` list:

```typescript
import { Component, Input, forwardRef } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

let nextInputId = 0;

@Component({
  selector: 'ui-input',
  standalone: true,
  templateUrl: './input.component.html',
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => InputComponent),
      multi: true,
    },
  ],
})
export class InputComponent implements ControlValueAccessor {
  @Input() label = '';
  @Input() type = 'text';
  @Input() placeholder = '';
  @Input() autocomplete?: string;
  @Input() step?: string;
  @Input() errorMessage?: string;

  readonly id = `ui-input-${nextInputId++}`;
  value = '';
  disabled = false;

  private onChange: (value: string) => void = () => {};
  private onTouched: () => void = () => {};

  writeValue(value: string): void {
    this.value = value ?? '';
  }

  registerOnChange(fn: (value: string) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
  }

  handleInput(value: string): void {
    this.value = value;
    this.onChange(value);
  }

  handleBlur(): void {
    this.onTouched();
  }
}
```

Modify `frontend/src/app/shared/components/input/input.component.html` — add `[attr.step]="step"` next to the existing `[attr.autocomplete]`:

```html
<div>
  <label [for]="id" class="mb-1 block text-sm font-medium text-brand-ink">{{ label }}</label>
  <input
    [id]="id"
    [type]="type"
    [placeholder]="placeholder"
    [value]="value"
    [disabled]="disabled"
    [attr.autocomplete]="autocomplete"
    [attr.step]="step"
    (input)="handleInput($any($event.target).value)"
    (blur)="handleBlur()"
    [attr.aria-describedby]="errorMessage ? id + '-error' : null"
    class="w-full rounded-lg border bg-brand-card px-3 py-2 text-sm text-brand-ink"
    [class.border-brand-line]="!errorMessage"
    [class.border-badge-error-ink]="errorMessage"
  />
  @if (errorMessage) {
    <p [id]="id + '-error'" class="mt-1 text-sm text-badge-error-ink" role="alert">{{ errorMessage }}</p>
  }
</div>
```

- [ ] **Step 8: Run both spec files to verify everything passes**

Run: `cd frontend && npx ng test --include='**/select.component.spec.ts' --include='**/input.component.spec.ts' --watch=false`
Expected: PASS, 5 + 9 = 14 tests.

- [ ] **Step 9: Commit**

```bash
cd frontend
git add src/app/shared/components/select src/app/shared/components/input
git commit -m "feat: agregar componente ui-select y step a ui-input"
```

---

### Task 2: `Employee` models + `EmployeeService`

**Files:**
- Create: `frontend/src/app/features/employees/models/employee.ts`
- Create: `frontend/src/app/features/employees/services/employee.service.ts`
- Test: `frontend/src/app/features/employees/services/employee.service.spec.ts`

**Interfaces:**
- Produces: `Employee`, `EstadoLaboral` (`'ACTIVO' | 'LICENCIA' | 'SUSPENDIDO' | 'BAJA'`), `EstadoLiquidacion` (`'AL_DIA' | 'PENDIENTE'`), `TipoContrato` (`'JORNADA_COMPLETA' | 'JORNADA_PARCIAL' | 'POR_HORA'`), `CreateEmployeeRequest`, `UpdateEmployeeRequest`, `UpdateEmployeeStatusRequest`, `EmployeeListFilters` (`{ estadoLaboral?: EstadoLaboral; branchId?: string; search?: string }`) — all from `models/employee.ts`. `EmployeeService` (`providedIn: 'root'`) with `list(filters?: EmployeeListFilters): Observable<Employee[]>`, `create(request: CreateEmployeeRequest): Observable<Employee>`, `update(id: string, request: UpdateEmployeeRequest): Observable<Employee>`, `updateStatus(id: string, request: UpdateEmployeeStatusRequest): Observable<Employee>`. Later tasks import from `models/employee.ts`; `employees-list` injects `EmployeeService`.

- [ ] **Step 1: Write the models (no test — pure types)**

Create `frontend/src/app/features/employees/models/employee.ts`:

```typescript
export type EstadoLaboral = 'ACTIVO' | 'LICENCIA' | 'SUSPENDIDO' | 'BAJA';
export type EstadoLiquidacion = 'AL_DIA' | 'PENDIENTE';
export type TipoContrato = 'JORNADA_COMPLETA' | 'JORNADA_PARCIAL' | 'POR_HORA';

export interface Employee {
  id: string;
  nombre: string;
  apellido: string;
  documento: string;
  fechaNacimiento: string;
  fechaIngreso: string;
  fechaEgreso: string | null;
  tipoContrato: TipoContrato;
  categoria: string;
  sueldoBase: number;
  telefono: string | null;
  emailContacto: string | null;
  estadoLaboral: EstadoLaboral;
  estadoLiquidacion: EstadoLiquidacion;
  branchIds: string[];
}

export interface CreateEmployeeRequest {
  nombre: string;
  apellido: string;
  documento: string;
  fechaNacimiento: string;
  fechaIngreso: string;
  fechaEgreso?: string;
  tipoContrato: TipoContrato;
  categoria: string;
  sueldoBase: number;
  telefono?: string;
  emailContacto?: string;
  branchIds: string[];
}

export interface UpdateEmployeeRequest {
  nombre?: string;
  apellido?: string;
  documento?: string;
  fechaNacimiento?: string;
  fechaIngreso?: string;
  fechaEgreso?: string;
  tipoContrato?: TipoContrato;
  categoria?: string;
  sueldoBase?: number;
  telefono?: string;
  emailContacto?: string;
  branchIds?: string[];
}

export interface UpdateEmployeeStatusRequest {
  estadoLaboral: EstadoLaboral;
}

export interface EmployeeListFilters {
  estadoLaboral?: EstadoLaboral;
  branchId?: string;
  search?: string;
}
```

- [ ] **Step 2: Write the failing test for `EmployeeService`**

Create `frontend/src/app/features/employees/services/employee.service.spec.ts`:

```typescript
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { environment } from '../../../../environments/environment';
import {
  CreateEmployeeRequest,
  Employee,
  UpdateEmployeeRequest,
  UpdateEmployeeStatusRequest,
} from '../models/employee';
import { EmployeeService } from './employee.service';

const baseUrl = `${environment.apiUrl}/employees`;

const mockEmployee: Employee = {
  id: '1',
  nombre: 'Ana',
  apellido: 'Gómez',
  documento: '30111222',
  fechaNacimiento: '1990-01-01',
  fechaIngreso: '2024-01-01',
  fechaEgreso: null,
  tipoContrato: 'JORNADA_COMPLETA',
  categoria: 'Cajera',
  sueldoBase: 500000,
  telefono: null,
  emailContacto: null,
  estadoLaboral: 'ACTIVO',
  estadoLiquidacion: 'AL_DIA',
  branchIds: ['branch-1'],
};

describe('EmployeeService', () => {
  let service: EmployeeService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(EmployeeService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('lists employees with a GET to /employees and no query params when no filters are given', () => {
    service.list().subscribe((employees) => expect(employees).toEqual([mockEmployee]));
    const req = httpMock.expectOne(baseUrl);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.keys().length).toBe(0);
    req.flush([mockEmployee]);
  });

  it('lists employees with matching query params when filters are given', () => {
    service.list({ estadoLaboral: 'ACTIVO', branchId: 'branch-1', search: 'ana' }).subscribe();
    const req = httpMock.expectOne(
      (r) =>
        r.url === baseUrl &&
        r.params.get('estadoLaboral') === 'ACTIVO' &&
        r.params.get('branchId') === 'branch-1' &&
        r.params.get('search') === 'ana',
    );
    expect(req.request.method).toBe('GET');
    req.flush([mockEmployee]);
  });

  it('creates an employee with a POST to /employees', () => {
    const request: CreateEmployeeRequest = {
      nombre: 'Ana',
      apellido: 'Gómez',
      documento: '30111222',
      fechaNacimiento: '1990-01-01',
      fechaIngreso: '2024-01-01',
      tipoContrato: 'JORNADA_COMPLETA',
      categoria: 'Cajera',
      sueldoBase: 500000,
      branchIds: ['branch-1'],
    };
    service.create(request).subscribe((employee) => expect(employee).toEqual(mockEmployee));
    const req = httpMock.expectOne(baseUrl);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(mockEmployee);
  });

  it('updates an employee with a PATCH to /employees/{id}', () => {
    const request: UpdateEmployeeRequest = { categoria: 'Encargada' };
    service.update('1', request).subscribe((employee) => expect(employee).toEqual(mockEmployee));
    const req = httpMock.expectOne(`${baseUrl}/1`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual(request);
    req.flush(mockEmployee);
  });

  it('updates an employee status with a PATCH to /employees/{id}/status', () => {
    const request: UpdateEmployeeStatusRequest = { estadoLaboral: 'BAJA' };
    service.updateStatus('1', request).subscribe((employee) => expect(employee).toEqual(mockEmployee));
    const req = httpMock.expectOne(`${baseUrl}/1/status`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual(request);
    req.flush(mockEmployee);
  });
});
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd frontend && npx ng test --include='**/employee.service.spec.ts' --watch=false`
Expected: FAIL — `Cannot find module './employee.service'`.

- [ ] **Step 4: Write minimal implementation**

Create `frontend/src/app/features/employees/services/employee.service.ts`:

```typescript
import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../../environments/environment';
import {
  CreateEmployeeRequest,
  Employee,
  EmployeeListFilters,
  UpdateEmployeeRequest,
  UpdateEmployeeStatusRequest,
} from '../models/employee';

@Injectable({ providedIn: 'root' })
export class EmployeeService {
  private http = inject(HttpClient);
  private baseUrl = `${environment.apiUrl}/employees`;

  list(filters: EmployeeListFilters = {}): Observable<Employee[]> {
    let params = new HttpParams();
    if (filters.estadoLaboral) {
      params = params.set('estadoLaboral', filters.estadoLaboral);
    }
    if (filters.branchId) {
      params = params.set('branchId', filters.branchId);
    }
    if (filters.search) {
      params = params.set('search', filters.search);
    }
    return this.http.get<Employee[]>(this.baseUrl, { params });
  }

  create(request: CreateEmployeeRequest): Observable<Employee> {
    return this.http.post<Employee>(this.baseUrl, request);
  }

  update(id: string, request: UpdateEmployeeRequest): Observable<Employee> {
    return this.http.patch<Employee>(`${this.baseUrl}/${id}`, request);
  }

  updateStatus(id: string, request: UpdateEmployeeStatusRequest): Observable<Employee> {
    return this.http.patch<Employee>(`${this.baseUrl}/${id}/status`, request);
  }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd frontend && npx ng test --include='**/employee.service.spec.ts' --watch=false`
Expected: PASS, 6 tests.

- [ ] **Step 6: Commit**

```bash
cd frontend
git add src/app/features/employees/models src/app/features/employees/services
git commit -m "feat: agregar modelos y servicio http de employees"
```

---

### Task 3: `EmployeeFormComponent`

**Files:**
- Create: `frontend/src/app/features/employees/components/employee-form/employee-form.component.ts`
- Create: `frontend/src/app/features/employees/components/employee-form/employee-form.component.html`
- Test: `frontend/src/app/features/employees/components/employee-form/employee-form.component.spec.ts`

**Interfaces:**
- Consumes: `Employee` from `../../models/employee` (Task 2); `Branch` from `../../../branches/models/branch` (FE-1.5, already merged); `InputComponent` and `SelectComponent`/`SelectOption` (Task 1) and `ButtonDirective` from the shared kit.
- Produces: `EmployeeFormComponent` (selector `app-employee-form`), `@Input() mode: 'create' | 'edit' = 'create'`, `@Input() initialValue?: Employee`, `@Input() branches: Branch[] = []`, `@Output() submitted = new EventEmitter<EmployeeFormValue>()`, exported type `EmployeeFormValue` (`{ nombre, apellido, documento, fechaNacimiento, fechaIngreso, fechaEgreso, tipoContrato, categoria, sueldoBase: number, telefono, emailContacto, branchIds: string[] }` — every field but `sueldoBase`/`branchIds` is `string`). Task 4 (`employees-list`) imports `EmployeeFormComponent`, `EmployeeFormValue`, and passes it `branches` (fetched via `BranchService`).
- `branchIds` is NOT a `FormControl` inside `form` — it's tracked as a separate `selectedBranchIds: string[]` class property, toggled by native (not `ui-input`-wrapped) checkboxes, and validated manually in `onSubmit` (at least one required, mirroring the backend's `@NotEmpty branchIds`).

- [ ] **Step 1: Write the failing test**

Create `frontend/src/app/features/employees/components/employee-form/employee-form.component.spec.ts`:

```typescript
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { Branch } from '../../../branches/models/branch';
import { Employee } from '../../models/employee';
import { EmployeeFormComponent, EmployeeFormValue } from './employee-form.component';

const mockBranches: Branch[] = [
  {
    id: 'branch-1',
    nombre: 'Casa Central',
    direccion: 'Av. Colón 1240',
    zonaHoraria: 'America/Buenos_Aires',
    estado: 'ACTIVA',
    horarioVisibleInicio: null,
    horarioVisibleFin: null,
  },
  {
    id: 'branch-2',
    nombre: 'Sucursal Norte',
    direccion: 'Av. Norte 500',
    zonaHoraria: 'America/Buenos_Aires',
    estado: 'ACTIVA',
    horarioVisibleInicio: null,
    horarioVisibleFin: null,
  },
];

const mockEmployee: Employee = {
  id: '1',
  nombre: 'Ana',
  apellido: 'Gómez',
  documento: '30111222',
  fechaNacimiento: '1990-01-01',
  fechaIngreso: '2024-01-01',
  fechaEgreso: null,
  tipoContrato: 'JORNADA_COMPLETA',
  categoria: 'Cajera',
  sueldoBase: 500000,
  telefono: null,
  emailContacto: null,
  estadoLaboral: 'ACTIVO',
  estadoLiquidacion: 'AL_DIA',
  branchIds: ['branch-1'],
};

@Component({
  standalone: true,
  imports: [EmployeeFormComponent],
  template: `
    <app-employee-form
      [mode]="mode"
      [initialValue]="initialValue"
      [branches]="branches"
      (submitted)="onSubmitted($event)"
    ></app-employee-form>
  `,
})
class HostComponent {
  mode: 'create' | 'edit' = 'create';
  initialValue?: Employee;
  branches: Branch[] = mockBranches;
  submittedValue?: EmployeeFormValue;

  onSubmitted(value: EmployeeFormValue): void {
    this.submittedValue = value;
  }
}

describe('EmployeeFormComponent', () => {
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

  it('renders one checkbox per branch and requires at least one selected', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const checkboxes = fixture.nativeElement.querySelectorAll('input[type="checkbox"]');
    expect(checkboxes.length).toBe(2);

    const form = fixture.nativeElement.querySelector('form') as HTMLFormElement;
    form.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Seleccioná al menos una sucursal.');
  });

  it('prefills the form and the checked branches from initialValue', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.componentInstance.mode = 'edit';
    fixture.componentInstance.initialValue = mockEmployee;
    fixture.detectChanges();
    const firstInput = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    expect(firstInput.value).toBe('Ana');
    const checkboxes = fixture.nativeElement.querySelectorAll(
      'input[type="checkbox"]',
    ) as NodeListOf<HTMLInputElement>;
    expect(checkboxes[0].checked).toBe(true);
    expect(checkboxes[1].checked).toBe(false);
  });

  it('emits submitted with the full form value, a numeric sueldoBase, and the checked branch ids', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();

    const component = fixture.debugElement.children[0].componentInstance as EmployeeFormComponent;
    component.form.setValue({
      nombre: 'Ana',
      apellido: 'Gómez',
      documento: '30111222',
      fechaNacimiento: '1990-01-01',
      fechaIngreso: '2024-01-01',
      fechaEgreso: '',
      tipoContrato: 'JORNADA_COMPLETA',
      categoria: 'Cajera',
      sueldoBase: '500000',
      telefono: '',
      emailContacto: '',
    });
    fixture.detectChanges();

    const checkboxes = fixture.nativeElement.querySelectorAll(
      'input[type="checkbox"]',
    ) as NodeListOf<HTMLInputElement>;
    checkboxes[0].checked = true;
    checkboxes[0].dispatchEvent(new Event('change'));
    fixture.detectChanges();

    const form = fixture.nativeElement.querySelector('form') as HTMLFormElement;
    form.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(fixture.componentInstance.submittedValue).toEqual({
      nombre: 'Ana',
      apellido: 'Gómez',
      documento: '30111222',
      fechaNacimiento: '1990-01-01',
      fechaIngreso: '2024-01-01',
      fechaEgreso: '',
      tipoContrato: 'JORNADA_COMPLETA',
      categoria: 'Cajera',
      sueldoBase: 500000,
      telefono: '',
      emailContacto: '',
      branchIds: ['branch-1'],
    });
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npx ng test --include='**/employee-form.component.spec.ts' --watch=false`
Expected: FAIL — `Cannot find module './employee-form.component'`.

- [ ] **Step 3: Write minimal implementation**

Create `frontend/src/app/features/employees/components/employee-form/employee-form.component.ts`:

```typescript
import { Component, EventEmitter, Input, OnInit, Output, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { ButtonDirective } from '../../../../shared/components/button/button.directive';
import { InputComponent } from '../../../../shared/components/input/input.component';
import { SelectComponent, SelectOption } from '../../../../shared/components/select/select.component';
import { Branch } from '../../../branches/models/branch';
import { Employee } from '../../models/employee';

export interface EmployeeFormValue {
  nombre: string;
  apellido: string;
  documento: string;
  fechaNacimiento: string;
  fechaIngreso: string;
  fechaEgreso: string;
  tipoContrato: string;
  categoria: string;
  sueldoBase: number;
  telefono: string;
  emailContacto: string;
  branchIds: string[];
}

@Component({
  selector: 'app-employee-form',
  standalone: true,
  imports: [ReactiveFormsModule, InputComponent, SelectComponent, ButtonDirective],
  templateUrl: './employee-form.component.html',
})
export class EmployeeFormComponent implements OnInit {
  @Input() mode: 'create' | 'edit' = 'create';
  @Input() initialValue?: Employee;
  @Input() branches: Branch[] = [];
  @Output() submitted = new EventEmitter<EmployeeFormValue>();

  private fb = inject(FormBuilder);

  readonly tipoContratoOptions: SelectOption[] = [
    { value: 'JORNADA_COMPLETA', label: 'Jornada completa' },
    { value: 'JORNADA_PARCIAL', label: 'Jornada parcial' },
    { value: 'POR_HORA', label: 'Por hora' },
  ];

  form = this.fb.group({
    nombre: ['', Validators.required],
    apellido: ['', Validators.required],
    documento: ['', Validators.required],
    fechaNacimiento: ['', Validators.required],
    fechaIngreso: ['', Validators.required],
    fechaEgreso: [''],
    tipoContrato: ['', Validators.required],
    categoria: ['', Validators.required],
    sueldoBase: ['', Validators.required],
    telefono: [''],
    emailContacto: ['', Validators.email],
  });

  selectedBranchIds: string[] = [];
  branchesError = false;

  get nombreCtrl() {
    return this.form.get('nombre')!;
  }
  get apellidoCtrl() {
    return this.form.get('apellido')!;
  }
  get documentoCtrl() {
    return this.form.get('documento')!;
  }
  get fechaNacimientoCtrl() {
    return this.form.get('fechaNacimiento')!;
  }
  get fechaIngresoCtrl() {
    return this.form.get('fechaIngreso')!;
  }
  get tipoContratoCtrl() {
    return this.form.get('tipoContrato')!;
  }
  get categoriaCtrl() {
    return this.form.get('categoria')!;
  }
  get sueldoBaseCtrl() {
    return this.form.get('sueldoBase')!;
  }
  get emailContactoCtrl() {
    return this.form.get('emailContacto')!;
  }

  ngOnInit(): void {
    if (this.initialValue) {
      this.form.patchValue({
        nombre: this.initialValue.nombre,
        apellido: this.initialValue.apellido,
        documento: this.initialValue.documento,
        fechaNacimiento: this.initialValue.fechaNacimiento,
        fechaIngreso: this.initialValue.fechaIngreso,
        fechaEgreso: this.initialValue.fechaEgreso ?? '',
        tipoContrato: this.initialValue.tipoContrato,
        categoria: this.initialValue.categoria,
        sueldoBase: String(this.initialValue.sueldoBase),
        telefono: this.initialValue.telefono ?? '',
        emailContacto: this.initialValue.emailContacto ?? '',
      });
      this.selectedBranchIds = [...this.initialValue.branchIds];
    }
  }

  isBranchSelected(branchId: string): boolean {
    return this.selectedBranchIds.includes(branchId);
  }

  toggleBranch(branchId: string, checked: boolean): void {
    this.selectedBranchIds = checked
      ? [...this.selectedBranchIds, branchId]
      : this.selectedBranchIds.filter((id) => id !== branchId);
  }

  onSubmit(): void {
    this.branchesError = this.selectedBranchIds.length === 0;
    if (this.form.invalid || this.branchesError) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue();
    this.submitted.emit({
      nombre: raw.nombre!,
      apellido: raw.apellido!,
      documento: raw.documento!,
      fechaNacimiento: raw.fechaNacimiento!,
      fechaIngreso: raw.fechaIngreso!,
      fechaEgreso: raw.fechaEgreso ?? '',
      tipoContrato: raw.tipoContrato!,
      categoria: raw.categoria!,
      sueldoBase: Number(raw.sueldoBase),
      telefono: raw.telefono ?? '',
      emailContacto: raw.emailContacto ?? '',
      branchIds: this.selectedBranchIds,
    });
  }
}
```

Create `frontend/src/app/features/employees/components/employee-form/employee-form.component.html`:

```html
<form [formGroup]="form" (ngSubmit)="onSubmit()" class="space-y-4">
  <ui-input
    label="Nombre"
    formControlName="nombre"
    [errorMessage]="nombreCtrl.invalid && nombreCtrl.touched ? 'El nombre es requerido.' : undefined"
  ></ui-input>
  <ui-input
    label="Apellido"
    formControlName="apellido"
    [errorMessage]="apellidoCtrl.invalid && apellidoCtrl.touched ? 'El apellido es requerido.' : undefined"
  ></ui-input>
  <ui-input
    label="Documento"
    formControlName="documento"
    [errorMessage]="documentoCtrl.invalid && documentoCtrl.touched ? 'El documento es requerido.' : undefined"
  ></ui-input>
  <ui-input
    label="Fecha de nacimiento"
    type="date"
    formControlName="fechaNacimiento"
    [errorMessage]="
      fechaNacimientoCtrl.invalid && fechaNacimientoCtrl.touched ? 'La fecha de nacimiento es requerida.' : undefined
    "
  ></ui-input>
  <ui-input
    label="Fecha de ingreso"
    type="date"
    formControlName="fechaIngreso"
    [errorMessage]="fechaIngresoCtrl.invalid && fechaIngresoCtrl.touched ? 'La fecha de ingreso es requerida.' : undefined"
  ></ui-input>
  <ui-input label="Fecha de egreso (opcional)" type="date" formControlName="fechaEgreso"></ui-input>
  <ui-select
    label="Tipo de contrato"
    formControlName="tipoContrato"
    [options]="tipoContratoOptions"
    placeholder="Seleccionar..."
    [errorMessage]="tipoContratoCtrl.invalid && tipoContratoCtrl.touched ? 'El tipo de contrato es requerido.' : undefined"
  ></ui-select>
  <ui-input
    label="Puesto"
    formControlName="categoria"
    [errorMessage]="categoriaCtrl.invalid && categoriaCtrl.touched ? 'El puesto es requerido.' : undefined"
  ></ui-input>
  <ui-input
    label="Sueldo base"
    type="number"
    step="0.01"
    formControlName="sueldoBase"
    [errorMessage]="sueldoBaseCtrl.invalid && sueldoBaseCtrl.touched ? 'El sueldo base es requerido.' : undefined"
  ></ui-input>
  <ui-input label="Teléfono (opcional)" formControlName="telefono"></ui-input>
  <ui-input
    label="Email de contacto (opcional)"
    type="email"
    formControlName="emailContacto"
    [errorMessage]="emailContactoCtrl.invalid && emailContactoCtrl.touched ? 'Ingresá un email válido.' : undefined"
  ></ui-input>

  <div>
    <span class="mb-1 block text-sm font-medium text-brand-ink">Sucursales asignadas</span>
    <div class="space-y-1">
      @for (branch of branches; track branch.id) {
        <label class="flex items-center gap-2 text-sm text-brand-ink">
          <input
            type="checkbox"
            [checked]="isBranchSelected(branch.id)"
            (change)="toggleBranch(branch.id, $any($event.target).checked)"
          />
          {{ branch.nombre }}
        </label>
      }
    </div>
    @if (branchesError) {
      <p class="mt-1 text-sm text-badge-error-ink" role="alert">Seleccioná al menos una sucursal.</p>
    }
  </div>

  <button ui-button type="submit" class="w-full">
    @if (mode === 'create') {
      Crear empleado
    } @else {
      Guardar cambios
    }
  </button>
</form>
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npx ng test --include='**/employee-form.component.spec.ts' --watch=false`
Expected: PASS, 4 tests.

- [ ] **Step 5: Commit**

```bash
cd frontend
git add src/app/features/employees/components/employee-form
git commit -m "feat: agregar formulario reactivo de alta y edicion de employees"
```

---

### Task 4: `EmployeesListComponent`

**Files:**
- Create: `frontend/src/app/features/employees/components/employees-list/employees-list.component.ts`
- Create: `frontend/src/app/features/employees/components/employees-list/employees-list.component.html`
- Test: `frontend/src/app/features/employees/components/employees-list/employees-list.component.spec.ts`

**Interfaces:**
- Consumes: `EmployeeService` (Task 2); `Employee`, `EstadoLaboral`, `TipoContrato`, `CreateEmployeeRequest` (Task 2); `EmployeeFormComponent`/`EmployeeFormValue` (Task 3); `SelectComponent`/`SelectOption` (Task 1); `BranchService`/`Branch` (`../../../branches/services/branch.service`, `../../../branches/models/branch` — FE-1.5, already merged); `AuthService`; `TableComponent`, `BadgeComponent`/`BadgeVariant`, `ModalComponent`, `ButtonDirective` from the shared kit.
- Produces: `EmployeesListComponent` (selector `app-employees-list`) with public properties `canWrite: boolean`, `employees: Employee[]`, `branches: Branch[]`, `loading: boolean`, `loadError: string | null`, `filterForm` (a 3-control `FormGroup`: `search`, `estadoLaboral`, `branchId`), `formMode: 'create' | 'edit' | null`, `formInitialValue?: Employee`, `formError: string | null`, `statusTarget: Employee | null`, `statusForm` (1-control `FormGroup`: `estadoLaboral`), `statusError: string | null`, and methods `loadEmployees()`, `openCreateModal()`, `openEditModal(employee: Employee)`, `closeFormModal()`, `handleFormSubmit(value: EmployeeFormValue)`, `openStatusModal(employee: Employee)`, `closeStatusModal()`, `confirmStatusChange()`. Task 5 (routing) lazy-loads this component by name.
- **File-size note**: this component is intentionally over the ~150-line guideline (filters + dual modals + branch-name resolution genuinely need the room — companies-list/branches-list were simpler single-modal-flow screens). Do not split it further as part of this task; if a future task adds more responsibility here, that's the trigger to extract the filter bar or the status modal into their own components.

- [ ] **Step 1: Write the failing test**

Create `frontend/src/app/features/employees/components/employees-list/employees-list.component.spec.ts`:

```typescript
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { AuthService } from '../../../../core/services/auth.service';
import { Branch } from '../../../branches/models/branch';
import { BranchService } from '../../../branches/services/branch.service';
import { Employee } from '../../models/employee';
import { EmployeeService } from '../../services/employee.service';
import { EmployeesListComponent } from './employees-list.component';

const mockBranch: Branch = {
  id: 'branch-1',
  nombre: 'Casa Central',
  direccion: 'Av. Colón 1240',
  zonaHoraria: 'America/Buenos_Aires',
  estado: 'ACTIVA',
  horarioVisibleInicio: null,
  horarioVisibleFin: null,
};

const mockEmployee: Employee = {
  id: '1',
  nombre: 'Ana',
  apellido: 'Gómez',
  documento: '30111222',
  fechaNacimiento: '1990-01-01',
  fechaIngreso: '2024-01-01',
  fechaEgreso: null,
  tipoContrato: 'JORNADA_COMPLETA',
  categoria: 'Cajera',
  sueldoBase: 500000,
  telefono: null,
  emailContacto: null,
  estadoLaboral: 'ACTIVO',
  estadoLiquidacion: 'AL_DIA',
  branchIds: ['branch-1'],
};

describe('EmployeesListComponent', () => {
  let employeeServiceStub: {
    list: ReturnType<typeof vi.fn>;
    create: ReturnType<typeof vi.fn>;
    update: ReturnType<typeof vi.fn>;
    updateStatus: ReturnType<typeof vi.fn>;
  };
  let branchServiceStub: { list: ReturnType<typeof vi.fn> };

  function configure(role: string): void {
    employeeServiceStub = {
      list: vi.fn().mockReturnValue(of([mockEmployee])),
      create: vi.fn(),
      update: vi.fn(),
      updateStatus: vi.fn(),
    };
    branchServiceStub = { list: vi.fn().mockReturnValue(of([mockBranch])) };

    TestBed.configureTestingModule({
      imports: [EmployeesListComponent],
      providers: [
        { provide: EmployeeService, useValue: employeeServiceStub },
        { provide: BranchService, useValue: branchServiceStub },
        { provide: AuthService, useValue: { getRole: () => role } },
      ],
    });
  }

  it('shows the loaded employees in the table, with the branch name resolved', () => {
    configure('ADMIN');
    const fixture = TestBed.createComponent(EmployeesListComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Ana Gómez');
    expect(fixture.nativeElement.textContent).toContain('Casa Central');
  });

  it('shows a loading error banner when the list request fails', () => {
    configure('ADMIN');
    employeeServiceStub.list.mockReturnValue(throwError(() => new Error('network error')));
    const fixture = TestBed.createComponent(EmployeesListComponent);
    fixture.detectChanges();
    const alert = fixture.nativeElement.querySelector('[role="alert"]');
    expect(alert?.textContent).toContain('No se pudo cargar');
  });

  it('debounces the search filter before calling list again', () => {
    vi.useFakeTimers();
    configure('ADMIN');
    const fixture = TestBed.createComponent(EmployeesListComponent);
    fixture.detectChanges();
    expect(employeeServiceStub.list).toHaveBeenCalledTimes(1);

    fixture.componentInstance.filterForm.get('search')!.setValue('ana');
    vi.advanceTimersByTime(299);
    expect(employeeServiceStub.list).toHaveBeenCalledTimes(1);
    vi.advanceTimersByTime(1);
    expect(employeeServiceStub.list).toHaveBeenCalledTimes(2);
    expect(employeeServiceStub.list).toHaveBeenLastCalledWith({
      estadoLaboral: undefined,
      branchId: undefined,
      search: 'ana',
    });
    vi.useRealTimers();
  });

  it('calls list again with the new value when the estadoLaboral filter changes', () => {
    vi.useFakeTimers();
    configure('ADMIN');
    const fixture = TestBed.createComponent(EmployeesListComponent);
    fixture.detectChanges();

    fixture.componentInstance.filterForm.get('estadoLaboral')!.setValue('BAJA');
    vi.advanceTimersByTime(300);
    expect(employeeServiceStub.list).toHaveBeenLastCalledWith({
      estadoLaboral: 'BAJA',
      branchId: undefined,
      search: undefined,
    });
    vi.useRealTimers();
  });

  it('hides write actions for a SUPERVISOR', () => {
    configure('SUPERVISOR');
    const fixture = TestBed.createComponent(EmployeesListComponent);
    fixture.detectChanges();
    const buttons = Array.from(fixture.nativeElement.querySelectorAll('button')).map((b) =>
      (b as HTMLButtonElement).textContent?.trim(),
    );
    expect(buttons).not.toContain('+ Nuevo empleado');
    expect(buttons).not.toContain('Editar');
  });

  it('shows write actions for an ADMIN', () => {
    configure('ADMIN');
    const fixture = TestBed.createComponent(EmployeesListComponent);
    fixture.detectChanges();
    const buttons = Array.from(fixture.nativeElement.querySelectorAll('button')).map((b) =>
      (b as HTMLButtonElement).textContent?.trim(),
    );
    expect(buttons).toContain('+ Nuevo empleado');
    expect(buttons).toContain('Editar');
  });

  it('opens the create modal, and on successful submit refreshes the list', () => {
    configure('ADMIN');
    employeeServiceStub.create.mockReturnValue(of(mockEmployee));
    const fixture = TestBed.createComponent(EmployeesListComponent);
    fixture.detectChanges();

    fixture.componentInstance.openCreateModal();
    fixture.detectChanges();
    expect(fixture.componentInstance.formMode).toBe('create');

    fixture.componentInstance.handleFormSubmit({
      nombre: 'Bruno',
      apellido: 'Diaz',
      documento: '30999888',
      fechaNacimiento: '1995-05-05',
      fechaIngreso: '2026-01-01',
      fechaEgreso: '',
      tipoContrato: 'JORNADA_COMPLETA',
      categoria: 'Cajero',
      sueldoBase: 400000,
      telefono: '',
      emailContacto: '',
      branchIds: ['branch-1'],
    });
    fixture.detectChanges();

    expect(employeeServiceStub.create).toHaveBeenCalled();
    expect(fixture.componentInstance.formMode).toBeNull();
    expect(employeeServiceStub.list).toHaveBeenCalledTimes(2);
  });

  it('opens the edit modal prefilled with the selected employee', () => {
    configure('ADMIN');
    const fixture = TestBed.createComponent(EmployeesListComponent);
    fixture.detectChanges();

    fixture.componentInstance.openEditModal(mockEmployee);

    expect(fixture.componentInstance.formMode).toBe('edit');
    expect(fixture.componentInstance.formInitialValue).toEqual(mockEmployee);
  });

  it('opens the status modal preloaded with the current estado, and applies the new one on confirm', () => {
    configure('ADMIN');
    employeeServiceStub.updateStatus.mockReturnValue(of({ ...mockEmployee, estadoLaboral: 'BAJA' }));
    const fixture = TestBed.createComponent(EmployeesListComponent);
    fixture.detectChanges();

    fixture.componentInstance.openStatusModal(mockEmployee);
    expect(fixture.componentInstance.statusForm.getRawValue().estadoLaboral).toBe('ACTIVO');

    fixture.componentInstance.statusForm.setValue({ estadoLaboral: 'BAJA' });
    fixture.componentInstance.confirmStatusChange();

    expect(employeeServiceStub.updateStatus).toHaveBeenCalledWith('1', { estadoLaboral: 'BAJA' });
    expect(fixture.componentInstance.statusTarget).toBeNull();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npx ng test --include='**/employees-list.component.spec.ts' --watch=false`
Expected: FAIL — `Cannot find module './employees-list.component'`.

- [ ] **Step 3: Write minimal implementation**

Create `frontend/src/app/features/employees/components/employees-list/employees-list.component.ts`:

```typescript
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { debounceTime } from 'rxjs';

import { AuthService } from '../../../../core/services/auth.service';
import { BadgeComponent, BadgeVariant } from '../../../../shared/components/badge/badge.component';
import { ButtonDirective } from '../../../../shared/components/button/button.directive';
import { ModalComponent } from '../../../../shared/components/modal/modal.component';
import { SelectComponent, SelectOption } from '../../../../shared/components/select/select.component';
import { TableComponent } from '../../../../shared/components/table/table.component';
import { Branch } from '../../../branches/models/branch';
import { BranchService } from '../../../branches/services/branch.service';
import { CreateEmployeeRequest, Employee, EstadoLaboral, TipoContrato } from '../../models/employee';
import { EmployeeService } from '../../services/employee.service';
import { EmployeeFormComponent, EmployeeFormValue } from '../employee-form/employee-form.component';

const ESTADO_LABORAL_VARIANTS: Record<EstadoLaboral, BadgeVariant> = {
  ACTIVO: 'success',
  LICENCIA: 'accent',
  SUSPENDIDO: 'warning',
  BAJA: 'neutral',
};

@Component({
  selector: 'app-employees-list',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    ButtonDirective,
    BadgeComponent,
    ModalComponent,
    TableComponent,
    SelectComponent,
    EmployeeFormComponent,
  ],
  templateUrl: './employees-list.component.html',
})
export class EmployeesListComponent implements OnInit {
  private employeeService = inject(EmployeeService);
  private branchService = inject(BranchService);
  private authService = inject(AuthService);
  private fb = inject(FormBuilder);
  private destroyRef = inject(DestroyRef);

  readonly canWrite = this.authService.getRole() === 'ADMIN' || this.authService.getRole() === 'RRHH';

  employees: Employee[] = [];
  branches: Branch[] = [];
  loading = true;
  loadError: string | null = null;

  filterForm = this.fb.group({
    search: [''],
    estadoLaboral: [''],
    branchId: [''],
  });

  readonly estadoLaboralOptions: SelectOption[] = [
    { value: 'ACTIVO', label: 'Activo' },
    { value: 'LICENCIA', label: 'Licencia' },
    { value: 'SUSPENDIDO', label: 'Suspendido' },
    { value: 'BAJA', label: 'Baja' },
  ];

  formMode: 'create' | 'edit' | null = null;
  formInitialValue?: Employee;
  formError: string | null = null;

  statusTarget: Employee | null = null;
  statusForm = this.fb.group({ estadoLaboral: [''] });
  statusError: string | null = null;

  ngOnInit(): void {
    this.branchService.list().subscribe({
      next: (branches) => {
        this.branches = branches;
      },
    });

    this.loadEmployees();

    this.filterForm.valueChanges
      .pipe(debounceTime(300), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.loadEmployees());
  }

  get branchFilterOptions(): SelectOption[] {
    return this.branches.map((b) => ({ value: b.id, label: b.nombre }));
  }

  loadEmployees(): void {
    this.loading = true;
    this.loadError = null;
    const filters = this.filterForm.getRawValue();
    this.employeeService
      .list({
        estadoLaboral: (filters.estadoLaboral || undefined) as EstadoLaboral | undefined,
        branchId: filters.branchId || undefined,
        search: filters.search || undefined,
      })
      .subscribe({
        next: (employees) => {
          this.employees = employees;
          this.loading = false;
        },
        error: () => {
          this.loading = false;
          this.loadError = 'No se pudo cargar el listado de empleados.';
        },
      });
  }

  branchNames(employee: Employee): string {
    return employee.branchIds
      .map((id) => this.branches.find((b) => b.id === id)?.nombre)
      .filter((name): name is string => !!name)
      .join(', ');
  }

  estadoLaboralVariant(estado: EstadoLaboral): BadgeVariant {
    return ESTADO_LABORAL_VARIANTS[estado];
  }

  openCreateModal(): void {
    this.formMode = 'create';
    this.formInitialValue = undefined;
    this.formError = null;
  }

  openEditModal(employee: Employee): void {
    this.formMode = 'edit';
    this.formInitialValue = employee;
    this.formError = null;
  }

  closeFormModal(): void {
    this.formMode = null;
  }

  private toRequest(value: EmployeeFormValue): CreateEmployeeRequest {
    return {
      nombre: value.nombre,
      apellido: value.apellido,
      documento: value.documento,
      fechaNacimiento: value.fechaNacimiento,
      fechaIngreso: value.fechaIngreso,
      fechaEgreso: value.fechaEgreso || undefined,
      tipoContrato: value.tipoContrato as TipoContrato,
      categoria: value.categoria,
      sueldoBase: value.sueldoBase,
      telefono: value.telefono || undefined,
      emailContacto: value.emailContacto || undefined,
      branchIds: value.branchIds,
    };
  }

  handleFormSubmit(value: EmployeeFormValue): void {
    this.formError = null;
    const request = this.toRequest(value);

    if (this.formMode === 'create') {
      this.employeeService.create(request).subscribe({
        next: () => {
          this.formMode = null;
          this.loadEmployees();
        },
        error: () => {
          this.formError = 'No se pudo crear el empleado. Intentá de nuevo.';
        },
      });
      return;
    }

    const target = this.formInitialValue;
    if (this.formMode === 'edit' && target) {
      this.employeeService.update(target.id, request).subscribe({
        next: () => {
          this.formMode = null;
          this.loadEmployees();
        },
        error: () => {
          this.formError = 'No se pudo guardar los cambios. Intentá de nuevo.';
        },
      });
    }
  }

  openStatusModal(employee: Employee): void {
    this.statusTarget = employee;
    this.statusForm.setValue({ estadoLaboral: employee.estadoLaboral });
    this.statusError = null;
  }

  closeStatusModal(): void {
    this.statusTarget = null;
  }

  confirmStatusChange(): void {
    const target = this.statusTarget;
    if (!target) {
      return;
    }
    const estadoLaboral = this.statusForm.getRawValue().estadoLaboral as EstadoLaboral;
    this.employeeService.updateStatus(target.id, { estadoLaboral }).subscribe({
      next: () => {
        this.statusTarget = null;
        this.loadEmployees();
      },
      error: () => {
        this.statusError = 'No se pudo cambiar el estado del empleado.';
      },
    });
  }
}
```

Create `frontend/src/app/features/employees/components/employees-list/employees-list.component.html`:

```html
<main class="mx-auto max-w-6xl px-4 py-8">
  <div class="mb-4 flex items-center justify-between">
    <h1 class="font-heading text-2xl font-bold text-brand-ink">Empleados</h1>
    @if (canWrite) {
      <button ui-button type="button" (click)="openCreateModal()">+ Nuevo empleado</button>
    }
  </div>

  <form [formGroup]="filterForm" class="mb-4 grid grid-cols-1 gap-3 sm:grid-cols-3">
    <ui-input label="Buscar" placeholder="Nombre o documento" formControlName="search"></ui-input>
    <ui-select
      label="Estado laboral"
      formControlName="estadoLaboral"
      [options]="estadoLaboralOptions"
      placeholder="Todos"
    ></ui-select>
    <ui-select
      label="Sucursal"
      formControlName="branchId"
      [options]="branchFilterOptions"
      placeholder="Todas"
    ></ui-select>
  </form>

  @if (loading) {
    <p class="text-sm text-brand-muted">Cargando empleados...</p>
  } @else if (loadError) {
    <p class="rounded-lg bg-badge-error-bg p-3 text-sm text-badge-error-ink" role="alert">{{ loadError }}</p>
  } @else if (employees.length === 0) {
    <p class="text-sm text-brand-muted">No se encontraron empleados con estos filtros.</p>
  } @else {
    <ui-table>
      <thead>
        <tr>
          <th class="px-3 py-2 text-left text-xs font-semibold uppercase text-brand-muted">Nombre</th>
          <th class="px-3 py-2 text-left text-xs font-semibold uppercase text-brand-muted">Sucursal(es)</th>
          <th class="px-3 py-2 text-left text-xs font-semibold uppercase text-brand-muted">Puesto</th>
          <th class="px-3 py-2 text-left text-xs font-semibold uppercase text-brand-muted">Estado laboral</th>
          <th class="px-3 py-2 text-left text-xs font-semibold uppercase text-brand-muted">Liquidación</th>
          @if (canWrite) {
            <th class="px-3 py-2"></th>
          }
        </tr>
      </thead>
      <tbody>
        @for (employee of employees; track employee.id) {
          <tr class="border-b border-brand-line">
            <td class="px-3 py-2">{{ employee.nombre }} {{ employee.apellido }}</td>
            <td class="px-3 py-2">{{ branchNames(employee) }}</td>
            <td class="px-3 py-2">{{ employee.categoria }}</td>
            <td class="px-3 py-2">
              <ui-badge [variant]="estadoLaboralVariant(employee.estadoLaboral)">{{ employee.estadoLaboral }}</ui-badge>
            </td>
            <td class="px-3 py-2">
              <ui-badge [variant]="employee.estadoLiquidacion === 'AL_DIA' ? 'success' : 'warning'">
                {{ employee.estadoLiquidacion }}
              </ui-badge>
            </td>
            @if (canWrite) {
              <td class="px-3 py-2 text-right">
                <button ui-button variant="secondary" size="sm" type="button" (click)="openEditModal(employee)">
                  Editar
                </button>
                <button ui-button variant="secondary" size="sm" type="button" (click)="openStatusModal(employee)">
                  Cambiar estado
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
  <ui-modal [title]="formMode === 'create' ? 'Nuevo empleado' : 'Editar empleado'" (closed)="closeFormModal()">
    @if (formError) {
      <p class="mb-4 rounded-lg bg-badge-error-bg p-3 text-sm text-badge-error-ink" role="alert">{{ formError }}</p>
    }
    <app-employee-form
      [mode]="formMode"
      [initialValue]="formInitialValue"
      [branches]="branches"
      (submitted)="handleFormSubmit($event)"
    ></app-employee-form>
  </ui-modal>
}

@if (statusTarget) {
  <ui-modal title="Cambiar estado laboral" (closed)="closeStatusModal()">
    @if (statusError) {
      <p class="mb-4 rounded-lg bg-badge-error-bg p-3 text-sm text-badge-error-ink" role="alert">{{ statusError }}</p>
    }
    <p class="mb-4 text-sm text-brand-ink">
      Empleado: <strong>{{ statusTarget.nombre }} {{ statusTarget.apellido }}</strong>
    </p>
    <form [formGroup]="statusForm">
      <ui-select label="Nuevo estado" formControlName="estadoLaboral" [options]="estadoLaboralOptions"></ui-select>
    </form>
    <div class="mt-6 flex justify-end gap-2">
      <button ui-button variant="secondary" type="button" (click)="closeStatusModal()">Cancelar</button>
      <button ui-button type="button" (click)="confirmStatusChange()">Guardar</button>
    </div>
  </ui-modal>
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npx ng test --include='**/employees-list.component.spec.ts' --watch=false`
Expected: PASS, 9 tests.

- [ ] **Step 5: Commit**

```bash
cd frontend
git add src/app/features/employees/components/employees-list
git commit -m "feat: agregar pantalla de listado de employees con filtros, alta, edicion y cambio de estado"
```

---

### Task 5: Routing — `/employees` route + home redirect for ADMIN/RRHH/SUPERVISOR

**Files:**
- Modify: `frontend/src/app/app.routes.ts`
- Modify: `frontend/src/app/core/home/home.component.ts`
- Modify: `frontend/src/app/core/home/home.component.spec.ts`

**Interfaces:**
- Consumes: `EmployeesListComponent` (Task 4, lazy-loaded by path); `roleGuard` (already exists, already imported in `app.routes.ts`).

- [ ] **Step 1: Write the failing tests for the extended home redirect**

Modify `frontend/src/app/core/home/home.component.spec.ts` (full file — replaces the FE-1.4 version, which only covered `SUPER_ADMIN` vs. one other role):

```typescript
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';

import { AuthService } from '../services/auth.service';
import { HomeComponent } from './home.component';

function setup(role: string) {
  const routerStub = { navigate: vi.fn() };
  const authServiceStub = {
    getRole: () => role,
    getCompanyId: () => null,
    logout: () => {},
  };

  TestBed.configureTestingModule({
    imports: [HomeComponent],
    providers: [
      { provide: AuthService, useValue: authServiceStub },
      { provide: Router, useValue: routerStub },
    ],
  });

  return routerStub;
}

describe('HomeComponent', () => {
  it('redirects a SUPER_ADMIN to /companies', () => {
    const routerStub = setup('SUPER_ADMIN');
    const fixture = TestBed.createComponent(HomeComponent);
    fixture.detectChanges();
    expect(routerStub.navigate).toHaveBeenCalledWith(['/companies']);
  });

  it('redirects an ADMIN to /employees', () => {
    const routerStub = setup('ADMIN');
    const fixture = TestBed.createComponent(HomeComponent);
    fixture.detectChanges();
    expect(routerStub.navigate).toHaveBeenCalledWith(['/employees']);
  });

  it('redirects an RRHH to /employees', () => {
    const routerStub = setup('RRHH');
    const fixture = TestBed.createComponent(HomeComponent);
    fixture.detectChanges();
    expect(routerStub.navigate).toHaveBeenCalledWith(['/employees']);
  });

  it('redirects a SUPERVISOR to /employees', () => {
    const routerStub = setup('SUPERVISOR');
    const fixture = TestBed.createComponent(HomeComponent);
    fixture.detectChanges();
    expect(routerStub.navigate).toHaveBeenCalledWith(['/employees']);
  });

  it('does not redirect an EMPLOYEE', () => {
    const routerStub = setup('EMPLOYEE');
    const fixture = TestBed.createComponent(HomeComponent);
    fixture.detectChanges();
    expect(routerStub.navigate).not.toHaveBeenCalled();
  });
});
```

- [ ] **Step 2: Run test to verify the new expectations fail**

Run: `cd frontend && npx ng test --include='**/home.component.spec.ts' --watch=false`
Expected: FAIL — 2 pass (`SUPER_ADMIN`, the old non-redirect case doesn't exist anymore so nothing lingers to pass there), 3 fail (`ADMIN`/`RRHH`/`SUPERVISOR` — `router.navigate` was never called, since `HomeComponent` doesn't redirect them yet).

- [ ] **Step 3: Extend the redirect and add the route**

Modify `frontend/src/app/core/home/home.component.ts` (full file):

```typescript
import { Component, OnInit, inject } from '@angular/core';
import { Router } from '@angular/router';

import { AuthService } from '../services/auth.service';

/**
 * Placeholder temporal de la ruta protegida raíz — solo queda para EMPLOYEE,
 * que todavía no tiene pantalla propia. El resto de los roles redirige a su
 * feature principal apenas loguea.
 */
@Component({
  selector: 'app-home',
  standalone: true,
  templateUrl: './home.component.html',
})
export class HomeComponent implements OnInit {
  private authService = inject(AuthService);
  private router = inject(Router);

  rol = this.authService.getRole();
  companyId = this.authService.getCompanyId();

  ngOnInit(): void {
    if (this.rol === 'SUPER_ADMIN') {
      this.router.navigate(['/companies']);
    } else if (this.rol === 'ADMIN' || this.rol === 'RRHH' || this.rol === 'SUPERVISOR') {
      this.router.navigate(['/employees']);
    }
  }

  onLogout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
```

Modify `frontend/src/app/app.routes.ts` (full file):

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
    path: 'employees',
    canActivate: [roleGuard(['ADMIN', 'RRHH', 'SUPERVISOR'])],
    loadComponent: () =>
      import('./features/employees/components/employees-list/employees-list.component').then(
        (m) => m.EmployeesListComponent,
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

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npx ng test --include='**/home.component.spec.ts' --watch=false`
Expected: PASS, 5 tests.

- [ ] **Step 5: Verify the app still builds**

Run: `cd frontend && npx ng build`
Expected: exit code 0, no TypeScript or template errors, and an `employees-list-component` entry appears among the lazy chunk files.

- [ ] **Step 6: Commit**

```bash
cd frontend
git add src/app/app.routes.ts src/app/core/home/home.component.ts src/app/core/home/home.component.spec.ts
git commit -m "feat: agregar ruta employees y redirigir admin/rrhh/supervisor desde home"
```

---

### Task 6: Full suite, build, and manual verification

**Files:** none created — verification only.

- [ ] **Step 1: Run the full test suite**

Run: `cd frontend && npx ng test --watch=false`
Expected: PASS, all specs green. Pre-existing suite on `main` after FE-1.5: 15 files, 67 tests. This feature's changes: `select.component.spec.ts` (new, 5), `input.component.spec.ts` (modified, +1 → 9), `employee.service.spec.ts` (new, 6), `employee-form.component.spec.ts` (new, 4), `employees-list.component.spec.ts` (new, 9), `home.component.spec.ts` (modified, +3 → 5). New files: 4 (`select`, `employee.service`, `employee-form`, `employees-list`). Total: 15 + 4 = **19 files**, 67 + 5 + 1 + 6 + 4 + 9 + 3 = **95 tests**.

- [ ] **Step 2: Run a production build**

Run: `cd frontend && npx ng build`
Expected: exit code 0, no TypeScript or template errors.

- [ ] **Step 3: Verify against the real backend with ADMIN and SUPERVISOR roles, multiple branches and employees**

Start the backend (`cd backend && ./mvnw spring-boot:run`, `dev` profile, H2 in memory). Insert via `/h2-console` (same fetch-based technique used for FE-1.4/FE-1.5, BCrypt hashes generated on the spot):

- One `company` row.
- One `app_user` with `rol = 'ADMIN'`.
- One `app_user` with `rol = 'SUPERVISOR'`.
- Two `branch` rows (check `V2__create_branch_employee_user_platformadmin.sql` for the exact column list).
- Two or three `employee` rows in different `estado_laboral` values, at least one assigned to both branches (`employee_branch` join table — check the same migration file for its exact name/columns), to exercise the branch-name join and the multi-branch checkbox prefill.

Then, with `npx ng serve` running:

1. Log in as the `ADMIN` user. Confirm the redirect from `/` lands on `/employees` automatically (not `/branches` — this is the new default per this plan's Task 5).
2. Confirm the table shows the seeded employees with correctly resolved branch names, and that `estadoLaboral`/`estadoLiquidacion` render as separate badge columns.
3. Type in the search box — confirm the request only fires after you stop typing for a beat (Network tab), not on every keystroke.
4. Filter by `estadoLaboral` and by sucursal — confirm the table narrows correctly for each, and that combining both works.
5. Click "+ Nuevo empleado", fill the form (including checking a branch checkbox), submit — confirm it appears in the table with the right branch name.
6. Click "Editar" on a row — confirm every field prefills correctly, including the right checkbox(es) checked and `sueldoBase` showing the numeric value in the input. Change the puesto, save — confirm it's reflected.
7. Click "Cambiar estado" on a row — confirm the modal's select starts on the employee's current `estadoLaboral`. Change it to `BAJA`, confirm — badge updates, `estadoLiquidacion` is untouched (still whatever it was — this screen never edits it).
8. Log out, log back in as the `SUPERVISOR` user — confirm redirect to `/employees` also happens for this role, the table is visible, but there is no "+ Nuevo empleado" button and no per-row actions.
9. Check the browser console and network tab for errors during the whole flow.

- [ ] **Step 4: Final commit if any fixes were needed**

If manual verification surfaced a bug, fix it and commit:

```bash
cd frontend
git add -A
git commit -m "fix: corregir <lo que haya fallado en la verificacion manual>"
```

If nothing needed fixing, skip this commit — the branch is ready for review.

---

## Self-Review Notes

- **Spec coverage:** the new `ui-select` kit component and `ui-input` `step` addition (Task 1), server-side filters with debounce (Task 4), separate `estadoLaboral`/`estadoLiquidacion` badge columns (Task 4, matches `ux-decisions.md` #3), the branch-checkbox list instead of a custom multi-select (Task 3), the `canWrite` generalization for `SUPERVISOR` (Task 4), and the resolved home-redirect decision (Task 5) are all covered by a task each.
- **Type consistency:** `EmployeeFormValue` (Task 3) is defined once and consumed as-is by Task 4's `handleFormSubmit`/`toRequest`. `Employee`/`EstadoLaboral`/`EstadoLiquidacion`/`TipoContrato`/`CreateEmployeeRequest`/`UpdateEmployeeRequest`/`UpdateEmployeeStatusRequest`/`EmployeeListFilters` (Task 2) are referenced identically across every later task, field names matching the backend DTOs verbatim. `SelectOption`/`SelectComponent` (Task 1) are imported with the same names in Task 3 and Task 4. `EmployeeService`'s method names (`list`, `create`, `update`, `updateStatus`) match what Task 4's spec mocks and what Task 4's implementation calls — same names as `CompanyService`/`BranchService`, keeping all three features' services consistent.
- **No placeholders:** every step has complete, runnable code; no "add tests for the above" or "TBD" in any task. Task 6's manual-verification step lists concrete actions rather than a vague "test the feature."
