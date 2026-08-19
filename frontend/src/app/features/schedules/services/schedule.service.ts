import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { environment } from '../../../../environments/environment';
import {
  CreateScheduleRequest,
  DuplicateWeeklyRequest,
  DuplicateWeeklyResponse,
  Schedule,
  ScheduleFilters,
  UpdateScheduleRequest,
  UpdateStatusRequest,
} from '../models/schedule';

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

  update(id: string, request: UpdateScheduleRequest): Observable<Schedule> {
    return this.http.patch<Schedule>(`${this.baseUrl}/${id}`, request);
  }

  confirm(id: string): Observable<Schedule> {
    return this.http.post<Schedule>(`${this.baseUrl}/${id}/confirm`, null);
  }

  updateStatus(id: string, request: UpdateStatusRequest): Observable<Schedule> {
    return this.http.patch<Schedule>(`${this.baseUrl}/${id}/status`, request);
  }

  duplicateWeekly(id: string, request: DuplicateWeeklyRequest): Observable<DuplicateWeeklyResponse> {
    return this.http.post<DuplicateWeeklyResponse>(`${this.baseUrl}/${id}/duplicate-weekly`, request);
  }
}
