# Aspia Frister

Android-app og widget til Aspias SMV-kunder. Den viser kundens næste frist for moms og årsregnskab, og hvornår Aspia senest skal have materialet:

- Moms: 1 måned og 10 dage før momsfristen.
- Årsregnskab: 3 måneder før fristen.

En Hjælp-knap beder Aspia om at ringe kunden op.

- Fristerne beregnes i `DeadlineEngine.kt` og er testet mod skat.dk's tabel for 2026.
- Weekender og helligdage rykkes til næste bankdag.
- Aspias modtageradresse og Web3Forms-nøglen står i `Config.kt`.
- Hvert push bygger i GitHub Actions og laver en Release `build-N` med `AspiaFrister.apk`.
