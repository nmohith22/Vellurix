# APK releases

1. Run the full JVM and installed-app emulator suites on the source for the release. Both must pass before building or signing the release APK. If the source changes, rerun both suites. [Regression test instructions](../tests/README.md)
2. Build and sign the release APK. Verify the application ID, version name, increasing version code, and signing certificate. Record the results in the local development tracker.
3. Push the new `release/Vellurix-major.minor.patch.apk` to `main`. The publication job waits for both regression suites to pass on the pushed commit, then tags the APK version, uploads the APK, and uses the push commit subject as one bullet in the release description. After publishing, it updates the README download label and direct-release URL.
