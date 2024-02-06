package com.lza.android.inter.process.compiler

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSVisitorVoid
import com.lza.android.inter.process.annotation.RemoteProcessInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * @author liuzhongao
 * @since 2024/2/4 16:27
 */
class InterProcessSymbolProcessProvider : SymbolProcessorProvider {

    private val coroutineScope = CoroutineScope(context = Dispatchers.Default + SupervisorJob())

    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return object : SymbolProcessor {
            override fun process(resolver: Resolver): List<KSAnnotated> {
                runBlocking(this@InterProcessSymbolProcessProvider.coroutineScope.coroutineContext) {
                    findKSClassDeclaration(
                        resolver = resolver,
                        annotationClass = RemoteProcessInterface::class.java
                    ).forEach { annotatedClassDeclaration ->
                        InterfaceProxyClassGenerator.buildInterfaceProxyImplementationClass(
                            resolver = resolver,
                            codeGenerator = environment.codeGenerator,
                            interfaceClassDeclaration = annotatedClassDeclaration
                        )
                        val generatedStubClassDeclaration = resolver.getClassDeclarationByName(
                            resolver.getKSNameFromString("com.lza.android.inter.process.library.interfaces.GeneratedStubFunction")
                        ) ?: return@forEach
                        InterfaceStubClassGenerator.buildInterfaceProxyImplementationClass(
                            codeGenerator = environment.codeGenerator,
                            interfaceClassDeclaration = annotatedClassDeclaration,
                            generatedStubClassDeclaration = generatedStubClassDeclaration
                        )
                    }
                }
                return emptyList()
            }
        }
    }

    private suspend fun findKSClassDeclaration(
        resolver: Resolver,
        annotationClass: Class<out Annotation>
    ): List<KSClassDeclaration> {
        val annotationSymbols = resolver.getSymbolsWithAnnotation(annotationClass.name)
        return annotationSymbols.toList().map { ksAnnotated ->
            this.coroutineScope.async {
                suspendCoroutine { continuation ->
                    val visitor = object : KSVisitorVoid() {
                        override fun visitClassDeclaration(
                            classDeclaration: KSClassDeclaration,
                            data: Unit
                        ) {
                            continuation.resume(classDeclaration)
                        }
                    }
                    ksAnnotated.accept(visitor = visitor, data = Unit)
                }
            }
        }.awaitAll()
    }

}