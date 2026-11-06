# Horarios Bus El Puerto

Offline map shell for El Puerto de Santa María urban buses. Package `es.elpuertobus.app`.

## Maintainer build

Requires JDK 21 (Temurin), Android SDK platform **37.0** (`platforms;android-37.0`), and a populated gitignored `local/pipeline/` tree.

1. Rebuild the snapshot and route lines in the database:

   ```bash
   PYTHONPATH=pipeline python3 pipeline/run.py --offline
   ```

2. Build the municipal PMTiles basemap (needs `tippecanoe` and `pmtiles` on `PATH`):

   ```bash
   PYTHONPATH=pipeline python3 pipeline/build_basemap.py --refresh
   ```

3. Run the host unit test and compile the debug APK (set `JAVA_HOME` to JDK 21 if the default JDK is newer):

   ```bash
   cd buselpuertolibre
   ./gradlew testDebugUnitTest assembleDebug
   ```

Gradle copies `local/pipeline/output/transit.db` and `local/pipeline/assets/el_puerto.pmtiles` into `app/src/main/assets/` before each build. If either file is missing, the build fails with an explicit message.

Install on a connected phone (or run `./install-debug.sh` from this directory):

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Tap a stop on the map to open the timetable sheet (English or Spanish from the phone language). The app reads only bundled assets at runtime; it does not open the pipeline directory.
