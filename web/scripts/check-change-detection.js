#!/usr/bin/env node
/**
 * Fails when an @Component in src/ (spec files excluded) does not set
 * `changeDetection: ChangeDetectionStrategy.Eager`.
 *
 * Angular 22 defaults components to OnPush. This app sets plain properties
 * inside RxJS subscribes, which only re-render under Eager change detection
 * (see app.config.ts and CLAUDE.md "Angular Change Detection"). A component
 * without Eager compiles, passes lint and unit tests, and then freezes on its
 * loading state in the browser — so this check runs as its own CI step.
 *
 * Run: npm run check:change-detection
 */
const fs = require('fs');
const path = require('path');
const ts = require('typescript');

const root = path.join(__dirname, '..', 'src');

function sourceFiles(dir) {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) return sourceFiles(full);
    return entry.name.endsWith('.ts') && !entry.name.endsWith('.spec.ts') ? [full] : [];
  });
}

function isEager(initializer) {
  return (
    ts.isPropertyAccessExpression(initializer) &&
    initializer.expression.getText() === 'ChangeDetectionStrategy' &&
    initializer.name.text === 'Eager'
  );
}

// Returns { components, problems } for one file.
function checkFile(file) {
  const text = fs.readFileSync(file, 'utf8');
  if (!text.includes('@Component')) return { components: 0, problems: [] };
  const source = ts.createSourceFile(file, text, ts.ScriptTarget.Latest, true);
  let components = 0;
  const problems = [];

  function visit(node) {
    if (
      ts.isDecorator(node) &&
      ts.isCallExpression(node.expression) &&
      node.expression.expression.getText() === 'Component'
    ) {
      components++;
      const [metadata] = node.expression.arguments;
      const property =
        metadata && ts.isObjectLiteralExpression(metadata)
          ? metadata.properties.find(
              (p) => ts.isPropertyAssignment(p) && p.name.getText() === 'changeDetection',
            )
          : undefined;
      if (!property || !isEager(property.initializer)) {
        const { line } = source.getLineAndCharacterOfPosition(node.getStart());
        const found = property ? property.initializer.getText() : 'not set (Angular 22 default: OnPush)';
        problems.push(`${path.relative(process.cwd(), file)}:${line + 1}  changeDetection ${found}`);
      }
    }
    ts.forEachChild(node, visit);
  }

  visit(source);
  return { components, problems };
}

const results = sourceFiles(root).map(checkFile);
const components = results.reduce((n, r) => n + r.components, 0);
const problems = results.flatMap((r) => r.problems);

if (problems.length) {
  console.error(`${problems.length} of ${components} components do not set changeDetection: ChangeDetectionStrategy.Eager:\n`);
  problems.forEach((p) => console.error(`  ${p}`));
  console.error(
    '\nAdd `changeDetection: ChangeDetectionStrategy.Eager` to the @Component metadata ' +
      '(import ChangeDetectionStrategy from @angular/core). See CLAUDE.md "Angular Change Detection".',
  );
  process.exit(1);
}
console.log(`OK: all ${components} components set ChangeDetectionStrategy.Eager.`);
