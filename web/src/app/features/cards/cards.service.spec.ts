import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { CardsService } from './cards.service';
import { ApiResponse } from '../../core/models/api-response.model';

/**
 * CardsService talks to card-service directly (not via ApiService) across TWO base
 * URLs — `/api/v1` (internal) and `/card-api/v1` (BaaS). The high-value assertions
 * here are which base each call uses (a documented footgun: `listCards`, limits,
 * authorizations, API keys and webhooks must hit `/card-api/v1`), and that
 * `disputeCommand` uses path-segment routing, not `?command=`.
 */
describe('CardsService', () => {
  const BASE = 'http://localhost:8081/api/v1';
  const CARDAPI = 'http://localhost:8081/card-api/v1';
  let service: CardsService;
  let httpMock: HttpTestingController;

  function envelope<T>(data: T): ApiResponse<T> {
    return { data, meta: {}, errors: [] };
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [CardsService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(CardsService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('listProducts hits the internal /api/v1 base', () => {
    service.listProducts().subscribe();
    const req = httpMock.expectOne(`${BASE}/cards/products`);
    expect(req.request.method).toBe('GET');
    req.flush(envelope([]));
  });

  it('listCards hits the /card-api/v1 base (NOT /api/v1)', () => {
    service.listCards({ customerId: 'c1' }).subscribe();
    const req = httpMock.expectOne(r => r.url === `${CARDAPI}/cards`);
    expect(req.request.params.get('customerId')).toBe('c1');
    req.flush(envelope([]));
  });

  it('getCard / issueCard use the internal base', () => {
    service.getCard('cd1').subscribe();
    httpMock.expectOne(`${BASE}/cards/cd1`).flush(envelope({}));
    service.issueCard({ customerId: 'c1', productId: 'p1', linkedEntityId: 'a1', virtual: true }).subscribe();
    const req = httpMock.expectOne(`${BASE}/cards`);
    expect(req.request.method).toBe('POST');
    req.flush(envelope({}));
  });

  it('commandCard appends ?command= against the internal base', () => {
    service.commandCard('cd1', 'block').subscribe();
    const req = httpMock.expectOne(`${BASE}/cards/cd1?command=block`);
    expect(req.request.method).toBe('POST');
    req.flush(envelope({}));
  });

  it('card limits + authorizations use the /card-api/v1 base', () => {
    service.getCardLimits('cd1').subscribe();
    httpMock.expectOne(`${CARDAPI}/cards/cd1/limits`).flush(envelope({}));
    service.updateCardLimits('cd1', {
      dailyPurchaseLimit: 1, dailyWithdrawalLimit: 1, perTxnLimit: 1, monthlyLimit: 1,
    }).subscribe();
    const put = httpMock.expectOne(`${CARDAPI}/cards/cd1/limits`);
    expect(put.request.method).toBe('PUT');
    put.flush(envelope({}));
    service.listAuthorizations('cd1').subscribe();
    httpMock.expectOne(`${CARDAPI}/cards/cd1/authorizations`).flush(envelope([]));
  });

  it('disputeCommand uses path-segment routing, not ?command=', () => {
    service.disputeCommand('d1', 'chargeback', { reasonCodeId: 'rc1' }).subscribe();
    const req = httpMock.expectOne(`${BASE}/cards/disputes/d1/chargeback`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ reasonCodeId: 'rc1' });
    req.flush(envelope({}));
  });

  it('resolveDispute posts to the resolve sub-path', () => {
    service.resolveDispute('d1', { resolvedBy: 'u1', resolutionFavor: 'ISSUER', notes: 'ok' }).subscribe();
    httpMock.expectOne(`${BASE}/cards/disputes/d1/resolve`).flush(envelope({}));
  });

  it('listDisputes adds a status filter only when given', () => {
    service.listDisputes('RAISED').subscribe();
    const req = httpMock.expectOne(r => r.url === `${BASE}/cards/disputes`);
    expect(req.request.params.get('status')).toBe('RAISED');
    req.flush(envelope([]));
  });

  it('API keys + webhooks use the /card-api/v1 base', () => {
    service.listApiKeys().subscribe();
    httpMock.expectOne(`${CARDAPI}/api-keys`).flush(envelope([]));
    service.issueApiKey({ name: 'k', scopes: [], tier: 'BASIC' }).subscribe();
    const post = httpMock.expectOne(`${CARDAPI}/api-keys`);
    expect(post.request.method).toBe('POST');
    post.flush(envelope({}));
    service.revokeApiKey('k1').subscribe();
    const del = httpMock.expectOne(`${CARDAPI}/api-keys/k1`);
    expect(del.request.method).toBe('DELETE');
    del.flush(envelope({}));
    service.listWebhooks().subscribe();
    httpMock.expectOne(`${CARDAPI}/webhooks`).flush(envelope([]));
  });

  it('settlement, BIN, interchange, simulator use the internal base', () => {
    service.listBatches().subscribe();
    httpMock.expectOne(`${BASE}/cards/settlement/batches`).flush(envelope([]));
    service.triggerExport('b1').subscribe();
    httpMock.expectOne(`${BASE}/cards/settlement/export/b1`).flush(envelope(null));
    service.listBins().subscribe();
    httpMock.expectOne(`${BASE}/bins`).flush(envelope([]));
    service.listRates().subscribe();
    httpMock.expectOne(`${BASE}/interchange/rates`).flush(envelope([]));
    service.simulatePurchase({
      cardNumber: '4', expiryDate: '2612', amount: 10, currency: '840',
      terminalId: 't', merchantId: 'm', merchantName: 'M', entryMode: 'CHIP',
    }).subscribe();
    httpMock.expectOne(`${BASE}/simulate/purchase`).flush(envelope({}));
  });
});
