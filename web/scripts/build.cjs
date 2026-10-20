const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
let ts;
try {
  ts = require(process.env.TYPESCRIPT_PATH || 'typescript');
} catch {
  console.error(
      '请先在 web 目录运行 npm install，或设置 TYPESCRIPT_PATH 指向已有 TypeScript 5.9.3。');
  process.exit(1);
}
// transpileModule 只转换语法；先运行完整核心类型检查，避免错误代码被发布为静态产物。
function checkSharedTypes() {
  const config = ts.readConfigFile(path.join(root, '../client/tsconfig.core.json'), ts.sys.readFile);
  if (config.error) throw new Error(ts.flattenDiagnosticMessageText(config.error.messageText, '\n'));
  const parsed = ts.parseJsonConfigFileContent(config.config, ts.sys, path.join(root, '../client'));
  const program = ts.createProgram(parsed.fileNames, parsed.options);
  const errors = [...parsed.errors, ...ts.getPreEmitDiagnostics(program)];
  if (!errors.length) return;
  console.error(ts.formatDiagnosticsWithColorAndContext(errors, {
    getCanonicalFileName: file => file,
    getCurrentDirectory: () => root,
    getNewLine: () => '\n'
  }));
  process.exit(1);
}
checkSharedTypes();
const dist = path.join(root, 'dist');
fs.mkdirSync(path.join(dist, 'shared'), {recursive: true});
for (const file of
    ['index.html', 'app.js', 'app.css', 'test.html', 'test.js']) {
  fs.copyFileSync(path.join(root, 'src', file), path.join(dist, file));
}
for (const name of ['model', 'Selection', 'Transport']) {
  const source = fs.readFileSync(
      path.join(root, '../client/assets/scripts', name + '.ts'), 'utf8');
  const output = ts.transpileModule(source, {
    fileName: name + '.ts',
    compilerOptions: {
      target: ts.ScriptTarget.ES2020,
      module: ts.ModuleKind.ES2020
    }
  }).outputText.replace(/(from\s+['"])(\.\.?\/[^'"]+)(['"])/g,
      (_, a, b, c) => a + (path.extname(b) ? b : b + '.js') + c);
  fs.writeFileSync(path.join(dist, 'shared', name + '.js'),
      '// Generated from client/assets/scripts; edit shared TypeScript source.\n'
      + output);
}
const target = path.join(root, '../server/src/main/resources/static');
fs.mkdirSync(target, {recursive: true});
fs.cpSync(dist, target, {recursive: true});
console.log(
    'H5 build ready: web/dist and server/src/main/resources/static (same-origin HTTP + WebSocket).');
