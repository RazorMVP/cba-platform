import type {SidebarsConfig} from '@docusaurus/plugin-content-docs';

const sidebars: SidebarsConfig = {
  mainSidebar: [
    {
      type: 'doc',
      id: 'getting-started',
      label: 'Getting Started',
    },
    {
      type: 'doc',
      id: 'authentication',
      label: 'Authentication',
    },
    {
      type: 'doc',
      id: 'core-concepts',
      label: 'Core Concepts',
    },
    {
      type: 'category',
      label: 'API Reference',
      collapsed: false,
      items: [
        {type: 'doc', id: 'api/open-banking', label: 'Open Banking API'},
        {type: 'doc', id: 'api/card', label: 'Card API'},
        {type: 'doc', id: 'api/internal', label: 'Internal API'},
      ],
    },
    {
      type: 'doc',
      id: 'webhooks',
      label: 'Webhook Guide',
    },
    {
      type: 'doc',
      id: 'rate-limiting',
      label: 'Rate Limiting',
    },
    {
      type: 'category',
      label: 'Tutorials',
      items: [
        {type: 'doc', id: 'tutorials/initiate-payment', label: 'Initiate a Payment'},
        {type: 'doc', id: 'tutorials/issue-card', label: 'Issue a Card'},
        {type: 'doc', id: 'tutorials/check-available-funds', label: 'Check Available Funds'},
      ],
    },
    {
      type: 'doc',
      id: 'sdks-tools',
      label: 'SDKs & Tools',
    },
    {
      type: 'doc',
      id: 'error-reference',
      label: 'Error Reference',
    },
    {
      type: 'doc',
      id: 'changelog',
      label: 'Changelog',
    },
    {
      type: 'category',
      label: '🤝 Partner Developer Portal',
      collapsed: false,
      items: [
        {
          type: 'link',
          label: 'Partner Docs Home',
          href: 'https://partner-portal-omega-two.vercel.app',
        },
        {
          type: 'link',
          label: 'Getting Started (Partners)',
          href: 'https://partner-portal-omega-two.vercel.app/docs/getting-started',
        },
        {
          type: 'link',
          label: 'Partner Authentication',
          href: 'https://partner-portal-omega-two.vercel.app/docs/authentication',
        },
        {
          type: 'link',
          label: 'Open Banking v3.1',
          href: 'https://partner-portal-omega-two.vercel.app/docs/open-banking',
        },
        {
          type: 'link',
          label: 'Card API (BaaS)',
          href: 'https://partner-portal-omega-two.vercel.app/docs/card-api',
        },
        {
          type: 'link',
          label: 'Webhooks',
          href: 'https://partner-portal-omega-two.vercel.app/docs/webhooks',
        },
        {
          type: 'link',
          label: 'Partner API Reference (HTML)',
          href: 'https://partner-portal-omega-two.vercel.app/partner-api-reference.html',
        },
      ],
    },
  ],
};

export default sidebars;
