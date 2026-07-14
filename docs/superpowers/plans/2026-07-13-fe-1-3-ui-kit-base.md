# FE-1.3 — UI Kit base (`shared/`) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the 6 base UI components (button, input, table, card, modal, badge) for `frontend/src/app/shared/`, driven by design tokens ported from the Staffly design prototype, so every screen from FE-1.4 onward has a consistent, non-generic visual identity.

**Architecture:** Angular 21 standalone components/directive under `shared/components/<name>/`, styled exclusively with Tailwind 4 utility classes that reference brand tokens defined once via `@theme` in `styles.css`. No custom CSS in components. `ui-input` implements `ControlValueAccessor` so it drops into existing Reactive Forms (`formControlName`) without adapters.

**Tech Stack:** Angular 21.2 (standalone, decorator-based `@Input`/`@Output`, no signals), Tailwind CSS 4 (CSS-first `@theme` config), `@angular/forms` (`ControlValueAccessor`), Vitest via `@angular/build:unit-test` (`ng test`, Jasmine-style `describe`/`it`/`expect` API through `TestBed`).

**Reference:** Spec at `docs/superpowers/specs/2026-07-13-fe-1-3-ui-kit-base-design.md`.

## Global Constraints

- Standalone components only, no NgModules (`angular-frontend` skill).
- No signals — local state is plain class properties (`angular-frontend` skill).
- `@Input()`/`@Output()` decorators, never `input()`/`output()` functions (`angular-frontend` skill).
- `inject()` for DI inside the class body (`angular-frontend` skill).
- Control flow: `@if`/`@for` with `track`, never `*ngIf`/`*ngFor` (`angular-frontend` skill).
- Styles: Tailwind utility classes only. Zero `style=""` inline, zero custom CSS (`frontend/CLAUDE.md`).
- Zero `any` without a justifying comment; every `<img>` has `alt`; every input has an associated `<label>` or `aria-label`; max ~150 lines per file (`angular-frontend` skill).
- `shared/` holds only generic, business-logic-free components (`frontend/CLAUDE.md`).
- Commits: Conventional Commits, messages in Spanish, lowercase, no trailing period. Allowed types: `feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:` (`frontend/CLAUDE.md`).
- Branch: `feature/ui-kit-base`, already created from `main` with the spec doc committed. This plan's commits land on top of it.
- Login migration is explicitly OUT of scope for this plan (see spec's "Rama base y migración del login" section) — `core/login/` doesn't exist on this branch yet.

---

## File Structure

```
frontend/src/
├── index.html                                    [MODIFY] Google Fonts links
├── styles.css                                     [MODIFY] @theme tokens
└── app/shared/components/
    ├── badge/
    │   ├── badge.component.ts
    │   ├── badge.component.html
    │   └── badge.component.spec.ts
    ├── card/
    │   ├── card.component.ts
    │   ├── card.component.html
    │   └── card.component.spec.ts
    ├── button/
    │   ├── button.directive.ts
    │   └── button.directive.spec.ts
    ├── input/
    │   ├── input.component.ts
    │   ├── input.component.html
    │   └── input.component.spec.ts
    ├── table/
    │   ├── table.component.ts
    │   ├── table.component.html
    │   └── table.component.spec.ts
    └── modal/
        ├── modal.component.ts
        ├── modal.component.html
        └── modal.component.spec.ts
```

Each component lives in its own folder (established pattern: `core/login/login.component.ts`). Task order: tokens first (everything depends on them), then components with no internal dependencies (badge, card), then button (needed by modal), then input, table, modal last (it composes `ui-button`).

---

### Task 1: Design tokens (fonts + Tailwind `@theme`)

**Files:**
- Modify: `frontend/src/index.html`
- Modify: `frontend/src/styles.css`

**Interfaces:**
- Produces: Tailwind color utilities `bg-brand-acc`, `bg-brand-acc-dark`, `bg-brand-acc-soft`, `text-brand-acc-ink`, `bg-brand-bg`, `bg-brand-card`, `text-brand-ink`, `text-brand-muted`, `border-brand-line`, `bg-badge-success-bg`, `text-badge-success-ink`, `bg-badge-warning-bg`, `text-badge-warning-ink`, `bg-badge-error-bg`, `text-badge-error-ink`, `bg-badge-neutral-bg`, `text-badge-neutral-ink`, plus `font-heading` (Sora) and default `font-sans` (Albert Sans). All later tasks consume these class names.

This task has no unit-testable logic (it's build config), so it's verified by a successful production build and by confirming the generated CSS contains the token values.

- [ ] **Step 1: Add Google Fonts links to `index.html`**

Replace the `<head>` block in `frontend/src/index.html`:

```html
<!doctype html>
<html lang="en">
  <head>
    <meta charset="utf-8" />
    <title>StafflyFrontend</title>
    <base href="/" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <link rel="icon" type="image/x-icon" href="favicon.ico" />
    <link rel="preconnect" href="https://fonts.googleapis.com" />
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin />
    <link
      rel="stylesheet"
      href="https://fonts.googleapis.com/css2?family=Sora:wght@600;700&family=Albert+Sans:ital,wght@0,400;0,500;0,600;0,700;1,400&display=swap"
    />
  </head>
  <body>
    <app-root></app-root>
  </body>
</html>
```

- [ ] **Step 2: Add the `@theme` token block to `styles.css`**

Replace the full contents of `frontend/src/styles.css`:

```css
@import 'tailwindcss';

@theme {
  --color-brand-acc: #0e8f78;
  --color-brand-acc-dark: #0a6e5c;
  --color-brand-acc-soft: #e0f2ed;
  --color-brand-acc-ink: #0a5c4e;
  --color-brand-bg: #f6f4f1;
  --color-brand-card: #fffdfa;
  --color-brand-ink: #22201c;
  --color-brand-muted: #8a8378;
  --color-brand-line: #e7e2da;

  --color-badge-success-bg: #e3f2e5;
  --color-badge-success-ink: #2e6b39;
  --color-badge-warning-bg: #fdf0dc;
  --color-badge-warning-ink: #96660f;
  --color-badge-error-bg: #fbe7e8;
  --color-badge-error-ink: #a63641;
  --color-badge-neutral-bg: #efece6;
  --color-badge-neutral-ink: #6d675d;

  --font-heading: 'Sora', sans-serif;
  --font-sans: 'Albert Sans', system-ui, sans-serif;
}

body {
  background-color: var(--color-brand-bg);
  color: var(--color-brand-ink);
  font-family: var(--font-sans);
}
```

- [ ] **Step 3: Verify the build picks up the tokens**

Run: `cd frontend && npx ng build`
Expected: exit code 0, no PostCSS/Tailwind errors.

Then confirm the token made it into the compiled CSS:

Run: `cd frontend && rg "0e8f78" dist -l`
Expected: prints at least one path under `dist/` (the compiled stylesheet).

- [ ] **Step 4: Commit**

```bash
cd frontend
git add src/index.html src/styles.css
git commit -m "feat: agregar tokens de diseño de marca (colores, tipografia) via tailwind theme"
```

---

### Task 2: `ui-badge`

**Files:**
- Create: `frontend/src/app/shared/components/badge/badge.component.ts`
- Create: `frontend/src/app/shared/components/badge/badge.component.html`
- Test: `frontend/src/app/shared/components/badge/badge.component.spec.ts`

**Interfaces:**
- Consumes: Tailwind tokens from Task 1 (`bg-badge-*-bg`, `text-badge-*-ink`, `bg-brand-acc-soft`, `text-brand-acc-ink`).
- Produces: `BadgeComponent` (selector `ui-badge`), `@Input() variant: 'success' | 'warning' | 'error' | 'neutral' | 'accent' = 'neutral'`, exported type `BadgeVariant`. Consumed via `<ui-badge variant="success">texto</ui-badge>`.

- [ ] **Step 1: Write the failing test**

Create `frontend/src/app/shared/components/badge/badge.component.spec.ts`:

```typescript
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { BadgeComponent, BadgeVariant } from './badge.component';

@Component({
  standalone: true,
  imports: [BadgeComponent],
  template: `<ui-badge [variant]="variant">{{ label }}</ui-badge>`,
})
class HostComponent {
  variant: BadgeVariant = 'neutral';
  label = 'Activo';
}

describe('BadgeComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
  });

  it('renders projected content', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const span = fixture.nativeElement.querySelector('span') as HTMLElement;
    expect(span.textContent?.trim()).toBe('Activo');
  });

  it('applies neutral classes by default', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const span = fixture.nativeElement.querySelector('span') as HTMLElement;
    expect(span.className).toContain('bg-badge-neutral-bg');
  });

  it('applies success classes when variant is success', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.componentInstance.variant = 'success';
    fixture.detectChanges();
    const span = fixture.nativeElement.querySelector('span') as HTMLElement;
    expect(span.className).toContain('bg-badge-success-bg');
  });

  it('applies accent classes using the brand accent-soft tokens', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.componentInstance.variant = 'accent';
    fixture.detectChanges();
    const span = fixture.nativeElement.querySelector('span') as HTMLElement;
    expect(span.className).toContain('bg-brand-acc-soft');
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npx ng test --include='**/badge.component.spec.ts' --watch=false`
Expected: FAIL — `Cannot find module './badge.component'` (file doesn't exist yet).

- [ ] **Step 3: Write minimal implementation**

Create `frontend/src/app/shared/components/badge/badge.component.ts`:

```typescript
import { Component, Input } from '@angular/core';

export type BadgeVariant = 'success' | 'warning' | 'error' | 'neutral' | 'accent';

@Component({
  selector: 'ui-badge',
  standalone: true,
  templateUrl: './badge.component.html',
})
export class BadgeComponent {
  @Input() variant: BadgeVariant = 'neutral';

  get variantClasses(): string {
    switch (this.variant) {
      case 'success':
        return 'bg-badge-success-bg text-badge-success-ink';
      case 'warning':
        return 'bg-badge-warning-bg text-badge-warning-ink';
      case 'error':
        return 'bg-badge-error-bg text-badge-error-ink';
      case 'accent':
        return 'bg-brand-acc-soft text-brand-acc-ink';
      case 'neutral':
        return 'bg-badge-neutral-bg text-badge-neutral-ink';
    }
  }
}
```

Create `frontend/src/app/shared/components/badge/badge.component.html`:

```html
<span class="inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[11px] font-semibold {{ variantClasses }}">
  <ng-content></ng-content>
</span>
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npx ng test --include='**/badge.component.spec.ts' --watch=false`
Expected: PASS, 4 tests.

- [ ] **Step 5: Commit**

```bash
cd frontend
git add src/app/shared/components/badge
git commit -m "feat: agregar componente ui-badge al kit de shared"
```

---

### Task 3: `ui-card`

**Files:**
- Create: `frontend/src/app/shared/components/card/card.component.ts`
- Create: `frontend/src/app/shared/components/card/card.component.html`
- Test: `frontend/src/app/shared/components/card/card.component.spec.ts`

**Interfaces:**
- Consumes: Tailwind tokens from Task 1 (`border-brand-line`, `bg-brand-card`, `font-heading`).
- Produces: `CardComponent` (selector `ui-card`), `@Input() title?: string`. Card styling (`rounded-xl border ...`) lives on the component's own host element (via `host: { class: ... }`), so callers can add layout classes directly on the `<ui-card>` tag (e.g. `<ui-card class="w-full max-w-sm">`) and they merge with the host's own classes.

- [ ] **Step 1: Write the failing test**

Create `frontend/src/app/shared/components/card/card.component.spec.ts`:

```typescript
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { CardComponent } from './card.component';

@Component({
  standalone: true,
  imports: [CardComponent],
  template: `<ui-card [title]="title">{{ body }}</ui-card>`,
})
class HostComponent {
  title?: string = 'Datos laborales';
  body = 'contenido de la card';
}

describe('CardComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
  });

  it('renders the title in an h3 when provided', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const h3 = fixture.nativeElement.querySelector('h3') as HTMLElement;
    expect(h3.textContent?.trim()).toBe('Datos laborales');
  });

  it('does not render an h3 when title is not provided', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.componentInstance.title = undefined;
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('h3')).toBeNull();
  });

  it('projects the body content', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('contenido de la card');
  });

  it('applies card surface classes to its own host element', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const host = fixture.nativeElement.querySelector('ui-card') as HTMLElement;
    expect(host.className).toContain('rounded-xl');
    expect(host.className).toContain('bg-brand-card');
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npx ng test --include='**/card.component.spec.ts' --watch=false`
Expected: FAIL — `Cannot find module './card.component'`.

- [ ] **Step 3: Write minimal implementation**

Create `frontend/src/app/shared/components/card/card.component.ts`:

```typescript
import { Component, Input } from '@angular/core';

@Component({
  selector: 'ui-card',
  standalone: true,
  templateUrl: './card.component.html',
  host: {
    class: 'block rounded-xl border border-brand-line bg-brand-card p-5',
  },
})
export class CardComponent {
  @Input() title?: string;
}
```

Create `frontend/src/app/shared/components/card/card.component.html`:

```html
@if (title) {
  <h3 class="mb-3 font-heading text-[13.5px] font-semibold">{{ title }}</h3>
}
<ng-content></ng-content>
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npx ng test --include='**/card.component.spec.ts' --watch=false`
Expected: PASS, 4 tests.

- [ ] **Step 5: Commit**

```bash
cd frontend
git add src/app/shared/components/card
git commit -m "feat: agregar componente ui-card al kit de shared"
```

---

### Task 4: `ui-button` directive

**Files:**
- Create: `frontend/src/app/shared/components/button/button.directive.ts`
- Test: `frontend/src/app/shared/components/button/button.directive.spec.ts`

**Interfaces:**
- Consumes: Tailwind tokens from Task 1 (`bg-brand-acc`, `bg-brand-acc-dark`, `border-brand-line`, `bg-brand-card`, `text-brand-ink`).
- Produces: `ButtonDirective` (selector `button[ui-button]`), `@Input() variant: 'primary' | 'secondary' = 'primary'`, `@Input() size: 'default' | 'sm' = 'default'`, exported types `ButtonVariant`/`ButtonSize`. Applied directly on a native `<button>` — `disabled`, `type`, and click events stay native (no wrapper inputs). Task 7 (`ui-modal`) imports this directive to style its close button.

- [ ] **Step 1: Write the failing test**

Create `frontend/src/app/shared/components/button/button.directive.spec.ts`:

```typescript
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { ButtonDirective, ButtonSize, ButtonVariant } from './button.directive';

@Component({
  standalone: true,
  imports: [ButtonDirective],
  template: `<button ui-button [variant]="variant" [size]="size" [disabled]="isDisabled">Enviar</button>`,
})
class HostComponent {
  variant: ButtonVariant = 'primary';
  size: ButtonSize = 'default';
  isDisabled = false;
}

describe('ButtonDirective', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
  });

  it('applies primary variant classes by default', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const button = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
    expect(button.className).toContain('bg-brand-acc');
  });

  it('applies secondary variant classes when variant is secondary', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.componentInstance.variant = 'secondary';
    fixture.detectChanges();
    const button = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
    expect(button.className).toContain('border-brand-line');
  });

  it('applies smaller padding classes when size is sm', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.componentInstance.size = 'sm';
    fixture.detectChanges();
    const button = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
    expect(button.className).toContain('text-xs');
  });

  it('leaves the native disabled attribute under the caller\'s control', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.componentInstance.isDisabled = true;
    fixture.detectChanges();
    const button = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
    expect(button.disabled).toBe(true);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npx ng test --include='**/button.directive.spec.ts' --watch=false`
Expected: FAIL — `Cannot find module './button.directive'`.

- [ ] **Step 3: Write minimal implementation**

Create `frontend/src/app/shared/components/button/button.directive.ts`:

```typescript
import { Directive, HostBinding, Input } from '@angular/core';

export type ButtonVariant = 'primary' | 'secondary';
export type ButtonSize = 'default' | 'sm';

@Directive({
  selector: 'button[ui-button]',
  standalone: true,
})
export class ButtonDirective {
  @Input() variant: ButtonVariant = 'primary';
  @Input() size: ButtonSize = 'default';

  @HostBinding('class')
  get hostClasses(): string {
    const base = 'rounded-lg font-semibold disabled:opacity-50 disabled:cursor-not-allowed';
    const variantClasses =
      this.variant === 'secondary'
        ? 'border border-brand-line bg-brand-card text-brand-ink hover:border-brand-muted'
        : 'bg-brand-acc text-white hover:bg-brand-acc-dark';
    const sizeClasses = this.size === 'sm' ? 'px-2.5 py-1 text-xs' : 'px-3.5 py-2 text-sm';
    return `${base} ${variantClasses} ${sizeClasses}`;
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npx ng test --include='**/button.directive.spec.ts' --watch=false`
Expected: PASS, 4 tests.

- [ ] **Step 5: Commit**

```bash
cd frontend
git add src/app/shared/components/button
git commit -m "feat: agregar directiva ui-button al kit de shared"
```

---

### Task 5: `ui-input`

**Files:**
- Create: `frontend/src/app/shared/components/input/input.component.ts`
- Create: `frontend/src/app/shared/components/input/input.component.html`
- Test: `frontend/src/app/shared/components/input/input.component.spec.ts`

**Interfaces:**
- Consumes: Tailwind tokens from Task 1 (`border-brand-line`, `text-brand-ink`, `text-badge-error-ink`).
- Produces: `InputComponent` (selector `ui-input`), implements `ControlValueAccessor`, `@Input() label = ''`, `@Input() type = 'text'`, `@Input() placeholder = ''`, `@Input() errorMessage?: string`. Consumed as `<ui-input formControlName="email" label="Email"></ui-input>` inside a `[formGroup]`.

- [ ] **Step 1: Write the failing test**

Create `frontend/src/app/shared/components/input/input.component.spec.ts`:

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
      <ui-input formControlName="email" label="Email" [errorMessage]="error"></ui-input>
    </form>
  `,
})
class HostComponent {
  form = new FormGroup({ email: new FormControl('') });
  error?: string;
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
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npx ng test --include='**/input.component.spec.ts' --watch=false`
Expected: FAIL — `Cannot find module './input.component'`.

- [ ] **Step 3: Write minimal implementation**

Create `frontend/src/app/shared/components/input/input.component.ts`:

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

Create `frontend/src/app/shared/components/input/input.component.html`:

```html
<div>
  <label [for]="id" class="mb-1 block text-sm font-medium text-brand-ink">{{ label }}</label>
  <input
    [id]="id"
    [type]="type"
    [placeholder]="placeholder"
    [value]="value"
    [disabled]="disabled"
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

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npx ng test --include='**/input.component.spec.ts' --watch=false`
Expected: PASS, 5 tests.

- [ ] **Step 5: Commit**

```bash
cd frontend
git add src/app/shared/components/input
git commit -m "feat: agregar componente ui-input (controlvalueaccessor) al kit de shared"
```

---

### Task 6: `ui-table`

**Files:**
- Create: `frontend/src/app/shared/components/table/table.component.ts`
- Create: `frontend/src/app/shared/components/table/table.component.html`
- Test: `frontend/src/app/shared/components/table/table.component.spec.ts`

**Interfaces:**
- Consumes: Tailwind tokens from Task 1 (`border-brand-line`, `bg-brand-card`).
- Produces: `TableComponent` (selector `ui-table`), no inputs — pure content-projection shell. Consumed as `<ui-table><thead>...</thead><tbody>...</tbody></ui-table>`, with the caller responsible for `<tr>/<th>/<td>` markup and its own Tailwind classes on those cells.

- [ ] **Step 1: Write the failing test**

Create `frontend/src/app/shared/components/table/table.component.spec.ts`:

```typescript
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { TableComponent } from './table.component';

@Component({
  standalone: true,
  imports: [TableComponent],
  template: `
    <ui-table>
      <thead>
        <tr><th>Nombre</th></tr>
      </thead>
      <tbody>
        <tr><td>Ana Gomez</td></tr>
      </tbody>
    </ui-table>
  `,
})
class HostComponent {}

describe('TableComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
  });

  it('projects the caller\'s thead and tbody inside a native table element', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const table = fixture.nativeElement.querySelector('table') as HTMLTableElement;
    expect(table.querySelector('th')?.textContent).toBe('Nombre');
    expect(table.querySelector('td')?.textContent).toBe('Ana Gomez');
  });

  it('wraps the table in a scrollable, bordered container', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const wrapper = fixture.nativeElement.querySelector('div') as HTMLElement;
    expect(wrapper.className).toContain('overflow-x-auto');
    expect(wrapper.className).toContain('border-brand-line');
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npx ng test --include='**/table.component.spec.ts' --watch=false`
Expected: FAIL — `Cannot find module './table.component'`.

- [ ] **Step 3: Write minimal implementation**

Create `frontend/src/app/shared/components/table/table.component.ts`:

```typescript
import { Component } from '@angular/core';

@Component({
  selector: 'ui-table',
  standalone: true,
  templateUrl: './table.component.html',
})
export class TableComponent {}
```

Create `frontend/src/app/shared/components/table/table.component.html`:

```html
<div class="overflow-x-auto rounded-xl border border-brand-line bg-brand-card">
  <table class="w-full text-sm">
    <ng-content></ng-content>
  </table>
</div>
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npx ng test --include='**/table.component.spec.ts' --watch=false`
Expected: PASS, 2 tests.

- [ ] **Step 5: Commit**

```bash
cd frontend
git add src/app/shared/components/table
git commit -m "feat: agregar componente ui-table al kit de shared"
```

---

### Task 7: `ui-modal`

**Files:**
- Create: `frontend/src/app/shared/components/modal/modal.component.ts`
- Create: `frontend/src/app/shared/components/modal/modal.component.html`
- Test: `frontend/src/app/shared/components/modal/modal.component.spec.ts`

**Interfaces:**
- Consumes: `ButtonDirective` from Task 4 (for the close button), Tailwind tokens from Task 1 (`bg-brand-ink/40`, `bg-brand-card`, `font-heading`).
- Produces: `ModalComponent` (selector `ui-modal`), `@Input() title?: string`, `@Output() closed = new EventEmitter<void>()`. Purely presentational — visibility is controlled by the parent's `@if`, matching every other modal in the design prototype.

- [ ] **Step 1: Write the failing test**

Create `frontend/src/app/shared/components/modal/modal.component.spec.ts`:

```typescript
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { ModalComponent } from './modal.component';

@Component({
  standalone: true,
  imports: [ModalComponent],
  template: `
    <ui-modal title="Confirmar" (closed)="onClosed()">
      <p>Cuerpo del modal</p>
    </ui-modal>
  `,
})
class HostComponent {
  closedCount = 0;

  onClosed(): void {
    this.closedCount++;
  }
}

describe('ModalComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
  });

  it('renders the title and projected body', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('h3')?.textContent).toBe('Confirmar');
    expect(fixture.nativeElement.textContent).toContain('Cuerpo del modal');
  });

  it('emits closed when the backdrop is clicked', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const backdrop = fixture.nativeElement.querySelector('[data-testid="modal-backdrop"]') as HTMLElement;
    backdrop.click();
    expect(fixture.componentInstance.closedCount).toBe(1);
  });

  it('does not emit closed when the dialog body is clicked', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const dialog = fixture.nativeElement.querySelector('[data-testid="modal-dialog"]') as HTMLElement;
    dialog.click();
    expect(fixture.componentInstance.closedCount).toBe(0);
  });

  it('emits closed when the close button is clicked', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const closeButton = fixture.nativeElement.querySelector('[aria-label="Cerrar"]') as HTMLButtonElement;
    closeButton.click();
    expect(fixture.componentInstance.closedCount).toBe(1);
  });

  it('emits closed when Escape is pressed', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    expect(fixture.componentInstance.closedCount).toBe(1);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npx ng test --include='**/modal.component.spec.ts' --watch=false`
Expected: FAIL — `Cannot find module './modal.component'`.

- [ ] **Step 3: Write minimal implementation**

Create `frontend/src/app/shared/components/modal/modal.component.ts`:

```typescript
import { Component, EventEmitter, HostListener, Input, Output } from '@angular/core';

import { ButtonDirective } from '../button/button.directive';

@Component({
  selector: 'ui-modal',
  standalone: true,
  imports: [ButtonDirective],
  templateUrl: './modal.component.html',
})
export class ModalComponent {
  @Input() title?: string;
  @Output() closed = new EventEmitter<void>();

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.closed.emit();
  }

  onBackdropClick(): void {
    this.closed.emit();
  }

  stopPropagation(event: Event): void {
    event.stopPropagation();
  }
}
```

Create `frontend/src/app/shared/components/modal/modal.component.html`:

```html
<div
  data-testid="modal-backdrop"
  class="fixed inset-0 z-50 flex items-center justify-center bg-brand-ink/40 p-5"
  (click)="onBackdropClick()"
>
  <div
    data-testid="modal-dialog"
    class="w-full max-w-md rounded-2xl bg-brand-card p-6 shadow-xl"
    (click)="stopPropagation($event)"
  >
    <div class="mb-4 flex items-center justify-between gap-4">
      @if (title) {
        <h3 class="font-heading text-base font-semibold">{{ title }}</h3>
      }
      <button ui-button variant="secondary" size="sm" type="button" aria-label="Cerrar" (click)="closed.emit()">
        ✕
      </button>
    </div>
    <ng-content></ng-content>
  </div>
</div>
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npx ng test --include='**/modal.component.spec.ts' --watch=false`
Expected: PASS, 5 tests.

- [ ] **Step 5: Commit**

```bash
cd frontend
git add src/app/shared/components/modal
git commit -m "feat: agregar componente ui-modal al kit de shared"
```

---

### Task 8: Full suite, lint, and build verification

**Files:** none created — verification only.

- [ ] **Step 1: Run the full test suite**

Run: `cd frontend && npx ng test --watch=false`
Expected: PASS, all specs green (`app.spec.ts`'s 2 tests + the 6 new spec files' 4+4+4+5+2+5 = 24 tests, 26 total).

- [ ] **Step 2: Run a production build**

Run: `cd frontend && npx ng build`
Expected: exit code 0, no TypeScript or template errors.

- [ ] **Step 3: Manual visual check**

Run: `cd frontend && npx ng serve`, open `http://localhost:4200`. The default app shell won't show the new components yet (nothing routes to them until a feature screen uses them) — this step is a smoke check that the app still boots cleanly with the new tokens/fonts loaded (inspect via browser devtools that `--color-brand-acc` resolves and Sora/Albert Sans fonts load in the Network tab), not a full visual QA. Stop the server after confirming.

- [ ] **Step 4: Final commit if any lint/format fixes were needed**

If `ng build` or manual review surfaced formatting issues, run `npx prettier --write src/app/shared` and commit:

```bash
cd frontend
git add src/app/shared
git commit -m "chore: aplicar formato de prettier al kit de shared"
```

If nothing needed fixing, skip this commit — the branch is ready for PR.

---

## Self-Review Notes

- **Spec coverage:** all 6 components (Task 2–7), tokens (Task 1), and the "fuera de alcance" login migration is explicitly excluded per the spec's branch-dependency note — nothing from the spec's in-scope sections is missing a task.
- **Type consistency:** `BadgeVariant` (Task 2), `ButtonVariant`/`ButtonSize` (Task 4) are each defined once and only referenced (not redefined) by later tasks/tests. `ButtonDirective` selector `button[ui-button]` matches its usage in Task 7's modal template exactly.
- **No placeholders:** every step has complete, runnable code — no "add tests for the above" or "TBD" left in any task.
