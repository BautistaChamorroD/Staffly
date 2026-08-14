import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../../environments/environment';
import { LeaveRequest, LeaveRequestFilters } from '../models/leave-request';

@Injectable({ providedIn: 'root' })
export class LeaveRequestService {
  private http = inject(HttpClient);
  private baseUrl = `${environment.apiUrl}/leave-requests`;

  list(filters: LeaveRequestFilters = {}): Observable<LeaveRequest[]> {
    let params = new HttpParams();
    if (filters.employeeId) params = params.set('employeeId', filters.employeeId);
    if (filters.estado) params = params.set('estado', filters.estado);
    return this.http.get<LeaveRequest[]>(this.baseUrl, { params });
  }
}
