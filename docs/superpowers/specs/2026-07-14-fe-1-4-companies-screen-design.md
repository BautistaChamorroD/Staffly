# FE-1.4 — `features/companies` (solo Super Admin)

**Issue**: [#16](https://github.com/BautistaChamorroD/Staffly/issues/16), rama `feature/companies-screen`. Depende de FE-1.2 (mergeado), FE-1.3 (mergeado) y BE-1.3 (mergeado).

## Contexto

Primera feature real del frontend — hasta ahora `core/home/home.component.ts` es un placeholder explícito ("se reemplaza por un dashboard real cuando existan features"). El roadmap acota FE-1.4 a "alta y listado de empresas", pero el backend (BE-1.3, ya mergeado) expone también edición (`PATCH /companies/{id}`) y cambio de estado (`PATCH /companies/{id}/status`). Confirmado con el usuario: este issue incluye las 4 operaciones (listado, alta, edición, suspender/activar), no solo las 2 del roadmap original.

## Contrato del backend (`backend/src/main/java/com/staffly/backend/company/`)

Todos los endpoints bajo `@PreAuthorize("hasRole('SUPER_ADMIN')")`, prefijo `/api/v1/companies`:

| Método | Path | Request | Response |
|---|---|---|---|
| GET | `/companies` | — | `CompanyResponse[]` |
| GET | `/companies/{id}` | — | `CompanyResponse` |
| POST | `/companies` | `CreateCompanyRequest` | `201` `CreateCompanyResponse` |
| PATCH | `/companies/{id}` | `UpdateCompanyRequest` | `CompanyResponse` |
| PATCH | `/companies/{id}/status` | `UpdateCompanyStatusRequest` | `CompanyResponse` |

```java
record CompanyResponse(UUID id, String nombre, String razonSocial, String pais, String moneda,
                        String zonaHoraria, EstadoEmpresa estado, String plan, Instant fechaAlta)

enum EstadoEmpresa { ACTIVA, SUSPENDIDA }

record CreateCompanyRequest(@NotBlank String nombre, @NotBlank String razonSocial, @NotBlank String pais,
                             @NotBlank String moneda, @NotBlank String zonaHoraria, String plan,
                             @NotBlank @Email String adminEmail)

record CreateCompanyResponse(CompanyResponse company, String adminEmail, String adminTemporaryPassword)

// Actualización parcial: nulls se dejan sin tocar. Sin adminEmail (no editable acá).
record UpdateCompanyRequest(String nombre, String razonSocial, String pais, String moneda,
                             String zonaHoraria, String plan)

record UpdateCompanyStatusRequest(@NotNull EstadoEmpresa estado)
```

`pais`/`moneda`/`zonaHoraria` son `String` libres en el backend, sin enum ni catálogo — no hay nada que validar contra una lista cerrada.

## Decisión de alcance: país/moneda/zona horaria como texto libre

El sistema tiene hoy un solo cliente real (una heladería en Argentina) y el backend no valida estos campos contra ningún catálogo. Construir un selector de países/monedas/zonas horarias sería resolver un problema que todavía no existe (YAGNI) — se resuelve como texto libre por ahora. Si en el futuro se necesita un catálogo real, es un cambio acotado a `company-form` sin tocar el resto de la feature.

## Decisión de alcance: revelado único de la contraseña provisoria

`CreateCompanyResponse.adminTemporaryPassword` es la contraseña provisoria del primer `User` `ADMIN` de la empresa recién creada (RF-01). Es una única oportunidad de verla en texto plano — se muestra en un modal dedicado inmediatamente después del alta exitosa, con botón de copiar y aviso explícito de que no se vuelve a mostrar. No se persiste en ningún estado del frontend más allá de ese modal (se descarta al cerrarlo).

## Ruteo

- Nueva ruta `/companies` en `app.routes.ts`, `canActivate: [roleGuard(['SUPER_ADMIN'])]`, lazy vía `loadComponent`.
- `core/home/home.component.ts`: si `rol === 'SUPER_ADMIN'`, redirige a `/companies` en el constructor/`ngOnInit` (vía `Router.navigate`). Otros roles siguen viendo el placeholder actual — no se toca su comportamiento, porque sus features (branches/employees) todavía no existen.

## Estructura de archivos

```
frontend/src/app/features/companies/
├── models/
│   └── company.ts                  → Company, EstadoEmpresa, CreateCompanyRequest, CreateCompanyResponse,
│                                       UpdateCompanyRequest, UpdateCompanyStatusRequest (reflejan los DTOs de arriba 1:1)
├── services/
│   └── company.service.ts          → list(), create(), update(), updateStatus() vía HttpClient
└── components/
    ├── companies-list/
    │   ├── companies-list.component.ts/.html/.spec.ts    → smart: fetch inicial, estado de qué modal está abierto,
    │   │                                                     maneja las 4 acciones, arma la tabla con ui-table
    │   │                                                     + ui-badge por estado
    ├── company-form/
    │   ├── company-form.component.ts/.html/.spec.ts       → form reactivo (nombre, razonSocial, pais, moneda,
    │   │                                                     zonaHoraria, plan, adminEmail), @Input() mode:
    │   │                                                     'create' | 'edit', @Input() initialValue?: Company,
    │   │                                                     @Output() submitted: EventEmitter<FormValue>.
    │   │                                                     En modo 'edit' no renderiza el campo adminEmail.
    └── company-created-modal/
        ├── company-created-modal.component.ts/.html/.spec.ts  → presentacional puro: @Input() adminEmail,
                                                                    @Input() temporaryPassword, @Output() closed.
                                                                    Botón "Copiar" (Clipboard API), sin persistir nada.
```

El modal de confirmación de suspender/activar y el estado de carga/error de la tabla viven directo en `companies-list` (contenido simple, no ameritan su propio componente).

## Interacción — `companies-list`

- Al entrar: `company.service.list()`, muestra estado de carga; si falla, banner de error (mismo patrón que login: `bg-badge-error-bg`/`text-badge-error-ink`).
- Tabla vacía (sin empresas): mensaje simple en vez de tabla, sin diseño especial adicional.
- Botón "+ Nueva empresa" → abre `ui-modal` con `company-form` en modo `create`. Al emitir `submitted`, llama `company.service.create()`; si OK, cierra el modal de alta y abre `company-created-modal` con `adminEmail`/`adminTemporaryPassword` de la respuesta, y refresca la lista. Si falla, banner de error dentro del modal de alta (no lo cierra).
- Acción "Editar" por fila → abre `ui-modal` con `company-form` en modo `edit`, precargado. Al emitir `submitted`, llama `company.service.update()`; si OK, cierra y refresca la fila/lista; si falla, banner de error dentro del modal.
- Acción "Suspender"/"Activar" por fila (label según el estado actual) → abre `ui-modal` de confirmación simple ("¿Confirmás suspender/activar `{{nombre}}`?", botones Cancelar/Confirmar). Al confirmar, llama `company.service.updateStatus()`; si OK, cierra y refresca; si falla, banner de error dentro del modal.

## Fuera de alcance

- Selector de países/monedas/zonas horarias con catálogo — texto libre por ahora (ver decisión arriba).
- Paginación/búsqueda/filtros en la tabla — v1 asume pocas empresas (un solo cliente real hoy); se agrega cuando el volumen lo justifique.
- Historial de auditoría de cambios sobre `Company` — no está en el modelo de esta fase (`AuditLog` completo es Fase 5).
- Dashboard real para otros roles — el placeholder de `home` para `ADMIN`/`RRHH`/`SUPERVISOR`/`EMPLOYEE` no se toca en este issue.

## Testing

- `company.service.spec.ts`: los 4 métodos contra `HttpClientTestingModule`, verificando método HTTP, URL y body.
- `company-form.component.spec.ts`: validación de campos requeridos, que en modo `edit` no se renderiza `adminEmail`, que `submitted` emite el valor correcto del form.
- `company-created-modal.component.spec.ts`: renderiza email/password recibidos, `closed` se emite correctamente, copiar interactúa con el clipboard (mockeado).
- `companies-list.component.spec.ts`: carga inicial (loading → datos), error de carga, flujo de alta completo (abre modal → submit → cierra → abre modal de contraseña → refresca lista), flujo de edición, flujo de suspender/activar con confirmación — todo contra un `CompanyService` mockeado (no HTTP real), verificando comportamiento observable (qué modal está abierto, qué se llama), no implementación interna.
- No hace falta test e2e — se verifica manualmente en el navegador como parte del plan (login real de un `SUPER_ADMIN` no existe todavía en este punto del roadmap del backend más allá de datos insertados a mano; se puede verificar con datos de prueba insertados en H2, mismo mecanismo usado para verificar FE-1.2).
