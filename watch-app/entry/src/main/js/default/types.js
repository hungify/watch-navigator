export function getCanonicalTurn(turn) {
    const normalizedTurn = (turn || '').toLowerCase().trim();
    switch (normalizedTurn) {
        case 'left':
        case 'turn-left':
        case 'turn_left':
            return 'left';
        case 'right':
        case 'turn-right':
        case 'turn_right':
            return 'right';
        case 'slight-left':
        case 'slight_left':
        case 'turn-slight-left':
            return 'slight-left';
        case 'slight-right':
        case 'slight_right':
        case 'turn-slight-right':
            return 'slight-right';
        case 'sharp-left':
        case 'sharp_left':
        case 'turn-sharp-left':
            return 'sharp-left';
        case 'sharp-right':
        case 'sharp_right':
        case 'turn-sharp-right':
            return 'sharp-right';
        case 'ramp-left':
            return 'ramp-left';
        case 'ramp-right':
            return 'ramp-right';
        case 'fork-left':
            return 'fork-left';
        case 'fork-right':
            return 'fork-right';
        case 'uturn':
        case 'uturn-left':
        case 'uturn_left':
        case 'u-turn':
            return 'uturn-left';
        case 'uturn-right':
        case 'uturn_right':
            return 'uturn-right';
        case 'roundabout':
            return 'roundabout';
        case 'roundabout-left':
        case 'roundabout_left':
            return 'roundabout-left';
        case 'roundabout-right':
        case 'roundabout_right':
            return 'roundabout-right';
        case 'arrive':
        case 'destination':
            return 'arrive';
        case 'depart':
            return 'depart';
        case 'straight':
            return 'straight';
        case 'continue':
            return 'continue';
        case 'merge':
            return 'merge';
        case 'stop':
        case 'terminate':
        case 'exit':
        case 'reset':
            return 'stop';
        default:
            return normalizedTurn || 'straight';
    }
}
export function getTurnIcon(turn) {
    const canonical = getCanonicalTurn(turn);
    switch (canonical) {
        case 'left':
            return '/common/turn_left.png';
        case 'right':
            return '/common/turn_right.png';
        case 'slight-left':
        case 'ramp-left':
        case 'fork-left':
            return '/common/turn_slight_left.png';
        case 'slight-right':
        case 'ramp-right':
        case 'fork-right':
            return '/common/turn_slight_right.png';
        case 'sharp-left':
            return '/common/turn_sharp_left.png';
        case 'sharp-right':
            return '/common/turn_sharp_right.png';
        case 'uturn-left':
        case 'uturn-right':
            return '/common/turn_uturn.png';
        case 'roundabout':
        case 'roundabout-left':
        case 'roundabout-right':
            return '/common/turn_roundabout.png';
        case 'arrive':
            return '/common/turn_arrive.png';
        case 'depart':
            return '/common/turn_depart.png';
        case 'straight':
        case 'continue':
        case 'merge':
        default:
            return '/common/turn_straight.png';
    }
}
export function formatDistance(meters) {
    if (meters == null) {
        return { value: '0', unit: 'm' };
    }
    const numericMeters = typeof meters === 'number' ? meters : Number(meters);
    if (!Number.isFinite(numericMeters) || numericMeters <= 0) {
        return { value: '0', unit: 'm' };
    }
    if (numericMeters < 1000) {
        return {
            value: String(Math.round(numericMeters)),
            unit: 'm'
        };
    }
    const km = numericMeters / 1000;
    return {
        value: (Math.round(km * 10) / 10).toFixed(1),
        unit: 'km'
    };
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
