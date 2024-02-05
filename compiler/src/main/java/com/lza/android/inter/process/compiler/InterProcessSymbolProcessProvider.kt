package com.lza.android.inter.process.compiler

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.isInternal
import com.google.devtools.ksp.isOpen
import com.google.devtools.ksp.isProtected
import com.google.devtools.ksp.outerType
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSVisitorVoid
import com.google.devtools.ksp.symbol.Modifier
import com.lza.android.inter.process.annotation.RemoteProcessInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.io.Writer
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * @author liuzhongao
 * @since 2024/2/4 16:27
 */
class InterProcessSymbolProcessProvider : SymbolProcessorProvider {

    private val coroutineScope = CoroutineScope(context = Dispatchers.Default)

    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return object : SymbolProcessor {
            override fun process(resolver: Resolver): List<KSAnnotated> {
                runBlocking(this@InterProcessSymbolProcessProvider.coroutineScope.coroutineContext) {
                    findKSClassDeclaration(
                        resolver = resolver,
                        annotationClass = RemoteProcessInterface::class.java
                    ).map { ksClassDeclaration ->
                        this@InterProcessSymbolProcessProvider.coroutineScope.async {
                            this@InterProcessSymbolProcessProvider.buildInterfaceCallerProxyImplementationClass(
                                resolver = resolver,
                                codeGenerator = environment.codeGenerator,
                                interfaceClassDeclaration = ksClassDeclaration
                            )
                        }
                    }.awaitAll()
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

    /**
     * build caller interface proxy implementation class.
     */
    private fun buildInterfaceCallerProxyImplementationClass(
        resolver: Resolver,
        codeGenerator: CodeGenerator,
        interfaceClassDeclaration: KSClassDeclaration,
    ) {
        require(interfaceClassDeclaration.classKind == ClassKind.INTERFACE) {
            "annotation requires ${interfaceClassDeclaration.qualifiedName?.asString() ?: ""} declared as interface"
        }
        val implClassName = "${interfaceClassDeclaration.simpleName.asString()}_Generated_Proxy"
        val writer = codeGenerator.createNewFile(
            dependencies = Dependencies(
                aggregating = true,
                sources = arrayOf(
                    requireNotNull(interfaceClassDeclaration.containingFile)
                )
            ),
            packageName = interfaceClassDeclaration.packageName.asString(),
            fileName = implClassName
        ).bufferedWriter()

        this.buildKtClassPackage(writer, interfaceClassDeclaration.packageName.asString())
        this.buildKtClassBlock(writer, interfaceClassDeclaration, implClassName) {
            interfaceClassDeclaration.getDeclaredProperties().filter { it.isOpen() }
                .forEach { propertyDeclaration ->
                    this.buildProperty(
                        writer = writer,
                        propertyDeclaration = propertyDeclaration
                    ) { this.buildPropertyBody(implClassName, writer, propertyDeclaration) }
                }
            interfaceClassDeclaration.getDeclaredFunctions().filter { it.isOpen() }
                .forEach { functionDeclaration ->
                    this.buildFunction(
                        writer = writer,
                        functionDeclaration = functionDeclaration
                    ) { this.buildFunctionBody(implClassName, writer, functionDeclaration)}
                }
        }
        writer.flush()
        writer.close()
    }

    private fun buildKtClassPackage(
        writer: Writer,
        packageName: String,
    ) {
        writer.appendLine("package $packageName")
            .appendLine()
            .appendLine("import android.content.Context")
            .appendLine("import com.lza.android.inter.process.library.interfaces.ExceptionHandler")
            .appendLine("import com.lza.android.inter.process.library.interfaces.IPCNoProguard")
            .appendLine("import com.lza.android.inter.process.library.kotlin.UnHandledRuntimeException")
            .appendLine("import kotlinx.coroutines.CoroutineExceptionHandler")
            .appendLine("import kotlinx.coroutines.Dispatchers")
            .appendLine("import kotlinx.coroutines.SupervisorJob")
            .appendLine("import kotlinx.coroutines.runBlocking")
            .appendLine("import java.lang.reflect.InvocationHandler")
            .appendLine("import java.lang.reflect.Method")
            .appendLine("import kotlin.coroutines.Continuation")
            .appendLine("import kotlin.coroutines.CoroutineContext")
            .appendLine("import kotlin.coroutines.EmptyCoroutineContext")
            .appendLine("import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn")
            .appendLine("import kotlin.reflect.jvm.isAccessible")
            .appendLine("import kotlin.reflect.jvm.javaType")
            .appendLine()
    }

    private inline fun buildKtClassBlock(
        writer: Writer,
        interfaceDeclaration: KSClassDeclaration,
        className: String,
        body: () -> Unit
    ) {
        writer.appendLine("class $className @JvmOverloads constructor (")
            .appendLine("\tprivate val context: android.content.Context,")
            .appendLine("\tprivate val proxyInterfaceClass: java.lang.Class<*>,")
            .appendLine("\tprivate val currentProcessKey: kotlin.String,")
            .appendLine("\tprivate val destinationProcessKey: kotlin.String,")
            .appendLine("\tprivate val coroutineContext: kotlin.coroutines.CoroutineContext = kotlin.coroutines.EmptyCoroutineContext,")
            .appendLine("\tprivate val interfaceDefaultImpl: ${requireNotNull(interfaceDeclaration.qualifiedName).asString()}? = null,")
            .appendLine("\tprivate val exceptionHandler: com.lza.android.inter.process.library.interfaces.ExceptionHandler? = null")
            .appendLine(") : ${requireNotNull(interfaceDeclaration.qualifiedName).asString()} {")
            .apply { body() }
            .appendLine("}")
    }

    private fun buildProperty(
        writer: Writer,
        propertyDeclaration: KSPropertyDeclaration,
        propertyBody: () -> Unit
    ) {
        writer.appendLine()
            .append("\toverride ")
            .apply {
                if (propertyDeclaration.isMutable) {
                    append("var")
                } else append("val")
            }
            .append(" ")
            .apply {
                val receiverType = propertyDeclaration.extensionReceiver?.resolve()
                if (receiverType != null) {
                    append(this@InterProcessSymbolProcessProvider.buildType(receiverType)).append(".")
                }
            }
            .append(propertyDeclaration.simpleName.asString())
            .append(": ").append(this@InterProcessSymbolProcessProvider.buildType(propertyDeclaration.type.resolve()))
            .appendLine()
            .append("\t\tget() ").append("{").appendLine()
            .apply { propertyBody() }
            .append("\t\t}")
            .appendLine()
    }

    private fun buildPropertyBody(
        className: String,
        writer: Writer,
        propertyDeclaration: KSPropertyDeclaration
    ) {
        writer
            .appendLine("\t\t\tval data = com.lza.android.inter.process.library.invokeDirectProperty<${buildType(propertyDeclaration.type.resolve(), false)}>(")
            .appendLine("\t\t\t\tcoroutineContext = this@${className}.coroutineContext,")
            .appendLine("\t\t\t\tandroidContext = this@${className}.context,")
            .appendLine("\t\t\t\tcurrentProcessKey = this@${className}.currentProcessKey,")
            .appendLine("\t\t\t\tdestinationProcessKey = this@${className}.destinationProcessKey,")
            .appendLine("\t\t\t\tdeclaringClassName = \"${requireNotNull(propertyDeclaration.parentDeclaration?.qualifiedName).asString()}\",")
            .appendLine("\t\t\t\tpropertyName = \"${propertyDeclaration.simpleName.asString()}\"")
            .appendLine("\t\t\t)")
            .appendLine("\t\t\tif(data != null) {")
            .appendLine("\t\t\t\treturn data")
            .appendLine("\t\t\t}")
            .appendLine("\t\t\tif (this@${className}.interfaceDefaultImpl != null) {")
            .apply {
                if (propertyDeclaration.extensionReceiver != null) {
                    appendLine("\t\t\t\treturn this@${className}.interfaceDefaultImpl.run { ${propertyDeclaration.simpleName.asString()} }")
                } else appendLine("\t\t\t\treturn this@${className}.interfaceDefaultImpl.${propertyDeclaration.simpleName.asString()}")
            }
            .appendLine("\t\t\t}")
            .appendLine("\t\t\tthrow kotlin.IllegalArgumentException(\"function return type requires non-null type, but returns null type after IPC call and the fallback operation!! please check.\")")
    }

    private fun buildFunction(
        writer: Writer,
        functionDeclaration: KSFunctionDeclaration,
        functionBody: () -> Unit
    ) {
        writer.appendLine()
            .append("\toverride").append(" ")
            .apply {
                if (functionDeclaration.isProtected()) {
                    append("protected").append(" ")
                } else if (functionDeclaration.isInternal()) {
                    append("internal").append(" ")
                } else append("public").append(" ")
                if (functionDeclaration.modifiers.contains(Modifier.SUSPEND)) {
                    append("suspend").append(" ")
                }
            }
            .append("fun ")
            .apply {
                val receiverType = functionDeclaration.extensionReceiver?.resolve()
                if (receiverType != null) {
                    append(this@InterProcessSymbolProcessProvider.buildType(receiverType)).append(".")
                }
            }
            .append("${functionDeclaration.simpleName.asString()}(")
            .append(this.buildFunctionParameters(functionDeclaration))
            .append(")")
            .apply {
                val returnKSType = functionDeclaration.returnType?.resolve()
                if (returnKSType != null) {
                    append(": ${this@InterProcessSymbolProcessProvider.buildType(returnKSType)}")
                }
            }
            .appendLine(" {")
            .apply { functionBody() }
            .appendLine("\t}")
    }

    private fun buildFunctionParameters(
        functionDeclaration: KSFunctionDeclaration,
    ): String {
        val stringBuilder = StringBuilder()
        functionDeclaration.parameters.forEachIndexed { index, ksValueParameter ->
            val parameterTypeClassDeclaration = ksValueParameter.type.resolve()
            if (index != 0) {
                stringBuilder.append(", ")
            }
            if (ksValueParameter.isVararg) {
                stringBuilder.append("vararg ").append(requireNotNull(ksValueParameter.name).asString()).append(": ")
                    .append(this.buildType(parameterTypeClassDeclaration))
            } else {
                stringBuilder.append(requireNotNull(ksValueParameter.name).asString()).append(": ")
                    .append(this.buildType(parameterTypeClassDeclaration))
            }
        }
        return stringBuilder.toString()
    }

    private fun buildFunctionBody(
        className: String,
        writer: Writer,
        functionDeclaration: KSFunctionDeclaration,
    ) {
        writer
            .apply {
                if (functionDeclaration.modifiers.contains(Modifier.SUSPEND)) {
                } else {
                    appendLine("\t\tval data = com.lza.android.inter.process.library.invokeDirectKotlinFunction<${buildType(requireNotNull(functionDeclaration.returnType).resolve(), false)}>(")
                        .appendLine("\t\t\tcoroutineContext = this@${className}.coroutineContext,")
                        .appendLine("\t\t\tandroidContext = this@${className}.context,")
                        .appendLine("\t\t\tcurrentProcessKey = this@${className}.currentProcessKey,")
                        .appendLine("\t\t\tdestinationProcessKey = this@${className}.destinationProcessKey,")
                        .appendLine("\t\t\tdeclaringClassName = \"${requireNotNull(functionDeclaration.parentDeclaration?.qualifiedName).asString()}\",")
                        .appendLine("\t\t\tfunctionName = \"${functionDeclaration.simpleName.asString()}\",")
                        .appendLine("\t\t\tfunctionParameterTypes = kotlin.collections.listOf(${buildFunctionCallParameterTypes(functionDeclaration)}),")
                        .appendLine("\t\t\tfunctionParameters = kotlin.collections.listOf(${buildFunctionCallParameter(functionDeclaration)}),")
                        .appendLine("\t\t)")
                        .appendLine("\t\tif(data != null) {")
                        .appendLine("\t\t\treturn data")
                        .appendLine("\t\t}")
                }
            }
            .appendLine("\t\tif (this@${className}.interfaceDefaultImpl != null) {")
            .apply {
                if (functionDeclaration.extensionReceiver != null) {
                    appendLine("\t\t\treturn this@${className}.interfaceDefaultImpl.run { ${functionDeclaration.simpleName.asString()}(${this@InterProcessSymbolProcessProvider.buildFunctionCallParameter(functionDeclaration)}) }")
                } else {
                    appendLine("\t\t\treturn this@${className}.interfaceDefaultImpl.${functionDeclaration.simpleName.asString()}(${this@InterProcessSymbolProcessProvider.buildFunctionCallParameter(functionDeclaration)})")
                }
            }
            .appendLine("\t\t}")
            .appendLine("\t\tthrow kotlin.IllegalArgumentException(\"function return type requires non-null type, but returns null type after IPC call and the fallback operation!! please check.\")")
    }

    private fun buildFunctionCallParameterTypes(
        functionDeclaration: KSFunctionDeclaration
    ): String {
        val stringBuilder = StringBuilder()
        functionDeclaration.parameters.forEachIndexed { index, ksValueParameter ->
            if (index != 0) {
                stringBuilder.append(", ")
            }
            val parameterizedTypeArguments = ksValueParameter.type.resolve().arguments
            if (parameterizedTypeArguments.isNotEmpty()) {

            } else {
                stringBuilder.append("${requireNotNull(ksValueParameter.type.resolve().declaration.qualifiedName).asString()}::class.java")
            }
        }
        return stringBuilder.toString()
    }

    private fun buildFunctionCallParameter(
        functionDeclaration: KSFunctionDeclaration
    ): String {
        val stringBuilder = StringBuilder()
        functionDeclaration.parameters.forEachIndexed { index, ksValueParameter ->
            if (index != 0) {
                stringBuilder.append(", ")
            }
            if (ksValueParameter.isVararg) {
                stringBuilder.append("*").append(requireNotNull(ksValueParameter.name).asString())
            } else stringBuilder.append(requireNotNull(ksValueParameter.name).asString())
        }
        return stringBuilder.toString()
    }

    private fun buildType(ksType: KSType, nullableEnabled: Boolean = true): String {
        val stringBuilder = StringBuilder()
            .append(requireNotNull(ksType.declaration.qualifiedName).asString())
        val typeDeclaration = ksType.arguments
        if (typeDeclaration.isNotEmpty()) {
            stringBuilder.append("<")
                .apply {
                    typeDeclaration.forEachIndexed { index, ksTypeParameter ->
                        if (index != 0) {
                            append(", ")
                        }
                        val parameterType = requireNotNull(ksTypeParameter.type).resolve()
                        val parameterTypeString = this@InterProcessSymbolProcessProvider.buildType(parameterType)
                        append(parameterTypeString)
                    }
                }
                .append(">")
        }
        if (nullableEnabled && ksType.isMarkedNullable) {
            stringBuilder.append("?")
        }

        return stringBuilder.toString()
    }
}