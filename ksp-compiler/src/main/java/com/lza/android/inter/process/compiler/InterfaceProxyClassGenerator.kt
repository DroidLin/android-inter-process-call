package com.lza.android.inter.process.compiler

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.isInternal
import com.google.devtools.ksp.isOpen
import com.google.devtools.ksp.isProtected
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.Modifier
import java.io.Writer

/**
 * @author liuzhongao
 * @since 2024/2/6 10:56
 */
internal object InterfaceProxyClassGenerator {

    @JvmStatic
    fun buildInterfaceProxyImplementationClass(
        resolver: Resolver,
        codeGenerator: CodeGenerator,
        interfaceClassDeclaration: KSClassDeclaration
    ) {
        this.buildInterfaceCallerProxyImplementationClass(
            resolver = resolver,
            codeGenerator = codeGenerator,
            interfaceClassDeclaration = interfaceClassDeclaration
        )
    }

    /**
     * build caller interface proxy implementation class.
     */
    @JvmStatic
    private fun buildInterfaceCallerProxyImplementationClass(
        resolver: Resolver,
        codeGenerator: CodeGenerator,
        interfaceClassDeclaration: KSClassDeclaration
    ) {
        require(interfaceClassDeclaration.classKind == ClassKind.INTERFACE) {
            "annotation requires ${interfaceClassDeclaration.qualifiedName?.asString() ?: ""} declared as interface"
        }

        val implClassName = "${interfaceClassDeclaration.simpleName.asString()}_Generated_Proxy"
        val writer = codeGenerator.createNewFile(
            dependencies = Dependencies(
                aggregating = true,
                sources = arrayOf(requireNotNull(interfaceClassDeclaration.containingFile))
            ),
            packageName = interfaceClassDeclaration.packageName.asString(),
            fileName = implClassName
        ).bufferedWriter()

        buildKtClassPackage(writer, interfaceClassDeclaration.packageName.asString())
        buildKtClassBlock(writer, interfaceClassDeclaration, implClassName) {
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
            buildExceptionHandlerRunCatchingFunction(writer)
        }
        writer.flush()
        writer.close()
    }

    private fun buildKtClassPackage(
        writer: Writer,
        packageName: String
    ) {
        writer.appendLine("package $packageName")
            .appendLine()
    }

    private inline fun buildKtClassBlock(
        writer: Writer,
        interfaceDeclaration: KSClassDeclaration,
        className: String,
        body: () -> Unit
    ) {
        writer.appendLine("class $className @JvmOverloads constructor(")
            .appendLine("\tprivate val context: android.content.Context,")
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
                    append(buildType(receiverType)).append(".")
                }
            }
            .append(propertyDeclaration.simpleName.asString())
            .append(": ").append(buildType(propertyDeclaration.type.resolve()))
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
        val propertyType = propertyDeclaration.type.resolve()
        writer
            .appendLine("\t\t\tvar data = this@${className}.runWithExceptionHandle {")
            .appendLine("\t\t\t\tcom.lza.android.inter.process.library.invokeDirectProperty<${buildType(propertyDeclaration.type.resolve(), false)}>(")
            .appendLine("\t\t\t\t\tcoroutineContext = this@${className}.coroutineContext,")
            .appendLine("\t\t\t\t\tandroidContext = this@${className}.context,")
            .appendLine("\t\t\t\t\tcurrentProcessKey = this@${className}.currentProcessKey,")
            .appendLine("\t\t\t\t\tdestinationProcessKey = this@${className}.destinationProcessKey,")
            .appendLine("\t\t\t\t\tdeclaringClassName = \"${requireNotNull(propertyDeclaration.parentDeclaration?.qualifiedName).asString()}\",")
            .appendLine("\t\t\t\t\tpropertyName = \"${buildPropertyUniqueKey(propertyDeclaration)}\"")
            .appendLine("\t\t\t\t)")
            .appendLine("\t\t\t}")
            .appendLine("\t\t\tif (data == null && this@${className}.interfaceDefaultImpl != null) {")
            .apply {
                if (propertyDeclaration.extensionReceiver != null) {
                    appendLine("\t\t\t\tdata = this@${className}.interfaceDefaultImpl.run { ${propertyDeclaration.simpleName.asString()} }")
                } else appendLine("\t\t\t\tdata = this@${className}.interfaceDefaultImpl.${propertyDeclaration.simpleName.asString()}")
            }
            .appendLine("\t\t\t}")
            .apply {
                if (!propertyType.isMarkedNullable) {
                    appendLine("\t\t\tif (data == null) {")
                    appendLine("\t\t\t\tthrow kotlin.IllegalArgumentException(\"function return type requires non-null type, but returns null type after IPC call and the fallback operation!! please check.\")")
                    appendLine("\t\t\t}")
                }
            }
            .appendLine("\t\t\treturn data")
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
                    append(buildType(receiverType)).append(".")
                }
            }
            .append("${functionDeclaration.simpleName.asString()}(")
            .append(buildFunctionParameters(functionDeclaration))
            .append(")")
            .apply {
                val returnKSType = functionDeclaration.returnType?.resolve()
                if (returnKSType != null) {
                    append(": ${buildType(returnKSType)}")
                }
            }
            .appendLine(" {")
            .apply { functionBody() }
            .appendLine("\t}")
    }

    private fun buildFunctionBody(
        className: String,
        writer: Writer,
        functionDeclaration: KSFunctionDeclaration
    ) {
        val functionReturnType = requireNotNull(functionDeclaration.returnType?.resolve())
        val hasReturnValue = functionReturnType.declaration.simpleName.asString() != "Unit"
        writer
            .apply {
                if (hasReturnValue) {
                    appendLine("\t\tvar data = this@${className}.runWithExceptionHandle {")
                } else appendLine("\t\tthis@${className}.runWithExceptionHandle {")
                if (functionDeclaration.modifiers.contains(Modifier.SUSPEND)) {
                    appendLine("\t\t\tcom.lza.android.inter.process.library.invokeDirectSuspendKotlinFunction<${buildType(functionReturnType, false)}>(")
                } else {
                    appendLine("\t\t\tcom.lza.android.inter.process.library.invokeDirectKotlinFunction<${buildType(functionReturnType, false)}>(")
                    appendLine("\t\t\t\tcoroutineContext = this@${className}.coroutineContext,")
                }
                    .appendLine("\t\t\t\tandroidContext = this@${className}.context,")
                    .appendLine("\t\t\t\tcurrentProcessKey = this@${className}.currentProcessKey,")
                    .appendLine("\t\t\t\tdestinationProcessKey = this@${className}.destinationProcessKey,")
                    .appendLine("\t\t\t\tdeclaringClassName = \"${requireNotNull(functionDeclaration.parentDeclaration?.qualifiedName).asString()}\",")
                    .appendLine("\t\t\t\tfunctionName = \"${buildFunctionUniqueKey(functionDeclaration)}\",")
                    .appendLine("\t\t\t\tfunctionParameters = kotlin.collections.listOf(${buildFunctionCallParameter(functionDeclaration)}),")
                    .appendLine("\t\t\t)")
                    .appendLine("\t\t}")
            }
            .apply {
                if (!hasReturnValue) {
                    return@apply
                }
                appendLine("\t\tif (data == null && this@${className}.interfaceDefaultImpl != null) {")
                append("\t\t\tdata = ")
                if (functionDeclaration.extensionReceiver != null) {
                    appendLine("this@${className}.interfaceDefaultImpl.run { ${functionDeclaration.simpleName.asString()}(${buildFunctionCallParameter(functionDeclaration)}) }")
                } else {
                    appendLine("this@${className}.interfaceDefaultImpl.${functionDeclaration.simpleName.asString()}(${buildFunctionCallParameter(functionDeclaration)})")
                }
                appendLine("\t\t}")
            }
            .apply {
                if (functionReturnType.isMarkedNullable) {
                    appendLine("\t\treturn data")
                } else {
                    if (hasReturnValue) {
                        appendLine("\t\tif (data == null) {")
                        appendLine("\t\t\tthrow kotlin.IllegalArgumentException(\"function return type requires non-null type, but returns null type after IPC call and the fallback operation!! please check.\")")
                        appendLine("\t\t}")
                        appendLine("\t\treturn data")
                    }
                }
            }
    }

    @JvmStatic
    private fun buildExceptionHandlerRunCatchingFunction(writer: Writer) {
        writer.appendLine()
            .appendLine("\tprivate inline fun <T : Any> runWithExceptionHandle(block: () -> T?): T? {")
            .appendLine("\t\tval result = kotlin.runCatching(block)")
            .appendLine("\t\tval throwable = result.exceptionOrNull()")
            .appendLine("\t\tif (result.isFailure && throwable != null) {")
            .appendLine("\t\t\tif (this.isExceptionHandled(throwable)) {")
            .appendLine("\t\t\t\treturn null")
            .appendLine("\t\t\t}")
            .appendLine("\t\t\tthrow com.lza.android.inter.process.library.kotlin.UnHandledRuntimeException(throwable)")
            .appendLine("\t\t}")
            .appendLine("\t\tval data = result.getOrNull()")
            .appendLine("\t\tif (data != null) {")
            .appendLine("\t\t\treturn data")
            .appendLine("\t\t}")
            .appendLine("\t\treturn null")
            .appendLine("\t}")

        writer.appendLine()
            .appendLine("\tprivate fun isExceptionHandled(throwable: Throwable?): Boolean {")
            .appendLine("\t\tthrowable ?: return true")
            .appendLine("\t\treturn this.exceptionHandler?.handleException(throwable = throwable) ?: false")
            .appendLine("\t}")
    }

}