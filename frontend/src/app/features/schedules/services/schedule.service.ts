import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../../environments/environment';
import { CreateScheduleRequest, Schedule, ScheduleFilters } from '../models/schedule';

@Injectable({ providedIn: 'root' })
export class ScheduleService {
  private http = inject(HttpClient);
  private baseUrl = `${environment.apiUrl}/schedules`;

  list(filters: ScheduleFilters = {}): Observable<Schedule[]> {
    let params = new HttpParams();
    if (filters.branchId) params = params.set('branchId', filters.branchId);
    if (filters.employeeId) params = params.set('employeeId', filters.employeeId);
    if (filters.desde) params = params.set('desde', filters.desde);
    if (filters.hasta) params = params.set('hasta', filters.hasta);
    return this.http.get<Schedule[]>(this.baseUrl, { params });
  }

  create(request: CreateScheduleRequest): Observable<Schedule> {
    return this.http.post<Schedule>(this.baseUrl, request);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
