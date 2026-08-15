import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ReportService } from '../../services/report.service';
import { PayrollCostRow } from '../../models/report';

@Component({
  selector: 'app-payroll-cost',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './payroll-cost.component.html',
})
export class PayrollCostComponent {
  // FE-4.3: implementar filtros, tabla y exportación
}
