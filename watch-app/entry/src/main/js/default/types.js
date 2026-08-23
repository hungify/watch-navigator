export function getTurnIcon(turn) {
    const normalizedTurn = (turn || '').trim().toLowerCase().replace(/_/g, '-');
    switch (normalizedTurn) {
        case 'left':
        case 'turn-left':
        case 'sharp-left':
            return '←';
        case 'right':
        case 'turn-right':
        case 'sharp-right':
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
    if (typeof candidate.turn !== 'string' || candidate.turn.trim().length === 0) {
        return false;
    }
    if (candidate.distance_m !== undefined &&
        (typeof candidate.distance_m !== 'number' || !Number.isFinite(candidate.distance_m))) {
        return false;
    }
    if (candidate.distanceMeters !== undefined &&
        (typeof candidate.distanceMeters !== 'number' || !Number.isFinite(candidate.distanceMeters))) {
        return false;
    }
    if (candidate.street !== undefined && typeof candidate.street !== 'string') {
        return false;
    }
    if (candidate.streetName !== undefined && typeof candidate.streetName !== 'string') {
        return false;
    }
    return true;
}
