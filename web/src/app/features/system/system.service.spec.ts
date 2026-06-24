import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { ApiService } from '../../core/api/api.service';
import { SystemService } from './system.service';

describe('SystemService', () => {
  let service: SystemService;
  let api: Record<'get' | 'getPage' | 'post' | 'put' | 'delete', ReturnType<typeof vi.fn>>;

  beforeEach(() => {
    api = {
      get: vi.fn().mockReturnValue(of([])),
      getPage: vi.fn().mockReturnValue(of({ content: [] })),
      post: vi.fn().mockReturnValue(of({})),
      put: vi.fn().mockReturnValue(of({})),
      delete: vi.fn().mockReturnValue(of({})),
    };
    TestBed.configureTestingModule({
      providers: [SystemService, { provide: ApiService, useValue: api }],
    });
    service = TestBed.inject(SystemService);
  });

  it('codes + nested code values route correctly', () => {
    service.listCodes().subscribe();
    expect(api.get).toHaveBeenCalledWith('/codes');
    service.listCodeValues('cd1').subscribe();
    expect(api.get).toHaveBeenCalledWith('/codes/cd1/codevalues');
    service.createCodeValue('cd1', { name: 'V', description: 'd', position: 1 }).subscribe();
    expect(api.post).toHaveBeenCalledWith('/codes/cd1/codevalues', { name: 'V', description: 'd', position: 1 });
    service.deleteCodeValue('cd1', 'v1').subscribe();
    expect(api.delete).toHaveBeenCalledWith('/codes/cd1/codevalues/v1');
  });

  it('global configuration list + update', () => {
    service.listConfigurations().subscribe();
    expect(api.get).toHaveBeenCalledWith('/configurations');
    service.updateConfiguration('cfg1', { enabled: true }).subscribe();
    expect(api.put).toHaveBeenCalledWith('/configurations/cfg1', { enabled: true });
  });

  it('holidays page + activate via sub-path (not ?command=)', () => {
    service.listHolidays(1).subscribe();
    expect(api.getPage).toHaveBeenCalledWith('/holidays', 1, 20);
    service.activateHoliday('h1').subscribe();
    expect(api.post).toHaveBeenCalledWith('/holidays/h1/activate', {});
  });

  it('payment types page', () => {
    service.listPaymentTypes().subscribe();
    expect(api.getPage).toHaveBeenCalledWith('/paymenttypes', 0, 20);
  });

  it('account-number algorithm config per tenant', () => {
    service.getAlgorithmConfig('t1').subscribe();
    expect(api.get).toHaveBeenCalledWith('/tenants/t1/account-algorithm');
    const req = { bankCode: '058', validationMode: 'STRICT' as const, algorithms: { SAVINGS: 'NUBAN' } };
    service.updateAlgorithmConfig('t1', req).subscribe();
    expect(api.put).toHaveBeenCalledWith('/tenants/t1/account-algorithm', req);
  });

  it('credit bureau activate/deactivate embed ?command= in the URL', () => {
    service.activateCreditBureau('cb1').subscribe();
    expect(api.post).toHaveBeenCalledWith('/creditbureaus/cb1?command=activate', {});
    service.deactivateCreditBureau('cb1').subscribe();
    expect(api.post).toHaveBeenCalledWith('/creditbureaus/cb1?command=deactivate', {});
  });

  it('credit bureau mappings route under the bureau', () => {
    service.listCreditBureauMappings('cb1').subscribe();
    expect(api.get).toHaveBeenCalledWith('/creditbureaus/cb1/mappings');
    service.deleteCreditBureauMapping('cb1', 'm1').subscribe();
    expect(api.delete).toHaveBeenCalledWith('/creditbureaus/cb1/mappings/m1');
  });

  it('exchange rates upsert + deactivate by currency pair', () => {
    const req = { fromCurrency: 'USD', toCurrency: 'KES', rate: 135.5 };
    service.upsertExchangeRate(req).subscribe();
    expect(api.post).toHaveBeenCalledWith('/exchange-rates', req);
    service.deactivateExchangeRate('USD', 'KES').subscribe();
    expect(api.delete).toHaveBeenCalledWith('/exchange-rates/USD/KES');
  });

  it('field configuration list by entity + update', () => {
    service.listFieldConfigsByEntity('CLIENT').subscribe();
    expect(api.get).toHaveBeenCalledWith('/fieldconfiguration/CLIENT');
    service.updateFieldConfig('fc1', { enabled: false }).subscribe();
    expect(api.put).toHaveBeenCalledWith('/fieldconfiguration/fc1', { enabled: false });
  });

  it('datatables delete by registered name', () => {
    service.deleteDataTable('m_extra').subscribe();
    expect(api.delete).toHaveBeenCalledWith('/datatables/m_extra');
  });

  it('floating rates + taxes basic routing', () => {
    service.listFloatingRates().subscribe();
    expect(api.get).toHaveBeenCalledWith('/floatingrates');
    service.listTaxComponents().subscribe();
    expect(api.get).toHaveBeenCalledWith('/taxes/components');
    service.listTaxGroups().subscribe();
    expect(api.get).toHaveBeenCalledWith('/taxes/groups');
  });
});
