package com.lza.android.inter.process.transform.plugin

import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.gradle.AppPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register

/**
 * @author liuzhongao
 * @since 2024/2/5 00:07
 */
class InterProcessPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.plugins.withType(AppPlugin::class.java) {

            val androidComponents =
                project.extensions.getByType(ApplicationAndroidComponentsExtension::class.java)
            androidComponents.onVariants { variant ->
                val taskProvider = project.tasks.register<InterProcessTransformTask>("${variant.name}InterProcessTransform")
                variant.artifacts.forScope(ScopedArtifacts.Scope.ALL)
                    .use(taskProvider)
                    .toTransform(
                        type = ScopedArtifact.CLASSES,
                        inputJars = InterProcessTransformTask::allJars,
                        inputDirectories = InterProcessTransformTask::allDirectories,
                        into = InterProcessTransformTask::output
                    )
            }
        }
    }
}