# GreenHands UI visual QA — Phase 2.1.2

Device: Pixel 7 Pro API 35 (AVD). Theme captures use Dark unless noted. Screenshots: `docs/ui-review/`.

## Pass 1 — Pixel 7 Pro API 35

Reviewed first instrumented captures of Welcome, Login, Dashboard, Crops, Stage, Climate Day/Night, Circulation Automatic/Advanced, Summary, Sources, Account, Splash.

### Findings

1. **Splash title contrast.** The wordmark sat on the dark forest background without an explicit on-background colour, so “GreenHands” could read as near-black.
2. **Stage cards showed academic copy.** Early stage cards used `stage.explanation`, which contained Shamshiri / Table 4 language on the main form.
3. **Climate Edit / Reset / View Source sat below the fold.** Full-width 52 dp secondary buttons pushed View Source under the sticky Save bar.
4. **Account Log out sat below the fold.** Separate nav cards stacked too tall.
5. **Invisible vector strokes.** Some crop/logo paths used transparent strokes (fixed before recapture).
6. **`14_dashboard_landscape.png` was a black framebuffer.** Headless `wm size` and activity recreation after `requestedOrientation` wiped Compose content.
7. **`15_dashboard_light.png` was also black.** Captured after the orientation recreation, so `setContent` was gone.
8. **Stage Continue was not on-screen.** Selecting a card navigated, but the spec asks for a clear Continue action.
9. **Summary primary action was below the fold.** “Continue to Demo Simulation” lived in the scroll body under several Edit sections.
10. **Dashboard modules collapsed in screenshots.** `BoxWithConstraints` inside a vertically scrolling column failed to measure the module list, so Sensor Placement / Heat Distribution were missing and the viewport jumped to later cards.
11. **Equipment Advanced third field sat under sticky Save.** Continuous-operation and Reset to Automatic remain reachable by scroll.

### Corrections applied

1. Splash wrapped in a background `Surface`; wordmark uses `onBackground`. Theme now provides `LocalContentColor`.
2. Stage cards show display name + Day/Night summary only. Research explanations stay in View Source / Sources / evidence docs.
3. Climate Edit / Reset are compact text actions. View Source sits with the suggested-profile badges, above Day/Night.
4. Account groups Profile and Application rows; Log out is on the first screen.
5. Logo, greenhouse, and crop glyphs stroke with emerald/teal.
6. Debug `ComponentActivity` declares `configChanges` for orientation. Screenshots prefer a drawn decor-view bitmap when `screencap` is black. Landscape is captured after rotation, not via `wm size`.
7. Light Dashboard is captured before landscape rotation.
8. Stage screen has a sticky **Continue** (enabled when a stage is selected).
9. Summary uses a sticky **Continue to Demo Simulation**.
10. Dashboard module grid uses `LocalConfiguration` width/orientation (two columns in landscape / ≥600 dp).
11. Equipment Reset is a compact text action; Advanced fields remain scrollable above the sticky Save bar.

## Pass 2 — final screenshots

| File | Review |
| --- | --- |
| `docs/ui-review/01_splash_dark.png` | White GreenHands wordmark, greenhouse mark, preparing workspace. Pass. |
| `docs/ui-review/02_welcome_dark.png` | Required title, body, trust chips, Get Started / Sign In. Pass. |
| `docs/ui-review/03_login_dark.png` | Compact brand, Demo Authentication, fields, Sign In. Pass. |
| `docs/ui-review/04_dashboard_dark.png` | Header, sample env hero, Sensor / Heat / Harvesting modules, Demo Environment. Pass. Decision Making and recent configuration remain on scroll. |
| `docs/ui-review/05_crops_dark.png` | Crop cards with scientific names, available / research-supported, profile counts. Pass. |
| `docs/ui-review/06_stage_dark.png` | Crop header, progress, Day/Night summaries, Selected state, sticky Continue. Pass. |
| `docs/ui-review/07_climate_day_dark.png` | Suggested profile, View Source, Day selected, 25.5 °C / 75 %, demo-calc line, Edit / Reset, sticky Save. Pass. |
| `docs/ui-review/08_climate_night_dark.png` | Night 19.0 °C, demo-calc for Night. Pass. |
| `docs/ui-review/09_equipment_automatic.png` | Human-readable thresholds, Automatic recommended, Demo disconnected, Day target. Pass. |
| `docs/ui-review/10_equipment_advanced.png` | Advanced fields; third threshold and Reset remain on scroll. Accepted limitation. |
| `docs/ui-review/11_summary_dark.png` | Day/Night climate sections with Edit; sticky Continue to Demo Simulation. Pass. |
| `docs/ui-review/12_sources_dark.png` | Evidence library, search, crop filters, source cards, Open source. Pass. |
| `docs/ui-review/13_account_dark.png` | Avatar, demo account, grouped actions, Log out. Pass. |
| `docs/ui-review/14_dashboard_landscape.png` | Real landscape UI (not black). Env hero fills the short viewport; modules on scroll. Pass with limitation. |
| `docs/ui-review/15_dashboard_light.png` | Light forest surfaces, turquoise metrics, modules visible. Pass. |

## Remaining visual limitations

- Landscape Dashboard: the environmental hero uses most of the short height; workspace modules need a short scroll. Two-column modules apply when width ≥ 600 dp or orientation is landscape.
- Circulation Advanced: the third threshold and **Reset to Automatic Values** sit just below the sticky Save bar.
- Identity graphics are original Compose line symbols, not photoreal greenhouse photography.
- System sans-serif only (no licensed display font).
- Headless emulator `screencap` can go black on orientation change; Pass 2 landscape/light shots were recovered from the window drawing path.
