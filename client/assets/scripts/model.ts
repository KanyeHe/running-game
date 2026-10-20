export interface Combination {
  type: string;
  cardIds: number[];
  wildRanks: Record<string, number>;
  value: number;
  groups: number;
  bodyRank: number;
  basis: string;
}

// Matches the server key byte for byte, including Java's list/map spacing.
export function combinationKey(c: Combination): string {
  const ids = `[${[...c.cardIds].sort((a, b) => a - b).join(', ')}]`;
  const wild = `{${Object.keys(c.wildRanks).map(Number).sort((a, b) => a - b).map(id => `${id}=${c.wildRanks[id]}`).join(', ')}}`;
  return `${c.type}:${ids}:${wild}:${c.value}:${c.groups}:${c.bodyRank}`;
}

export interface Snapshot {
  type: string;
  requestId?: string;
  code?: string;
  roomId: string;
  version: number;
  playerId: string;
  players: string[];
  ready: string[];
  online: Record<string, boolean>;
  policy: string;
  hand?: { id: number }[];
  counts?: number[];
  turn?: number;
  table?: Combination;
  trick?: number;
  winner?: number;
  scores?: number[];
  deadline?: number;
  serverTime?: number;
  legal?: Combination[];
  gameSerial?: number;
  events?: { version: number; type: string; player: number; trick: number; detail: string }[];
}

export interface Command {
  type: string;
  requestId: string;
  seq: number;
  roomId?: string;
  expectedVersion: number;
  token?: string;
  data: Record<string, string>;
}

export function cardText(id: number): string {
  if (id >= 52) return id === 52 ? '小王' : '大王';
  const rank = id % 13 + 1;
  return `${['♠', '♥', '♣', '♦'][Math.floor(id / 13)]}${({
    1: 'A',
    11: 'J',
    12: 'Q',
    13: 'K'
  } as Record<number, string>)[rank] ?? rank}`;
}
