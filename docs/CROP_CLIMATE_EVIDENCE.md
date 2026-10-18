# Crop climate evidence matrix (Phase 2.1.1, presentation 2.1.2)

This document records every numerical climate recommendation used in GreenHands. Agricultural source records were accepted in **2.1.1**. Profile version **2.1.2** changes only the user-facing Day/Night schedule model. Original published values are unchanged.

**Audit date:** 14 August 2026. **Correction date:** 14 August 2026 (Phase 2.1.1). **Presentation date:** 14 August 2026 (Phase 2.1.2).

**Phase 2.1.1 corrections:** Jayathilaka Tables 2 and 3 now supply salad-cucumber RH and the four-period temperature schedule. Chilli no longer inherits Alberta bell-pepper numbers; it uses Oh et al. 2019 hot-pepper guidance plus Gunawardena 2014 as local stress evidence only.

**Phase 2.1.2 presentation (does not invent numbers):**

- The configuration form uses **Day** and **Night** only. Selecting a period changes which values are shown or edited; both periods are saved together.
- The retired “Selected climate target (Demo equipment)” field is no longer in the active UI or new persistence. Migration rule: if a legacy custom `topt` differs from stored Day, it becomes the custom Day target and the stored Night suggestion is kept.
- Profiles that publish only a general target apply that same published value to both Day and Night internally (tomato nursery 25.05°C; bell-pepper nursery 25.5°C; lettuce germination 20°C).
- Salad cucumber **original Table 3 schedule remains**: 9PM–6AM 15°C, 6AM–9AM 20°C, 9AM–5PM 25°C, 5PM–9PM 20°C. The main form presents Day 25°C (range 20–25°C) and Night 15°C. Morning/evening 20°C transitions stay in Sources and View Source.
- Research limitations, author names and derivation classifications belong in Sources, View Source and this document, not on the main form.

**Rules applied:** no invented numbers; no AI-snippet-only values; midpoints labelled `DERIVED_MIDPOINT` with the calculation; missing stage-specific numbers inherited from a crop-level published band and labelled `CROP_LEVEL_INHERITED`; Sri Lankan field-crop pages are supporting evidence only, not greenhouse setpoints.

## Citation registry

| Source ID | Title | Author / organisation | Year | DOI or URL | Geographic context |
| --- | --- | --- | --- | --- | --- |
| SRC-DOA-TOMATO | Tomato (HORDI crop page) | Sri Lanka Department of Agriculture / HORDI | n.d. (web) | https://doa.gov.lk/hordi-crop-tomato/ | Sri Lanka field / highland guidance |
| SRC-DOA-CUCUMBER | Cucumber (HORDI crop page) | Sri Lanka Department of Agriculture / HORDI | n.d. (web) | https://doa.gov.lk/hordi-crop-cucumber/ | Sri Lanka field (wet zone / dry-zone Maha) |
| SRC-DOA-CAPSICUM | Capsicum (HORDI crop page) | Sri Lanka Department of Agriculture / HORDI | n.d. (web) | https://doa.gov.lk/hordi-crop-capsicum/ | Sri Lanka; mentions rain shelters / protected houses without T/RH setpoints |
| SRC-JAYATHILAKA-2022 | Impact of an IoT-based micro-climate monitoring and control system on salad cucumber | Jayathilaka, Adikaram, Kumarasinghe, Jayasinghe; *Sri Lankan Journal of Agriculture and Ecosystems* 4(2):25–40 | 2022 | https://doi.org/10.4038/sljae.v4i2.83 | Sri Lankan protected houses (salad cucumber) |
| SRC-GUNAWARDENA-2014 | The effects of temperature and water stresses on growth, yield and related characteristics of chilli (*Capsicum annuum* L.) | Gunawardena and De Silva; *OUSL Journal* 7:25–42 | 2014 | https://doi.org/10.4038/ouslj.v7i0.7306 | Nawala, Sri Lanka; temperature-regulated polytunnels, cv. MI-2 |
| SRC-OH-2019 | Fruit Development and Quality of Hot Pepper (*Capsicum annuum* L.) under Various Temperature Regimes | Oh and Koh; *Horticultural Science and Technology* 37:313–321 | 2019 | https://doi.org/10.7235/HORT.20190032 (HTML: https://www.hst-j.org/articles/xml/Db2K/) | Korea; cv. Muhanjilju in walk-in growth chambers |
| SRC-SHAMSHIRI-2018 | Review of optimum temperature, humidity, and vapour pressure deficit for microclimate evaluation and control in greenhouse cultivation of tomato | Shamshiri, Jones, Thorp, Ahmad, Man, Taheri; *Int. Agrophys.* 32:287–302 | 2018 | https://doi.org/10.1515/intag-2017-0005 | International greenhouse review; Table 4 is ‘Caruso’/‘Carusso’, Ohio A-shade greenhouse (Short et al. 2005 / El-Attal 1995) |
| SRC-ALBERTA-PEPPER | Production of sweet bell peppers (greenhouse) | Government of Alberta | n.d. (web guide, accessed 2026-08-14) | https://www.alberta.ca/production-of-sweet-bell-peppers | Commercial greenhouse, Alberta, Canada (temperate). Bell pepper only; not used for chilli. |
| SRC-ALBERTA-ENV | Management of the Greenhouse Environment | Government of Alberta | n.d. | https://www1.agric.gov.ab.ca/$department/deptdocs.nsf/all/opp2902?opendocument | Alberta greenhouse vegetable production. Bell pepper night T at flowering. |
| SRC-CORNELL-LETTUCE | Hydroponic Lettuce Handbook | Brechner, Both, Cornell CEA Program | 2013 (© Cornell University CEA Program 2013; PDF hosted 2019) | https://cea.cals.cornell.edu/files/2019/06/Cornell-CEA-Lettuce-Handbook-.pdf | Cornell CEA hydroponic greenhouse / pond system, USA |
| SRC-CAROTTI-2020 | Plant factories are heating up: hunting for the best combination of light intensity, air temperature and root-zone temperature in lettuce production | Carotti et al.; *Front. Plant Sci.* 11:592171 | 2020 | https://doi.org/10.3389/fpls.2020.592171 | Climate rooms, Wageningen, Netherlands; *Lactuca sativa* cv. Batavia Othilie |
| SRC-HAREL-2014 | The effect of mean daily temperature and relative humidity on pollen, fruit set and yield of tomato grown in naturally ventilated greenhouses | Harel, Fadida, Slepoy, Gantz, Shilo; *Agronomy* 4:167–177 | 2014 | https://doi.org/10.3390/agronomy4010167 | Protected cultivation (Israel / naturally ventilated GH). **Original PDF was not retrieved in this audit** (publisher access denied). The app does **not** store a number taken only from Harel. Shamshiri’s citation of Harel is recorded under SRC-SHAMSHIRI-2018. |
| SRC-PROJECT-CONTROL | GreenHands Demo Mode equipment formulas | GreenHands project | 2026 | `docs/DEMO_CONTROL_LOGIC.md` | Application Demo Mode only |

Jayathilaka PDF used: https://sljae.sljol.info/articles/83/files/submission/proof/83-1-458-1-10-20230202.pdf

## Evidence classification used in the app

| Code | App label |
| --- | --- |
| DIRECT_SRI_LANKA_PROTECTED_CULTURE | Sri Lankan protected-house evidence |
| SRI_LANKA_OFFICIAL_CROP_GUIDANCE | Sri Lankan official crop guidance |
| INTERNATIONAL_GREENHOUSE_GUIDANCE | International greenhouse guidance |
| CONTROLLED_ENVIRONMENT_RESEARCH | Controlled-environment research |
| DERIVED_MIDPOINT | Derived from cited range |
| CROP_LEVEL_INHERITED | Crop-level inherited (no false stage precision) |
| PROJECT_CONTROL_RULE | Demo control logic (not agronomic) |
| LOCAL_VALIDATION_REQUIRED | Local validation required |

## Phase 2 Tomato placeholders (audited — not retained)

| Stage | Phase 2 placeholder | Decision |
| --- | --- | --- |
| Nursery 22–26 °C / 24 °C; RH 65–75 / 70 | Uncited Demo placeholder | **Removed.** Replaced by Shamshiri Table 4 early-growth 24.0–26.1 °C |
| Vegetative 20–26 °C / 24 °C; RH 60–70 / 65 | Uncited | **Removed.** Replaced by Table 4 vegetative sun/night bands |
| Flowering 20–24 °C / 23 °C; RH 60–70 / 65 | Uncited | **Removed.** Replaced by Table 4 flowering-to-mature fruiting |
| Ripening 20–24 °C / 22 °C; RH 55–65 / 60 | Uncited | **Removed.** Table 4 does not split ripening; ripening inherits flowering-to-mature plus Shamshiri/Omafra harvest listings |

## Scientific names

| Crop | App scientific name | Notes from sources |
| --- | --- | --- |
| Tomato | *Solanum lycopersicum* | DOA page writes *Solanum lycopersicon*; Shamshiri uses *Lycopersicon esculentum* Mill. App keeps the requested *S. lycopersicum*. |
| Salad cucumber | *Cucumis sativus* | Matches DOA cucumber page. |
| Bell pepper / capsicum | *Capsicum annuum* | Matches DOA capsicum page and Alberta sweet bell pepper guide. |
| Chilli | *Capsicum annuum* | Same species as sweet pepper; Gunawardena used cv. MI-2; Oh et al. used cv. Muhanjilju. **Not** a copy of Bell Pepper or Tomato values. |
| Greenhouse lettuce | *Lactuca sativa* | Cornell handbook and Carotti. |

Stage names follow the Phase 2.1 prompt. Shamshiri Table 4 uses “Early growth”, “Vegetative”, and a combined “Flowering to mature fruiting”. Fruit-development/ripening stages that lack a separate table row are **inherited** from that combined band (documented below). Jayathilaka Table 2 uses nursery days 1–4 and 5–8, vegetative 9–28, and reproductive/maturity 29–120; app flowering and harvest both inherit the reproductive/maturity RH row.

---

## Evidence matrix

Columns: Crop | Stage | Parameter | Suggested range | Suggested target | Day/night | Source ID | Location in source | Classification | Derivation | App warning

### Tomato — supporting Sri Lankan official (not used as greenhouse setpoint)

| Crop | Stage | Parameter | Suggested range | Suggested target | Day/night | Source ID | Location | Classification | Derivation | App warning |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Tomato | Crop-level (field) | Air temperature | 21–24 °C | none in app | Not split | SRC-DOA-TOMATO | “Climatic requirements”: “Optimum temperature is 21-24 °C” | SRI_LANKA_OFFICIAL_CROP_GUIDANCE | Direct quote | Field/highland guidance (elevation 1000–2000 m). **Not** a greenhouse setpoint. Shown only as supporting context. |

### Tomato — greenhouse suggested starting profile (Shamshiri Table 4)

Table 4: “Optimal and failure values of T, RH and VPD for the tomato cultivar ‘Carusso’, according to the decision support system of Short et al. (2005)”. Lower/upper **optimal** bounds are used as the suggested range. Cultivar ‘Caruso’/‘Carusso’, Ohio greenhouse. **Sri Lankan greenhouse validation is not available for these setpoints.**

| Crop | Stage | Parameter | Suggested range | Suggested target | Day/night | Source ID | Location | Classification | Derivation | App warning |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Tomato | Germination and Nursery | Air temperature | 24.0–26.1 °C | 25.05 °C | Table 4 “Early growth / any” (no day/night split) | SRC-SHAMSHIRI-2018 | Table 4, Early growth, any light: lower optimal T 24, upper optimal T 26.1 | INTERNATIONAL_GREENHOUSE_GUIDANCE | DERIVED_MIDPOINT: (24.0+26.1)/2 = 25.05 | Ohio ‘Caruso’ DSS. Validate locally. Shamshiri also lists TA=25 °C as optimum for germination day and night (Van Ploeg and Heuvelink 2005; Omafra 2005) in the compiled T table — supporting, same review. |
| Tomato | Germination and Nursery | RH | 75–100 % | 75 % | Any light | SRC-SHAMSHIRI-2018 | Table 4 Early growth any: lower optimal RH 75, upper optimal RH 100 | INTERNATIONAL_GREENHOUSE_GUIDANCE | DIRECT lower-optimal as starting target (**not** midpoint of 75–100; midpoint 87.5 % would be a high disease-risk starting point) | Upper DSS bound 100 % RH is not a practical production setpoint. Crop-level review text also states 50–70 % RH as an optimal band “during the entire growth stages” (same paper, Optimum humidity section). LOCAL_VALIDATION_REQUIRED. |
| Tomato | Vegetative Growth | Day air temperature | 24–27 °C | 25.5 °C | Day / sun | SRC-SHAMSHIRI-2018 | Table 4 Vegetative, sun: lower opt T 24, upper 27 | INTERNATIONAL_GREENHOUSE_GUIDANCE | DERIVED_MIDPOINT: (24+27)/2 = 25.5 | Cloud row is 22–24 °C; night row is 18–20 °C. Demo \(T_{\mathrm{opt}}\) uses the **day/sun** target. |
| Tomato | Vegetative Growth | Night air temperature | 18–20 °C | 19.0 °C | Night | SRC-SHAMSHIRI-2018 | Table 4 Vegetative, night: 18–20 | INTERNATIONAL_GREENHOUSE_GUIDANCE | DERIVED_MIDPOINT: (18+20)/2 = 19.0 | Do not collapse day and night into one number. |
| Tomato | Vegetative Growth | RH | 70–80 % | 75 % | Sun/cloud/night rows all use 70–80 | SRC-SHAMSHIRI-2018 | Table 4 Vegetative RH lower 70 upper 80 | INTERNATIONAL_GREENHOUSE_GUIDANCE | DERIVED_MIDPOINT: (70+80)/2 = 75 | Same paper: ASABE 60–90 % described as appropriate for most greenhouse tomato varieties; 50–70 % as a crop-level optimal band. Not mixed from a second paper. |
| Tomato | Flowering and Fruit Set | Day air temperature | 24–27 °C | 25.5 °C | Day / sun | SRC-SHAMSHIRI-2018 | Table 4 “Flowering to mature fruiting”, sun: 24–27 | INTERNATIONAL_GREENHOUSE_GUIDANCE | DERIVED_MIDPOINT: (24+27)/2 = 25.5 | Combined source stage. Fruit-set progressively fails at TA ≥ 32 °C (Adams et al. 2001, compiled in Shamshiri). |
| Tomato | Flowering and Fruit Set | Night air temperature | 18–20 °C | 19.0 °C | Night | SRC-SHAMSHIRI-2018 | Table 4 flowering-to-mature, night: 18–20 | INTERNATIONAL_GREENHOUSE_GUIDANCE | DERIVED_MIDPOINT: (18+20)/2 = 19.0 | |
| Tomato | Flowering and Fruit Set | RH | 60–80 % | 70 % | All light rows 60–80 | SRC-SHAMSHIRI-2018 | Table 4 flowering-to-mature RH 60–80; body text: “Tomato pollination is significantly enhanced when RH is around 60% (Harel et al., 2014)” | INTERNATIONAL_GREENHOUSE_GUIDANCE | DERIVED_MIDPOINT: (60+80)/2 = 70 | 60 % pollination figure is **Shamshiri citing Harel**. Harel’s PDF was not independently re-read. |
| Tomato | Fruit Development, Ripening and Harvest | Day air temperature | 24–27 °C | 25.5 °C | Day / sun | SRC-SHAMSHIRI-2018 | Table 4 does **not** split ripening from flowering-to-mature fruiting | CROP_LEVEL_INHERITED | Inherited Table 4 flowering-to-mature sun band; midpoint (24+27)/2 = 25.5 | Shamshiri compiled Omafra (2005): TA = 19 °C day and night for mature fruit / harvest initiation; TA 20–22 °C daylight for full harvest. Those Omafra rows are **not** used as the app target because they conflict with Table 4 and were not re-read in the Omafra original. Shown as a warning only. |
| Tomato | Fruit Development, Ripening and Harvest | Night air temperature | 18–20 °C | 19.0 °C | Night | SRC-SHAMSHIRI-2018 | Inherited Table 4 night 18–20 | CROP_LEVEL_INHERITED | DERIVED_MIDPOINT of inherited night band | |
| Tomato | Fruit Development, Ripening and Harvest | RH | 60–80 % | 70 % | | SRC-SHAMSHIRI-2018 | Inherited Table 4 flowering-to-mature RH | CROP_LEVEL_INHERITED | DERIVED_MIDPOINT (60+80)/2 = 70 | |

**Tomato humidity vs temperature mixing:** T and RH for each stage both come from SRC-SHAMSHIRI-2018 Table 4 (plus Shamshiri body text for warnings). DOA 21–24 °C is **not** mixed into the greenhouse target.

### Salad cucumber

Jayathilaka et al. 2022 PDF (https://sljae.sljol.info/articles/83/files/submission/proof/83-1-458-1-10-20230202.pdf). **Table 2** and **Table 3** are the primary protected-house recommendations. The paper describes these as optimal conditions attempted in the IoT house, and states that maintaining the recommended temperature was **not economically feasible under local conditions**.

**Withdrawn 2.1.0 statements (incorrect):** “Cucumber RH not published”; “Cucumber night temperature unavailable”; “RH must remain empty until entered.”

| Crop | Stage | Parameter | Suggested range | Suggested target | Time of day | Source ID | Location | Classification | Derivation | App warning |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Salad cucumber | Crop-level (field) | Air temperature | “Optimum temperature is 30 °C” | none in app | Not split | SRC-DOA-CUCUMBER | HORDI cucumber page | SRI_LANKA_OFFICIAL_CROP_GUIDANCE | Direct | Field guidance only. **Not** a greenhouse setpoint. |
| Salad cucumber | All four app stages | Air temperature | 15 °C / 20 °C / 25 °C / 20 °C (four periods; not a single day/night pair) | Demo \(T_{\mathrm{opt}}\) = **25.0 °C** (9AM–5PM period only) | 9PM–6AM **15 °C**; 6AM–9AM **20 °C**; 9AM–5PM **25 °C**; 5PM–9PM **20 °C** | SRC-JAYATHILAKA-2022 | Table 3, “throughout the whole crop cycle” | DIRECT_SRI_LANKA_PROTECTED_CULTURE | DIRECT point values | Authors: recommended temperature was not feasible locally due to high cost. Measured IoT-house mean day T 31.61 °C and conventional 34.29 °C are **not** targets. The 22–30 °C Berghage band cited in the Results is **supporting background only** and does not replace Table 3. |
| Salad cucumber | Germination and Nursery | RH, days 1–4 | 80–90 % (6AM–6PM); 50 % (6PM–6AM) | Day **85 %**; night **50 %**. Demo RH = **85 %** | Table 2 | SRC-JAYATHILAKA-2022 | Table 2 nursery days 1–4 | DIRECT_SRI_LANKA_PROTECTED_CULTURE | Day `DERIVED_MIDPOINT` (80+90)/2 = 85; night `DIRECT` 50 | Two nursery sub-periods are shown; they are not averaged together. |
| Salad cucumber | Germination and Nursery | RH, days 5–8 | 65–70 % (6AM–6PM); 50 % (6PM–6AM) | Day **67.5 %**; night **50 %** | Table 2 | SRC-JAYATHILAKA-2022 | Table 2 nursery days 5–8 | DIRECT_SRI_LANKA_PROTECTED_CULTURE | Day `DERIVED_MIDPOINT` (65+70)/2 = 67.5; night `DIRECT` 50 | Shown as the second nursery sub-period. |
| Salad cucumber | Vegetative and Vine Development | RH | 65–75 % (6AM–6PM); 45–50 % (6PM–6AM) | Day **70 %**; night **47.5 %**. Demo RH = **70 %** | Table 2 vegetative days 9–28 | SRC-JAYATHILAKA-2022 | Table 2 | DIRECT_SRI_LANKA_PROTECTED_CULTURE | `DERIVED_MIDPOINT` (65+75)/2 = 70; (45+50)/2 = 47.5 | Day and night RH are not collapsed. |
| Salad cucumber | Flowering and Fruit Set | RH | 55–65 % (6AM–6PM); 50–55 % (6PM–6AM) | Day **60 %**; night **52.5 %**. Demo RH = **60 %** | Table 2 reproductive and maturity days 29–120 | SRC-JAYATHILAKA-2022 | Table 2 | `CROP_LEVEL_INHERITED` from reproductive/maturity row | `DERIVED_MIDPOINT` (55+65)/2 = 60; (50+55)/2 = 52.5 | Mapped onto Flowering because the paper does not split flowering from maturity RH. |
| Salad cucumber | Fruit Development and Harvest | RH | Same reproductive/maturity row | Same | Table 2 days 29–120 | SRC-JAYATHILAKA-2022 | Table 2 | `CROP_LEVEL_INHERITED` | Same midpoints | Mapped onto Harvest for the same reason. |

Jayathilaka Table 2 day counts (1–4, 5–8, 9–28, 29–120) do not exactly match the Results narrative (nursery 0–4 / 4–8, vegetative 8–20, reproductive 28–120). The app uses **Table 2 / Table 3 numbers**, not the narrative day-count variants.

### Bell pepper / capsicum

Alberta greenhouse sweet bell pepper (*C. annuum*). Temperate commercial greenhouse — **not Sri Lanka**. DOA capsicum page mentions protected houses but **does not publish T/RH setpoints**. Leaf-spot text “20–25 °C and higher RH” is **disease-favourable**, not a crop optimum.

| Crop | Stage | Parameter | Suggested range | Suggested target | Day/night | Source ID | Location | Classification | Derivation | App warning |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Bell pepper | Germination and Nursery | Air temperature | 25–26 °C | 25.5 °C | Day **and** night (constant) | SRC-ALBERTA-PEPPER | Germination: air 25–26 °C day and night | INTERNATIONAL_GREENHOUSE_GUIDANCE | DERIVED_MIDPOINT: (25+26)/2 = 25.5 | After emergence Alberta drops plug temperature to 23–24 °C and RH to 65–70 %. Sequential, not a second invented stage target. Warning shown. LOCAL_VALIDATION_REQUIRED in Sri Lanka. |
| Bell pepper | Germination and Nursery | RH | 75–80 % | 77.5 % | Day and night germination | SRC-ALBERTA-PEPPER | Germination RH 75–80 % | INTERNATIONAL_GREENHOUSE_GUIDANCE | DERIVED_MIDPOINT: (75+80)/2 = 77.5 | After ~4 days Alberta: RH 65–70 %. |
| Bell pepper | Vegetative Establishment | Day air temperature | 24 °C (point) | 24.0 °C | Day | SRC-ALBERTA-PEPPER | After first transplant: air 24 °C day / 22 °C night; 24-h average 22 °C | INTERNATIONAL_GREENHOUSE_GUIDANCE | DIRECT (published day point) | Later “5 weeks: 20 °C day and night” and “first week in GH: 20–21 °C constant” are additional sequential Alberta steps — shown as warnings, not extra invented stages. Vegetative growth optimum 21–23 °C and 24-h yield ~21 °C (Bakker 1989 cited by Alberta) also shown as context. |
| Bell pepper | Vegetative Establishment | Night air temperature | 22 °C (point) | 22.0 °C | Night | SRC-ALBERTA-PEPPER | After first transplant night 22 °C | INTERNATIONAL_GREENHOUSE_GUIDANCE | DIRECT | |
| Bell pepper | Vegetative Establishment | 24-h mean | 22 °C | 22.0 °C | Daily mean | SRC-ALBERTA-PEPPER | 24-h average 22 °C after first transplant | INTERNATIONAL_GREENHOUSE_GUIDANCE | DIRECT | Demo \(T_{\mathrm{opt}}\) uses **day 24 °C**, not the 24-h mean, so day/night evidence is not collapsed. |
| Bell pepper | Vegetative Establishment | RH | 70–80 % | 75 % | First week in greenhouse | SRC-ALBERTA-PEPPER | First week in GH: RH 70–80 % | INTERNATIONAL_GREENHOUSE_GUIDANCE | DERIVED_MIDPOINT: (70+80)/2 = 75 | After-emergence 65–70 % is an earlier sequential value. |
| Bell pepper | Flowering and Fruit Set | Day air temperature | 21–23 °C | 22.0 °C | Day / 24-h production context | SRC-ALBERTA-PEPPER | Vegetative growth optimum 21–23 °C; yield ~21 °C 24-h (Bakker 1989 cited) | INTERNATIONAL_GREENHOUSE_GUIDANCE | DERIVED_MIDPOINT of 21–23 = 22.0 for day/demo target | Fruit set reduced **above 27 °C**; high T 32–38 °C reduces set; female organs inhibited at night ≤14 °C. Pressman 1998 optimum **16 °C** for flowering is cited by Alberta and **conflicts** with 24-h yield ~21 °C — both shown; app does not collapse them. Night suggested range uses Pressman 16–18 °C as compiled on the Alberta management page (16–18 °C night for flowering/fruit setting). |
| Bell pepper | Flowering and Fruit Set | Night air temperature | 16–18 °C | 17.0 °C | Night | SRC-ALBERTA-PEPPER (Pressman 1998 cited); Alberta “Management of the Greenhouse Environment” states optimum night T for flowering and fruit setting 16–18 °C (Pressman 1998) | INTERNATIONAL_GREENHOUSE_GUIDANCE | DERIVED_MIDPOINT: (16+18)/2 = 17.0 | Not a Sri Lankan setpoint. |
| Bell pepper | Flowering and Fruit Set | RH | 70–80 % | 75 % | | SRC-ALBERTA-PEPPER | No separate flowering RH; inherit first-week GH 70–80 % | CROP_LEVEL_INHERITED | Midpoint 75 | Alberta: fruit set reduced by **low RH** (no numeric low-RH threshold on the pepper page). |
| Bell pepper | Fruit Development and Colouring | Day / night / RH | Same as flowering/production band | Same | | SRC-ALBERTA-PEPPER | No separate colouring T/RH | CROP_LEVEL_INHERITED | Inherited | LOCAL_VALIDATION_REQUIRED |

DOA capsicum: irrigation intervals and disease notes only — **not** used as greenhouse setpoints.

### Chilli

**Withdrawn 2.1.0 statement:** chilli climate is **not** inherited from Alberta sweet bell pepper. Same species (`Capsicum annuum`) does not justify copying bell-pepper setpoints.

Oh et al. 2019 (https://doi.org/10.7235/HORT.20190032; https://www.hst-j.org/articles/xml/Db2K/) Introduction: “Favorable temperatures for the growth of hot pepper are in the range of 25–28°C during the day and 18–22°C during the night. When the temperature falls below 15°C or exceeds 32°C, growth is usually retarded and the yield is decreased (Mercado et al., 1997; Erickson and Markhart, 2002).” Mercado and Erickson were **not** independently re-read; the numbers are taken from Oh’s statement. Methods: walk-in chambers held at **60–70 % RH**. That RH is an **experimental growing condition**, not a proven universal optimum. Oh’s own treatments found **20–25 °C** favourable for fruit development of cv. Muhanjilju — supporting context, not the app day/night targets.

Gunawardena & De Silva (2014) compared polytunnel **maximum** temperatures of **32 °C and 34 °C** versus ambient as **stress treatments**, not recommended setpoints.

| Crop | Stage | Parameter | Suggested range | Suggested target | Day/night | Source ID | Classification | Derivation | App warning |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Chilli | All four stages | Day air temperature | 25–28 °C | **26.5 °C**; Demo \(T_{\mathrm{opt}}\) = 26.5 | Day | SRC-OH-2019 | INTERNATIONAL_GREENHOUSE_GUIDANCE | `DERIVED_MIDPOINT` (25+28)/2 = 26.5, applied to every stage as `CROP_LEVEL_INHERITED` | Below 15 °C or above 32 °C: growth and yield warning. LOCAL_VALIDATION_REQUIRED. |
| Chilli | All four stages | Night air temperature | 18–22 °C | **20.0 °C** | Night | SRC-OH-2019 | INTERNATIONAL_GREENHOUSE_GUIDANCE | `DERIVED_MIDPOINT` (18+22)/2 = 20.0, `CROP_LEVEL_INHERITED` per stage | Same 15/32 °C warning. |
| Chilli | All four stages | RH | 60–70 % | **65 %** | Chamber condition (not a day/night split) | SRC-OH-2019 | CONTROLLED_ENVIRONMENT_RESEARCH | `DERIVED_MIDPOINT` (60+70)/2 = 65, `CROP_LEVEL_INHERITED` per stage | Experimental chamber RH, **not** a universal optimum. |
| Chilli | All four stages | High-temperature local evidence | 32 °C and 34 °C treatments | none (not targets) | Stress maxima | SRC-GUNAWARDENA-2014 | DIRECT_SRI_LANKA_PROTECTED_CULTURE (warning only) | Not used as setpoints | cv. MI-2, Nawala polytunnels. |

No Alberta source ID or Alberta numerical profile is attached to chilli.

### Greenhouse lettuce

Cornell CEA Hydroponic Lettuce Handbook, §3.3 Set-points (p. 19) and Chapter 4 production (germination room). Carotti is **experimental treatments**, not a production handbook setpoint.

| Crop | Stage | Parameter | Suggested range | Suggested target | Day/night | Source ID | Location | Classification | Derivation | App warning |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Lettuce | Germination and Nursery | Air temperature | Sequential published points 20 °C then 25 °C | 20.0 °C (germination room) | Germination room; not a day/night split | SRC-CORNELL-LETTUCE | Ch. 4: “The temperature is set for 20C (68F) in the germination room.” After first 24 h: “The temperature is raised to 25C” | CONTROLLED_ENVIRONMENT_RESEARCH / INTERNATIONAL_GREENHOUSE_GUIDANCE | DIRECT 20 °C starting target; 25 °C is the subsequent nursery setpoint (warning, not midpoint) | High humidity first two days (unquantified %). Carotti germinated dark at 18 °C then 20 °C — supporting, not the app target. |
| Lettuce | Germination and Nursery | RH | 50–70 % | 60 % | Production handbook set-point inherited for nursery (first two days “high humidity” has no %) | SRC-CORNELL-LETTUCE | §3.3 RH minimum 50 and no higher than 70 % | CROP_LEVEL_INHERITED | DERIVED_MIDPOINT: (50+70)/2 = 60 | First two days need high humidity to prevent desiccation (no numeric RH). |
| Lettuce | Vegetative Leaf Expansion | Day air temperature | 24 °C (point) | 24.0 °C | Day | SRC-CORNELL-LETTUCE | §3.3: “Air Temperature 24 C Day/19 C Night (75 F/65 F)” | INTERNATIONAL_GREENHOUSE_GUIDANCE | DIRECT | Cornell CEA hydroponic pond greenhouse, not Sri Lanka. |
| Lettuce | Vegetative Leaf Expansion | Night air temperature | 19 °C (point) | 19.0 °C | Night | SRC-CORNELL-LETTUCE | §3.3 | INTERNATIONAL_GREENHOUSE_GUIDANCE | DIRECT | |
| Lettuce | Vegetative Leaf Expansion | RH | 50–70 % | 60 % | | SRC-CORNELL-LETTUCE | §3.3 | INTERNATIONAL_GREENHOUSE_GUIDANCE | DERIVED_MIDPOINT (50+70)/2 = 60 | High RH encourages botrytis and mildew (handbook Relative Humidity section). |
| Lettuce | Head or Marketable Biomass Development | T day/night and RH | Same as §3.3 production set-points | Same | | SRC-CORNELL-LETTUCE | Handbook does not split heading from production set-points | CROP_LEVEL_INHERITED | Inherited §3.3 | Carotti: tip-burn increased with air temperature; combinations with Tair ≥ 28 °C, Troot ≤ 24 °C and high PPFD were discarded. **Warning only** (experimental plant factory, Netherlands). |
| Lettuce | Harvest Readiness | T day/night and RH | Same as §3.3 | Same | | SRC-CORNELL-LETTUCE | No separate harvest climate set-point | CROP_LEVEL_INHERITED | Inherited | LOCAL_VALIDATION_REQUIRED |

Demo \(T_{\mathrm{opt}}\) for lettuce vegetative/heading/harvest = **day 24 °C**. For germination/nursery = **20 °C**.

---

## Parameters without Sri Lankan greenhouse evidence

| Parameter | Status |
| --- | --- |
| Tomato greenhouse T/RH setpoints | No Sri Lankan protected-culture numeric setpoints. International (Shamshiri Table 4). DOA 21–24 °C is field guidance only. |
| Cucumber Table 3 feasibility | Table 3 **is** published. Authors could not maintain it economically under local conditions. |
| Capsicum/chilli greenhouse T/RH **setpoints** in Sri Lanka | DOA capsicum: no setpoints. Gunawardena: stress treatments only. Chilli starting profile is Oh et al. (Korea, international). |
| Lettuce in Sri Lankan greenhouses | No SL greenhouse lettuce climate paper in this source list. Cornell + Carotti only. |
| Bell pepper in Sri Lankan greenhouses | Jayathilaka survey notes some houses grew salad cucumber **and** bell pepper, without pepper T/RH setpoints. |

## Recommendations supported only by international evidence

Tomato Table 4; Alberta pepper (**bell pepper only**); Oh et al. hot-pepper day/night T and experimental RH (chilli); Cornell lettuce 24/19 °C and 50–70 % RH; Carotti tip-burn warning.

## Values needing later agronomist review

All five crops’ starting profiles under Sri Lankan protected-house weather, cultivar, substrate and greenhouse design. Especially: whether Table 3 cucumber temperatures can be achieved locally; chilli inherited crop-level Oh profile vs Sri Lankan chilli cultivars; Tomato Table 4 ‘Caruso’ Ohio DSS vs tropical houses; lettuce Cornell hydroponic pond vs local systems; using one Demo \(T_{\mathrm{opt}}\) alongside a four-period cucumber schedule.

## Demo Mode formulas

Circulation, Exhaust and Fogger offsets are **`PROJECT_CONTROL_RULE`**. See `DEMO_CONTROL_LOGIC.md`.
