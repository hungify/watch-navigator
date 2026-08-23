function resolveSystemVibrator() {
    try {
        // In HarmonyOS Lite Wearable JS environment, '@system.vibrator' is provided as a built-in module
        // or global vibrator object.
        const globalScope = globalThis;
        if (globalScope.vibrator && typeof globalScope.vibrator.vibrate === 'function') {
            return globalScope.vibrator;
        }
        if (typeof globalScope.require === 'function') {
            const systemVibrator = globalScope.require('@system.vibrator');
            if (systemVibrator && typeof systemVibrator.vibrate === 'function') {
                return systemVibrator;
            }
        }
    }
    catch {
        // Vibrator resolution failed or not in Lite Wearable runtime
    }
    return null;
}
export class HapticsService {
    constructor(driver) {
        if (driver !== undefined) {
            this.driver = driver;
        }
        else {
            this.driver = resolveSystemVibrator();
        }
    }
    vibrateTurn() {
        this.triggerVibration('short');
    }
    vibrateArrival() {
        this.triggerVibration('long');
    }
    triggerVibration(mode) {
        if (!this.driver) {
            return;
        }
        try {
            this.driver.vibrate({ mode });
        }
        catch (error) {
            console.warn('Vibration failed:', error);
        }
    }
}
