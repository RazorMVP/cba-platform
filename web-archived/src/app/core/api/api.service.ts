import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import { ApiResponse, PageResponse } from '../models/api-response.model';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  get<T>(path: string, params?: Record<string, string | number>): Observable<T> {
    let httpParams = new HttpParams();
    if (params) {
      Object.entries(params).forEach(([k, v]) => httpParams = httpParams.set(k, String(v)));
    }
    return this.http.get<ApiResponse<T>>(`${this.base}${path}`, { params: httpParams })
      .pipe(map(r => r.data));
  }

  getPage<T>(path: string, page = 0, size = 20, params?: Record<string, string>): Observable<PageResponse<T>> {
    let httpParams = new HttpParams().set('page', page).set('size', size);
    if (params) {
      Object.entries(params).forEach(([k, v]) => httpParams = httpParams.set(k, v));
    }
    return this.http.get<ApiResponse<PageResponse<T>>>(`${this.base}${path}`, { params: httpParams })
      .pipe(map(r => r.data));
  }

  post<T>(path: string, body: unknown): Observable<T> {
    return this.http.post<ApiResponse<T>>(`${this.base}${path}`, body)
      .pipe(map(r => r.data));
  }

  put<T>(path: string, body: unknown): Observable<T> {
    return this.http.put<ApiResponse<T>>(`${this.base}${path}`, body)
      .pipe(map(r => r.data));
  }

  delete<T>(path: string): Observable<T> {
    return this.http.delete<ApiResponse<T>>(`${this.base}${path}`)
      .pipe(map(r => r.data));
  }

  postParams<T>(path: string, params: Record<string, string>): Observable<T> {
    let httpParams = new HttpParams();
    Object.entries(params).forEach(([k, v]) => httpParams = httpParams.set(k, v));
    return this.http.post<ApiResponse<T>>(`${this.base}${path}`, {}, { params: httpParams })
      .pipe(map(r => r.data));
  }

  putParams<T>(path: string, params: Record<string, string>): Observable<T> {
    let httpParams = new HttpParams();
    Object.entries(params).forEach(([k, v]) => httpParams = httpParams.set(k, v));
    return this.http.put<ApiResponse<T>>(`${this.base}${path}`, {}, { params: httpParams })
      .pipe(map(r => r.data));
  }

  command<T>(path: string, command: string, body?: unknown): Observable<T> {
    return this.http.post<ApiResponse<T>>(`${this.base}${path}?command=${command}`, body ?? {})
      .pipe(map(r => r.data));
  }
}
