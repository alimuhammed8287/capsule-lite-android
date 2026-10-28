# Capsule Lite collaboration rules

- Before starting any APK build, ask for and receive explicit user confirmation. A request to edit code or push source is not by itself build approval; a direct request to build is approval.
- Before implementing a substantial feature, summarize the intended behavior and wait for confirmation unless the user explicitly asked to implement it.
- Use human perception and practical device behavior to guide UI decisions. Explain the reason for consequential choices in plain language.
- At the end of each implementation update, report what is complete, what remains, an honest progress estimate for the current scope, and a short timeline for the next milestones. Separate source/build completion from physical-device validation.
- Include one or two relevant improvement suggestions in implementation updates. Suggestions are advisory; do not implement extra features beyond the confirmed scope.
- Never describe a feature as device-verified unless it has been exercised on a physical device and the result is known.
- Keep user-specific Android Studio and Gradle setup changes out of commits unless the user asks to include them.
