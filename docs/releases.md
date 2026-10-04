# APK releases

Push a new `release/Vellurix-major.minor.patch.apk` file to `main` to publish a GitHub release. The workflow tags it with the APK version, uploads the APK, and uses the push commit subject as one bullet in the release description. It does not generate release notes containing commit IDs. Existing README download links are managed separately.
