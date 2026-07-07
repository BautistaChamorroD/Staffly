# ERD — Staffly (modelo de datos v1.4)

Diagrama entidad-relación correspondiente a la versión 1.4 del documento de requerimientos. Ver `requerimientos-sistema-gestion-personal.md` para el detalle de cada entidad.

```mermaid
erDiagram
  COMPANY ||--o{ BRANCH : tiene
  COMPANY ||--o{ EMPLOYEE : emplea
  COMPANY ||--o{ USER : registra
  COMPANY ||--|| PAYROLL_CONFIG : configura
  COMPANY ||--o{ PAYROLL_PERIOD : abre
  COMPANY ||--o{ HOLIDAY : define
  COMPANY ||--o{ LEAVE_TYPE : define
  BRANCH ||--o{ EMPLOYEE : asigna
  BRANCH ||--o{ SCHEDULE : programa
  BRANCH ||--o{ HOLIDAY : aplica
  EMPLOYEE ||--o| USER : accede
  EMPLOYEE ||--o{ AVAILABILITY : declara
  EMPLOYEE ||--o{ SCHEDULE : cumple
  EMPLOYEE ||--o{ ADVANCE : recibe
  EMPLOYEE ||--o{ PAYSLIP : recibe
  EMPLOYEE ||--o{ LEAVE_REQUEST : solicita
  LEAVE_TYPE ||--o{ LEAVE_REQUEST : clasifica
  PAYROLL_PERIOD ||--o{ PAYSLIP : genera
  ADVANCE }o--|| PAYROLL_PERIOD : imputa
  PAYSLIP |o--o| PAYSLIP : ajusta
  USER ||--o{ AUDIT_LOG : genera

  COMPANY {
    uuid id PK
    string nombre
    string razon_social
    string pais
    string moneda
    string zona_horaria
    string estado
    timestamp fecha_alta
  }
  BRANCH {
    uuid id PK
    uuid company_id FK
    string nombre
    string direccion
    string zona_horaria
    string estado
  }
  USER {
    uuid id PK
    uuid company_id FK
    uuid employee_id FK
    string email
    string password_hash
    string rol
    string estado
    boolean debe_cambiar_password
  }
  EMPLOYEE {
    uuid id PK
    uuid company_id FK
    string nombre
    string apellido
    string documento
    date fecha_nacimiento
    date fecha_ingreso
    date fecha_egreso
    string tipo_contrato
    string categoria
    decimal sueldo_base
    string estado_laboral
    string estado_liquidacion
  }
  AVAILABILITY {
    uuid id PK
    uuid employee_id FK
    string dia_semana
    time hora_inicio
    time hora_fin
    string tipo
  }
  SCHEDULE {
    uuid id PK
    uuid employee_id FK
    uuid branch_id FK
    timestamp fecha_hora_inicio
    timestamp fecha_hora_fin
    string tipo_turno
    string estado
  }
  HOLIDAY {
    uuid id PK
    uuid company_id FK
    uuid branch_id FK
    date fecha
    string nombre
    boolean recurrente
  }
  LEAVE_TYPE {
    uuid id PK
    uuid company_id FK
    string nombre
    boolean es_paga
    boolean tiene_cupo_anual
    int cupo_dias_anual
  }
  LEAVE_REQUEST {
    uuid id PK
    uuid employee_id FK
    uuid leave_type_id FK
    date fecha_inicio
    date fecha_fin
    string motivo
    string estado
    uuid aprobado_por
  }
  PAYROLL_CONFIG {
    uuid id PK
    uuid company_id FK
    decimal umbral_hora_extra
    decimal valor_hora_extra
    string periodicidad
  }
  PAYROLL_PERIOD {
    uuid id PK
    uuid company_id FK
    date fecha_inicio
    date fecha_fin
    string estado
  }
  ADVANCE {
    uuid id PK
    uuid employee_id FK
    uuid payroll_period_id FK
    date fecha
    decimal monto
    string motivo
    string estado
  }
  PAYSLIP {
    uuid id PK
    uuid employee_id FK
    uuid payroll_period_id FK
    uuid payslip_original_id FK
    decimal total_bruto
    decimal total_neto
    string estado
    timestamp fecha_pago
  }
  AUDIT_LOG {
    uuid id PK
    uuid company_id FK
    uuid user_id FK
    string entidad
    uuid entidad_id
    string campo
    string valor_anterior
    string valor_nuevo
    timestamp fecha
  }
```
