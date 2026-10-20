// Generated from client/assets/scripts; edit shared TypeScript source.
// Matches the server key byte for byte, including Java's list/map spacing.
export function combinationKey(c) {
    const ids = `[${[...c.cardIds].sort((a, b) => a - b).join(', ')}]`;
    const wild = `{${Object.keys(c.wildRanks).map(Number).sort((a, b) => a - b).map(id => `${id}=${c.wildRanks[id]}`).join(', ')}}`;
    return `${c.type}:${ids}:${wild}:${c.value}:${c.groups}:${c.bodyRank}`;
}
export function cardText(id) {
    if (id >= 52)
        return id === 52 ? '小王' : '大王';
    const rank = id % 13 + 1;
    return `${['♠', '♥', '♣', '♦'][Math.floor(id / 13)]}${{
        1: 'A',
        11: 'J',
        12: 'Q',
        13: 'K'
    }[rank] ?? rank}`;
}
