import type {ReactNode} from 'react';
import Link from '@docusaurus/Link';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import Layout from '@theme/Layout';
import Heading from '@theme/Heading';
import styles from './index.module.css';

const API_FAMILIES = [
  {
    icon: '🏦',
    title: 'Open Banking API',
    description: 'AISP, PISP, and CBPII — UK Open Banking v3.1 compliant endpoints for account data, payments, and funds confirmation.',
    link: '/docs/api/open-banking',
    badge: 'FAPI 2.0',
    badgeColor: '#1d4ed8',
  },
  {
    icon: '💳',
    title: 'Card API',
    description: 'Issue virtual and physical cards, manage limits, controls, and fraud rules. Full BaaS-grade card issuance platform.',
    link: '/docs/api/card',
    badge: 'ISO 8583',
    badgeColor: '#065f46',
  },
  {
    icon: '⚙️',
    title: 'Internal API',
    description: 'Full /api/v1/ reference for bank staff applications — customers, accounts, loans, payments, and reporting.',
    link: '/docs/api/internal',
    badge: 'REST',
    badgeColor: '#92400e',
  },
];

const QUICK_LINKS = [
  {icon: '🚀', label: 'Getting Started', to: '/docs/getting-started', desc: 'Make your first API call in minutes'},
  {icon: '🔐', label: 'Authentication', to: '/docs/authentication', desc: 'OAuth2 PKCE, API keys, token refresh'},
  {icon: '📘', label: 'Core Concepts', to: '/docs/core-concepts', desc: 'Consents, idempotency, pagination'},
  {icon: '🔔', label: 'Webhooks', to: '/docs/webhooks', desc: 'Real-time event notifications'},
  {icon: '⚡', label: 'Rate Limiting', to: '/docs/rate-limiting', desc: 'Tier limits, headers, backoff strategy'},
  {icon: '🧰', label: 'SDKs & Tools', to: '/docs/sdks-tools', desc: 'Postman collection, OpenAPI spec'},
];

function HeroBanner() {
  useDocusaurusContext();
  return (
    <header className={styles.heroBanner}>
      <div className={styles.heroInner}>
        <div className={styles.heroText}>
          <div className={styles.heroBadge}>Developer Documentation</div>
          <Heading as="h1" className={styles.heroTitle}>
            Build on NubBank APIs
          </Heading>
          <p className={styles.heroSubtitle}>
            Integrate Open Banking, card issuance, payments, and core banking operations
            into your applications. Production-grade APIs with enterprise security.
          </p>
          <div className={styles.heroActions}>
            <Link className={styles.btnPrimary} to="/docs/getting-started">
              Get Started →
            </Link>
            <Link className={styles.btnSecondary} to="/docs/api/open-banking">
              View API Reference
            </Link>
          </div>
        </div>
        <div className={styles.heroCode}>
          <div className={styles.codeWindow}>
            <div className={styles.codeWindowHeader}>
              <span className={styles.dot} style={{background: '#ef4444'}} />
              <span className={styles.dot} style={{background: '#f59e0b'}} />
              <span className={styles.dot} style={{background: '#10b981'}} />
              <span className={styles.codeWindowTitle}>Quick Example</span>
            </div>
            <pre className={styles.codeBlock}>
{`curl -X GET \\
  https://api.nubbank.com/open-banking/v3.1/accounts \\
  -H "Authorization: Bearer {access_token}" \\
  -H "x-fapi-interaction-id: abc-123"

# Response
{
  "Data": {
    "Account": [{
      "AccountId": "22289",
      "Currency": "GBP",
      "AccountType": "Personal",
      "Nickname": "Current Account"
    }]
  }
}`}
            </pre>
          </div>
        </div>
      </div>
    </header>
  );
}

function ApiFamilies() {
  return (
    <section className={styles.section}>
      <div className={styles.container}>
        <Heading as="h2" className={styles.sectionTitle}>API Families</Heading>
        <p className={styles.sectionSubtitle}>
          Everything you need to build financial products — from consumer banking to card issuing.
        </p>
        <div className={styles.apiFamilyGrid}>
          {API_FAMILIES.map((api) => (
            <Link key={api.title} to={api.link} className={styles.apiCard}>
              <div className={styles.apiCardIcon}>{api.icon}</div>
              <div className={styles.apiCardBody}>
                <div className={styles.apiCardHeader}>
                  <strong>{api.title}</strong>
                  <span className={styles.apiBadge} style={{background: api.badgeColor + '20', color: api.badgeColor}}>
                    {api.badge}
                  </span>
                </div>
                <p className={styles.apiCardDesc}>{api.description}</p>
                <span className={styles.apiCardLink}>Read the docs →</span>
              </div>
            </Link>
          ))}
        </div>
      </div>
    </section>
  );
}

function QuickLinks() {
  return (
    <section className={styles.sectionAlt}>
      <div className={styles.container}>
        <Heading as="h2" className={styles.sectionTitle}>Quick Links</Heading>
        <div className={styles.quickGrid}>
          {QUICK_LINKS.map((item) => (
            <Link key={item.label} to={item.to} className={styles.quickCard}>
              <span className={styles.quickIcon}>{item.icon}</span>
              <strong>{item.label}</strong>
              <p>{item.desc}</p>
            </Link>
          ))}
        </div>
      </div>
    </section>
  );
}

function TrustBanner() {
  return (
    <section className={styles.trustBanner}>
      <div className={styles.container}>
        <div className={styles.trustGrid}>
          <div className={styles.trustItem}>
            <strong>FAPI 2.0</strong>
            <span>Security profile</span>
          </div>
          <div className={styles.trustDivider} />
          <div className={styles.trustItem}>
            <strong>UK Open Banking v3.1</strong>
            <span>Compliant</span>
          </div>
          <div className={styles.trustDivider} />
          <div className={styles.trustItem}>
            <strong>ISO 8583-1987</strong>
            <span>Card protocol</span>
          </div>
          <div className={styles.trustDivider} />
          <div className={styles.trustItem}>
            <strong>99.9% SLA</strong>
            <span>Uptime guarantee</span>
          </div>
        </div>
      </div>
    </section>
  );
}

export default function Home(): ReactNode {
  return (
    <Layout title="Developer Guide" description="Build on NubBank APIs — Open Banking, Card Issuance, Payments, and Core Banking">
      <HeroBanner />
      <TrustBanner />
      <ApiFamilies />
      <QuickLinks />
    </Layout>
  );
}
