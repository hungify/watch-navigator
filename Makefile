.PHONY: help device phone-build phone-install phone-run phone-test phone-lint phone-logs phone-clean \
        watch-build watch-dev watch-test watch-lint watch-format watch-validate watch-clean \
        server-dev server-test server-deploy test build lint format clean

# Default target: show help
help:
	@echo "======================================================================"
	@echo "                   WatchNavigator Developer CLI                       "
	@echo "======================================================================"
	@echo "Phone App (Android):"
	@echo "  make device          - Check ADB connected devices & troubleshooting"
	@echo "  make phone-build     - Build Phone App Debug APK"
	@echo "  make phone-install   - Install Debug APK to connected Android phone"
	@echo "  make phone-run       - Build, install and launch app on phone"
	@echo "  make phone-test      - Run Phone App unit tests (JVM)"
	@echo "  make phone-lint      - Run Android Lint analysis on Phone App"
	@echo "  make phone-logs      - Stream live Logcat filtered for WatchNavigator"
	@echo "  make phone-clean     - Clean Phone App Gradle build outputs"
	@echo ""
	@echo "Watch App (Huawei Lite Wearable / GT5):"
	@echo "  make watch-build     - Compile TypeScript to JS watch bundle"
	@echo "  make watch-dev       - Compile TypeScript in watch mode (auto-rebuild)"
	@echo "  make watch-test      - Run Watch App unit tests, linter & validation"
	@echo "  make watch-lint      - Run ESLint on Watch App TypeScript sources"
	@echo "  make watch-format    - Format Watch App code using Prettier"
	@echo "  make watch-validate  - Validate config.json, permissions & icon assets"
	@echo "  make watch-clean     - Clean compiled Watch App JS bundle"
	@echo ""
	@echo "Directions Proxy Server (Cloudflare Workers):"
	@echo "  make server-dev      - Run local Cloudflare Worker development server"
	@echo "  make server-test     - Run Server unit tests & typecheck"
	@echo "  make server-deploy   - Deploy Directions Proxy Worker to Cloudflare"
	@echo ""
	@echo "Monorepo / General:"
	@echo "  make build           - Build both phone APK and watch JS bundle"
	@echo "  make test            - Run full test suite across all subprojects"
	@echo "  make lint            - Run linters across phone and watch modules"
	@echo "  make format          - Format source code across the monorepo"
	@echo "  make clean           - Clean all build outputs and caches"
	@echo "======================================================================"

# --- Phone App Targets ---
device:
	@echo "Checking ADB connected devices..."
	@adb devices -l
	@if [ -z "$$(adb devices | grep -v 'List of devices' | grep -v '^$$')" ]; then \
		echo ""; \
		echo "⚠️  No device detected! Please check:"; \
		echo "  1. USB cable is securely connected (use File Transfer/MTP mode)."; \
		echo "  2. Developer Options > USB Debugging is turned ON on phone."; \
		echo "  3. Accept the 'Allow USB debugging?' popup prompt on phone screen."; \
	elif adb devices | grep -q "unauthorized"; then \
		echo ""; \
		echo "⚠️  Device detected but UNAUTHORIZED! Please check:"; \
		echo "  1. Unlock your phone screen."; \
		echo "  2. Accept the 'Allow USB debugging?' popup prompt."; \
	else \
		echo ""; \
		echo "✅ Device is connected and ready!"; \
	fi

phone-build:
	@echo "Building Android debug APK..."
	@cd phone-app && ./gradlew assembleDebug
	@echo "✅ APK ready at: phone-app/app/build/outputs/apk/debug/app-debug.apk"

phone-install:
	@echo "Installing Debug APK onto device..."
	@if [ -f "phone-app/app/build/outputs/apk/debug/app-debug.apk" ]; then \
		adb install -r phone-app/app/build/outputs/apk/debug/app-debug.apk || (cd phone-app && ./gradlew installDebug); \
	else \
		cd phone-app && ./gradlew installDebug; \
	fi
	@echo "✅ Installed successfully!"

phone-run: phone-install
	@echo "Launching WatchNavigator on device..."
	@adb shell am start -n com.watchnavigator/.MainActivity
	@echo "🚀 App launched on phone!"

phone-test:
	@echo "Running Phone App unit tests..."
	@cd phone-app && ./gradlew test

phone-lint:
	@echo "Running Phone App Android Lint..."
	@cd phone-app && ./gradlew lintDebug

phone-logs:
	@echo "Streaming Logcat (Ctrl+C to stop)..."
	@adb logcat -v time -s WatchNavigator:V WearEngine:V NavigationSessionManager:V HuaweiWearEngineService:V NavForegroundService:V NavigationService:V AndroidRuntime:E

phone-clean:
	@echo "Cleaning Phone App Gradle outputs..."
	@cd phone-app && ./gradlew clean
	@echo "✅ Phone clean complete."

# --- Watch App Targets ---
watch-build:
	@echo "Building Watch App TypeScript bundle..."
	@cd watch-app && pnpm install && pnpm build
	@echo "✅ Watch JS bundle ready in watch-app/entry/src/main/js/default/"

watch-dev:
	@echo "Starting Watch App TypeScript compiler in watch mode..."
	@cd watch-app && pnpm run watch

watch-test:
	@echo "Running Watch App tests & validation..."
	@cd watch-app && pnpm test

watch-lint:
	@echo "Running ESLint on Watch App..."
	@cd watch-app && pnpm run lint

watch-format:
	@echo "Formatting Watch App source code with Prettier..."
	@cd watch-app && pnpm run format

watch-validate:
	@echo "Validating Watch App config, permissions & icon assets..."
	@cd watch-app && pnpm run validate

watch-clean:
	@echo "Cleaning Watch App compiled bundle..."
	@rm -rf watch-app/entry/src/main/js/default/types.js \
		watch-app/entry/src/main/js/default/session.js \
		watch-app/entry/src/main/js/default/wearengine.js \
		watch-app/entry/src/main/js/default/haptics.js \
		watch-app/entry/src/main/js/default/app.js \
		watch-app/entry/src/main/js/default/pages/index/index.js
	@echo "✅ Watch clean complete."

# --- Directions Proxy Server Targets ---
server-dev:
	@echo "Starting local Directions Proxy Worker..."
	@cd server && pnpm dev

server-test:
	@echo "Running Directions Proxy Server tests..."
	@cd server && pnpm test

server-deploy:
	@echo "Deploying Directions Proxy Worker to Cloudflare..."
	@cd server && pnpm run deploy

# --- Monorepo / Combined Targets ---
build: phone-build watch-build
	@echo "✅ Both phone APK and watch bundle built successfully."

test: phone-test watch-test server-test
	@echo "✅ All tests passed across all subprojects (Phone, Watch, Server)."

lint: phone-lint watch-lint
	@echo "✅ Lint checks passed across Phone and Watch modules."

format: watch-format
	@echo "✅ Code formatting applied."

clean: phone-clean watch-clean
	@echo "✅ All build outputs and caches cleaned."
