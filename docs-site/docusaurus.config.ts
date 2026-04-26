import {themes as prismThemes} from 'prism-react-renderer';
import type {Config} from '@docusaurus/types';
import type * as Preset from '@docusaurus/preset-classic';

const config: Config = {
  title: 'NubBank Developer Guide',
  tagline: 'Build financial products on top of NubBank APIs',
  favicon: 'img/favicon.png',

  future: {
    v4: true,
  },

  url: 'https://docs-nubbank.vercel.app',
  baseUrl: '/',

  organizationName: 'RazorMVP',
  projectName: 'cba-platform',

  onBrokenLinks: 'throw',
  markdown: {
    hooks: {
      onBrokenMarkdownLinks: 'warn',
    },
  },

  i18n: {
    defaultLocale: 'en',
    locales: ['en'],
  },

  presets: [
    [
      'classic',
      {
        docs: {
          sidebarPath: './sidebars.ts',
          editUrl: 'https://github.com/RazorMVP/cba-platform/edit/main/docs-site/',
          routeBasePath: 'docs',
        },
        blog: false,
        theme: {
          customCss: './src/css/custom.css',
        },
        sitemap: {
          changefreq: 'weekly',
          priority: 0.5,
        },
      } satisfies Preset.Options,
    ],
  ],

  themeConfig: {
    image: 'img/nubbank-social-card.png',
    colorMode: {
      defaultMode: 'light',
      respectPrefersColorScheme: true,
    },
    announcementBar: {
      id: 'beta',
      content: '🚀 NubBank APIs are in public beta — <a href="/docs/changelog">see what\'s new</a>',
      backgroundColor: '#1e2833',
      textColor: '#ffffff',
      isCloseable: true,
    },
    navbar: {
      title: 'NubBank',
      logo: {
        alt: 'NubBank Logo',
        src: 'img/nubeero-logo.png',
        style: { borderRadius: '50%', background: '#0a1628', padding: '2px' },
      },
      items: [
        {
          type: 'docSidebar',
          sidebarId: 'mainSidebar',
          position: 'left',
          label: 'Documentation',
        },
        {
          to: '/docs/api/open-banking',
          label: 'API Reference',
          position: 'left',
        },
        {
          to: '/docs/tutorials/initiate-payment',
          label: 'Tutorials',
          position: 'left',
        },
        {
          href: 'https://github.com/RazorMVP/cba-platform',
          label: 'GitHub',
          position: 'right',
        },
      ],
      hideOnScroll: false,
    },
    footer: {
      style: 'dark',
      links: [
        {
          title: 'Documentation',
          items: [
            {label: 'Getting Started', to: '/docs/getting-started'},
            {label: 'Authentication', to: '/docs/authentication'},
            {label: 'Core Concepts', to: '/docs/core-concepts'},
            {label: 'Error Reference', to: '/docs/error-reference'},
          ],
        },
        {
          title: 'API Reference',
          items: [
            {label: 'Open Banking API', to: '/docs/api/open-banking'},
            {label: 'Card API', to: '/docs/api/card'},
            {label: 'Internal API', to: '/docs/api/internal'},
            {label: 'Webhook Guide', to: '/docs/webhooks'},
          ],
        },
        {
          title: 'Resources',
          items: [
            {label: 'Tutorials', to: '/docs/tutorials/initiate-payment'},
            {label: 'SDKs & Tools', to: '/docs/sdks-tools'},
            {label: 'Rate Limiting', to: '/docs/rate-limiting'},
            {label: 'Changelog', to: '/docs/changelog'},
          ],
        },
        {
          title: 'NubBank',
          items: [
            {label: 'Partner Portal', href: 'https://partners.nubbank.com'},
            {label: 'Status Page', href: 'https://status.nubbank.com'},
            {label: 'Support', href: 'mailto:api-support@nubbank.com'},
          ],
        },
      ],
      copyright: `Copyright © ${new Date().getFullYear()} NubBank. All rights reserved.`,
    },
    prism: {
      theme: prismThemes.github,
      darkTheme: prismThemes.vsDark,
      additionalLanguages: ['java', 'bash', 'json', 'yaml', 'http'],
    },
    algolia: undefined,
  } satisfies Preset.ThemeConfig,
};

export default config;
