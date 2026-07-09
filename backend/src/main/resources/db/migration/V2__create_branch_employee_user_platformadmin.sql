CREATE TABLE branch (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES company (id),
    nombre VARCHAR(255) NOT NULL,
    direccion VARCHAR(255) NOT NULL,
    zona_horaria VARCHAR(100) NOT NULL,
    estado VARCHAR(20) NOT NULL,
    horario_visible_inicio TIME,
    horario_visible_fin TIME
);

CREATE TABLE employee (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES company (id),
    nombre VARCHAR(255) NOT NULL,
    apellido VARCHAR(255) NOT NULL,
    documento VARCHAR(50) NOT NULL,
    fecha_nacimiento DATE NOT NULL,
    fecha_ingreso DATE NOT NULL,
    fecha_egreso DATE,
    tipo_contrato VARCHAR(30) NOT NULL,
    categoria VARCHAR(100) NOT NULL,
    sueldo_base NUMERIC(14, 2) NOT NULL,
    telefono VARCHAR(50),
    email_contacto VARCHAR(255),
    estado_laboral VARCHAR(20) NOT NULL,
    estado_liquidacion VARCHAR(20) NOT NULL
);

CREATE TABLE employee_branch (
    employee_id UUID NOT NULL REFERENCES employee (id),
    branch_id UUID NOT NULL REFERENCES branch (id),
    PRIMARY KEY (employee_id, branch_id)
);

CREATE TABLE app_user (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES company (id),
    employee_id UUID UNIQUE REFERENCES employee (id),
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    rol VARCHAR(20) NOT NULL,
    estado VARCHAR(20) NOT NULL,
    debe_cambiar_password BOOLEAN NOT NULL
);

CREATE TABLE user_branch (
    user_id UUID NOT NULL REFERENCES app_user (id),
    branch_id UUID NOT NULL REFERENCES branch (id),
    PRIMARY KEY (user_id, branch_id)
);

CREATE TABLE platform_admin (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    estado VARCHAR(20) NOT NULL
);
