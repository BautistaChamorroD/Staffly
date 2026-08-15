import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ReportService } from '../../services/report.service';
import { HoursWorkedRow } from '../../models/report';

@Component({
  selector: 'app-hours-worked',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './hours-worked.component.html',
})
export class HoursWorkedComponent {
  // FE-4.2: implementar filtros, tabla y exportación
}
