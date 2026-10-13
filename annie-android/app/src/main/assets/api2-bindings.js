// Keep API 1's convenience wrappers; API 2 preserves types and rejects extra arguments/options.
globalThis.__annieInstallApi2 = function(definitions) {
  function invalid(id, message) {
    return Object.assign(new TypeError(message), {code:'INVALID_ARGUMENT',operation:id,retryable:false});
  }
  function object(value) { return value !== null && typeof value === 'object' && !Array.isArray(value); }
  for (const definition of definitions) {
    const path = definition.path.split('.');
    let namespace = annie;
    for (const part of path.slice(0,-1)) namespace = namespace[part] || (namespace[part] = {});
    namespace[path[path.length - 1]] = async function(...args) {
      const arity = definition.spread ? 1 : definition.positional.length + (definition.options.length ? 1 : 0);
      if (args.length > arity) throw invalid(definition.id, 'Too many arguments');
      let input = {};
      if (definition.spread) {
        if (args[0] !== undefined) {
          if (!object(args[0])) throw invalid(definition.id, 'Expected an options object');
          input = args[0];
        }
      } else {
        definition.positional.forEach((name, index) => { if (args[index] !== undefined) input[name] = args[index]; });
        const options = args[definition.positional.length];
        if (definition.options.length && options !== undefined) {
          if (!object(options)) throw invalid(definition.id, 'Expected an options object');
          for (const name of Object.keys(options)) {
            if (!definition.options.includes(name)) throw invalid(definition.id, 'Unsupported option: ' + name);
            input[name] = options[name];
          }
        }
      }
      const result = JSON.parse(await annieAndroidBridge(definition.id, JSON.stringify(input)));
      if (result && result.__annieError) throw Object.assign(new Error(result.__annieError.message), result.__annieError);
      return result;
    };
  }
};
void 0;
