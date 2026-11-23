# Calcs

MEP design calculations for Android (phone and tablet), fully offline.

- **Water Supply** – storage tank, WSFU → Hunter peak flow, main pipe, friction, pump; segment-by-segment pipe sizing; hot water heater.
- **Drainage** – fixtures & DFU (soil / waste), branches, stacks, building drain, vents (IPC).
- **Rainwater** – roof flow & leaders, horizontal storm drains (Manning).
- **Fire Fighting** – density/area demand, UAE wet risers, fire pump & tank, building zones, branch hydraulic calc with velocity check and sketch.
- **HVAC** – cooling load room by room (TR, CFM), fresh air & exhaust (ASHRAE 62.1), duct sizing (equal friction) & fan ESP.
- **Unit converter.**

Every branch exports its calculation as **PDF** or **Excel** to `Downloads/Calcs`, with Open / Share.

Native Android app in Kotlin + Jetpack Compose. Layouts adapt to phone and tablet: tables on wide screens, row cards on narrow ones; rows can be added and removed freely; everything is saved automatically.

- `core/` – pure Kotlin: calculation engines (`Engine.kt`, `Hvac.kt`), screen definitions (`Tools*.kt`), report model, Excel writer, PDF layout.
- `ui/` – Compose screens, adaptive grid, fire-branch sketch, export (Android `PdfDocument`, MediaStore).

Build: every push runs GitHub Actions → download **Calcs-APK** from the run's artifacts.

Educational tool – verify every value against the governing code edition and authority requirements.
