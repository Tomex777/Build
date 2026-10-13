// Hand-written glue: the generated file declares `interface Annie`; this exposes it as the global `annie`.
// (Non-generated parts of the API such as http/files/env get their typings added here as M0a proceeds.)
declare const annie: Annie;

interface Annie {
  runtime: {
    call(path: string, input: unknown, functionName?: string): Promise<unknown>;
  };
}
