ALTER TABLE leave_request ADD COLUMN aprobado_por UUID NULL;
ALTER TABLE leave_request ADD CONSTRAINT fk_leave_request_aprobado_por FOREIGN KEY (aprobado_por) REFERENCES app_user (id);
