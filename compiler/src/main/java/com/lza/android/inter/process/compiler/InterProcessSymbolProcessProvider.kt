package com.lza.android.inter.process.compiler

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSVisitorVoid
import com.google.devtools.ksp.validate
import com.lza.android.inter.process.annotation.RemoteProcessInterface
import kotlin.math.abs

/**
 * @author liuzhongao
 * @since 2024/2/4 16:27
 */
class InterProcessSymbolProcessProvider : SymbolProcessorProvider {

    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return object : SymbolProcessor {
            override fun process(resolver: Resolver): List<KSAnnotated> {
                var ksFile: KSFile? = null
                var packageName: String? = null
                val referenceClasses: MutableList<RemoteProcessInterfaceModel> = ArrayList()
                val visitor = object : KSVisitorVoid() {

                    override fun visitClassDeclaration(
                        classDeclaration: KSClassDeclaration,
                        data: Unit
                    ) {
                        if (ksFile == null) {
                            ksFile = classDeclaration.containingFile
                        }
                        if (packageName.isNullOrEmpty()) {
                            packageName = classDeclaration.packageName.asString()
                        }
                        classDeclaration.annotations.forEach { annotation ->
                            val interfaceFullClassName =
                                annotation.arguments.find { it.name?.asString() == "interfaceClass" }?.value?.let { it as? KSType }?.declaration?.qualifiedName
                            val selfFullClassName = classDeclaration.qualifiedName
                            if (interfaceFullClassName != null && selfFullClassName != null) {
                                referenceClasses += RemoteProcessInterfaceModel(
                                    interfaceClassName = interfaceFullClassName,
                                    selfImplementationClassName = selfFullClassName
                                )
                            }
                        }
                    }
                }
                val returnList =
                    resolver.getSymbolsWithAnnotation(remoteProcessInterfaceFullName).toList()
                returnList.filter { it.validate() }
                    .onEach { it.accept(visitor, Unit) }
                if (ksFile != null && packageName != null && referenceClasses.isNotEmpty()) {
                    val processCenterName =
                        resolver.getKSNameFromString("com.lza.android.inter.process.library.ProcessCenter")
                    val fileName =
                        "RemoteProcessMapping_${abs(referenceClasses.hashCode())}_Generated"
                    environment.codeGenerator.createNewFile(
                        dependencies = Dependencies(
                            aggregating = true,
                            sources = arrayOf(requireNotNull(ksFile))
                        ), requireNotNull(packageName), fileName
                    ).writer().use { newGeneratedFile ->
                        newGeneratedFile.run {
                            write("package ${packageName}\n")
                            write("\n")
                            write("class $fileName : com.lza.android.inter.process.library.interfaces.IPCNoProguard {\n")
                            write(
                                StringBuilder()
                                    .appendLine("\tcompanion object {")
                                    .appendLine("\t\t@JvmStatic")
                                    .appendLine("\t\tfun collectService() {")
                                    .apply {
                                        referenceClasses.forEach { model ->
                                            val classDeclaration =
                                                requireNotNull(resolver.getClassDeclarationByName(model.selfImplementationClassName))
                                            val implementationName =
                                                if (classDeclaration.classKind == ClassKind.OBJECT) {
                                                    model.selfImplementationClassName.asString()
                                                } else "${model.selfImplementationClassName.asString()}()"
                                            appendLine("\t\t\t${processCenterName.asString()}.putService(${model.interfaceClassName.asString()}::class.java, ${implementationName})")
                                        }
                                    }
                                    .appendLine("\t\t}")
                                    .appendLine("\t}")
                                    .toString()
                            )
                            write("}")
                        }
                        newGeneratedFile.flush()
                        newGeneratedFile.close()
                    }
                }

                return returnList.filter { !it.validate() }
            }
        }
    }

    companion object {
        private val remoteProcessInterfaceFullName =
            requireNotNull(RemoteProcessInterface::class.qualifiedName)
    }
}