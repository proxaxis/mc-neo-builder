// CheckBot: REPL-controlled second bot for NeoBuilder verification.
// stdin lines are evaluated as JS expressions/statements with `bot` and
// helpers (scan/entities/pos/sleep) in scope. Results print as "=> ...".
import mineflayer from 'mineflayer';
import readline from 'node:readline';

const username = process.env.BOT_NAME || 'CheckBot';
const bot = mineflayer.createBot({
  host: process.env.MC_HOST || 'testserver',
  port: Number(process.env.MC_PORT || 25565),
  username,
  version: '26.1',
});

// Workaround: full vanilla resource-pack handshake for 26.x servers
const client = bot._client;
client.removeAllListeners('add_resource_pack');
client.on('add_resource_pack', (data) => {
  for (const result of [3, 4, 0]) {
    client.write('resource_pack_receive', { uuid: data.uuid, result });
  }
});

bot.once('spawn', () => {
  // Workaround (mineflayer#4189): one move packet per client tick
  bot.on('physicsTick', () => { bot._client.write('tick_end', {}); });
  console.log('[spawn]', JSON.stringify(pos()));
});
bot.on('kicked', (r) => console.log('[kicked]', typeof r === 'string' ? r : JSON.stringify(r)));
bot.on('error', (e) => console.log('[error]', e.message));
bot.on('end', (r) => { console.log('[end]', r); process.exit(0); });
bot.on('death', () => console.log('[death]'));
bot.on('respawn', () => console.log('[respawn]', JSON.stringify(pos())));
bot.on('message', (json) => {
  const t = json.toString().trim();
  if (t) console.log('[msg]', t.slice(0, 300));
});
bot.on('entityHurt', (e) => console.log('[hurt]', name(e), 'hp=', e.health));
bot.on('entitySpawn', (e) => {
  if (e.entityType !== undefined && e.name !== 'item' && e.name !== 'experience_orb')
    console.log('[ent+]', name(e), e.name || e.entityType, at(e));
});
bot.on('entityGone', (e) => { if (e.name && e.name !== 'item') console.log('[ent-]', name(e), e.name); });

function pos() {
  const p = bot.entity?.position;
  return p ? { x: Math.floor(p.x), y: Math.floor(p.y), z: Math.floor(p.z), dim: bot.game?.dimension } : null;
}
function name(e) { return e.username || e.displayName || e.name || String(e.entityType); }
function at(e) { const p = e.position; return p ? `${Math.floor(p.x)},${Math.floor(p.y)},${Math.floor(p.z)}` : '?'; }
function entities(maxDist = 32) {
  const me = bot.entity?.position;
  return Object.values(bot.entities)
    .filter((e) => e !== bot.entity && (!me || !e.position || e.position.distanceTo(me) <= maxDist))
    .map((e) => ({
      name: e.name, type: e.entityType, uuid: e.uuid?.slice(0, 8),
      pos: at(e), custom: e.metadata?.[2]?.toString?.() || undefined,
    }));
}
function sleep(ms) { return new Promise((r) => setTimeout(r, ms)); }
async function scan(what, maxDistance = 64, count = 256) {
  const found = await bot.findBlocks({ matching: what, maxDistance, count });
  return found.map((v) => `${v.x},${v.y},${v.z}`);
}

const rl = readline.createInterface({ input: process.stdin, terminal: false });
rl.on('line', async (raw) => {
  const line = raw.trim();
  if (!line) return;
  try {
    let r;
    try {
      r = await eval(`(async()=>{return(${line}\n)})()`);
    } catch (e) {
      if (e instanceof SyntaxError) r = await eval(`(async()=>{${line}\n})()`);
      else throw e;
    }
    console.log('=>', typeof r === 'object' ? JSON.stringify(r) : String(r));
  } catch (e) {
    console.log('ERR', e.message);
  }
});
console.log('[boot] connecting as', username);
