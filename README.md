# India Offline Map

A GPS-only Android map for India.

## Design

- Map data is bundled into the APK as a PMTiles vector archive.
- The app reads only `LocationManager.GPS_PROVIDER` for live positioning.
- The manifest explicitly removes `INTERNET`, `ACCESS_NETWORK_STATE`, and `ACCESS_WIFI_STATE`.
- The release workflow fails if `INTERNET` appears in the final APK.
- No map tile server, geocoding API, routing API, or cloud location service is used at runtime.

## Map data

The build downloads the current India OpenStreetMap extract from Geofabrik and converts it to a compact PMTiles archive with Tilemaker. The resulting map is then copied into the APK, so the installed app can work without mobile data or Wi-Fi.

At the current build settings the map is generated through zoom level 12, with roads, waterways, water, land use/land cover, and place points.

OpenStreetMap data is © OpenStreetMap contributors and is distributed under the Open Database License (ODbL). Geofabrik provides the India extract used during the build.

## Build

Pushes to `main` automatically:

1. Download the current India OSM extract.
2. Generate `india.pmtiles`.
3. Bundle it into the Android APK.
4. Build a release APK.
5. Verify the APK has no `INTERNET` permission.
6. Publish the APK as a GitHub Release asset.

The generated APK can be installed directly on an Android device. The first launch copies the bundled map into the app's private storage; this is local file I/O only.
