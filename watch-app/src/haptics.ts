export interface VibratorOptions {
  mode: 'short' | 'long';
}

export interface VibratorDriver {
  vibrate(options: VibratorOptions): void;
}

function resolveSystemVibrator(): VibratorDriver | null {
  try {
    // In HarmonyOS Lite Wearable JS environment, '@system.vibrator' is provided as a built-in module
    // or global vibrator object.
    const globalScope = globalThis as unknown as {
      vibrator?: VibratorDriver;
      require?: (module: string) => VibratorDriver;
    };

    if (globalScope.vibrator && typeof globalScope.vibrator.vibrate === 'function') {
      return globalScope.vibrator;
    }

    if (typeof globalScope.require === 'function') {
      const systemVibrator = globalScope.require('@system.vibrator');
      if (systemVibrator && typeof systemVibrator.vibrate === 'function') {
        return systemVibrator;
      }
    }
  } catch {
    // Vibrator resolution failed or not in Lite Wearable runtime
  }
  return null;
}

export class HapticsService {
  private driver: VibratorDriver | null;

  constructor(driver?: VibratorDriver | null) {
    if (driver !== undefined) {
      this.driver = driver;
    } else {
      this.driver = resolveSystemVibrator();
    }
  }

  vibrateTurn(): void {
    this.triggerVibration('short');
  }

  vibrateArrival(): void {
    this.triggerVibration('long');
  }

  private triggerVibration(mode: 'short' | 'long'): void {
    if (!this.driver) {
      return;
    }

    try {
      this.driver.vibrate({ mode });
    } catch (error) {
      console.warn('Vibration failed:', error);
    }
  }
}
