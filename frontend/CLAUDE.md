# CLAUDE.md — Staffly Frontend

Este archivo es la guía de contexto para Claude Code (o cualquier agente) trabajando en este repositorio. Léelo completo antes de generar código.

## Qué es este proyecto

Frontend web del sistema de gestión de personal Staffly (multi-tenant SaaS). Consume la API REST del backend (`staffly-backend`) — el contrato completo de endpoints está en `docs/api-design.md`, el detalle funcional en `docs/requerimientos-sistema-gestion-personal.md`, y las decisiones de UX ya validadas (wireframes) en `docs/ux-decisions.md`. Consultalos antes de tomar decisiones de UI/UX, alcance, o integración con el backend. No agregues pantallas, funcionalidad, ni llamadas a endpoints inexistentes sin preguntar primero.

## Stack

| Capa | Tecnología |
|---|---|
| Framework | Angular (última versión estable) |
| Formularios | `ReactiveFormsModule` (`FormBuilder`, `FormGroup`, `FormControl`) — nunca template-driven forms |
| Estilos | Tailwind CSS |
| HTTP | `HttpClient` |
| Reactividad | RxJS |
| Componentes UI | **Propios, con Tailwind.** No usar Angular Material ni ninguna librería de componentes de terceros — decisión de producto para mantener identidad visual propia (ver sección 8 del documento de requerimientos, decisión #2). |
| Internacionalización | Preparar la estructura con archivos de traducción separados desde el día 1 (`@angular/localize` o similar), aunque solo se cargue español en v1. No hardcodear strings de UI directo en los templates. |

No propongas cambios de stack ni sumes librerías de componentes sin que se te pida explícitamente.

## Identidad visual

Producto a comercializar (no herramienta interna) — la UI debe tener identidad propia de marca, no un look genérico de dashboard. Antes de construir pantallas nuevas, definir/consultar tokens de diseño (colores, tipografía, espaciados) y mantenerlos consistentes en todo el sistema. Evitar defaults visuales que "se noten" como plantilla sin personalizar.

## Roles y qué ve cada uno

La UI debe reflejar el alcance de cada rol (definido en la sección 2 del documento de requerimientos) — no construir una sola pantalla "todo para todos" con elementos ocultos por CSS:

- **Admin/Dueño**: control total de su empresa (configuración, sucursales, roles, nómina, reportes).
- **RRHH/Encargado**: personal, horarios, disponibilidad, adelantos, liquidaciones. Sin acceso a configuración global de la empresa.
- **Supervisor**: horarios y disponibilidad de sus sucursales/área asignada únicamente.
- **Empleado**: **solo su propio registro** — su horario, su disponibilidad, su historial de pagos/adelantos, su recibo de sueldo. Nunca debe poder ver datos de otro empleado (RF-29 del documento de requerimientos) — esto aplica también en frontend: no traer ni renderizar datos de otros empleados aunque el backend los filtre, para no depender de una sola capa de defensa.

## Autenticación

JWT emitido por el backend (access token corto + refresh token). El `company_id` y `rol` del usuario vienen embebidos en el token — la UI debe leer el rol desde ahí para decidir qué mostrar, nunca asumirlo desde un parámetro propio o de la URL.

## Organización sugerida del código

Por **feature/módulo de dominio**, alineado 1 a 1 con los módulos del backend, para que sea fácil ubicar qué pantalla corresponde a qué entidad:

```
src/app
├── core/            → servicios transversales: auth, interceptor HTTP (JWT), guards de rol
├── shared/          → componentes UI propios reutilizables (botones, inputs, tablas, modales, cards)
├── features/
│   ├── companies/     → (solo Super Admin) alta/gestión de empresas
│   ├── branches/      → sucursales
│   ├── employees/     → empleados
│   ├── availability/  → disponibilidad
│   ├── schedules/      → horarios/turnos
│   ├── leaves/         → licencias y vacaciones
│   ├── payroll/        → configuración de nómina, períodos
│   ├── advances/        → adelantos
│   └── payslips/         → recibos de sueldo
```

No mezclar componentes de distintos features en `shared/` — ahí solo van componentes verdaderamente genéricos y reutilizables sin lógica de negocio de un módulo específico.

## Reglas de negocio que la UI debe reflejar (ya decididas, no reabrir)

- **Disponibilidad**: el empleado la carga y se guarda sin necesidad de aprobación — no armar un flujo de "pendiente de aprobación" para esto.
- **Turno fuera de disponibilidad declarada**: al asignar, mostrar una advertencia visual clara (no bloquear el guardado). El supervisor puede confirmar igual.
- **Solicitud de licencia/vacaciones**: sí requiere aprobación — la UI debe mostrar un estado claro (pendiente/aprobada/rechazada) y una pantalla de aprobación para Supervisor/RRHH.
- **Turnos que cruzan medianoche**: los formularios de horario deben permitir seleccionar fecha+hora de inicio y fecha+hora de fin de forma independiente (no asumir que el turno termina el mismo día).
- **Corrección de un recibo de sueldo ya pagado**: la UI nunca debe permitir editar un `Payslip` pagado directamente — el flujo correcto es anular + generar uno nuevo de ajuste (rol Admin únicamente).
- **Exportación**: priorizar PDF para recibos/reportes formales; Excel/CSV es de menor prioridad (fase de reportes).

## Comandos habituales

```bash
ng serve             # levantar en desarrollo
ng test              # correr tests
ng build --configuration production
```

## Orden de construcción sugerido (alineado al roadmap del backend)

1. **Fase 1**: `core` (auth, guards, interceptor JWT), `companies` (solo si aplica al rol logueado), `branches`, `employees`.
2. **Fase 2**: `availability`, `schedules`, `leaves`.
3. **Fase 3**: `payroll`, `advances`, `payslips`.
4. **Fase 4**: reportes.
5. **Fase 5**: pulido visual, i18n si aplica.

Si te piden construir una pantalla de una fase posterior sin que el backend correspondiente esté listo, señalalo antes de arrancar (puede requerir mockear la API temporalmente).

## Qué NO hacer sin preguntar

- No agregar Angular Material ni ninguna librería de componentes UI de terceros.
- No usar `localStorage`/`sessionStorage` para nada distinto a lo estrictamente necesario para el token — y evaluar el riesgo XSS antes de decidir dónde guardar el JWT.
- No construir pantallas o flujos de asistencia/fichaje, integraciones gubernamentales, ni billing del SaaS — fuera de alcance v1 (ver sección 10 del documento de requerimientos).
- No hardcodear textos de idioma directo en los componentes sin pasar por el mecanismo de traducción.

## Convención de commits

Este repo usa **Conventional Commits**, con mensajes **en español**: tipo + descripción breve en minúsculas.

Tipos permitidos: `feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:`.

Ejemplo: `feat: agregar endpoint de alta de empleado`.

El agente debe respetar esta convención en cada commit que haga a partir de ahora.
