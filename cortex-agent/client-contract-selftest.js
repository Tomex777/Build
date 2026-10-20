// A build-time contract between the installed Android client and Cortex Agent.
// No tokens, network access, or live WhatsApp operations are required.
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const app = readFileSync('cortex-android/app/src/main/java/com/night/cortex/server/CortexServerApi.kt', 'utf8');
const agent = readFileSync('cortex-agent/index.js', 'utf8');

const androidPaths = [...app.matchAll(/["'](\/api\/cortex\/[^"'\n]+)["']/g)].map((match) =>
  match[1].split('?')[0].replace(/\$\{[^}]+\}/g, ':id')
);
const exactAgentPaths = new Set(
  [...agent.matchAll(/url\.pathname === ['"](\/api\/cortex\/[^'"]+)['"]/g)].map((m) => m[1])
);
const dynamicAgentPaths = [
  { match: /^\/api\/cortex\/mscc\/modules\/:id\/reload$/, proof: 'const moduleReloadRoute = url.pathname.match(' },
  { match: /^\/api\/cortex\/mscc\/accounts\/:id\/(pair|reconnect|disconnect|repair)$/, proof: 'const pairRoute = url.pathname.match(' },
  { match: /^\/api\/cortex\/mscc\/accounts\/:id$/, proof: 'const removeAccountRoute = url.pathname.match(' },
];
const routes = [...new Set(androidPaths)];
assert.ok(routes.length >= 38, 'The Android API inventory is incomplete');
const unmatched = routes.filter((route) => {
  if (exactAgentPaths.has(route)) return false;
  return !dynamicAgentPaths.some(({ match, proof }) => match.test(route) && agent.includes(proof));
});
assert.deepEqual(unmatched, [], 'Cortex Android requests a route not exposed by the Agent');

for (const [method, route] of [
  ['GET', '/api/cortex/host/status'],
  ['GET', '/api/cortex/host/logs'],
  ['GET', '/api/cortex/host/files'],
  ['GET', '/api/cortex/host/backups'],
  ['GET', '/api/cortex/host/startup'],
  ['GET', '/api/cortex/host/settings'],
  ['GET', '/api/cortex/host/activity'],
  ['GET', '/api/cortex/host/environment'],
  ['POST', '/api/cortex/host/environment'],
  ['POST', '/api/cortex/host/environment/reveal'],
  ['GET', '/api/cortex/host/cloud'],
  ['GET', '/api/cortex/host/cloud/discover'],
  ['POST', '/api/cortex/host/cloud/verify'],
  ['GET', '/api/cortex/mscc/registry'],
  ['GET', '/api/cortex/mscc/pairing'],
]) {
  assert.ok(app.includes(route), 'Missing Android API route: ' + route);
  assert.ok(
    agent.includes("req.method === '" + method + "' && url.pathname === '" + route + "'"),
    'Agent method/path drift: ' + method + ' ' + route,
  );
}
// Ensure Cloud and ENV remain separate HTTP operations. One must not mask the other.
assert.ok(app.includes('fun environment(): EnvironmentState'));
assert.ok(app.includes('fun cloudDashboard(): CloudDashboard'));
console.log('PASS Cortex Android/Agent route contract: ' + routes.length + ' client routes, Cloud/ENV methods checked');
