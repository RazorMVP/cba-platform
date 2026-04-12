import { Routes } from '@angular/router';
import { CardListComponent }          from './card-list/card-list';
import { CardDetailComponent }        from './card-detail/card-detail';
import { CardProductsComponent }      from './card-products/card-products';
import { FraudRulesComponent }        from './fraud-rules/fraud-rules';
import { SettlementComponent }        from './settlement/settlement';
import { DisputesComponent }          from './disputes/disputes';
import { TerminalSimulatorComponent } from './terminal-simulator/terminal-simulator';
import { ApiKeysComponent }           from './api-keys/api-keys';
import { WebhooksComponent }          from './webhooks/webhooks';
import { BinManagementComponent }     from './bin-management/bin-management';
import { SchemeConfigComponent }      from './scheme-config/scheme-config';
import { InterchangeComponent }       from './interchange/interchange';

export const CARDS_ROUTES: Routes = [
  { path: '',           component: CardListComponent },
  { path: ':id',        component: CardDetailComponent },
  { path: 'products',   component: CardProductsComponent },
  { path: 'fraud',      component: FraudRulesComponent },
  { path: 'settlement', component: SettlementComponent },
  { path: 'disputes',   component: DisputesComponent },
  { path: 'terminal',   component: TerminalSimulatorComponent },
  { path: 'api-keys',   component: ApiKeysComponent },
  { path: 'webhooks',   component: WebhooksComponent },
  { path: 'bins',       component: BinManagementComponent },
  { path: 'schemes',    component: SchemeConfigComponent },
  { path: 'interchange',component: InterchangeComponent },
];
