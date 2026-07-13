# FE-1.3 — UI Kit base (`shared/`)

**Issue**: FE-1.3, rama `feature/ui-kit-base`. Depende de FE-1.1 (mergeado en `feature/angular-scaffold`). No depende de ningún backend.

## Contexto

`frontend/src/app/shared/` está vacío. Todas las pantallas siguientes (companies, branches, employees, y las de Fase 2+) van a construirse sobre estos componentes, así que quedan mal desde el día 1 si no arrancan con identidad visual propia — `frontend/CLAUDE.md` es explícito: "Antes de construir pantallas nuevas, definir/consultar tokens de diseño... Evitar defaults visuales que 'se noten' como plantilla sin personalizar." El login actual (`core/login/login.component.html`) usa gris/azul default de Tailwind — se migra a los componentes de este kit como parte de este cambio, para no dejar una pantalla con la identidad vieja conviviendo con las nuevas.

Fuente de la identidad visual: prototipo `Staffly.dc.html` (Claude Design, proyecto `a4a06e18-8e46-46b1-8d7f-2949cebf6e7f`), importado y confirmado con el usuario.

## Decisión de enfoque

El prototipo define la identidad con CSS custom properties (`--acc`, `--card`, `--ink`...) y clases propias (`.btn`, `.card`, `.tbl`...). El stack del proyecto prohíbe CSS a mano fuera de Tailwind (`frontend/CLAUDE.md` — "Componentes UI: Propios, con Tailwind"; skill `angular-frontend` — "Solo clases Tailwind. Cero CSS custom salvo excepción justificada"). Por eso los tokens del prototipo se portan a Tailwind 4 vía `@theme` (config CSS-first, sin `tailwind.config.js`), y los componentes se escriben con utilities de Tailwind puras — cero CSS propio en los componentes del kit.

Alternativa descartada: portar el prototipo casi 1:1 con hojas de estilo por componente (`.btn`, `.card` como clases CSS reales). Es más fiel al prototipo pero viola la convención "solo Tailwind" del proyecto sin necesidad — Tailwind 4 con `@theme` permite los mismos tokens (color, tipografía, radios) sin escribir CSS.

No se portan los temas alternativos del prototipo (`t-frutilla`, `t-mora` — variantes de acento por tenant). Es una feature de theming por tenant, fuera de alcance de este issue; el kit queda preparado para agregarla después (los tokens de color ya están centralizados en `@theme`, así que un futuro theming por tenant sería swap de esas variables, no reescritura de componentes).

## Tokens (`frontend/src/styles.css`)

Vía `@theme` de Tailwind 4, después de `@import 'tailwindcss'`:

**Colores** (tomados de las custom properties del tema base `.app` del prototipo):
| Token | Valor | Uso |
|---|---|---|
| `--color-brand-acc` | `#0e8f78` | acento primario (botón primario, focus, links) |
| `--color-brand-acc-dark` | `#0a6e5c` | hover de acento |
| `--color-brand-acc-soft` | `#e0f2ed` | fondos suaves de acento (nav activo, badge accent) |
| `--color-brand-acc-ink` | `#0a5c4e` | texto sobre fondo suave de acento |
| `--color-brand-bg` | `#f6f4f1` | fondo de página |
| `--color-brand-card` | `#fffdfa` | fondo de card/input/modal |
| `--color-brand-ink` | `#22201c` | texto principal |
| `--color-brand-muted` | `#8a8378` | texto secundario |
| `--color-brand-line` | `#e7e2da` | bordes |

**Semánticos de badge** (de `.b-ok/.b-warn/.b-err/.b-mut`):
| Token | bg | texto |
|---|---|---|
| `success` | `#e3f2e5` | `#2e6b39` |
| `warning` | `#fdf0dc` | `#96660f` |
| `error` | `#fbe7e8` | `#a63641` |
| `neutral` | `#efece6` | `#6d675d` |

(`accent` reusa `brand-acc-soft` / `brand-acc-ink` de la tabla de arriba.)

**Tipografía**: Google Fonts `Sora` (600/700) y `Albert Sans` (400/500/600/700 + italic 400), cargadas por `<link>` en `index.html` (igual que el prototipo). `--font-heading: 'Sora', sans-serif` para títulos; `--font-sans` override a `'Albert Sans', system-ui, sans-serif` como tipografía base del body.

**Radios**: se usan las utilities estándar de Tailwind ya alineadas al prototipo — `rounded-lg` (8px, botones/inputs), `rounded-xl` (12px, cards), `rounded-2xl` (16px, modal — el prototipo usa 14px, la diferencia de 2px no justifica un token custom), `rounded-full` (badges/avatares). No hace falta token custom, son valores default de Tailwind.

## Componentes (`frontend/src/app/shared/components/`)

Cada uno en su carpeta (`button/`, `input/`, `table/`, `card/`, `modal/`, `badge/`), standalone, sin lógica de negocio, siguiendo las reglas duras de `angular-frontend` (imports explícitos, sin `any`, ~150 líneas máx, `@if`/`@for` con `track`).

### `ui-button` (`shared/components/button/`)
- `@Input() variant: 'primary' | 'secondary' = 'primary'` — mapea a `.btn`/`.btn2` del prototipo (primary: fondo `brand-acc`, texto blanco; secondary: fondo `brand-card`, borde `brand-line`).
- `@Input() size: 'default' | 'sm' = 'default'`.
- Selector de atributo `button[ui-button]` (se aplica sobre un `<button>` nativo, no lo envuelve) para conservar semántica HTML nativa. `disabled`, `type` y los eventos de click quedan siendo los del elemento real — no se duplican como `@Input()` propios: el consumidor los setea nativos (`<button ui-button type="submit" [disabled]="isLoading">`), evitando que la directiva compita con el binding nativo del mismo atributo.

### `ui-input` (`shared/components/input/`)
- Implementa `ControlValueAccessor` (+ `NG_VALUE_ACCESSOR` provider) para usarse directo con `formControlName` en Reactive Forms, consistente con el resto del proyecto.
- `@Input() label: string`, `@Input() type: string = 'text'`, `@Input() placeholder?`, `@Input() errorMessage?: string`.
- Label con `for`/`id` asociado (regla dura: todo input con label). Si `errorMessage` está presente, se muestra con `role="alert"` y el input toma borde de error (mismo patrón que ya usa `login.component.html`).

### `ui-table` (`shared/components/table/`)
- Shell presentacional: `<div class="overflow-x-auto rounded-xl border border-brand-line">` + `<table class="w-full text-sm">` + `<ng-content>`.
- No es un data-grid genérico configurable por `@Input()` de columnas — las tablas reales (empleados, horarios, licencias) tienen celdas muy distintas (avatares, badges, acciones) y forzar una API genérica ahora es especular sobre necesidades que todavía no existen (YAGNI). Cada feature arma su propio `<thead>/<tbody>` con `@for`/`track` proyectado adentro, con utilities Tailwind directas para header (uppercase, `text-brand-muted`) y filas (`border-b`, hover).

### `ui-card` (`shared/components/card/`)
- `@Input() title?: string`.
- `<ng-content>` para el cuerpo. Si `title` está seteado, renderiza `<h3>` con `font-heading`.

### `ui-modal` (`shared/components/modal/`)
- `@Input() title?: string`, `@Output() closed = new EventEmitter<void>()`.
- Presentacional puro: no maneja su propia visibilidad — el padre lo renderiza condicionalmente con `@if`, igual que el prototipo (`sc-if value="{{modalX}}"`). Mientras esté en el DOM, se muestra.
- Cierra emitiendo `closed`: click en backdrop, botón de cerrar, y tecla `Escape` (`HostListener` o binding de `(keydown.escape)` en el host).
- `<ng-content>` para el cuerpo/acciones.

### `ui-badge` (`shared/components/badge/`)
- `@Input() variant: 'success' | 'warning' | 'error' | 'neutral' | 'accent' = 'neutral'`.
- `<ng-content>` para el texto. Pill (`rounded-full`), padding chico, texto 11px/600 igual al prototipo.

## Rama base y migración del login (nota de dependencia)

`feature/ui-kit-base` sale de `main` (regla del roadmap: todas las ramas salen de `main`). Hoy `main` solo tiene FE-1.1 mergeado — FE-1.2 (`feature/core-auth`, `core/login/`) todavía tiene el PR pendiente. Por eso la migración de `login.component.html` a los componentes del kit **no** es parte de este plan: `core/login/` no existe todavía en la base de esta rama, y además el roadmap le asigna a FE-1.3 el alcance de archivos `frontend/shared/` únicamente. Queda como follow-up de una línea (cambiar 3 tags en el template) una vez que FE-1.2 esté mergeado a `main` — no bloquea ni necesita re-diseño, los componentes del kit ya están pensados para ese reemplazo directo (`ui-card` envolviendo el form, `ui-input` con `formControlName`, `button[ui-button]` en el submit).

## Fuera de alcance

- Theming por tenant (colores alternativos `t-frutilla`/`t-mora` del prototipo).
- Data-grid genérico con columnas configurables — se resuelve por feature cuando haga falta.
- i18n de los textos de estos componentes (labels los define quien consume el componente) — Fase 5 (FE-5.2).
- Componentes que no están en el prototipo ni en el alcance de FE-1.3 (dropdown, tabs, tooltip, etc.) — se agregan cuando una pantalla los necesite.

## Testing

Cada componente lleva su `.spec.ts` (`ng test`) cubriendo: render de variantes/inputs, emisión de outputs (`ui-modal` cierra con backdrop/Escape/botón), y que `ui-input` propague valor y `touched` correctamente como `ControlValueAccessor`. No hace falta test e2e para este issue — no hay flujo de negocio todavía, son componentes de presentación.
