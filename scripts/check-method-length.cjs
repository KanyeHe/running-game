// Use the TypeScript parser for JS/TS so nested callbacks count as functions too.
const fs = require('node:fs');
const path = require('node:path');
const ts = require('../web/node_modules/typescript');
const roots = process.argv.slice(2);
let violations = 0;

function check(file) {
  const source = ts.createSourceFile(file, fs.readFileSync(file, 'utf8'),
    ts.ScriptTarget.Latest, true);
  function visit(node) {
    if (ts.isFunctionLike(node) && node.body) {
      const start = source.getLineAndCharacterOfPosition(node.getStart(source)).line;
      const end = source.getLineAndCharacterOfPosition(node.end - 1).line;
      if (end - start + 1 > 35) {
        console.error(`${file}:${start + 1} ${node.name?.getText(source) || '<callback>'} ${end - start + 1} lines`);
        violations++;
      }
    }
    ts.forEachChild(node, visit);
  }
  visit(source);
}

function walk(entry) {
  if (!fs.statSync(entry).isDirectory()) return check(entry);
  for (const item of fs.readdirSync(entry)) {
    const file = path.join(entry, item);
    if (fs.statSync(file).isDirectory()) walk(file);
    else if (/\.(?:[cm]?js|ts)$/.test(file)) check(file);
  }
}

roots.forEach(walk);
if (violations) process.exitCode = 1;
else console.log('PASS JS/TS function length <= 35 lines');
