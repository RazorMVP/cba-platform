// @ts-check
const eslint = require('@eslint/js');
const { defineConfig } = require('eslint/config');
const tseslint = require('typescript-eslint');
const angular = require('angular-eslint');

module.exports = defineConfig([
  {
    files: ['**/*.ts'],
    extends: [
      eslint.configs.recommended,
      tseslint.configs.recommended,
      angular.configs.tsRecommended,
    ],
    processor: angular.processInlineTemplates,
    rules: {
      '@angular-eslint/directive-selector': [
        'error',
        { type: 'attribute', prefix: 'app', style: 'camelCase' },
      ],
      '@angular-eslint/component-selector': [
        'error',
        { type: 'element', prefix: 'app', style: 'kebab-case' },
      ],
      // Downgrade unused-vars to warn (common during active development)
      '@typescript-eslint/no-unused-vars': ['warn', { argsIgnorePattern: '^_' }],
      // Allow empty functions in services/event callbacks
      '@typescript-eslint/no-empty-function': 'warn',
      // Allow explicit any in banking data structures (runtime API responses)
      '@typescript-eslint/no-explicit-any': 'warn',
    },
  },
  {
    files: ['**/*.html'],
    extends: [
      // templateRecommended: structural correctness (required)
      // templateAccessibility omitted — add as dedicated a11y pass
      angular.configs.templateRecommended,
    ],
    rules: {
      // Allow @if/@for alongside legacy *ngIf/*ngFor in stub components
      '@angular-eslint/template/prefer-control-flow': 'warn',
    },
  },
]);
