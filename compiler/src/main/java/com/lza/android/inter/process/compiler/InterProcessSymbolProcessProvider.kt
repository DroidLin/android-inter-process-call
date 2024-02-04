package com.lza.android.inter.process.compiler

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSVisitorVoid
import com.google.devtools.ksp.validate
import com.lza.android.inter.process.annotation.RemoteProcessInterface

/**
 * @author liuzhongao
 * @since 2024/2/4 16:27
 */
class InterProcessSymbolProcessProvider : SymbolProcessorProvider {

    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return object : SymbolProcessor {
            override fun process(resolver: Resolver): List<KSAnnotated> {
                var ksFile: KSFile? = null
                val referenceClasses: MutableList<String> = ArrayList()
                val visitor = object : KSVisitorVoid() {

                    override fun visitAnnotation(annotation: KSAnnotation, data: Unit) {
                    }

                    override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
                        if (ksFile == null) {
                            ksFile = classDeclaration.containingFile
                        }
                        referenceClasses += "\n"
                        referenceClasses += (classDeclaration.qualifiedName?.asString() ?: "")
                        classDeclaration.annotations.forEach { annotation ->
                            referenceClasses += annotation.shortName.asString()
                            annotation.arguments.forEach { annotationArguments ->
                                referenceClasses += annotationArguments.value?.javaClass?.name ?: ""
                                referenceClasses += annotationArguments.value?.toString() ?: ""
                            }
                        }
                    }
                }
                val returnList = resolver.getSymbolsWithAnnotation(remoteProcessInterfaceFullName).toList()
                    .onEach { if (it.validate()) it.accept(visitor, Unit) }.filter { !it.validate() }
                if (ksFile != null && referenceClasses.isNotEmpty()) {
                    environment.codeGenerator.createNewFile(
                        dependencies = Dependencies(
                            aggregating = true,
                            sources = arrayOf(requireNotNull(ksFile))
                        ), "com.lza.android.inter.process", "TestFile_Generated"
                    ).writer().use { newGeneratedFile ->
                        newGeneratedFile.write("package com.lza.android.inter.process\n\n")
                        newGeneratedFile.write("/*")
                        referenceClasses.forEach { classNames ->
                            newGeneratedFile.write(classNames)
                            newGeneratedFile.write("\n")
                        }
                        newGeneratedFile.write("*/")
                        newGeneratedFile.flush()
                        newGeneratedFile.close()
                    }
                }

                return returnList
            }
        }
    }

    companion object {
        private val remoteProcessInterfaceFullName =
            requireNotNull(RemoteProcessInterface::class.qualifiedName)
    }
}