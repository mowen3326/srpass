# 第三方组件许可声明

SrPass 以源码和 APK 两种形式分发，本文档同时适用于两者：第二节列出随 APK 打包的第三方组件，第六节列出随源码提供的第三方文件，第三、四、八节是与分发形式无关的许可条款与 NOTICE 原文。

APK 部分的清单取自 `./gradlew :app:dependencies --configuration releaseRuntimeClasspath` 的实际解析结果，共 151 个构件。只有在增删依赖或升级依赖版本时才需要更新本文档，应用自身版本号变化不影响其有效性。

最后按依赖树核对：2026-11-11。

## 一、许可证全文位置

| 许可证 | 全文位置 | 适用组件 |
|--------|----------|----------|
| Apache License 2.0 | 仓库根目录 `LICENSE`（与本项目自身许可为同一文本） | 其余全部组件 |
| MIT | 本文 3.1 | app.keemobile:kotpass、org.checkerframework:checker-qual |
| Bouncy Castle Licence | 本文 3.2 | org.bouncycastle:bcprov-jdk18on |

## 二、组件清单

本节按 `releaseRuntimeClasspath` 的实际解析结果逐条列出，与 `gradlew :app:dependencies` 输出一一对应：共 151 个构件，其中 2.1 为构建脚本直接声明的 20 个，2.2 为其余 131 个。每个组件的版本都是最终解析版本，不是声明版本。

### 2.1 直接依赖

由 `app/build.gradle.kts` 中 `implementation` 声明直接引入的构件，版本为 `releaseRuntimeClasspath` 的实际解析结果（KMP 库的 `-android` 变体与声明名不同，已在用途列注明）。

| 组件 | 版本 | 许可证 | 版权声明 | 用途 |
|------|------|--------|----------|------|
| androidx.core:core-ktx | 1.19.0 | Apache-2.0 | - | Kotlin 扩展 API |
| androidx.activity:activity-compose | 1.10.1 | Apache-2.0 | - | Activity 与 Compose 桥接 |
| androidx.lifecycle:lifecycle-runtime-ktx | 2.9.4 | Apache-2.0 | - | 生命周期感知 |
| androidx.lifecycle:lifecycle-runtime-compose | 2.9.4 | Apache-2.0 | - | Compose 生命周期工具 |
| androidx.lifecycle:lifecycle-viewmodel-compose | 2.9.4 | Apache-2.0 | - | ViewModel 与 Compose 桥接 |
| androidx.biometric:biometric | 1.1.0 | Apache-2.0 | - | 指纹 / 生物识别解锁 |
| androidx.compose:compose-bom | 2026.05.01 | Apache-2.0 | - | Compose 版本对齐（BOM） |
| androidx.compose.ui:ui-android | 1.11.2 | Apache-2.0 | - | Compose UI 基础（声明为 `ui`） |
| androidx.compose.ui:ui-graphics-android | 1.11.2 | Apache-2.0 | - | Compose 图形与绘制（声明为 `ui-graphics`） |
| androidx.compose.ui:ui-tooling-preview-android | 1.11.2 | Apache-2.0 | - | Compose 预览注解（声明为 `ui-tooling-preview`） |
| androidx.compose.material3:material3-android | 1.4.0 | Apache-2.0 | - | Material 3 组件库 |
| androidx.compose.material:material-icons-extended-android | 1.7.8 | Apache-2.0 | - | Material 图标集 |
| app.keemobile:kotpass | 0.13.0 | MIT | Copyright (c) 2021 Denis T. | KeePass KDBX 4.1 格式读写 |
| com.squareup.okio:okio-jvm | 3.15.0 | Apache-2.0 | - | ByteString 二进制读写（声明为 `okio`） |
| org.bouncycastle:bcprov-jdk18on | 1.79 | Bouncy Castle Licence | Copyright (c) 2000-2023 The Legion of the Bouncy Castle Inc. | Argon2id 密钥派生 |
| androidx.camera:camera-camera2 | 1.6.2 | Apache-2.0 | - | 验证器扫码取景 |
| androidx.camera:camera-lifecycle | 1.6.2 | Apache-2.0 | - | 相机与生命周期绑定 |
| androidx.camera:camera-view | 1.6.2 | Apache-2.0 | - | PreviewView 取景控件 |
| com.google.zxing:core | 3.5.4 | Apache-2.0 | - | QR 码解码 |
| androidx.exifinterface:exifinterface | 1.4.2 | Apache-2.0 | - | 导入图片的 EXIF 方向 |

### 2.2 传递依赖

以下构件由上述直接依赖与 Compose BOM 间接引入，未在 `app/build.gradle.kts` 中单独声明。

| 组件 | 版本 | 许可证 |
|------|------|--------|
| androidx.activity:activity | 1.10.1 | Apache-2.0 |
| androidx.activity:activity-ktx | 1.10.1 | Apache-2.0 |
| androidx.annotation:annotation | 1.10.0 | Apache-2.0 |
| androidx.annotation:annotation-experimental | 1.4.1 | Apache-2.0 |
| androidx.annotation:annotation-jvm | 1.10.0 | Apache-2.0 |
| androidx.appcompat:appcompat | 1.2.0 | Apache-2.0 |
| androidx.appcompat:appcompat-resources | 1.2.0 | Apache-2.0 |
| androidx.arch.core:core-common | 2.2.0 | Apache-2.0 |
| androidx.arch.core:core-runtime | 2.2.0 | Apache-2.0 |
| androidx.autofill:autofill | 1.0.0 | Apache-2.0 |
| androidx.camera.featurecombinationquery:featurecombinationquery | 1.6.2 | Apache-2.0 |
| androidx.camera.viewfinder:viewfinder-core | 1.5.1 | Apache-2.0 |
| androidx.camera:camera-camera2-pipe | 1.6.2 | Apache-2.0 |
| androidx.camera:camera-core | 1.6.2 | Apache-2.0 |
| androidx.camera:camera-video | 1.6.2 | Apache-2.0 |
| androidx.collection:collection | 1.5.0 | Apache-2.0 |
| androidx.collection:collection-jvm | 1.5.0 | Apache-2.0 |
| androidx.collection:collection-ktx | 1.5.0 | Apache-2.0 |
| androidx.compose.animation:animation | 1.11.2 | Apache-2.0 |
| androidx.compose.animation:animation-android | 1.11.2 | Apache-2.0 |
| androidx.compose.animation:animation-core | 1.11.2 | Apache-2.0 |
| androidx.compose.animation:animation-core-android | 1.11.2 | Apache-2.0 |
| androidx.compose.foundation:foundation | 1.11.2 | Apache-2.0 |
| androidx.compose.foundation:foundation-android | 1.11.2 | Apache-2.0 |
| androidx.compose.foundation:foundation-layout | 1.11.2 | Apache-2.0 |
| androidx.compose.foundation:foundation-layout-android | 1.11.2 | Apache-2.0 |
| androidx.compose.material:material-icons-core | 1.7.8 | Apache-2.0 |
| androidx.compose.material:material-icons-core-android | 1.7.8 | Apache-2.0 |
| androidx.compose.material:material-ripple | 1.11.2 | Apache-2.0 |
| androidx.compose.material:material-ripple-android | 1.11.2 | Apache-2.0 |
| androidx.compose.runtime:runtime | 1.11.2 | Apache-2.0 |
| androidx.compose.runtime:runtime-android | 1.11.2 | Apache-2.0 |
| androidx.compose.runtime:runtime-annotation | 1.11.2 | Apache-2.0 |
| androidx.compose.runtime:runtime-annotation-android | 1.11.2 | Apache-2.0 |
| androidx.compose.runtime:runtime-retain | 1.11.2 | Apache-2.0 |
| androidx.compose.runtime:runtime-retain-android | 1.11.2 | Apache-2.0 |
| androidx.compose.runtime:runtime-saveable | 1.11.2 | Apache-2.0 |
| androidx.compose.runtime:runtime-saveable-android | 1.11.2 | Apache-2.0 |
| androidx.compose.ui:ui | 1.11.2 | Apache-2.0 |
| androidx.compose.ui:ui-geometry | 1.11.2 | Apache-2.0 |
| androidx.compose.ui:ui-geometry-android | 1.11.2 | Apache-2.0 |
| androidx.compose.ui:ui-graphics | 1.11.2 | Apache-2.0 |
| androidx.compose.ui:ui-text | 1.11.2 | Apache-2.0 |
| androidx.compose.ui:ui-text-android | 1.11.2 | Apache-2.0 |
| androidx.compose.ui:ui-unit | 1.11.2 | Apache-2.0 |
| androidx.compose.ui:ui-unit-android | 1.11.2 | Apache-2.0 |
| androidx.compose.ui:ui-util | 1.11.2 | Apache-2.0 |
| androidx.compose.ui:ui-util-android | 1.11.2 | Apache-2.0 |
| androidx.concurrent:concurrent-futures | 1.1.0 | Apache-2.0 |
| androidx.concurrent:concurrent-futures-ktx | 1.1.0 | Apache-2.0 |
| androidx.core:core | 1.19.0 | Apache-2.0 |
| androidx.core:core-backported-fixes | 1.0.0 | Apache-2.0 |
| androidx.core:core-viewtree | 1.0.0 | Apache-2.0 |
| androidx.cursoradapter:cursoradapter | 1.0.0 | Apache-2.0 |
| androidx.customview:customview | 1.0.0 | Apache-2.0 |
| androidx.customview:customview-poolingcontainer | 1.0.0 | Apache-2.0 |
| androidx.documentfile:documentfile | 1.0.0 | Apache-2.0 |
| androidx.drawerlayout:drawerlayout | 1.0.0 | Apache-2.0 |
| androidx.dynamicanimation:dynamicanimation | 1.0.0 | Apache-2.0 |
| androidx.emoji2:emoji2 | 1.4.0 | Apache-2.0 |
| androidx.fragment:fragment | 1.2.5 | Apache-2.0 |
| androidx.graphics:graphics-path | 1.0.1 | Apache-2.0 |
| androidx.interpolator:interpolator | 1.0.0 | Apache-2.0 |
| androidx.legacy:legacy-support-core-utils | 1.0.0 | Apache-2.0 |
| androidx.lifecycle:lifecycle-common | 2.9.4 | Apache-2.0 |
| androidx.lifecycle:lifecycle-common-java8 | 2.9.4 | Apache-2.0 |
| androidx.lifecycle:lifecycle-common-jvm | 2.9.4 | Apache-2.0 |
| androidx.lifecycle:lifecycle-livedata | 2.9.4 | Apache-2.0 |
| androidx.lifecycle:lifecycle-livedata-core | 2.9.4 | Apache-2.0 |
| androidx.lifecycle:lifecycle-livedata-core-ktx | 2.9.4 | Apache-2.0 |
| androidx.lifecycle:lifecycle-process | 2.9.4 | Apache-2.0 |
| androidx.lifecycle:lifecycle-runtime | 2.9.4 | Apache-2.0 |
| androidx.lifecycle:lifecycle-runtime-android | 2.9.4 | Apache-2.0 |
| androidx.lifecycle:lifecycle-runtime-compose-android | 2.9.4 | Apache-2.0 |
| androidx.lifecycle:lifecycle-runtime-ktx-android | 2.9.4 | Apache-2.0 |
| androidx.lifecycle:lifecycle-viewmodel | 2.9.4 | Apache-2.0 |
| androidx.lifecycle:lifecycle-viewmodel-android | 2.9.4 | Apache-2.0 |
| androidx.lifecycle:lifecycle-viewmodel-compose-android | 2.9.4 | Apache-2.0 |
| androidx.lifecycle:lifecycle-viewmodel-ktx | 2.9.4 | Apache-2.0 |
| androidx.lifecycle:lifecycle-viewmodel-savedstate | 2.9.4 | Apache-2.0 |
| androidx.lifecycle:lifecycle-viewmodel-savedstate-android | 2.9.4 | Apache-2.0 |
| androidx.loader:loader | 1.0.0 | Apache-2.0 |
| androidx.localbroadcastmanager:localbroadcastmanager | 1.0.0 | Apache-2.0 |
| androidx.media3:media3-common | 1.9.0 | Apache-2.0 |
| androidx.media3:media3-container | 1.9.0 | Apache-2.0 |
| androidx.media3:media3-muxer | 1.9.0 | Apache-2.0 |
| androidx.print:print | 1.0.0 | Apache-2.0 |
| androidx.profileinstaller:profileinstaller | 1.4.0 | Apache-2.0 |
| androidx.savedstate:savedstate | 1.3.2 | Apache-2.0 |
| androidx.savedstate:savedstate-android | 1.3.2 | Apache-2.0 |
| androidx.savedstate:savedstate-compose | 1.3.2 | Apache-2.0 |
| androidx.savedstate:savedstate-compose-android | 1.3.2 | Apache-2.0 |
| androidx.savedstate:savedstate-ktx | 1.3.2 | Apache-2.0 |
| androidx.startup:startup-runtime | 1.1.1 | Apache-2.0 |
| androidx.tracing:tracing | 1.3.0 | Apache-2.0 |
| androidx.tracing:tracing-android | 1.3.0 | Apache-2.0 |
| androidx.tracing:tracing-ktx | 1.3.0 | Apache-2.0 |
| androidx.transition:transition | 1.6.0 | Apache-2.0 |
| androidx.vectordrawable:vectordrawable | 1.1.0 | Apache-2.0 |
| androidx.vectordrawable:vectordrawable-animated | 1.1.0 | Apache-2.0 |
| androidx.versionedparcelable:versionedparcelable | 1.1.1 | Apache-2.0 |
| androidx.viewpager:viewpager | 1.0.0 | Apache-2.0 |
| androidx.window:window | 1.5.0 | Apache-2.0 |
| androidx.window:window-core | 1.5.0 | Apache-2.0 |
| androidx.window:window-core-android | 1.5.0 | Apache-2.0 |
| com.google.auto.value:auto-value-annotations | 1.6.3 | Apache-2.0 |
| com.google.code.findbugs:jsr305 | 3.0.2 | Apache-2.0 |
| com.google.dagger:dagger | 2.59 | Apache-2.0 |
| com.google.errorprone:error_prone_annotations | 2.28.0 | Apache-2.0 |
| com.google.guava:failureaccess | 1.0.2 | Apache-2.0 |
| com.google.guava:guava | 33.3.1-android | Apache-2.0 |
| com.google.guava:listenablefuture | 9999.0-empty-to-avoid-conflict-with-guava | Apache-2.0 |
| com.google.j2objc:j2objc-annotations | 3.0.0 | Apache-2.0 |
| com.squareup.okio:okio | 3.15.0 | Apache-2.0 |
| jakarta.inject:jakarta.inject-api | 2.0.1 | Apache-2.0（见第四节 NOTICE） |
| javax.inject:javax.inject | 1 | Apache-2.0 |
| org.checkerframework:checker-qual | 3.43.0 | MIT |
| org.jetbrains.kotlin:kotlin-bom | 1.8.0 | Apache-2.0 |
| org.jetbrains.kotlin:kotlin-stdlib | 2.3.0 | Apache-2.0 |
| org.jetbrains.kotlin:kotlin-stdlib-common | 2.3.0 | Apache-2.0 |
| org.jetbrains.kotlinx:atomicfu | 0.28.0 | Apache-2.0 |
| org.jetbrains.kotlinx:atomicfu-jvm | 0.28.0 | Apache-2.0 |
| org.jetbrains.kotlinx:kotlinx-coroutines-android | 1.9.0 | Apache-2.0 |
| org.jetbrains.kotlinx:kotlinx-coroutines-bom | 1.9.0 | Apache-2.0 |
| org.jetbrains.kotlinx:kotlinx-coroutines-core | 1.9.0 | Apache-2.0 |
| org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm | 1.9.0 | Apache-2.0 |
| org.jetbrains.kotlinx:kotlinx-serialization-bom | 1.7.3 | Apache-2.0 |
| org.jetbrains.kotlinx:kotlinx-serialization-core | 1.7.3 | Apache-2.0 |
| org.jetbrains.kotlinx:kotlinx-serialization-core-jvm | 1.7.3 | Apache-2.0 |
| org.jetbrains:annotations | 23.0.0 | Apache-2.0 |
| org.jspecify:jspecify | 1.0.0 | Apache-2.0 |

## 三、许可条款

### 3.1 MIT License

适用于 app.keemobile:kotpass、org.checkerframework:checker-qual。

```text
MIT License

Copyright (c) 2021 Denis T.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

org.checkerframework:checker-qual 版权声明：`Checker Framework qualifiers / Copyright 2004-present by the Checker Framework developers`，许可条款同上。

### 3.2 Bouncy Castle Licence

适用于 org.bouncycastle:bcprov-jdk18on。

```text
Copyright (c) 2000-2023 The Legion of the Bouncy Castle Inc. (https://www.bouncycastle.org)

Permission is hereby granted, free of charge, to any person obtaining a copy of this software
and associated documentation files (the "Software"), to deal in the Software without restriction,
including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so,
subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial
portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
DEALINGS IN THE SOFTWARE.
```

## 四、NOTICE

`jakarta.inject:jakarta.inject-api:2.0.1` 附带 NOTICE 文件，内容如下：

```text
# Notices for Eclipse Jakarta Dependency Injection

This content is produced and maintained by the Eclipse Jakarta Dependency Injection project.

* Project home: https://projects.eclipse.org/projects/cdi.batch

## Trademarks

Jakarta Dependency Injection is a trademark of the Eclipse Foundation.

## Copyright

All content is the property of the respective authors or their employers. For
more information regarding authorship of content, please consult the listed
source code repository logs.

## Declared Project Licenses

This program and the accompanying materials are made available under the terms
of the Apache License, Version 2.0 which is available at
https://www.apache.org/licenses/LICENSE-2.0.

SPDX-License-Identifier: Apache-2.0

## Source Code

The project maintains the following source code repositories:

https://github.com/eclipse-ee4j/injection-api
https://github.com/eclipse-ee4j/injection-spec
https://github.com/eclipse-ee4j/injection-tck

## Third-party Content

This project leverages the following third party content.

None

## Cryptography


None
```

## 五、APK 内的许可文件

release APK 当前包含的许可相关条目（`unzip -l` 实测）：

| 条目 | 来源 |
|------|------|
| `META-INF/NOTICE.md` | jakarta.inject-api |
| `META-INF/androidx/annotation/annotation/LICENSE.txt` | androidx.annotation |
| `META-INF/androidx/collection/collection/LICENSE.txt` | androidx.collection |
| `META-INF/androidx/collection/collection-ktx/LICENSE.txt` | androidx.collection:collection-ktx |
| `META-INF/androidx/lifecycle/lifecycle-common/LICENSE.txt` | androidx.lifecycle:lifecycle-common |
| `META-INF/androidx/lifecycle/lifecycle-common-java8/LICENSE.txt` | androidx.lifecycle:lifecycle-common-java8 |

以上条目由 AAR 随打包自动合入，均为 Apache License 2.0 文本。其余组件所需的许可文本由本应用自己提供：`app/src/main/assets/licenses/THIRD-PARTY-NOTICES.txt` 收录本文件全部内容（组件清单、MIT 全文、Bouncy Castle Licence 全文、jakarta NOTICE、Apache-2.0 全文），应用内入口为「设置 → 关于 → 开源许可」，对应源码 `ui/screens/LicensesScreen.kt`。

## 六、随源码分发的文件

源码分发时，仓库内随源码提供的第三方文件如下：

| 文件 | 来源 | 版本 | 许可证 |
|------|------|------|--------|
| `gradlew`、`gradlew.bat` | Gradle Wrapper | 9.7.1 | Apache-2.0 |
| `gradle/wrapper/gradle-wrapper.jar` | Gradle Wrapper | 9.7.1 | Apache-2.0 |

Gradle 发行版自带 Apache License 2.0 全文，与本项目根目录 `LICENSE` 为同一文本，不重复收录。`gradle/wrapper/gradle-wrapper.properties` 是本项目自有配置（其中 distributionUrl 指向腾讯云镜像）。

构建期插件不随源码或 APK 分发，仅在 `gradle/libs.versions.toml` 中声明版本：Android Gradle Plugin 9.3.0、Kotlin 2.3.0（含 Compose 编译器插件）、KSP 2.3.6（声明但未应用）。这些工具在构建时由 Gradle 从 Maven 仓库获取，不进入仓库也不进入产物。

仓库内没有 `libs/` 目录或其它本地 jar / aar，全部第三方二进制依赖均由 Gradle 构建时解析（见第二节）。

## 七、本项目自身

SrPass 的源码与 APK 均以 Apache License 2.0 发布，全文见本文件第八节（仓库版另见根目录 `LICENSE`）。

## 八、本项目许可全文（Apache License 2.0）

```text
                                 Apache License
                           Version 2.0, January 2004
                        http://www.apache.org/licenses/

   TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION

   1. Definitions.

      "License" shall mean the terms and conditions for use, reproduction,
      and distribution as defined by Sections 1 through 9 of this document.

      "Licensor" shall mean the copyright owner or entity authorized by
      the copyright owner that is granting the License.

      "Legal Entity" shall mean the union of the acting entity and all
      other entities that control, are controlled by, or are under common
      control with that entity. For the purposes of this definition,
      "control" means (i) the power, direct or indirect, to cause the
      direction or management of such entity, whether by contract or
      otherwise, or (ii) ownership of fifty percent (50%) or more of the
      outstanding shares, or (iii) beneficial ownership of such entity.

      "You" (or "Your") shall mean an individual or Legal Entity
      exercising permissions granted by this License.

      "Source" form shall mean the preferred form for making modifications,
      including but not limited to software source code, documentation
      source, and configuration files.

      "Object" form shall mean any form resulting from mechanical
      transformation or translation of a Source form, including but
      not limited to compiled object code, generated documentation,
      and conversions to other media types.

      "Work" shall mean the work of authorship, whether in Source or
      Object form, made available under the License, as indicated by a
      copyright notice that is included in or attached to the work
      (an example is provided in the Appendix below).

      "Derivative Works" shall mean any work, whether in Source or Object
      form, that is based on (or derived from) the Work and for which the
      editorial revisions, annotations, elaborations, or other modifications
      represent, as a whole, an original work of authorship. For the purposes
      of this License, Derivative Works shall not include works that remain
      separable from, or merely link (or bind by name) to the interfaces of,
      the Work and Derivative Works thereof.

      "Contribution" shall mean any work of authorship, including
      the original version of the Work and any modifications or additions
      to that Work or Derivative Works thereof, that is intentionally
      submitted to Licensor for inclusion in the Work by the copyright owner
      or by an individual or Legal Entity authorized to submit on behalf of
      the copyright owner. For the purposes of this definition, "submitted"
      means any form of electronic, verbal, or written communication sent
      to the Licensor or its representatives, including but not limited to
      communication on electronic mailing lists, source code control systems,
      and issue tracking systems that are managed by, or on behalf of, the
      Licensor for the purpose of discussing and improving the Work, but
      excluding communication that is conspicuously marked or otherwise
      designated in writing by the copyright owner as "Not a Contribution."

      "Contributor" shall mean Licensor and any individual or Legal Entity
      on behalf of whom a Contribution has been received by Licensor and
      subsequently incorporated within the Work.

   2. Grant of Copyright License. Subject to the terms and conditions of
      this License, each Contributor hereby grants to You a perpetual,
      worldwide, non-exclusive, no-charge, royalty-free, irrevocable
      copyright license to reproduce, prepare Derivative Works of,
      publicly display, publicly perform, sublicense, and distribute the
      Work and such Derivative Works in Source or Object form.

   3. Grant of Patent License. Subject to the terms and conditions of
      this License, each Contributor hereby grants to You a perpetual,
      worldwide, non-exclusive, no-charge, royalty-free, irrevocable
      (except as stated in this section) patent license to make, have made,
      use, offer to sell, sell, import, and otherwise transfer the Work,
      where such license applies only to those patent claims licensable
      by such Contributor that are necessarily infringed by their
      Contribution(s) alone or by combination of their Contribution(s)
      with the Work to which such Contribution(s) was submitted. If You
      institute patent litigation against any entity (including a
      cross-claim or counterclaim in a lawsuit) alleging that the Work
      or a Contribution incorporated within the Work constitutes direct
      or contributory patent infringement, then any patent licenses
      granted to You under this License for that Work shall terminate
      as of the date such litigation is filed.

   4. Redistribution. You may reproduce and distribute copies of the
      Work or Derivative Works thereof in any medium, with or without
      modifications, and in Source or Object form, provided that You
      meet the following conditions:

      (a) You must give any other recipients of the Work or
          Derivative Works a copy of this License; and

      (b) You must cause any modified files to carry prominent notices
          stating that You changed the files; and

      (c) You must retain, in the Source form of any Derivative Works
          that You distribute, all copyright, patent, trademark, and
          attribution notices from the Source form of the Work,
          excluding those notices that do not pertain to any part of
          the Derivative Works; and

      (d) If the Work includes a "NOTICE" text file as part of its
          distribution, then any Derivative Works that You distribute must
          include a readable copy of the attribution notices contained
          within such NOTICE file, excluding those notices that do not
          pertain to any part of the Derivative Works, in at least one
          of the following places: within a NOTICE text file distributed
          as part of the Derivative Works; within the Source form or
          documentation, if provided along with the Derivative Works; or,
          within a display generated by the Derivative Works, if and
          wherever such third-party notices normally appear. The contents
          of the NOTICE file are for informational purposes only and
          do not modify the License. You may add Your own attribution
          notices within Derivative Works that You distribute, alongside
          or as an addendum to the NOTICE text from the Work, provided
          that such additional attribution notices cannot be construed
          as modifying the License.

      You may add Your own copyright statement to Your modifications and
      may provide additional or different license terms and conditions
      for use, reproduction, or distribution of Your modifications, or
      for any such Derivative Works as a whole, provided Your use,
      reproduction, and distribution of the Work otherwise complies with
      the conditions stated in this License.

   5. Submission of Contributions. Unless You explicitly state otherwise,
      any Contribution intentionally submitted for inclusion in the Work
      by You to the Licensor shall be under the terms and conditions of
      this License, without any additional terms or conditions.
      Notwithstanding the above, nothing herein shall supersede or modify
      the terms of any separate license agreement you may have executed
      with Licensor regarding such Contributions.

   6. Trademarks. This License does not grant permission to use the trade
      names, trademarks, service marks, or product names of the Licensor,
      except as required for reasonable and customary use in describing the
      origin of the Work and reproducing the content of the NOTICE file.

   7. Disclaimer of Warranty. Unless required by applicable law or
      agreed to in writing, Licensor provides the Work (and each
      Contributor provides its Contributions) on an "AS IS" BASIS,
      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
      implied, including, without limitation, any warranties or conditions
      of TITLE, NON-INFRINGEMENT, MERCHANTABILITY, or FITNESS FOR A
      PARTICULAR PURPOSE. You are solely responsible for determining the
      appropriateness of using or redistributing the Work and assume any
      risks associated with Your exercise of permissions under this License.

   8. Limitation of Liability. In no event and under no legal theory,
      whether in tort (including negligence), contract, or otherwise,
      unless required by applicable law (such as deliberate and grossly
      negligent acts) or agreed to in writing, shall any Contributor be
      liable to You for damages, including any direct, indirect, special,
      incidental, or consequential damages of any character arising as a
      result of this License or out of the use or inability to use the
      Work (including but not limited to damages for loss of goodwill,
      work stoppage, computer failure or malfunction, or any and all
      other commercial damages or losses), even if such Contributor
      has been advised of the possibility of such damages.

   9. Accepting Warranty or Additional Liability. While redistributing
      the Work or Derivative Works thereof, You may choose to offer,
      and charge a fee for, acceptance of support, warranty, indemnity,
      or other liability obligations and/or rights consistent with this
      License. However, in accepting such obligations, You may act only
      on Your own behalf and on Your sole responsibility, not on behalf
      of any other Contributor, and only if You agree to indemnify,
      defend, and hold each Contributor harmless for any liability
      incurred by, or claims asserted against, such Contributor by reason
      of your accepting any such warranty or additional liability.

   END OF TERMS AND CONDITIONS

   APPENDIX: How to apply the Apache License to your work.

      To apply the Apache License to your work, attach the following
      boilerplate notice, with the fields enclosed by brackets "[]"
      replaced with your own identifying information. (Don't include
      the brackets!)  The text should be enclosed in the appropriate
      comment syntax for the file format. We also recommend that a
      file or class name and description of purpose be included on the
      same "printed page" as the copyright notice for easier
      identification within third-party archives.

   Copyright [yyyy] [name of copyright owner]

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
```
