# S51: builds and installs the Banglu input method (no Xcode — SPM + scripts).
APP      = build/Banglu.app
BINARY   = .build/release/BangluIME
INSTALL  = $(HOME)/Library/Input\ Methods/Banglu.app

.PHONY: engine build bundle install uninstall test icon

engine:
	./scripts/build-engine.sh

test: engine
	swift test

build:
	swift build -c release

icon:
	mkdir -p Resources/built
	sips -z 32 32 ../desktop-app/icons/banglu.png \
	  --out Resources/built/menuicon.png >/dev/null

bundle: build icon
	rm -rf $(APP)
	mkdir -p $(APP)/Contents/MacOS $(APP)/Contents/Resources
	cp $(BINARY) $(APP)/Contents/MacOS/BangluIME
	cp Resources/Info.plist $(APP)/Contents/
	cp Resources/built/banglu-engine.bundle.js $(APP)/Contents/Resources/
	cp Resources/built/banglu-slim.json $(APP)/Contents/Resources/
	cp Resources/built/menuicon.png $(APP)/Contents/Resources/
	cp Resources/appcompat.json $(APP)/Contents/Resources/
	codesign --force --deep --sign - $(APP)
	@echo "bundle ready: $(APP)"

install: engine bundle
	rm -rf $(INSTALL)
	cp -R $(APP) $(HOME)/Library/Input\ Methods/
	@echo "Installed. Now: System Settings → Keyboard → Input Sources → + → বাংলা → বাংলু"
	@echo "(If it does not appear, log out and back in once — TIS caches the list.)"

uninstall:
	rm -rf $(INSTALL)
	@echo "Removed. Also remove the input source in System Settings."
