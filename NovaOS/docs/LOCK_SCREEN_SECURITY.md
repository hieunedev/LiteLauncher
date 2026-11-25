# Nova Lock Screen — Technical Boundary

## Product decision
Nova Lock Screen is an optional companion experience, not a replacement security boundary. Android's system Keyguard remains the only authority for PIN, password, pattern and biometric authentication.

## Safe implementation options
1. Wallpaper and clock customization surfaces that do not claim to secure the device.
2. An opt-in companion activity launched by the user, with clear disclosure that it is not the system lock screen.
3. Notification content only through supported Android APIs and only after the user grants the relevant permission.
4. Quick actions limited to app-owned actions or official system panels.

## Prohibited implementation
- Capturing, drawing over, storing or forwarding credentials.
- Fake PIN/password screens or biometric prompts.
- Attempting to dismiss, bypass or weaken Keyguard.
- Accessibility abuse or overlay tricks to impersonate system UI.
- Claiming secure lock-screen protection where Android does not provide it.

## Compatibility gates
Test on AOSP emulator and OEM devices. OEM behavior differs for lock-screen wallpaper, notifications, full-screen intents and background activity launch. Unsupported behavior must degrade gracefully.

## Initial milestone
Deliver a design prototype and capability matrix first. Implement only the companion surface and supported wallpaper/preferences. Defer notification listener and AOD until a separate privacy and battery review.
