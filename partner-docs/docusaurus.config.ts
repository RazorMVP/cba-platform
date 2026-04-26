import {themes as prismThemes} from 'prism-react-renderer';
import type {Config} from '@docusaurus/types';
import type * as Preset from '@docusaurus/preset-classic';

const config: Config = {
  title: 'NubBank Developer Portal',
  tagline: 'Build fintech products on NubBank Open Banking APIs',
  favicon: 'img/favicon.ico',

  future: {
    v4: true,
  },

  url: 'https://developers.nubbank.com',
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
          editUrl: 'https://github.com/RazorMVP/cba-platform/edit/main/partner-docs/',
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
      defaultMode: 'dark',
      disableSwitch: false,
      respectPrefersColorScheme: false,
    },
    navbar: {
      title: 'NubBank Developers',
      logo: {
        alt: 'NubBank Logo',
        src: 'img/logo.png',
        srcDark: 'img/logo.png',
      },
      items: [
        {
          type: 'docSidebar',
          sidebarId: 'partnerSidebar',
          position: 'left',
          label: 'Docs',
        },
        {
          href: 'pathname:///partner-api-reference.html',
          label: 'API Reference',
          position: 'left',
        },
        {
          href: 'pathname:///card-api-reference.html',
          label: 'Card API',
          position: 'left',
        },
        {
          href: 'https://partners.nubbank.com',
          label: 'Partner Portal',
          position: 'right',
        },
        {
          href: 'https://github.com/RazorMVP/cba-platform',
          label: 'GitHub',
          position: 'right',
        },
      ],
    },
    footer: {
      style: 'dark',
      links: [
        {
          title: 'APIs',
          items: [
            {label: 'Getting Started', to: '/docs/getting-started'},
            {label: 'Authentication', to: '/docs/authentication'},
            {label: 'Open Banking v3.1', to: '/docs/open-banking'},
            {label: 'Card API', to: '/docs/card-api'},
          ],
        },
        {
          title: 'Reference',
          items: [
            {label: 'Partner API Reference', href: 'pathname:///partner-api-reference.html'},
            {label: 'Card API Reference', href: 'pathname:///card-api-reference.html'},
            {label: 'Error Reference', to: '/docs/error-reference'},
            {label: 'Webhooks', to: '/docs/webhooks'},
          ],
        },
        {
          title: 'Resources',
          items: [
            {label: 'SDKs & Tools', to: '/docs/sdks-tools'},
            {label: 'Postman Collection', href: 'pathname:///postman/cba-postman-collection-v2.json'},
            {label: 'Partner Portal', href: 'https://partners.nubbank.com'},
            {label: 'API Support', href: 'mailto:api-support@nubbank.com'},
          ],
        },
        {
          title: 'Company',
          items: [
            {label: 'NubBank.com', href: 'https://nubbank.com'},
            {label: 'GitHub', href: 'https://github.com/RazorMVP/cba-platform'},
            {label: 'Status', href: 'https://status.nubbank.com'},
          ],
        },
      ],
      copyright: `Copyright © ${new Date().getFullYear()} NubBank. All rights reserved.`,
    },
    prism: {
      theme: prismThemes.vsDark,
      darkTheme: prismThemes.vsDark,
      additionalLanguages: ['java', 'bash', 'json', 'yaml', 'python', 'go', 'ruby', 'csharp'],
    },
    algolia: undefined,
  } satisfies Preset.ThemeConfig,
};

export default config;
