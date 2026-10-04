# APK releases

Push a new `release/Vellurix-major.minor.patch.apk` file to `main` to publish a GitHub release. The workflow tags it with the APK version, uploads the APK, and uses the push commit subject as one bullet in the release description. It does not generate release notes containing commit IDs. After publishing, it updates the README download label and direct-release URL to the newest APK already in `release/`.
