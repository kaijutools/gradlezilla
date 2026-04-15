package tools.kaiju.gradlezilla.generator

class DockerfileGenerator : Generator {
    override fun generate(spec: GeneratorSpec): Dockerfile {
        val sdkPackages =
            buildList {
                add("platforms;android-${spec.androidSdkVersion}")
                add("platform-tools")
                add("build-tools;${spec.androidPlatformToolsVersion}")
                spec.androidNdkVersion?.let { add("ndk;$it") }
            }

        val runLayer =
            DockerLayer(
                instruction = DockerInstruction.RUN,
                entries =
                    buildList {
                        add("apt-get update")
                        add("apt-get install -y --no-install-recommends wget unzip")
                        add("rm -rf /var/lib/apt/lists/*")
                        add("mkdir -p \$ANDROID_HOME/cmdline-tools")
                        add(
                            "wget -q \"https://dl.google.com/android/repository/" +
                                "commandlinetools-linux-${spec.androidCommandLineToolsVersion}_latest.zip\"" +
                                " -O /tmp/cmdline-tools.zip",
                        )
                        add("unzip -q /tmp/cmdline-tools.zip -d \$ANDROID_HOME/cmdline-tools")
                        add("mv \$ANDROID_HOME/cmdline-tools/cmdline-tools \$ANDROID_HOME/cmdline-tools/latest")
                        add("rm /tmp/cmdline-tools.zip")
                        add("yes | sdkmanager --licenses > /dev/null")
                        add("sdkmanager ${sdkPackages.joinToString(" ") { "\"$it\"" }}")
                    },
            )

        val envLayer =
            DockerLayer(
                instruction = DockerInstruction.ENV,
                entries =
                    listOf(
                        "ANDROID_HOME=/opt/android-sdk",
                        "PATH=\$PATH:/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools",
                    ),
                next = runLayer,
            )

        val fromLayer =
            DockerLayer(
                instruction = DockerInstruction.FROM,
                entries = listOf("eclipse-temurin:${spec.jdkVersion}-jdk-jammy"),
                next = envLayer,
            )

        return Dockerfile(head = fromLayer)
    }
}
