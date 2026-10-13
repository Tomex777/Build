// TypeScript 5.9.3, ES2020/ES modules. Transpilation only; no semantic type checking.
globalThis.annieTranspile = function(source, filename) {
  const result = ts.transpileModule(source, {
    fileName: filename,
    reportDiagnostics: true,
    compilerOptions: {
      target: ts.ScriptTarget.ES2020,
      module: ts.ModuleKind.ESNext,
      isolatedModules: true,
      inlineSourceMap: true,
      inlineSources: true
    }
  });
  const errors = (result.diagnostics || []).filter(d => d.category === ts.DiagnosticCategory.Error);
  if (errors.length) return {error: errors.map(d => {
    const pos = d.file && d.file.getLineAndCharacterOfPosition(d.start || 0);
    return filename + (pos ? ':' + (pos.line + 1) + ':' + (pos.character + 1) : '') +
      ' TS' + d.code + ': ' + ts.flattenDiagnosticMessageText(d.messageText, '\n');
  }).join('\n')};
  return {code: result.outputText};
};
void 0;
