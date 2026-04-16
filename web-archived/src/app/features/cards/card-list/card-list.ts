import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { CardsService, Card, CardType, CardStatus, IssueCardRequest } from '../cards.service';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge';

@Component({
  selector: 'app-card-list',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule, StatusBadgeComponent],
  templateUrl: './card-list.html',
  styleUrl: './card-list.scss',
})
export class CardListComponent implements OnInit {
  private readonly svc = inject(CardsService);

  cards: Card[] = [];
  filtered: Card[] = [];
  loading = true;
  searchQuery = '';
  statusFilter: CardStatus | '' = '';
  typeFilter: CardType | '' = '';

  showIssueModal = false;
  issueForm: IssueCardRequest = { customerId: '', productId: '', linkedEntityId: '', virtual: false };

  readonly statuses: CardStatus[] = ['ACTIVE', 'BLOCKED', 'ORDERED', 'PRODUCED', 'DISPATCHED', 'ACTIVATION_PENDING', 'EXPIRED', 'CANCELLED'];
  readonly types: CardType[] = ['DEBIT', 'PREPAID', 'CREDIT'];

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.svc.listCards().subscribe({
      next: (cards) => { this.cards = cards; this.applyFilter(); this.loading = false; },
      error: () => { this.loading = false; },
    });
  }

  applyFilter(): void {
    this.filtered = this.cards.filter(c => {
      const q = this.searchQuery.toLowerCase();
      const matchQuery = !q || c.panSuffix.includes(q) || c.customerId.toLowerCase().includes(q) || c.productName?.toLowerCase().includes(q);
      const matchStatus = !this.statusFilter || c.status === this.statusFilter;
      const matchType   = !this.typeFilter   || c.cardType === this.typeFilter;
      return matchQuery && matchStatus && matchType;
    });
  }

  onSearch(q: string): void { this.searchQuery = q; this.applyFilter(); }

  openIssue(): void { this.showIssueModal = true; }
  closeIssue(): void { this.showIssueModal = false; this.issueForm = { customerId: '', productId: '', linkedEntityId: '', virtual: false }; }

  submitIssue(): void {
    this.svc.issueCard(this.issueForm).subscribe({ next: () => { this.closeIssue(); this.load(); } });
  }

  statusVariant(s: CardStatus): 'success' | 'warning' | 'error' | 'info' | 'neutral' | 'primary' {
    const map: Record<string, 'success' | 'warning' | 'error' | 'info' | 'neutral'> = {
      ACTIVE: 'success', BLOCKED: 'error', EXPIRED: 'neutral', CANCELLED: 'neutral',
      ORDERED: 'info', PRODUCED: 'info', DISPATCHED: 'warning', ACTIVATION_PENDING: 'warning',
    };
    return map[s] ?? 'neutral';
  }
}
