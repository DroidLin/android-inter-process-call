package com.lza.android.inter.process.compiler

import com.lza.android.inter.process.annotation.RemoteProcessInterface
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedAnnotationTypes
import javax.annotation.processing.SupportedSourceVersion
import javax.lang.model.SourceVersion
import javax.lang.model.element.ElementKind
import javax.lang.model.element.TypeElement

/**
 * @author liuzhongao
 * @since 2024/2/16 12:01
 */
@SupportedAnnotationTypes("com.lza.android.inter.process.annotation.RemoteProcessInterface")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
class RemoteProcessAnnotationProcessor : AbstractProcessor() {

    override fun process(
        set: MutableSet<out TypeElement>?,
        roundEnvironment: RoundEnvironment?
    ): Boolean {
        if (roundEnvironment == null) return false
        roundEnvironment.getElementsAnnotatedWith(RemoteProcessInterface::class.java).forEach { element ->
            require(element.kind == ElementKind.INTERFACE) {
                "annotation requires ${element.simpleName} declared as interface"
            }
            InterfaceProxyClassGenerator.buildInterfaceProxyImplementationClass(this.processingEnv, element)
            InterfaceStubClassGenerator.buildInterfaceProxyImplementationClass(this.processingEnv, element)
        }
        return false
    }
}