package com.lza.android.inter.process.transform.plugin

import com.android.build.gradle.AppExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * @author liuzhongao
 * @since 2024/2/5 00:07
 */
class InterProcessPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.extensions.findByType(AppExtension::class.java)
            ?.registerTransform(InterProcessGeneratedClassTransform())
    }
}