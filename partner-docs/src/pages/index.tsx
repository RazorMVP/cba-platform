import type {ReactNode} from 'react';
import Link from '@docusaurus/Link';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import Layout from '@theme/Layout';
import Heading from '@theme/Heading';
import clsx from 'clsx';
import styles from './index.module.css';

type FeatureItem = {
  icon: string;
  title: string;
  description: string;
  href: string;
  badge?: string;
};

const Features: FeatureItem[] = [
  {
    icon: '🔐',
    title: 'Authentication',
    description: 'OAuth2 PKCE, Partner JWT, and API Key authentication for every integration type.',
    href: '/docs/authentication',
  },
  {
    icon: '🏦',
    title: 'Open Banking v3.1',
    description: 'UK Open Banking AISP, PISP, and CBPII endpoints. Access accounts, initiate payments, and confirm funds.',
    href: '/docs/open-banking',
    badge: 'FAPI 2.0',
  },
  {
    icon: '💳',
    title: 'Card API',
    description: 'Issue virtual and physical cards, manage limits, controls, and spending analytics via API key.',
    href: '/docs/card-api',
    badge: 'BaaS',
  },
  {
    icon: '🔔',
    title: 'Webhooks',
    description: 'Real-time event delivery for payments, consents, card lifecycle, and API key events.',
    href: '/docs/webhooks',
  },
  {
    icon: '🧪',
    title: 'Sandbox',
    description: 'Full sandbox environment with pre-seeded test data. No approval required to get started.',
    href: '/docs/getting-started',
  },
  {
    icon: '📦',
    title: 'SDKs & Postman',
    description: 'Download the Postman collection, generate typed clients from OpenAPI specs, or use cURL.',
    href: '/docs/sdks-tools',
  },
];

const QuickStartSteps = [
  {step: '01', title: 'Register your organisation', desc: 'Sign up at partners.nubbank.com — sandbox access is immediate.'},
  {step: '02', title: 'Get your API key', desc: 'Issue an API key from the Partner Portal dashboard.'},
  {step: '03', title: 'Call your first endpoint', desc: 'List sandbox accounts or issue a test card in minutes.'},
  {step: '04', title: 'Apply for production', desc: 'Submit your production application for review when ready.'},
];

function FeatureCard({icon, title, description, href, badge}: FeatureItem) {
  return (
    <Link to={href} className={styles.featureCard}>
      <div className={styles.featureIcon}>{icon}</div>
      <div className={styles.featureHeader}>
        <span className={styles.featureTitle}>{title}</span>
        {badge && <span className={styles.featureBadge}>{badge}</span>}
      </div>
      <p className={styles.featureDesc}>{description}</p>
    </Link>
  );
}

export default function Home(): ReactNode {
  const {siteConfig} = useDocusaurusContext();
  return (
    <Layout
      title="NubBank Developer Portal"
      description="Build fintech products on NubBank Open Banking and Card APIs">
      {/* ── Hero ── */}
      <header className={styles.hero}>
        <div className="container">
          <div className={styles.heroBadge}>Open Banking · Card BaaS · Webhooks</div>
          <Heading as="h1" className={styles.heroTitle}>
            Build on NubBank APIs
          </Heading>
          <p className={styles.heroSubtitle}>
            Access UK Open Banking v3.1, issue cards, manage consents, and receive
            real-time webhooks — from sandbox to production in days.
          </p>
          <div className={styles.heroButtons}>
            <Link className={clsx('button button--primary button--lg', styles.btnPrimary)} to="/docs/getting-started">
              Get Started →
            </Link>
            <Link className={clsx('button button--secondary button--lg', styles.btnSecondary)} to="pathname:///partner-api-reference.html">
              API Reference
            </Link>
          </div>
          <div className={styles.heroMeta}>
            <span>✓ Sandbox in 2 minutes</span>
            <span>✓ FAPI 2.0 compliant</span>
            <span>✓ ISO 8583 card processing</span>
          </div>
        </div>
      </header>

      <main>
        {/* ── Quick Start ── */}
        <section className={styles.quickStart}>
          <div className="container">
            <h2 className={styles.sectionTitle}>Get up and running in 4 steps</h2>
            <div className={styles.stepsGrid}>
              {QuickStartSteps.map(({step, title, desc}) => (
                <div key={step} className={styles.step}>
                  <div className={styles.stepNumber}>{step}</div>
                  <h3 className={styles.stepTitle}>{title}</h3>
                  <p className={styles.stepDesc}>{desc}</p>
                </div>
              ))}
            </div>
          </div>
        </section>

        {/* ── Features ── */}
        <section className={styles.features}>
          <div className="container">
            <h2 className={styles.sectionTitle}>Everything you need to integrate</h2>
            <div className={styles.featuresGrid}>
              {Features.map((props) => (
                <FeatureCard key={props.title} {...props} />
              ))}
            </div>
          </div>
        </section>

        {/* ── CTA ── */}
        <section className={styles.cta}>
          <div className="container">
            <div className={styles.ctaBox}>
              <h2 className={styles.ctaTitle}>Ready to integrate?</h2>
              <p className={styles.ctaDesc}>
                Create a sandbox account and make your first API call in under 5 minutes.
              </p>
              <div className={styles.ctaButtons}>
                <Link className={clsx('button button--primary', styles.btnPrimary)} to="/docs/getting-started">
                  Start Building
                </Link>
                <Link className={clsx('button button--secondary', styles.btnSecondary)} href="https://partners.nubbank.com">
                  Partner Portal ↗
                </Link>
              </div>
            </div>
          </div>
        </section>
      </main>
    </Layout>
  );
}
