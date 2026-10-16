# ABR — App Building Rules

Applies to **every screen, component and Android application** developed under this standard.

## Visual language
- Design every screen as a coherent, considered experience; eliminate duplicate actions, filler labels and obvious explanations.
- Do not present stock Android/Material input fields, selection controls or sliders as the finished UI.
- **Slider rule (mandatory): never use an unstyled/default Android or Material slider.** Every range control must be intentionally customized to the app's own palette, track, thumb, value display and spacing. No default purple rails, oversized vertical thumbs, awkward split tracks or clashing controls.
- A styled slider is not a static decoration: dragging, tapping, keyboard/accessibility adjustments and value labels must work. Provide comfortable touch targets, readable contrast and a visible numeric/percentage/angle readout. Match its colors to light and dark modes and avoid visual overcrowding.
- Prefer polished vector/SVG icons, never emoji or arbitrary Unicode glyphs pretending to be UI icons. Keep spacing, typography, layout hierarchy and motion consistent.
- Use contextual tools rather than putting every control on screen simultaneously. Support smooth interactions without jank.

## Functionality & verification
- All displayed actions must have real behavior; never claim a placeholder is complete.
- For native Android builds use **minSdk 26** (Android 8.0) unless the user explicitly revises this requirement.
- Test user-visible flows, navigation, persistence, keyboard, resizing, exports and failure states.
- Build success is not enough: verify actual UI on the Android emulator/device, inspect screenshots, and review logcat.
- Check compatibility on API 26 and the project's current target emulator. Mark build success, emulator smoke success and visual review separately.
- Continue through implementation, compilation, functional checks, visual review and delivery. Do not claim untested functionality is verified.

## Slider-specific release gate
- Inspect the codebase for raw `Slider(` / `SeekBar` usages and either replace or explicitly style each one.
- Review every slider in each screen: range limits, sensitivity, thumb visibility, thumb touch area, dark/light appearance, readable values and accessibility.
- Confirm a real value changes as the user drags or taps. Capture at least one screenshot of a selected/adjusted control.
