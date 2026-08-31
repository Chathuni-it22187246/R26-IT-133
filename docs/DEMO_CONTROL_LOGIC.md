# Demo Mode equipment control logic (project rules)

These formulas convert the **active Day or Night climate target** for the current crop and growth stage into Circulation Fan, Exhaust Fan and Fogger thresholds. They are **GreenHands Demo Mode project rules**. They are **not** published agronomic recommendations, **not** validated physical models, and **not** Sri Lankan greenhouse standards.

App wording (Automatic calculation): **“Thresholds are calculated from the selected crop profile and schedule period. No physical equipment is connected.”**

Profile / formula version: `2.1.2` (same numeric offsets as Phase 2: CSP/CDP/CON, ESP/EDP/EON, FSP/FON/FDP). Salad cucumber Day Demo \(T_{\mathrm{opt}}\) is the Table 3 **9AM–5PM** value of 25 °C; Night Demo \(T_{\mathrm{opt}}\) is Table 3 **9PM–6AM** 15 °C. The four-period schedule is not collapsed into one research number.

## Climate target used by Demo equipment

Day and Night are both stored. Automatic thresholds are calculated from the **currently selected period**. There is no third editable “selected climate target”.

| Input | Meaning | Units |
| --- | --- | --- |
| \(T_{\mathrm{opt}}\) | Temperature of the **selected schedule period** (Day or Night). | °C |
| \(\mathrm{RH}_{\mathrm{opt}}\) | Humidity of the **selected schedule period**. | % RH |

Assumptions shared by all formulas:

- Single-point Demo control (no spatial heatmaps, no physics, no real equipment).
- Fixed ±2 °C or ±2 % RH bands; not tuned to greenhouse volume, fan capacity or Sri Lankan psychrometrics.
- Later greenhouse validation is required before any real actuator is driven.

Source category for every formula below: **`PROJECT_CONTROL_RULE`**. Not a published agronomic recommendation.

---

## Circulation Fan

| | |
| --- | --- |
| **Inputs** | \(T_{\mathrm{opt}}\) (°C) |
| **Outputs** | CSP, CDP, CON (°C) |
| **Formulas** | \(\mathrm{CSP} = T_{\mathrm{opt}}\); \(\mathrm{CDP} = \mathrm{CSP} - 2\); \(\mathrm{CON} = \mathrm{CSP} + 2\) |
| **Assumptions** | Circulation starts at CON and stops at CDP. CSP is the set-point used to derive the band. |
| **Need for later validation** | Yes. Band width is a project choice, not a crop response curve. |

Worked example: \(T_{\mathrm{opt}} = 25.0\) °C → CSP 25.0, CDP 23.0, CON 27.0.

## Exhaust Fan

| | |
| --- | --- |
| **Inputs** | CSP and CON from Circulation (°C) |
| **Outputs** | ESP, EDP, EON (°C) |
| **Formulas** | \(\mathrm{ESP} = \mathrm{CSP} + 4\); \(\mathrm{EDP} = \mathrm{CON}\); \(\mathrm{EON} = \mathrm{ESP} + 2\) |
| **Assumptions** | Exhaust is a second stage when circulation cannot hold the target. EDP stays linked to CON. Because \(\mathrm{CON} = \mathrm{CSP}+2\), it follows that \(\mathrm{EDP} = \mathrm{ESP}-2\). For \(T_{\mathrm{opt}}=25\), ESP is **29 °C**, not 30 °C. |
| **Need for later validation** | Yes. The +4 °C exhaust offset is a project rule. |

Worked example: CSP 25.0, CON 27.0 → ESP 29.0, EDP 27.0, EON 31.0.

## Fogger

| | |
| --- | --- |
| **Inputs** | \(\mathrm{RH}_{\mathrm{opt}}\) (% RH) |
| **Outputs** | FSP, FON, FDP (% RH) |
| **Formulas** | \(\mathrm{FSP} = \mathrm{RH}_{\mathrm{opt}}\); \(\mathrm{FON} = \mathrm{FSP} - 2\); \(\mathrm{FDP} = \mathrm{FSP} + 2\) |
| **Assumptions** | Fogger starts at FON (too dry) and stops at FDP (wet enough). Evaporative cooling is **not** modelled. |
| **Need for later validation** | Yes. Fogging may provide **limited evaporative cooling in warm, humid Sri Lankan conditions**; humidity may already be high, so the Demo band must not be read as a cooling design. |

Worked example: \(\mathrm{RH}_{\mathrm{opt}} = 70.0\) % → FON 68.0, FSP 70.0, FDP 72.0.

## Hard limits (also project rules)

Temperature input 5.0–50.0 °C and relative humidity 0–100 % are **application hard limits**, not agronomic optima. Values outside a research-supported suggested range may still be saved with a warning; values outside hard limits are rejected.
