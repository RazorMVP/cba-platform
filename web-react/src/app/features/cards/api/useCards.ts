// web-react/src/app/features/cards/api/useCards.ts
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { cardApiClient } from '@/core/api/cardApiClient'
import type {
  Card, CardLimit, CardProduct, FraudRule, AuthorizationLog,
  SettlementBatch, SettlementTransmission, CardDispute, ChargebackReasonCode,
  ApiKey, Webhook, WebhookDelivery, BinRange, InterchangeRate, SchemeFee,
  SimulateRequest, SimulateResponse,
  CardIssueRequest, CardLimitRequest, CardProductRequest,
  ApiKeyRequest, WebhookRequest, BinRangeRequest,
  InterchangeRateRequest, SchemeFeeRequest,
  DisputeRequest, ResolveDisputeRequest,
  SchemeType,
} from './types'

// ── Cards ─────────────────────────────────────────────────────────────────────

export function useCards(params?: { customerId?: string; cardType?: string; status?: string }) {
  return useQuery({
    queryKey: ['cards', params],
    queryFn: () =>
      cardApiClient.get<{ data: Card[] }>('/card-api/v1/cards', { params }).then(r => r.data),
  })
}

export function useCard(id: string) {
  return useQuery({
    queryKey: ['cards', id],
    queryFn: () =>
      cardApiClient.get<{ data: Card }>(`/card-api/v1/cards/${id}`).then(r => r.data),
    enabled: !!id && id !== 'new',
  })
}

export function useCardLimits(id: string) {
  return useQuery({
    queryKey: ['card-limits', id],
    queryFn: () =>
      cardApiClient.get<{ data: CardLimit }>(`/api/v1/cards/${id}/limits`).then(r => r.data),
    enabled: !!id,
  })
}

export function useCardAuthorizations(id: string, params?: { from?: string; to?: string }) {
  return useQuery({
    queryKey: ['card-authorizations', id, params],
    queryFn: () =>
      cardApiClient.get<{ data: AuthorizationLog[] }>(`/card-api/v1/cards/${id}/authorizations`, { params }).then(r => r.data),
    enabled: !!id,
  })
}

export function useIssueCard() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (req: CardIssueRequest) =>
      cardApiClient.post<{ data: Card }>('/card-api/v1/cards', req).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['cards'] }),
  })
}

export function useCardCommand(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (command: 'block' | 'unblock' | 'cancel' | 'activate') =>
      cardApiClient.post(`/api/v1/cards/${id}?command=${command}`, {}).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['cards'] }),
  })
}

export function useUpdateCardLimits(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (req: CardLimitRequest) =>
      cardApiClient.put<{ data: CardLimit }>(`/card-api/v1/cards/${id}/limits`, req).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['card-limits', id] }),
  })
}

// ── Card Products ─────────────────────────────────────────────────────────────

export function useCardProducts() {
  return useQuery({
    queryKey: ['card-products'],
    queryFn: () =>
      cardApiClient.get<{ data: CardProduct[] }>('/api/v1/cards/products').then(r => r.data),
  })
}

export function useCreateCardProduct() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (req: CardProductRequest) =>
      cardApiClient.post<{ data: CardProduct }>('/api/v1/cards/products', req).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['card-products'] }),
  })
}

// ── Fraud Rules ───────────────────────────────────────────────────────────────

export function useFraudRules() {
  return useQuery({
    queryKey: ['fraud-rules'],
    queryFn: () =>
      cardApiClient.get<{ data: FraudRule[] }>('/api/v1/cards/fraud/rules').then(r => r.data),
  })
}

export function useUpdateFraudRule(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (req: Partial<FraudRule>) =>
      cardApiClient.put<{ data: FraudRule }>(`/api/v1/cards/fraud/rules/${id}`, req).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['fraud-rules'] }),
  })
}

// ── Settlement ────────────────────────────────────────────────────────────────

export function useSettlementBatches() {
  return useQuery({
    queryKey: ['settlement-batches'],
    queryFn: () =>
      cardApiClient.get<{ data: SettlementBatch[] }>('/api/v1/cards/settlement/batches').then(r => r.data),
  })
}

export function useCloseBatch(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () =>
      cardApiClient.post(`/api/v1/cards/settlement/batches/${id}/close`, {}).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['settlement-batches'] }),
  })
}

export function useExportBatch(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () =>
      cardApiClient.post(`/api/v1/cards/settlement/export/${id}`, {}).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['settlement-transmissions'] }),
  })
}

export function useSettlementTransmissions(batchId?: string) {
  return useQuery({
    queryKey: ['settlement-transmissions', batchId],
    queryFn: () => {
      const url = batchId
        ? `/api/v1/cards/settlement/batches/${batchId}/transmissions`
        : '/api/v1/cards/settlement/transmissions'
      return cardApiClient.get<{ data: SettlementTransmission[] }>(url).then(r => r.data)
    },
  })
}

// ── Disputes ──────────────────────────────────────────────────────────────────

export function useCardDisputes(status?: string) {
  return useQuery({
    queryKey: ['card-disputes', status],
    queryFn: () =>
      cardApiClient.get<{ data: CardDispute[] }>('/api/v1/cards/disputes', { params: status ? { status } : {} }).then(r => r.data),
  })
}

export function useChargebackReasonCodes(scheme?: SchemeType) {
  return useQuery({
    queryKey: ['chargeback-reason-codes', scheme],
    queryFn: () =>
      cardApiClient.get<{ data: ChargebackReasonCode[] }>('/api/v1/cards/disputes/reason-codes', { params: scheme ? { scheme } : {} }).then(r => r.data),
  })
}

export function useRaiseDispute() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (req: DisputeRequest) =>
      cardApiClient.post<{ data: CardDispute }>('/api/v1/cards/disputes', req).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['card-disputes'] }),
  })
}

export function useDisputeAction(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ action, body }: { action: string; body?: unknown }) =>
      cardApiClient.post(`/api/v1/cards/disputes/${id}/${action}`, body ?? {}).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['card-disputes'] }),
  })
}

export function useResolveDispute(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (req: ResolveDisputeRequest) =>
      cardApiClient.post(`/api/v1/cards/disputes/${id}/resolve`, req).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['card-disputes'] }),
  })
}

// ── Terminal Simulator ────────────────────────────────────────────────────────

export function useSimulate(type: 'purchase' | 'withdrawal' | 'balance' | 'reversal' | 'network/signon' | 'network/echo') {
  return useMutation({
    mutationFn: (req: SimulateRequest) =>
      cardApiClient.post<SimulateResponse>(`/api/v1/simulate/${type}`, req).then(r => r.data),
  })
}

// ── API Keys ──────────────────────────────────────────────────────────────────

export function useApiKeys() {
  return useQuery({
    queryKey: ['api-keys'],
    queryFn: () =>
      cardApiClient.get<{ data: ApiKey[] }>('/card-api/v1/api-keys').then(r => r.data),
  })
}

export function useCreateApiKey() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (req: ApiKeyRequest) =>
      cardApiClient.post<{ data: ApiKey }>('/card-api/v1/api-keys', req).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['api-keys'] }),
  })
}

export function useRevokeApiKey(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => cardApiClient.delete(`/card-api/v1/api-keys/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['api-keys'] }),
  })
}

// ── Webhooks ──────────────────────────────────────────────────────────────────

export function useWebhooks() {
  return useQuery({
    queryKey: ['webhooks'],
    queryFn: () =>
      cardApiClient.get<{ data: Webhook[] }>('/card-api/v1/webhooks').then(r => r.data),
  })
}

export function useCreateWebhook() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (req: WebhookRequest) =>
      cardApiClient.post<{ data: Webhook }>('/card-api/v1/webhooks', req).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['webhooks'] }),
  })
}

export function useDeleteWebhook(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => cardApiClient.delete(`/card-api/v1/webhooks/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['webhooks'] }),
  })
}

export function useWebhookDeliveries(id: string) {
  return useQuery({
    queryKey: ['webhook-deliveries', id],
    queryFn: () =>
      cardApiClient.get<{ data: WebhookDelivery[] }>(`/card-api/v1/webhooks/${id}/deliveries`).then(r => r.data),
    enabled: !!id,
  })
}

// ── BIN Management ────────────────────────────────────────────────────────────

export function useBinRanges() {
  return useQuery({
    queryKey: ['bin-ranges'],
    queryFn: () =>
      cardApiClient.get<{ data: BinRange[] }>('/api/v1/bins').then(r => r.data),
  })
}

export function useCreateBinRange() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (req: BinRangeRequest) =>
      cardApiClient.post<{ data: BinRange }>('/api/v1/bins', req).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['bin-ranges'] }),
  })
}

export function useUpdateBinRange(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (req: BinRangeRequest) =>
      cardApiClient.put<{ data: BinRange }>(`/api/v1/bins/${id}`, req).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['bin-ranges'] }),
  })
}

export function useDeleteBinRange(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => cardApiClient.delete(`/api/v1/bins/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['bin-ranges'] }),
  })
}

// ── Interchange ───────────────────────────────────────────────────────────────

export function useInterchangeRates(scheme?: SchemeType) {
  return useQuery({
    queryKey: ['interchange-rates', scheme],
    queryFn: () =>
      cardApiClient.get<{ data: InterchangeRate[] }>('/api/v1/interchange/rates', { params: scheme ? { scheme } : {} }).then(r => r.data),
  })
}

export function useCreateInterchangeRate() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (req: InterchangeRateRequest) =>
      cardApiClient.post<{ data: InterchangeRate }>('/api/v1/interchange/rates', req).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['interchange-rates'] }),
  })
}

export function useDeleteInterchangeRate(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => cardApiClient.delete(`/api/v1/interchange/rates/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['interchange-rates'] }),
  })
}

export function useSchemeFees(scheme?: SchemeType) {
  return useQuery({
    queryKey: ['scheme-fees', scheme],
    queryFn: () =>
      cardApiClient.get<{ data: SchemeFee[] }>('/api/v1/interchange/fees', { params: scheme ? { scheme } : {} }).then(r => r.data),
  })
}

export function useCreateSchemeFee() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (req: SchemeFeeRequest) =>
      cardApiClient.post<{ data: SchemeFee }>('/api/v1/interchange/fees', req).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['scheme-fees'] }),
  })
}

export function useDeleteSchemeFee(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => cardApiClient.delete(`/api/v1/interchange/fees/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['scheme-fees'] }),
  })
}
