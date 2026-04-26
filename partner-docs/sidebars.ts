import type {SidebarsConfig} from '@docusaurus/plugin-content-docs';

const sidebars: SidebarsConfig = {
  partnerSidebar: [
    {
      type: 'category',
      label: '🚀 Start Here',
      collapsed: false,
      items: [
        'getting-started',
        'authentication',
        'core-concepts',
      ],
    },
    {
      type: 'category',
      label: '🏦 Open Banking v3.1',
      collapsed: false,
      items: [
        'open-banking',
      ],
    },
    {
      type: 'category',
      label: '💳 Card API',
      collapsed: false,
      items: [
        'card-api',
      ],
    },
    {
      type: 'category',
      label: '🔔 Webhooks',
      collapsed: false,
      items: [
        'webhooks',
      ],
    },
    {
      type: 'category',
      label: '📚 Tutorials',
      collapsed: true,
      items: [
        'tutorials/issue-first-card',
        'tutorials/initiate-payment',
        'tutorials/manage-consents',
      ],
    },
    {
      type: 'category',
      label: '📖 Reference',
      collapsed: false,
      items: [
        'error-reference',
        'rate-limiting',
        'sdks-tools',
        'changelog',
      ],
    },
  ],
};

export default sidebars;
