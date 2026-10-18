# Third-party notices

Number Lookup's original code and documentation use [Apache-2.0](LICENSE).
Third-party software, data, and icons retain their upstream licenses.

## Google libphonenumber

Components: `libphonenumber` 9.0.41, `geocoder` 3.41, `carrier` 2.41, and
`prefixmapper` 3.41, including their bundled offline prefix metadata.

Copyright The Libphonenumber Authors. Apache-2.0.

- [Source](https://github.com/google/libphonenumber/tree/v9.0.41)
- [License text](app/src/main/assets/licenses/libphonenumber.txt)

## AndroidX and Jetpack Compose

Components include Compose UI, Foundation, Animation, Material 3, Activity,
Lifecycle, Core, and their AndroidX runtime dependencies.

Copyright The Android Open Source Project. Apache-2.0.

- [Source](https://android.googlesource.com/platform/frameworks/support/)
- [Apache-2.0 text](LICENSE)

## Kotlin, kotlinx libraries, and JetBrains annotations

Copyright JetBrains s.r.o. and Kotlin Programming Language contributors.
Apache-2.0.

- [Kotlin source](https://github.com/JetBrains/kotlin)
- [kotlinx.coroutines source](https://github.com/Kotlin/kotlinx.coroutines)
- [kotlinx.serialization source](https://github.com/Kotlin/kotlinx.serialization)
- [JetBrains annotations source and license](https://github.com/JetBrains/java-annotations/blob/master/LICENSE.txt)
- [Apache-2.0 text](LICENSE)

## Guava ListenableFuture

The transitive `com.google.guava:listenablefuture:1.0` API is part of Google's
Guava project. Copyright The Guava Authors. Apache-2.0.

- [Source and licensing](https://github.com/google/guava)
- [Apache-2.0 text](LICENSE)

## JSpecify

The transitive `org.jspecify:jspecify:1.0.0` annotations use Apache-2.0.
Copyright the JSpecify Authors.

- [Source](https://github.com/jspecify/jspecify)
- [Upstream license](https://github.com/jspecify/jspecify/blob/main/LICENSE)
- [Apache-2.0 text](LICENSE)

## Google Fonts Material Symbols

Material Symbols Rounded vector drawables come from Google's official icon
repository. They use Apache-2.0. Local modifications change the Android theme
attribute namespace and, where applicable, enable automatic RTL mirroring.

- [Source](https://github.com/google/material-design-icons)
- [License text](app/src/main/assets/licenses/material-symbols.txt)

## China phone-prefix data

Copyright (c) 2026 Jieeit. MIT License.

The bundled `china_phone.dat` contains 520,170 seven-digit prefixes with an
upstream data cutoff of May 2026. It was obtained from commit
`e963c60de92383ca2c32eb318b10c469b34d11a4` of `jieeit/phone` and is bundled
unchanged. This data remains under MIT, independently of this project's license.

- [Source and pinned revision](https://github.com/jieeit/phone/tree/e963c60de92383ca2c32eb318b10c469b34d11a4)
- [License text](app/src/main/assets/licenses/jieeit-phone.txt)
- [Data provenance and checksum](docs/DATA_SOURCES.md)

## Gradle Wrapper

The Gradle Wrapper scripts and JAR are distributed under Apache-2.0.

- [Gradle source and licensing](https://github.com/gradle/gradle/blob/master/LICENSE)
- [Apache-2.0 text](LICENSE)

## Application artwork

The launcher artwork was generated for this project with GPT Image. It is
distributed with the project under Apache-2.0. The UI symbols above have their
separate upstream attribution. See [the icon record](docs/ICON.md).

The app's About → Open-source licenses pages also include the project license,
third-party attribution, and the relevant full Apache-2.0 and MIT license texts.
