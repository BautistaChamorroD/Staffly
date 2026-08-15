import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

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
    return this.http.get<Schedule[] | { content: Schedule[] }>(this.baseUrl, { params }).pipe(
      map((r) => (Array.isArray(r) ? r : r.content)),
    );
  }

  create(request: CreateScheduleRequest): Observable<Schedule> {
    return this.http.post<Schedule>(this.baseUrl, request);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
