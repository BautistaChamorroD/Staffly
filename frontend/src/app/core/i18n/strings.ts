/**
 * Strings centralizados para internacionalización futura.
 *
 * CONVENCIÓN:
 * - Cada tipo de dominio tiene su propio Record<Tipo, string> con el label de display.
 * - Los componentes importan las constantes que necesitan y las exponen como
 *   `protected readonly LABELS = LABELS` para usarlas en el template.
 * - Para strings estáticos en templates, usar el atributo `i18n="@@scope.key"`.
 * - Para agregar un segundo idioma en el futuro:
 *   1. Crear un objeto equivalente con los valores en el nuevo idioma.
 *   2. Seleccionar el objeto según el locale activo.
 *   3. Extraer strings estáticos con `ng extract-i18n`.
 */

import type { Rol } from '../models/rol';
import type { EstadoLicencia } from '../../features/leaves/models/leave';
import type { EstadoAdelanto } from '../../features/advances/models/advance';
import type {
  EstadoRecibo,
  TipoRecibo,
} from '../../features/payslips/models/payslip';
import type {
  EstadoTurno,
  TipoTurno,
} from '../../features/schedules/models/schedule';
import type {
  EstadoLaboral,
  EstadoLiquidacion,
  TipoContrato,
} from '../../features/employees/models/employee';

export const ROL_LABELS: Record<Rol, string> = {
  SUPER_ADMIN: 'Super Admin',
  ADMIN: 'Administrador',
  RRHH: 'RRHH',
  SUPERVISOR: 'Supervisor',
  EMPLOYEE: 'Empleado',
};

export const ESTADO_LICENCIA_LABELS: Record<EstadoLicencia, string> = {
  PENDIENTE: 'Pendiente',
  APROBADA: 'Aprobada',
  RECHAZADA: 'Rechazada',
  CANCELADA: 'Cancelada',
};

export const ESTADO_ADELANTO_LABELS: Record<EstadoAdelanto, string> = {
  PENDIENTE: 'Pendiente',
  DESCONTADO: 'Descontado',
  CANCELADO: 'Cancelado',
};

export const ESTADO_RECIBO_LABELS: Record<EstadoRecibo, string> = {
  GENERADO: 'Generado',
  PAGADO: 'Pagado',
  ANULADO: 'Anulado',
  AJUSTE: 'Ajuste',
};

export const TIPO_RECIBO_LABELS: Record<TipoRecibo, string> = {
  NORMAL: 'Normal',
  AJUSTE: 'Ajuste',
};

export const ESTADO_TURNO_LABELS: Record<EstadoTurno, string> = {
  PLANIFICADO: 'Planificado',
  CONFIRMADO: 'Confirmado',
  CUMPLIDO: 'Cumplido',
  AUSENTE: 'Ausente',
};

export const TIPO_TURNO_LABELS: Record<TipoTurno, string> = {
  FIJO: 'Fijo',
  ROTATIVO: 'Rotativo',
};

export const ESTADO_LABORAL_LABELS: Record<EstadoLaboral, string> = {
  ACTIVO: 'Activo',
  LICENCIA: 'En licencia',
  SUSPENDIDO: 'Suspendido',
  BAJA: 'Baja',
};

export const ESTADO_LIQUIDACION_LABELS: Record<EstadoLiquidacion, string> = {
  AL_DIA: 'Al día',
  PENDIENTE: 'Pendiente',
};

export const TIPO_CONTRATO_LABELS: Record<TipoContrato, string> = {
  JORNADA_COMPLETA: 'Jornada completa',
  JORNADA_PARCIAL: 'Jornada parcial',
  POR_HORA: 'Por hora',
};
