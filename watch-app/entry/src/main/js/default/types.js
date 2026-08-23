export function getTurnIcon(turn) {
    const normalizedTurn = (turn || '').toLowerCase();
    switch (normalizedTurn) {
        case 'left':
        case 'turn-left':
            return '←';
        case 'right':
        case 'turn-right':
            return '→';
        case 'slight-left':
            return '↖';
        case 'slight-right':
            return '↗';
        case 'uturn':
            return '⮌';
        case 'arrive':
            return '★';
        case 'straight':
        default:
            return '↑';
    }
}
export function isValidNavigationPayload(data) {
    if (!data || typeof data !== 'object' || Array.isArray(data)) {
        return false;
    }
    const candidate = data;
    return typeof candidate.turn === 'string' && candidate.turn.trim().length > 0;
}
