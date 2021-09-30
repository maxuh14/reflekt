package org.jetbrains.reflekt.util

data class MyCliOption(
    val name: String,
    val valueDescription: String,
    val description: String
)


object Util {
    /** Global constant with plugin identifier */
    const val PLUGIN_ID = "org.jetbrains.reflekt"

    const val GRADLE_GROUP_ID = "org.jetbrains.reflekt"
    /**
     * Just needs to be consistent with the artifactId in reflekt-plugin build.gradle.kts#publishJar
     */
    const val GRADLE_ARTIFACT_ID = "reflekt-plugin"
    const val VERSION = "1.5.30"

    val ENABLED_OPTION_INFO = MyCliOption(
        name = "enabled",
        valueDescription = "<true|false>",
        description = "whether to enable the Reflekt plugin or not"
    )

    val DEPENDENCY_JAR_OPTION_INFO =
        MyCliOption(
            name = "dependencyJar",
            valueDescription = "<dependency jar>",
            description = "Project dependency jar file"
        )

    val INTROSPECT_FILE_OPTION_INFO =
        MyCliOption(
            name = "fileToIntrospect",
            valueDescription = "<file's path>",
            description = "File's path from the library to introspect"
        )

    val OUTPUT_DIR_OPTION_INFO =
        MyCliOption(
            name = "outputDir",
            valueDescription = "<path>",
            description = "Resulting generated files"
        )
}