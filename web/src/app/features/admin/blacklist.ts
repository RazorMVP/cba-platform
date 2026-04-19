import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged, switchMap, takeUntil } from 'rxjs/operators';
import { AdminService, BlacklistEntry } from './admin.service';

@Component({
  selector: 'app-blacklist',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './blacklist.html',
  styleUrl: './blacklist.scss',
})
export class BlacklistComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();
  private search$ = new Subject<string>();

  entries: BlacklistEntry[] = [];
  total = 0;
  page = 0;
  pageSize = 20;
  loading = false;

  filterEntityType = '';
  filterActive: string = '';
  searchQuery = '';

  showAddModal = false;
  showEditModal = false;
  showDeactivateConfirm = false;
  selected: BlacklistEntry | null = null;

  form = { entityType: 'CUSTOMER', entityValue: '', reason: '', source: 'INTERNAL', expiresAt: '', addedBy: '' };
  editReason = '';
  editExpiresAt = '';

  readonly entityTypes = ['CUSTOMER', 'ACCOUNT_NUMBER', 'NATIONAL_ID', 'NAME', 'PHONE', 'EMAIL', 'IP_ADDRESS'];
  readonly sources = ['INTERNAL', 'OFAC', 'UN', 'EU', 'LOCAL_PEP'];

  constructor(private svc: AdminService) {}

  ngOnInit() {
    this.loadEntries();
    this.search$.pipe(
      debounceTime(300), distinctUntilChanged(),
      switchMap(q => this.svc.searchBlacklist(q)),
      takeUntil(this.destroy$)
    ).subscribe(r => { this.entries = r; this.total = r.length; });
  }
  ngOnDestroy() { this.destroy$.next(); this.destroy$.complete(); }

  loadEntries() {
    this.loading = true;
    const active = this.filterActive === '' ? undefined : this.filterActive === 'true';
    this.svc.listBlacklist(this.filterEntityType || undefined, active, this.page, this.pageSize)
      .pipe(takeUntil(this.destroy$))
      .subscribe({ next: r => { this.entries = r.content; this.total = r.totalElements; this.loading = false; },
                   error: () => { this.loading = false; } });
  }

  onSearch(q: string) { if (q.length >= 2) { this.search$.next(q); } else if (q.length === 0) { this.loadEntries(); } }
  applyFilters() { this.page = 0; this.loadEntries(); }
  clearFilters() { this.filterEntityType = ''; this.filterActive = ''; this.searchQuery = ''; this.applyFilters(); }
  prevPage() { if (this.page > 0) { this.page--; this.loadEntries(); } }
  nextPage() { if ((this.page + 1) * this.pageSize < this.total) { this.page++; this.loadEntries(); } }

  openAdd() {
    this.form = { entityType: 'CUSTOMER', entityValue: '', reason: '', source: 'INTERNAL', expiresAt: '', addedBy: '' };
    this.showAddModal = true;
  }
  confirmAdd() {
    this.svc.addBlacklistEntry(this.form)
      .subscribe({ next: () => { this.showAddModal = false; this.loadEntries(); } });
  }

  openEdit(e: BlacklistEntry) {
    this.selected = e; this.editReason = e.reason ?? ''; this.editExpiresAt = e.expiresAt ?? '';
    this.showEditModal = true;
  }
  confirmEdit() {
    if (!this.selected) return;
    this.svc.updateBlacklistEntry(this.selected.id, this.editReason, this.editExpiresAt || undefined)
      .subscribe({ next: () => { this.showEditModal = false; this.loadEntries(); } });
  }

  openDeactivate(e: BlacklistEntry) { this.selected = e; this.showDeactivateConfirm = true; }
  confirmDeactivate() {
    if (!this.selected) return;
    this.svc.deactivateBlacklistEntry(this.selected.id)
      .subscribe({ next: () => { this.showDeactivateConfirm = false; this.loadEntries(); } });
  }

  totalPages() { return Math.ceil(this.total / this.pageSize); }
}
