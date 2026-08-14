import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../../environments/environment';
import { LeaveType } from '../models/leave-type';

@Injectable({ providedIn: 'root' })
export class LeaveTypeService {
  private http = inject(HttpClient);
  private baseUrl = `${environment.apiUrl}/leave-types`;

  list(): Observable<LeaveType[]> {
    return this.http.get<LeaveType[]>(this.baseUrl);
  }
}
