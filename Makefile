# Glosdalen - Android App Makefile
# Usage: make <target>

# Variables
APP_NAME := glosdalen
APKS_DIR := apks

# Get version from Gradle (clean output)
VERSION := $(shell ./gradlew currentVersion -q | grep "Project version:" | cut -d' ' -f3)

# APK paths
RELEASE_APK_PATH := app/build/outputs/apk/release/app-release.apk
DEBUG_APK_PATH := app/build/outputs/apk/debug/app-debug.apk

# Default target
.PHONY: all
all: help

# Help target
.PHONY: help
help:
	@echo "Glosdalen Android App Build System"
	@echo ""
	@echo "Available targets:"
	@echo "  release       - Create a full release (tag, build, copy APK)"
	@echo "  build-release - Build release APK only"
	@echo "  build-debug   - Build debug APK"
	@echo "  install-debug - Install debug APK to connected device"
	@echo "  clean         - Clean build artifacts"
	@echo "  version       - Show current version"
	@echo "  test          - Run all tests"
	@echo "  lint          - Run lint checks"
	@echo ""
	@echo "Current version: $(VERSION)"

# Full release process
.PHONY: release
release: clean test lint
	@echo "🚀 Starting release process for $(APP_NAME) v$(VERSION)..."
	
	# Create release tag and push
	@echo "📋 Creating release tag..."
	./gradlew release
	
	make build-release
	make sync-apks

# Build release APK only (no tagging)
.PHONY: build-release
build-release:
	@echo "🔨 Building release APK..."
	./gradlew assembleRelease
	@mkdir -p $(APKS_DIR)
	@if [ -f "$(RELEASE_APK_PATH)" ]; then \
		cp "$(RELEASE_APK_PATH)" "$(APKS_DIR)/$(APP_NAME)-$(VERSION).apk"; \
		echo "✅ Release APK copied to: $(APKS_DIR)/$(APP_NAME)-$(VERSION).apk"; \
	else \
		echo "❌ Release APK not found"; \
		exit 1; \
	fi

# Build debug APK
.PHONY: build-debug
build-debug:
	@echo "🔨 Building debug APK..."
	./gradlew assembleDebug
	@mkdir -p $(APKS_DIR)
	@if [ -f "$(DEBUG_APK_PATH)" ]; then \
		cp "$(DEBUG_APK_PATH)" "$(APKS_DIR)/$(APP_NAME)-$(VERSION)-debug.apk"; \
		echo "✅ Debug APK copied to: $(APKS_DIR)/$(APP_NAME)-$(VERSION)-debug.apk"; \
	else \
		echo "❌ Debug APK not found"; \
		exit 1; \
	fi

# Install debug APK to connected device
.PHONY: install-debug
install-debug:
	@echo "📱 Installing debug APK..."
	./gradlew installDebug

# Clean build artifacts
.PHONY: clean
clean:
	@echo "🧹 Cleaning build artifacts..."
	./gradlew clean

# Show current version
.PHONY: version
version:
	@echo "Current version: $(VERSION)"

# Run all tests
.PHONY: test
test:
	@echo "🧪 Running tests..."
	./gradlew test testReleaseUnitTest

# Run lint checks
.PHONY: lint
lint:
	@echo "🔍 Running lint checks..."
	./gradlew lintRelease

# List available APKs
.PHONY: list-apks
list-apks:
	@echo "📦 Available APKs in $(APKS_DIR):"
	@if [ -d "$(APKS_DIR)" ]; then \
		ls -la $(APKS_DIR)/*.apk 2>/dev/null || echo "No APK files found"; \
	else \
		echo "$(APKS_DIR) directory does not exist"; \
	fi

# Sync APKs to cloud (customize the destination)
.PHONY: sync-apks
sync-apks:
	@echo "☁️ Syncing APKs to cloud..."
	@if [ -d "$(APKS_DIR)" ] && [ -d "$$HOME/Nextcloud/glosdalen/" ]; then \
		rsync -av $(APKS_DIR)/ $$HOME/Nextcloud/glosdalen/; \
		echo "✅ APKs synced to Nextcloud"; \
	else \
		echo "❌ APKs directory or Nextcloud directory not found"; \
	fi

shifted:
	gource --output-custom-log gource.log
	sed -i "s/Daniel Freiermuth/Daniel/g" gource.log
	# awk -F'|' '{ $$1 += 0; print $$0 }' OFS='|' gource.log > shifted.log

show-film: shifted
	gource shifted.log --log-format custom --auto-skip-seconds 0.2 --max-file-lag 3

save-film: shifted
	gource shifted.log --log-format custom -o - --auto-skip-seconds 1 --max-file-lag 1 | ffmpeg -y -f image2pipe -vcodec ppm -i - -vcodec libx265 -preset veryslow -crf 23 gource.mp4

# Verify release readiness
.PHONY: verify-release
verify-release:
	@echo "🔍 Verifying release readiness..."
	./gradlew verifyRelease
File: makefile
