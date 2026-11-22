# Calcs

MEP design calculations for Android (phone and tablet), fully offline.

- **Water Supply** – storage tank, WSFU → Hunter peak flow, main pipe, friction, pump; segment-by-segment pipe sizing; hot water heater.
- **Drainage** – fixtures & DFU (soil / waste), branches, stacks, building drain, vents (IPC).
- **Rainwater** – roof flow & leaders, horizontal storm drains (Manning).
- **Fire Fighting** – density/area demand, UAE wet risers, fire pump & tank, building zones, branch hydraulic calc with velocity check and sketch.
- **Unit converter.**

Every branch exports its calculation as **PDF** or **Excel** to `Downloads/Calcs`, with Open / Share.

The app is a local web page (`app/src/main/assets/www`) in a WebView. Calculations live in `engine.js`, tool screens in `tools.js`, and the PDF / Excel writers (`pdf.js`, `xlsx.js`) have no dependencies.

Build: every push runs GitHub Actions → download **Calcs-APK** from the run's artifacts.

Educational tool – verify every value against the governing code edition and authority requirements.
