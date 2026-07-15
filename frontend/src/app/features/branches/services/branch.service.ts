import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../../environments/environment';
import {
  Branch,
  CreateBranchRequest,
  UpdateBranchRequest,
  UpdateBranchStatusRequest,
} from '../models/branch';

@Injectable({ providedIn: 'root' })
export class BranchService {
  private http = inject(HttpClient);
  private baseUrl = `${environment.apiUrl}/branches`;

  list(): Observable<Branch[]> {
    return this.http.get<Branch[]>(this.baseUrl);
  }

  create(request: CreateBranchRequest): Observable<Branch> {
    return this.http.post<Branch>(this.baseUrl, request);
  }

  update(id: string, request: UpdateBranchRequest): Observable<Branch> {
    return this.http.patch<Branch>(`${this.baseUrl}/${id}`, request);
  }

  updateStatus(id: string, request: UpdateBranchStatusRequest): Observable<Branch> {
    return this.http.patch<Branch>(`${this.baseUrl}/${id}/status`, request);
  }
}
