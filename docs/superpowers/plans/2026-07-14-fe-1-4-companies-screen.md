# FE-1.4 — `features/companies` Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Super-Admin-only `/companies` screen: list, create, edit, and suspend/activate companies against the already-merged BE-1.3 backend, using the FE-1.3 UI kit.

**Architecture:** A `CompaniesListComponent` smart container (fetch + modal-state orchestration) composes a reusable `CompanyFormComponent` (create/edit reactive form) and a presentational `CompanyCreatedModalComponent` (one-time admin-password reveal). A `CompanyService` wraps the 4 HTTP calls. The root `''` route redirects `SUPER_ADMIN` to the new `/companies` route; other roles keep the existing placeholder home.

**Tech Stack:** Angular 21.2 (standalone, decorator-based `@Input`/`@Output`, no signals), Reactive Forms, `@angular/common/http` + `provideHttpClientTesting`/`HttpTestingController` for service tests, Vitest via `@angular/build:unit-test` (globals enabled — `describe`/`it`/`expect`/`vi` need no import, per `tsconfig.spec.json`'s `"types": ["vitest/globals"]`).

**Reference:** Spec at `docs/superpowers/specs/2026-07-14-fe-1-4-companies-screen-design.md`. Backend contract at `backend/src/main/java/com/staffly/backend/company/` (already merged, do not modify).

## Global Constraints

- Standalone components only, no NgModules; no signals — local state is plain class properties (`angular-frontend` skill).
- `@Input()`/`@Output()` decorators, never `input()`/`output()` functions; `inject()` for DI inside the class body (`angular-frontend` skill).
- Control flow: `@if`/`@for` with `track`, never `*ngIf`/`*ngFor` (`angular-frontend` skill).
- Reactive Forms only (`FormBuilder`/`FormGroup`), never template-driven (`frontend/CLAUDE.md`).
- Styles: Tailwind utility classes only, referencing the brand tokens from FE-1.3 (`brand-*`, `badge-*`). Zero `style=""` inline, zero custom CSS.
- Zero `any` without a justifying comment; every input has an associated `<label>`; max ~150 lines per file.
- Commits: Conventional Commits, messages in Spanish, lowercase, no trailing period. Allowed types: `feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:`.
- `company_id`/role are never read from the URL or a client-supplied parameter — the JWT (via `AuthService`) is the only source (`frontend/CLAUDE.md`). This screen only ever calls Super-Admin-scoped endpoints; no `company_id` is sent by the client anywhere in this feature.
- País/moneda/zonaHoraria are free-text inputs — no catalog/selector (spec decision, YAGNI: single real tenant today, backend has no enum for these fields).
- `adminTemporaryPassword` is shown exactly once, in `CompanyCreatedModalComponent`, and is never persisted beyond that modal's lifetime.
- Branch: `feature/companies-screen`, already created from `main` with the spec doc committed. This plan's commits land on top of it.

---

## File Structure

```
frontend/src/app/
├── app.routes.ts                                          [MODIFY] add /companies route
├── core/home/
│   ├── home.component.ts                                  [MODIFY] redirect SUPER_ADMIN to /companies
│   └── home.component.spec.ts                             [CREATE] test the redirect
└── features/companies/
    ├── models/
    │   └── company.ts                                     [CREATE] TS types mirroring the backend DTOs
    ├── services/
    │   ├── company.service.ts                              [CREATE] list/create/update/updateStatus
    │   └── company.service.spec.ts                         [CREATE]
    └── components/
        ├── company-form/
        │   ├── company-form.component.ts                   [CREATE]
        │   ├── company-form.component.html                 [CREATE]
        │   └── company-form.component.spec.ts               [CREATE]
        ├── company-created-modal/
        │   ├── company-created-modal.component.ts           [CREATE]
        │   ├── company-created-modal.component.html          [CREATE]
        │   └── company-created-modal.component.spec.ts       [CREATE]
        └── companies-list/
            ├── companies-list.component.ts                  [CREATE]
            ├── companies-list.component.html                 [CREATE]
            └── companies-list.component.spec.ts               [CREATE]
```

Task order: models+service (nothing depends on UI) → company-form → company-created-modal → companies-list (composes the previous three) → routing (depends on companies-list existing) → full verification.

---

### Task 1: `Company` models + `CompanyService`

**Files:**
- Create: `frontend/src/app/features/companies/models/company.ts`
- Create: `frontend/src/app/features/companies/services/company.service.ts`
- Test: `frontend/src/app/features/companies/services/company.service.spec.ts`

**Interfaces:**
- Produces: `Company`, `EstadoEmpresa` (`'ACTIVA' | 'SUSPENDIDA'`), `CreateCompanyRequest`, `CreateCompanyResponse`, `UpdateCompanyRequest`, `UpdateCompanyStatusRequest` (all from `models/company.ts`); `CompanyService` (`providedIn: 'root'`) with `list(): Observable<Company[]>`, `create(request: CreateCompanyRequest): Observable<CreateCompanyResponse>`, `update(id: string, request: UpdateCompanyRequest): Observable<Company>`, `updateStatus(id: string, request: UpdateCompanyStatusRequest): Observable<Company>`. All four later tasks (company-form, company-created-modal, companies-list) import from `models/company.ts`; `companies-list` also injects `CompanyService`.

- [ ] **Step 1: Write the models (no test — pure types)**

Create `frontend/src/app/features/companies/models/company.ts`:

```typescript
export type EstadoEmpresa = 'ACTIVA' | 'SUSPENDIDA';

export interface Company {
  id: string;
  nombre: string;
  razonSocial: string;
  pais: string;
  moneda: string;
  zonaHoraria: string;
  estado: EstadoEmpresa;
  plan: string | null;
  fechaAlta: string;
}

export interface CreateCompanyRequest {
  nombre: string;
  razonSocial: string;
  pais: string;
  moneda: string;
  zonaHoraria: string;
  plan?: string;
  adminEmail: string;
}

export interface CreateCompanyResponse {
  company: Company;
  adminEmail: string;
  adminTemporaryPassword: string;
}

export interface UpdateCompanyRequest {
  nombre?: string;
  razonSocial?: string;
  pais?: string;
  moneda?: string;
  zonaHoraria?: string;
  plan?: string;
}

export interface UpdateCompanyStatusRequest {
  estado: EstadoEmpresa;
}
```

- [ ] **Step 2: Write the failing test for `CompanyService`**

Create `frontend/src/app/features/companies/services/company.service.spec.ts`:

```typescript
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { environment } from '../../../../environments/environment';
import {
  Company,
  CreateCompanyRequest,
  CreateCompanyResponse,
  UpdateCompanyRequest,
  UpdateCompanyStatusRequest,
} from '../models/company';
import { CompanyService } from './company.service';

const baseUrl = `${environment.apiUrl}/companies`;

const mockCompany: Company = {
  id: '1',
  nombre: 'Heladería Lucca',
  razonSocial: 'Heladería Lucca S.R.L.',
  pais: 'Argentina',
  moneda: 'ARS',
  zonaHoraria: 'America/Buenos_Aires',
  estado: 'ACTIVA',
  plan: 'SaaS Inicial',
  fechaAlta: '2026-07-14T00:00:00Z',
};

describe('CompanyService', () => {
  let service: CompanyService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(CompanyService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('lists companies with a GET to /companies', () => {
    service.list().subscribe((companies) => expect(companies).toEqual([mockCompany]));
    const req = httpMock.expectOne(baseUrl);
    expect(req.request.method).toBe('GET');
    req.flush([mockCompany]);
  });

  it('creates a company with a POST to /companies', () => {
    const request: CreateCompanyRequest = {
      nombre: 'Heladería Lucca',
      razonSocial: 'Heladería Lucca S.R.L.',
      pais: 'Argentina',
      moneda: 'ARS',
      zonaHoraria: 'America/Buenos_Aires',
      adminEmail: 'admin@lucca.com',
    };
    const mockResponse: CreateCompanyResponse = {
      company: mockCompany,
      adminEmail: 'admin@lucca.com',
      adminTemporaryPassword: 'temp1234',
    };
    service.create(request).subscribe((response) => expect(response).toEqual(mockResponse));
    const req = httpMock.expectOne(baseUrl);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(mockResponse);
  });

  it('updates a company with a PATCH to /companies/{id}', () => {
    const request: UpdateCompanyRequest = { nombre: 'Nuevo nombre' };
    service.update('1', request).subscribe((company) => expect(company).toEqual(mockCompany));
    const req = httpMock.expectOne(`${baseUrl}/1`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual(request);
    req.flush(mockCompany);
  });

  it('updates a company status with a PATCH to /companies/{id}/status', () => {
    const request: UpdateCompanyStatusRequest = { estado: 'SUSPENDIDA' };
    service.updateStatus('1', request).subscribe((company) => expect(company).toEqual(mockCompany));
    const req = httpMock.expectOne(`${baseUrl}/1/status`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual(request);
    req.flush(mockCompany);
  });
});
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd frontend && npx ng test --include='**/company.service.spec.ts' --watch=false`
Expected: FAIL — `Cannot find module './company.service'`.

- [ ] **Step 4: Write minimal implementation**

Create `frontend/src/app/features/companies/services/company.service.ts`:

```typescript
import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../../environments/environment';
import {
  Company,
  CreateCompanyRequest,
  CreateCompanyResponse,
  UpdateCompanyRequest,
  UpdateCompanyStatusRequest,
} from '../models/company';

@Injectable({ providedIn: 'root' })
export class CompanyService {
  private http = inject(HttpClient);
  private baseUrl = `${environment.apiUrl}/companies`;

  list(): Observable<Company[]> {
    return this.http.get<Company[]>(this.baseUrl);
  }

  create(request: CreateCompanyRequest): Observable<CreateCompanyResponse> {
    return this.http.post<CreateCompanyResponse>(this.baseUrl, request);
  }

  update(id: string, request: UpdateCompanyRequest): Observable<Company> {
    return this.http.patch<Company>(`${this.baseUrl}/${id}`, request);
  }

  updateStatus(id: string, request: UpdateCompanyStatusRequest): Observable<Company> {
    return this.http.patch<Company>(`${this.baseUrl}/${id}/status`, request);
  }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd frontend && npx ng test --include='**/company.service.spec.ts' --watch=false`
Expected: PASS, 4 tests.

- [ ] **Step 6: Commit**

```bash
cd frontend
git add src/app/features/companies/models src/app/features/companies/services
git commit -m "feat: agregar modelos y servicio http de companies"
```

---

### Task 2: `CompanyFormComponent`

**Files:**
- Create: `frontend/src/app/features/companies/components/company-form/company-form.component.ts`
- Create: `frontend/src/app/features/companies/components/company-form/company-form.component.html`
- Test: `frontend/src/app/features/companies/components/company-form/company-form.component.spec.ts`

**Interfaces:**
- Consumes: `Company` from `../../models/company` (Task 1); `InputComponent` (selector `ui-input`) and `ButtonDirective` (selector `button[ui-button]`) from the FE-1.3 kit at `frontend/src/app/shared/components/`.
- Produces: `CompanyFormComponent` (selector `app-company-form`), `@Input() mode: 'create' | 'edit' = 'create'`, `@Input() initialValue?: Company`, `@Output() submitted = new EventEmitter<CompanyFormValue>()`, exported type `CompanyFormValue` (`{ nombre, razonSocial, pais, moneda, zonaHoraria, plan, adminEmail }`, all `string`). Task 4 (`companies-list`) imports `CompanyFormComponent` and `CompanyFormValue`.
- Design note: this component reads `mode` and `initialValue` once, in `ngOnInit` — it assumes the parent creates a fresh instance every time the surrounding modal opens (true here: Task 4 renders it inside an `@if`), so it does not need to react to `mode`/`initialValue` changing after construction.

- [ ] **Step 1: Write the failing test**

Create `frontend/src/app/features/companies/components/company-form/company-form.component.spec.ts`:

```typescript
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { Company } from '../../models/company';
import { CompanyFormComponent, CompanyFormValue } from './company-form.component';

@Component({
  standalone: true,
  imports: [CompanyFormComponent],
  template: `
    <app-company-form
      [mode]="mode"
      [initialValue]="initialValue"
      (submitted)="onSubmitted($event)"
    ></app-company-form>
  `,
})
class HostComponent {
  mode: 'create' | 'edit' = 'create';
  initialValue?: Company;
  submittedValue?: CompanyFormValue;

  onSubmitted(value: CompanyFormValue): void {
    this.submittedValue = value;
  }
}

const mockCompany: Company = {
  id: '1',
  nombre: 'Heladería Lucca',
  razonSocial: 'Heladería Lucca S.R.L.',
  pais: 'Argentina',
  moneda: 'ARS',
  zonaHoraria: 'America/Buenos_Aires',
  estado: 'ACTIVA',
  plan: 'SaaS Inicial',
  fechaAlta: '2026-07-14T00:00:00Z',
};

describe('CompanyFormComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
  });

  it('renders the admin email field in create mode', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const labels = Array.from(fixture.nativeElement.querySelectorAll('label')).map((l) =>
      (l as HTMLLabelElement).textContent?.trim(),
    );
    expect(labels).toContain('Email del admin');
  });

  it('does not render the admin email field in edit mode', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.componentInstance.mode = 'edit';
    fixture.detectChanges();
    const labels = Array.from(fixture.nativeElement.querySelectorAll('label')).map((l) =>
      (l as HTMLLabelElement).textContent?.trim(),
    );
    expect(labels).not.toContain('Email del admin');
  });

  it('prefills the form from initialValue', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.componentInstance.mode = 'edit';
    fixture.componentInstance.initialValue = mockCompany;
    fixture.detectChanges();
    const firstInput = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    expect(firstInput.value).toBe('Heladería Lucca');
  });

  it('marks fields touched and does not emit submitted when the form is invalid', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const form = fixture.nativeElement.querySelector('form') as HTMLFormElement;
    form.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
    expect(fixture.componentInstance.submittedValue).toBeUndefined();
    expect(fixture.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
  });

  it('emits submitted with the form value when valid', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const component = fixture.debugElement.children[0].componentInstance as CompanyFormComponent;
    component.form.setValue({
      nombre: 'Heladería Lucca',
      razonSocial: 'Heladería Lucca S.R.L.',
      pais: 'Argentina',
      moneda: 'ARS',
      zonaHoraria: 'America/Buenos_Aires',
      plan: '',
      adminEmail: 'admin@lucca.com',
    });
    fixture.detectChanges();
    const form = fixture.nativeElement.querySelector('form') as HTMLFormElement;
    form.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
    expect(fixture.componentInstance.submittedValue).toEqual({
      nombre: 'Heladería Lucca',
      razonSocial: 'Heladería Lucca S.R.L.',
      pais: 'Argentina',
      moneda: 'ARS',
      zonaHoraria: 'America/Buenos_Aires',
      plan: '',
      adminEmail: 'admin@lucca.com',
    });
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npx ng test --include='**/company-form.component.spec.ts' --watch=false`
Expected: FAIL — `Cannot find module './company-form.component'`.

- [ ] **Step 3: Write minimal implementation**

Create `frontend/src/app/features/companies/components/company-form/company-form.component.ts`:

```typescript
import { Component, EventEmitter, Input, OnInit, Output, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { ButtonDirective } from '../../../../shared/components/button/button.directive';
import { InputComponent } from '../../../../shared/components/input/input.component';
import { Company } from '../../models/company';

export interface CompanyFormValue {
  nombre: string;
  razonSocial: string;
  pais: string;
  moneda: string;
  zonaHoraria: string;
  plan: string;
  adminEmail: string;
}

@Component({
  selector: 'app-company-form',
  standalone: true,
  imports: [ReactiveFormsModule, InputComponent, ButtonDirective],
  templateUrl: './company-form.component.html',
})
export class CompanyFormComponent implements OnInit {
  @Input() mode: 'create' | 'edit' = 'create';
  @Input() initialValue?: Company;
  @Output() submitted = new EventEmitter<CompanyFormValue>();

  private fb = inject(FormBuilder);

  form = this.fb.group({
    nombre: ['', Validators.required],
    razonSocial: ['', Validators.required],
    pais: ['', Validators.required],
    moneda: ['', Validators.required],
    zonaHoraria: ['', Validators.required],
    plan: [''],
    adminEmail: [''],
  });

  get nombreCtrl() {
    return this.form.get('nombre')!;
  }
  get razonSocialCtrl() {
    return this.form.get('razonSocial')!;
  }
  get paisCtrl() {
    return this.form.get('pais')!;
  }
  get monedaCtrl() {
    return this.form.get('moneda')!;
  }
  get zonaHorariaCtrl() {
    return this.form.get('zonaHoraria')!;
  }
  get adminEmailCtrl() {
    return this.form.get('adminEmail')!;
  }

  get adminEmailErrorMessage(): string | undefined {
    if (!this.adminEmailCtrl.invalid || !this.adminEmailCtrl.touched) {
      return undefined;
    }
    if (this.adminEmailCtrl.errors?.['required']) {
      return 'El email del admin es requerido.';
    }
    if (this.adminEmailCtrl.errors?.['email']) {
      return 'Ingresá un email válido.';
    }
    return undefined;
  }

  ngOnInit(): void {
    if (this.mode === 'create') {
      this.adminEmailCtrl.addValidators([Validators.required, Validators.email]);
      this.adminEmailCtrl.updateValueAndValidity();
    }
    if (this.initialValue) {
      this.form.patchValue({
        nombre: this.initialValue.nombre,
        razonSocial: this.initialValue.razonSocial,
        pais: this.initialValue.pais,
        moneda: this.initialValue.moneda,
        zonaHoraria: this.initialValue.zonaHoraria,
        plan: this.initialValue.plan ?? '',
      });
    }
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitted.emit(this.form.getRawValue() as CompanyFormValue);
  }
}
```

Create `frontend/src/app/features/companies/components/company-form/company-form.component.html`:

```html
<form [formGroup]="form" (ngSubmit)="onSubmit()" class="space-y-4">
  <ui-input
    label="Nombre"
    formControlName="nombre"
    [errorMessage]="nombreCtrl.invalid && nombreCtrl.touched ? 'El nombre es requerido.' : undefined"
  ></ui-input>
  <ui-input
    label="Razón social"
    formControlName="razonSocial"
    [errorMessage]="razonSocialCtrl.invalid && razonSocialCtrl.touched ? 'La razón social es requerida.' : undefined"
  ></ui-input>
  <ui-input
    label="País"
    formControlName="pais"
    [errorMessage]="paisCtrl.invalid && paisCtrl.touched ? 'El país es requerido.' : undefined"
  ></ui-input>
  <ui-input
    label="Moneda"
    formControlName="moneda"
    [errorMessage]="monedaCtrl.invalid && monedaCtrl.touched ? 'La moneda es requerida.' : undefined"
  ></ui-input>
  <ui-input
    label="Zona horaria"
    formControlName="zonaHoraria"
    [errorMessage]="zonaHorariaCtrl.invalid && zonaHorariaCtrl.touched ? 'La zona horaria es requerida.' : undefined"
  ></ui-input>
  <ui-input label="Plan (opcional)" formControlName="plan"></ui-input>
  @if (mode === 'create') {
    <ui-input
      label="Email del admin"
      type="email"
      formControlName="adminEmail"
      [errorMessage]="adminEmailErrorMessage"
    ></ui-input>
  }
  <button ui-button type="submit" class="w-full">
    @if (mode === 'create') {
      Crear empresa
    } @else {
      Guardar cambios
    }
  </button>
</form>
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npx ng test --include='**/company-form.component.spec.ts' --watch=false`
Expected: PASS, 5 tests.

- [ ] **Step 5: Commit**

```bash
cd frontend
git add src/app/features/companies/components/company-form
git commit -m "feat: agregar formulario reactivo de alta y edicion de companies"
```

---

### Task 3: `CompanyCreatedModalComponent`

**Files:**
- Create: `frontend/src/app/features/companies/components/company-created-modal/company-created-modal.component.ts`
- Create: `frontend/src/app/features/companies/components/company-created-modal/company-created-modal.component.html`
- Test: `frontend/src/app/features/companies/components/company-created-modal/company-created-modal.component.spec.ts`

**Interfaces:**
- Consumes: `ModalComponent` (selector `ui-modal`, `@Output() closed`) and `ButtonDirective` from the FE-1.3 kit.
- Produces: `CompanyCreatedModalComponent` (selector `app-company-created-modal`), `@Input({ required: true }) adminEmail!: string`, `@Input({ required: true }) temporaryPassword!: string`, `@Output() closed = new EventEmitter<void>()`. Task 4 imports this and passes the two strings straight from `CreateCompanyResponse`.

- [ ] **Step 1: Write the failing test**

Create `frontend/src/app/features/companies/components/company-created-modal/company-created-modal.component.spec.ts`:

```typescript
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { CompanyCreatedModalComponent } from './company-created-modal.component';

@Component({
  standalone: true,
  imports: [CompanyCreatedModalComponent],
  template: `
    <app-company-created-modal
      [adminEmail]="email"
      [temporaryPassword]="password"
      (closed)="onClosed()"
    ></app-company-created-modal>
  `,
})
class HostComponent {
  email = 'admin@lucca.com';
  password = 'temp1234';
  closedCount = 0;

  onClosed(): void {
    this.closedCount++;
  }
}

describe('CompanyCreatedModalComponent', () => {
  let writeTextSpy: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    writeTextSpy = vi.fn().mockResolvedValue(undefined);
    Object.assign(navigator, { clipboard: { writeText: writeTextSpy } });
    await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
  });

  it('renders the admin email and temporary password', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('admin@lucca.com');
    expect(fixture.nativeElement.textContent).toContain('temp1234');
  });

  it('copies the temporary password to the clipboard when "Copiar contraseña" is clicked', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const buttons = Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[];
    const copyButton = buttons.find((b) => b.textContent?.includes('Copiar contraseña'))!;
    copyButton.click();
    expect(writeTextSpy).toHaveBeenCalledWith('temp1234');
  });

  it('emits closed when the "Entendido" button is clicked', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const buttons = Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[];
    const closeButton = buttons.find((b) => b.textContent?.includes('Entendido'))!;
    closeButton.click();
    expect(fixture.componentInstance.closedCount).toBe(1);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npx ng test --include='**/company-created-modal.component.spec.ts' --watch=false`
Expected: FAIL — `Cannot find module './company-created-modal.component'`.

- [ ] **Step 3: Write minimal implementation**

Create `frontend/src/app/features/companies/components/company-created-modal/company-created-modal.component.ts`:

```typescript
import { Component, EventEmitter, Input, Output } from '@angular/core';

import { ButtonDirective } from '../../../../shared/components/button/button.directive';
import { ModalComponent } from '../../../../shared/components/modal/modal.component';

@Component({
  selector: 'app-company-created-modal',
  standalone: true,
  imports: [ModalComponent, ButtonDirective],
  templateUrl: './company-created-modal.component.html',
})
export class CompanyCreatedModalComponent {
  @Input({ required: true }) adminEmail!: string;
  @Input({ required: true }) temporaryPassword!: string;
  @Output() closed = new EventEmitter<void>();

  copied = false;

  copyPassword(): void {
    navigator.clipboard.writeText(this.temporaryPassword);
    this.copied = true;
  }
}
```

Create `frontend/src/app/features/companies/components/company-created-modal/company-created-modal.component.html`:

```html
<ui-modal title="Empresa creada" (closed)="closed.emit()">
  <p class="text-sm text-brand-ink">
    Guardá esta contraseña provisoria ahora — <strong>no se va a volver a mostrar</strong>.
  </p>
  <dl class="mt-4 space-y-2 text-sm">
    <div>
      <dt class="font-medium text-brand-muted">Email del admin</dt>
      <dd class="font-mono">{{ adminEmail }}</dd>
    </div>
    <div>
      <dt class="font-medium text-brand-muted">Contraseña provisoria</dt>
      <dd class="font-mono">{{ temporaryPassword }}</dd>
    </div>
  </dl>
  <div class="mt-6 flex justify-end gap-2">
    <button ui-button variant="secondary" type="button" (click)="copyPassword()">
      @if (copied) {
        Copiado ✓
      } @else {
        Copiar contraseña
      }
    </button>
    <button ui-button type="button" (click)="closed.emit()">Entendido</button>
  </div>
</ui-modal>
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npx ng test --include='**/company-created-modal.component.spec.ts' --watch=false`
Expected: PASS, 3 tests.

- [ ] **Step 5: Commit**

```bash
cd frontend
git add src/app/features/companies/components/company-created-modal
git commit -m "feat: agregar modal de revelado unico de contrasena provisoria"
```

---

### Task 4: `CompaniesListComponent`

**Files:**
- Create: `frontend/src/app/features/companies/components/companies-list/companies-list.component.ts`
- Create: `frontend/src/app/features/companies/components/companies-list/companies-list.component.html`
- Test: `frontend/src/app/features/companies/components/companies-list/companies-list.component.spec.ts`

**Interfaces:**
- Consumes: `CompanyService` (Task 1) — `list()`, `create()`, `update()`, `updateStatus()`; `Company`, `EstadoEmpresa`, `CreateCompanyRequest`, `UpdateCompanyRequest` (Task 1); `CompanyFormComponent`/`CompanyFormValue` (Task 2); `CompanyCreatedModalComponent` (Task 3); `TableComponent` (`ui-table`), `BadgeComponent` (`ui-badge`, `@Input() variant`), `ModalComponent` (`ui-modal`), `ButtonDirective` (`button[ui-button]`) from the FE-1.3 kit.
- Produces: `CompaniesListComponent` (selector `app-companies-list`) with public properties `companies: Company[]`, `loading: boolean`, `loadError: string | null`, `formMode: 'create' | 'edit' | null`, `formInitialValue?: Company`, `formError: string | null`, `createdModal: { adminEmail: string; temporaryPassword: string } | null`, `statusTarget: Company | null`, `statusError: string | null`, and methods `openCreateModal()`, `openEditModal(company: Company)`, `closeFormModal()`, `handleFormSubmit(value: CompanyFormValue)`, `closeCreatedModal()`, `openStatusConfirm(company: Company)`, `closeStatusConfirm()`, `confirmStatusChange()`. Task 5 (routing) lazy-loads this component by name.

- [ ] **Step 1: Write the failing test**

Create `frontend/src/app/features/companies/components/companies-list/companies-list.component.spec.ts`:

```typescript
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { Company, CreateCompanyResponse } from '../../models/company';
import { CompanyService } from '../../services/company.service';
import { CompaniesListComponent } from './companies-list.component';

const mockCompany: Company = {
  id: '1',
  nombre: 'Heladería Lucca',
  razonSocial: 'Heladería Lucca S.R.L.',
  pais: 'Argentina',
  moneda: 'ARS',
  zonaHoraria: 'America/Buenos_Aires',
  estado: 'ACTIVA',
  plan: 'SaaS Inicial',
  fechaAlta: '2026-07-14T00:00:00Z',
};

describe('CompaniesListComponent', () => {
  let companyServiceStub: {
    list: ReturnType<typeof vi.fn>;
    create: ReturnType<typeof vi.fn>;
    update: ReturnType<typeof vi.fn>;
    updateStatus: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    companyServiceStub = {
      list: vi.fn().mockReturnValue(of([mockCompany])),
      create: vi.fn(),
      update: vi.fn(),
      updateStatus: vi.fn(),
    };

    TestBed.configureTestingModule({
      imports: [CompaniesListComponent],
      providers: [{ provide: CompanyService, useValue: companyServiceStub }],
    });
  });

  it('shows the loaded companies in the table', () => {
    const fixture = TestBed.createComponent(CompaniesListComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Heladería Lucca');
  });

  it('shows a loading error banner when the list request fails', () => {
    companyServiceStub.list.mockReturnValue(throwError(() => new Error('network error')));
    const fixture = TestBed.createComponent(CompaniesListComponent);
    fixture.detectChanges();
    const alert = fixture.nativeElement.querySelector('[role="alert"]');
    expect(alert?.textContent).toContain('No se pudo cargar');
  });

  it('opens the create modal, and on successful submit shows the created-password modal and refreshes the list', () => {
    const createResponse: CreateCompanyResponse = {
      company: { ...mockCompany, id: '2', nombre: 'Nueva Empresa' },
      adminEmail: 'admin@nueva.com',
      adminTemporaryPassword: 'temp123',
    };
    companyServiceStub.create.mockReturnValue(of(createResponse));

    const fixture = TestBed.createComponent(CompaniesListComponent);
    fixture.detectChanges();

    fixture.componentInstance.openCreateModal();
    expect(fixture.componentInstance.formMode).toBe('create');

    fixture.componentInstance.handleFormSubmit({
      nombre: 'Nueva Empresa',
      razonSocial: 'Nueva Empresa S.A.',
      pais: 'Argentina',
      moneda: 'ARS',
      zonaHoraria: 'America/Buenos_Aires',
      plan: '',
      adminEmail: 'admin@nueva.com',
    });
    fixture.detectChanges();

    expect(companyServiceStub.create).toHaveBeenCalled();
    expect(fixture.componentInstance.formMode).toBeNull();
    expect(fixture.componentInstance.createdModal).toEqual({
      adminEmail: 'admin@nueva.com',
      temporaryPassword: 'temp123',
    });
    expect(companyServiceStub.list).toHaveBeenCalledTimes(2);
  });

  it('shows a form error banner without closing the modal when create fails', () => {
    companyServiceStub.create.mockReturnValue(throwError(() => new Error('validation error')));
    const fixture = TestBed.createComponent(CompaniesListComponent);
    fixture.detectChanges();

    fixture.componentInstance.openCreateModal();
    fixture.componentInstance.handleFormSubmit({
      nombre: 'X',
      razonSocial: 'X',
      pais: 'X',
      moneda: 'X',
      zonaHoraria: 'X',
      plan: '',
      adminEmail: 'x@x.com',
    });
    fixture.detectChanges();

    expect(fixture.componentInstance.formMode).toBe('create');
    expect(fixture.componentInstance.formError).toContain('No se pudo crear');
  });

  it('opens the edit modal prefilled with the selected company', () => {
    const fixture = TestBed.createComponent(CompaniesListComponent);
    fixture.detectChanges();

    fixture.componentInstance.openEditModal(mockCompany);

    expect(fixture.componentInstance.formMode).toBe('edit');
    expect(fixture.componentInstance.formInitialValue).toEqual(mockCompany);
  });

  it('opens the status confirmation and applies the opposite estado on confirm', () => {
    companyServiceStub.updateStatus.mockReturnValue(of({ ...mockCompany, estado: 'SUSPENDIDA' }));
    const fixture = TestBed.createComponent(CompaniesListComponent);
    fixture.detectChanges();

    fixture.componentInstance.openStatusConfirm(mockCompany);
    expect(fixture.componentInstance.statusTarget).toEqual(mockCompany);

    fixture.componentInstance.confirmStatusChange();

    expect(companyServiceStub.updateStatus).toHaveBeenCalledWith('1', { estado: 'SUSPENDIDA' });
    expect(fixture.componentInstance.statusTarget).toBeNull();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npx ng test --include='**/companies-list.component.spec.ts' --watch=false`
Expected: FAIL — `Cannot find module './companies-list.component'`.

- [ ] **Step 3: Write minimal implementation**

Create `frontend/src/app/features/companies/components/companies-list/companies-list.component.ts`:

```typescript
import { Component, OnInit, inject } from '@angular/core';
import { DatePipe } from '@angular/common';

import { BadgeComponent } from '../../../../shared/components/badge/badge.component';
import { ButtonDirective } from '../../../../shared/components/button/button.directive';
import { ModalComponent } from '../../../../shared/components/modal/modal.component';
import { TableComponent } from '../../../../shared/components/table/table.component';
import {
  Company,
  CreateCompanyRequest,
  EstadoEmpresa,
  UpdateCompanyRequest,
} from '../../models/company';
import { CompanyService } from '../../services/company.service';
import { CompanyCreatedModalComponent } from '../company-created-modal/company-created-modal.component';
import { CompanyFormComponent, CompanyFormValue } from '../company-form/company-form.component';

@Component({
  selector: 'app-companies-list',
  standalone: true,
  imports: [
    DatePipe,
    ButtonDirective,
    BadgeComponent,
    ModalComponent,
    TableComponent,
    CompanyFormComponent,
    CompanyCreatedModalComponent,
  ],
  templateUrl: './companies-list.component.html',
})
export class CompaniesListComponent implements OnInit {
  private companyService = inject(CompanyService);

  companies: Company[] = [];
  loading = true;
  loadError: string | null = null;

  formMode: 'create' | 'edit' | null = null;
  formInitialValue?: Company;
  formError: string | null = null;

  createdModal: { adminEmail: string; temporaryPassword: string } | null = null;

  statusTarget: Company | null = null;
  statusError: string | null = null;

  ngOnInit(): void {
    this.loadCompanies();
  }

  loadCompanies(): void {
    this.loading = true;
    this.loadError = null;
    this.companyService.list().subscribe({
      next: (companies) => {
        this.companies = companies;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.loadError = 'No se pudo cargar el listado de empresas.';
      },
    });
  }

  openCreateModal(): void {
    this.formMode = 'create';
    this.formInitialValue = undefined;
    this.formError = null;
  }

  openEditModal(company: Company): void {
    this.formMode = 'edit';
    this.formInitialValue = company;
    this.formError = null;
  }

  closeFormModal(): void {
    this.formMode = null;
  }

  handleFormSubmit(value: CompanyFormValue): void {
    this.formError = null;

    if (this.formMode === 'create') {
      const request: CreateCompanyRequest = {
        nombre: value.nombre,
        razonSocial: value.razonSocial,
        pais: value.pais,
        moneda: value.moneda,
        zonaHoraria: value.zonaHoraria,
        plan: value.plan || undefined,
        adminEmail: value.adminEmail,
      };
      this.companyService.create(request).subscribe({
        next: (response) => {
          this.formMode = null;
          this.createdModal = {
            adminEmail: response.adminEmail,
            temporaryPassword: response.adminTemporaryPassword,
          };
          this.loadCompanies();
        },
        error: () => {
          this.formError = 'No se pudo crear la empresa. Intentá de nuevo.';
        },
      });
      return;
    }

    const target = this.formInitialValue;
    if (this.formMode === 'edit' && target) {
      const request: UpdateCompanyRequest = {
        nombre: value.nombre,
        razonSocial: value.razonSocial,
        pais: value.pais,
        moneda: value.moneda,
        zonaHoraria: value.zonaHoraria,
        plan: value.plan || undefined,
      };
      this.companyService.update(target.id, request).subscribe({
        next: () => {
          this.formMode = null;
          this.loadCompanies();
        },
        error: () => {
          this.formError = 'No se pudo guardar los cambios. Intentá de nuevo.';
        },
      });
    }
  }

  closeCreatedModal(): void {
    this.createdModal = null;
  }

  openStatusConfirm(company: Company): void {
    this.statusTarget = company;
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
    const nuevoEstado: EstadoEmpresa = target.estado === 'ACTIVA' ? 'SUSPENDIDA' : 'ACTIVA';
    this.companyService.updateStatus(target.id, { estado: nuevoEstado }).subscribe({
      next: () => {
        this.statusTarget = null;
        this.loadCompanies();
      },
      error: () => {
        this.statusError = 'No se pudo cambiar el estado de la empresa.';
      },
    });
  }
}
```

Create `frontend/src/app/features/companies/components/companies-list/companies-list.component.html`:

```html
<main class="mx-auto max-w-5xl px-4 py-8">
  <div class="mb-4 flex items-center justify-between">
    <h1 class="font-heading text-2xl font-bold text-brand-ink">Empresas</h1>
    <button ui-button type="button" (click)="openCreateModal()">+ Nueva empresa</button>
  </div>

  @if (loading) {
    <p class="text-sm text-brand-muted">Cargando empresas...</p>
  } @else if (loadError) {
    <p class="rounded-lg bg-badge-error-bg p-3 text-sm text-badge-error-ink" role="alert">{{ loadError }}</p>
  } @else if (companies.length === 0) {
    <p class="text-sm text-brand-muted">Todavía no hay empresas cargadas.</p>
  } @else {
    <ui-table>
      <thead>
        <tr>
          <th class="px-3 py-2 text-left text-xs font-semibold uppercase text-brand-muted">Nombre</th>
          <th class="px-3 py-2 text-left text-xs font-semibold uppercase text-brand-muted">País</th>
          <th class="px-3 py-2 text-left text-xs font-semibold uppercase text-brand-muted">Plan</th>
          <th class="px-3 py-2 text-left text-xs font-semibold uppercase text-brand-muted">Estado</th>
          <th class="px-3 py-2 text-left text-xs font-semibold uppercase text-brand-muted">Alta</th>
          <th class="px-3 py-2"></th>
        </tr>
      </thead>
      <tbody>
        @for (company of companies; track company.id) {
          <tr class="border-b border-brand-line">
            <td class="px-3 py-2">{{ company.nombre }}</td>
            <td class="px-3 py-2">{{ company.pais }}</td>
            <td class="px-3 py-2">{{ company.plan || '—' }}</td>
            <td class="px-3 py-2">
              <ui-badge [variant]="company.estado === 'ACTIVA' ? 'success' : 'error'">{{ company.estado }}</ui-badge>
            </td>
            <td class="px-3 py-2">{{ company.fechaAlta | date: 'dd/MM/yyyy' }}</td>
            <td class="px-3 py-2 text-right">
              <button ui-button variant="secondary" size="sm" type="button" (click)="openEditModal(company)">
                Editar
              </button>
              <button ui-button variant="secondary" size="sm" type="button" (click)="openStatusConfirm(company)">
                @if (company.estado === 'ACTIVA') {
                  Suspender
                } @else {
                  Activar
                }
              </button>
            </td>
          </tr>
        }
      </tbody>
    </ui-table>
  }
</main>

@if (formMode) {
  <ui-modal [title]="formMode === 'create' ? 'Nueva empresa' : 'Editar empresa'" (closed)="closeFormModal()">
    @if (formError) {
      <p class="mb-4 rounded-lg bg-badge-error-bg p-3 text-sm text-badge-error-ink" role="alert">{{ formError }}</p>
    }
    <app-company-form [mode]="formMode" [initialValue]="formInitialValue" (submitted)="handleFormSubmit($event)">
    </app-company-form>
  </ui-modal>
}

@if (createdModal) {
  <app-company-created-modal
    [adminEmail]="createdModal.adminEmail"
    [temporaryPassword]="createdModal.temporaryPassword"
    (closed)="closeCreatedModal()"
  ></app-company-created-modal>
}

@if (statusTarget) {
  <ui-modal title="Confirmar cambio de estado" (closed)="closeStatusConfirm()">
    @if (statusError) {
      <p class="mb-4 rounded-lg bg-badge-error-bg p-3 text-sm text-badge-error-ink" role="alert">{{ statusError }}</p>
    }
    <p class="text-sm text-brand-ink">
      @if (statusTarget.estado === 'ACTIVA') {
        ¿Confirmás suspender <strong>{{ statusTarget.nombre }}</strong>?
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

Run: `cd frontend && npx ng test --include='**/companies-list.component.spec.ts' --watch=false`
Expected: PASS, 6 tests.

- [ ] **Step 5: Commit**

```bash
cd frontend
git add src/app/features/companies/components/companies-list
git commit -m "feat: agregar pantalla de listado de companies con alta, edicion y suspension"
```

---

### Task 5: Routing — `/companies` route + Super-Admin home redirect

**Files:**
- Modify: `frontend/src/app/app.routes.ts`
- Modify: `frontend/src/app/core/home/home.component.ts`
- Test: `frontend/src/app/core/home/home.component.spec.ts` (new file)

**Interfaces:**
- Consumes: `CompaniesListComponent` (Task 4, lazy-loaded by path); `roleGuard` from `frontend/src/app/core/guards/role.guard.ts` (already exists, signature `roleGuard(rolesPermitidos: Rol[]): CanActivateFn` — it already redirects to `/login` if not authenticated, so no need to also add `authGuard` on this route).

- [ ] **Step 1: Write the failing test for the home redirect**

Create `frontend/src/app/core/home/home.component.spec.ts`:

```typescript
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';

import { AuthService } from '../services/auth.service';
import { HomeComponent } from './home.component';

describe('HomeComponent', () => {
  it('redirects a SUPER_ADMIN to /companies', () => {
    const routerStub = { navigate: vi.fn() };
    const authServiceStub = {
      getRole: () => 'SUPER_ADMIN',
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

    const fixture = TestBed.createComponent(HomeComponent);
    fixture.detectChanges();

    expect(routerStub.navigate).toHaveBeenCalledWith(['/companies']);
  });

  it('does not redirect a non-SUPER_ADMIN role', () => {
    const routerStub = { navigate: vi.fn() };
    const authServiceStub = {
      getRole: () => 'ADMIN',
      getCompanyId: () => 'company-1',
      logout: () => {},
    };

    TestBed.configureTestingModule({
      imports: [HomeComponent],
      providers: [
        { provide: AuthService, useValue: authServiceStub },
        { provide: Router, useValue: routerStub },
      ],
    });

    const fixture = TestBed.createComponent(HomeComponent);
    fixture.detectChanges();

    expect(routerStub.navigate).not.toHaveBeenCalled();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npx ng test --include='**/home.component.spec.ts' --watch=false`
Expected: FAIL — `expected "spy" to be called with [ '/companies' ]` (currently `HomeComponent` never calls `router.navigate` on init).

- [ ] **Step 3: Implement the redirect and the route**

Modify `frontend/src/app/core/home/home.component.ts` (full file):

```typescript
import { Component, OnInit, inject } from '@angular/core';
import { Router } from '@angular/router';

import { AuthService } from '../services/auth.service';

/**
 * Placeholder temporal de la ruta protegida raíz para roles sin feature
 * propia todavía. Super Admin redirige a /companies (FE-1.4); el resto de
 * los roles sigue viendo este placeholder hasta que existan sus features
 * (branches/employees en FE-1.5/1.6).
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
Expected: PASS, 2 tests.

- [ ] **Step 5: Commit**

```bash
cd frontend
git add src/app/app.routes.ts src/app/core/home/home.component.ts src/app/core/home/home.component.spec.ts
git commit -m "feat: agregar ruta companies y redirigir super admin desde home"
```

---

### Task 6: Full suite, build, and manual verification

**Files:** none created — verification only.

- [ ] **Step 1: Run the full test suite**

Run: `cd frontend && npx ng test --watch=false`
Expected: PASS, all specs green — the pre-existing suite on `main` (7 files, 32 tests, after FE-1.3 and the login migration follow-up) plus this feature's 5 new spec files (`company.service`: 4, `company-form`: 5, `company-created-modal`: 3, `companies-list`: 6, `home.component`: 2) = 20 new tests, 52 total across 12 files.

- [ ] **Step 2: Run a production build**

Run: `cd frontend && npx ng build`
Expected: exit code 0, no TypeScript or template errors.

- [ ] **Step 3: Insert a Super Admin test user and verify the screen end-to-end**

This mirrors the manual verification already done for FE-1.2 (real backend, not mocked): start the backend (`cd backend && ./mvnw spring-boot:run`, `dev` profile, H2 in memory), insert a `SUPER_ADMIN` user directly via `/h2-console` with a BCrypt hash generated on the spot (same mechanism used in FE-1.2's PR), then:

1. `cd frontend && npx ng serve`, log in as that Super Admin.
2. Confirm redirect from `/` to `/companies` happens automatically.
3. Confirm the table renders (or the empty state, if no companies exist yet).
4. Click "+ Nueva empresa", submit a valid company — confirm the created-password modal appears with the real `adminTemporaryPassword` from the backend response, and that the list refreshes to include it.
5. Click "Editar" on a row, change the name, save — confirm the table reflects the change.
6. Click "Suspender" on an active row — confirm the confirmation modal appears, confirm it, and confirm the badge updates to `SUSPENDIDA`. Repeat to activate it back.
7. Check the browser console and network tab for errors during the whole flow.

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

- **Spec coverage:** all 4 operations (listado, alta, edición, suspender/activar) are covered — Task 4 wires all four to `CompanyService`. The one-time password reveal (Task 3) and free-text país/moneda/zonaHoraria (Task 2, plain `ui-input` fields, no selector) both match the spec's explicit scope decisions. Routing (Task 5) matches the spec's "Ruteo" section exactly, including leaving other roles' home untouched.
- **Type consistency:** `CompanyFormValue` (Task 2) is defined once and consumed as-is by Task 4's `handleFormSubmit`. `Company`/`EstadoEmpresa`/`CreateCompanyRequest`/`UpdateCompanyRequest`/`UpdateCompanyStatusRequest`/`CreateCompanyResponse` (Task 1) are referenced identically across every later task — field names match the backend DTOs verbatim (`nombre`, `razonSocial`, `pais`, `moneda`, `zonaHoraria`, `plan`, `adminEmail`, `estado`, `fechaAlta`). `CompanyService`'s method names (`list`, `create`, `update`, `updateStatus`) match what Task 4's spec mocks and what Task 4's implementation calls.
- **No placeholders:** every step has complete, runnable code; no "add tests for the above" or "TBD" in any task.
