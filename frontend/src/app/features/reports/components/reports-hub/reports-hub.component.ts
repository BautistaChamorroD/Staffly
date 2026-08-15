import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

interface ReportCard {
  title: string;
  description: string;
  route: string;
}

@Component({
  selector: 'app-reports-hub',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './reports-hub.component.html',
})
export class ReportsHubComponent {
  readonly reportCards: ReportCard[] = [
    {
      title: 'Horas trabajadas',
      description: 'Horas normales, extra y feriado por empleado y sucursal en un período.',
      route: '/reports/hours-worked',
    },
    {
      title: 'Costo de nómina',
      description: 'Bruto, descuentos y neto total por sucursal y período de liquidación.',
      route: '/reports/payroll-cost',
    },
    {
      title: 'Adelantos pendientes',
      description: 'Adelantos otorgados aún no descontados en un recibo de sueldo.',
      route: '/reports/pending-advances',
    },
  ];
}
