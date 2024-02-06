package com.lza.android.inter.process.compiler

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.Modifier
import java.io.Writer

/**
 * @author liuzhongao
 * @since 2024/2/6 11:05
 */
internal object InterfaceStubClassGenerator {

    @JvmStatic
    fun buildInterfaceProxyImplementationClass(
        codeGenerator: CodeGenerator,
        interfaceClassDeclaration: KSClassDeclaration,
        generatedStubClassDeclaration: KSClassDeclaration
    ) {
        buildInterfaceCalleeStubImplementationClass(
            codeGenerator = codeGenerator,
            interfaceClassDeclaration = interfaceClassDeclaration,
            generatedStubClassDeclaration = generatedStubClassDeclaration
        )
    }

    @JvmStatic
    private fun buildInterfaceCalleeStubImplementationClass(
        codeGenerator: CodeGenerator,
        interfaceClassDeclaration: KSClassDeclaration,
        generatedStubClassDeclaration: KSClassDeclaration
    ) {
        require(interfaceClassDeclaration.classKind == ClassKind.INTERFACE) {
            "annotation requires ${interfaceClassDeclaration.qualifiedName?.asString() ?: ""} declared as interface"
        }
        val implClassName = "${interfaceClassDeclaration.simpleName.asString()}_Generated_Stub"
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

        buildKtClassPackage(writer, interfaceClassDeclaration.packageName.asString())
        buildKtClassBlock(writer, interfaceClassDeclaration, generatedStubClassDeclaration, implClassName) {
            buildInvokeFunctionAndBody(writer, interfaceClassDeclaration)
        }
        writer.flush()
        writer.close()
    }

    private fun buildKtClassPackage(writer: Writer, packageName: String) {
        writer.appendLine("package $packageName")
            .appendLine()
    }

    private inline fun buildKtClassBlock(
        writer: Writer,
        interfaceDeclaration: KSClassDeclaration,
        generatedStubClassDeclaration: KSClassDeclaration,
        className: String,
        body: () -> Unit
    ) {
        writer.appendLine("class $className constructor (")
            .appendLine("\tprivate val implementationInstance: ${requireNotNull(interfaceDeclaration.qualifiedName).asString()}")
            .appendLine(") : ${requireNotNull(generatedStubClassDeclaration.qualifiedName).asString()} {")
            .apply { body() }
            .appendLine("}")
    }

    private fun buildInvokeFunctionAndBody(
        writer: Writer,
        interfaceClassDeclaration: KSClassDeclaration
    ) {
        writer.appendLine()
            .appendLine("\toverride fun invokeNonSuspendFunction(functionName: String, functionParameter: List<Any?>): Any? {")
            .apply {
                val nonSuspendMemberDeclaration = (interfaceClassDeclaration.getDeclaredProperties() +
                        interfaceClassDeclaration.getDeclaredFunctions().filter { !it.modifiers.contains(Modifier.SUSPEND) }).toList()
                if (nonSuspendMemberDeclaration.isNotEmpty()) {
                    appendLine("\t\treturn when (functionName) {")
                    nonSuspendMemberDeclaration.forEach { ksDeclaration ->
                        when (ksDeclaration) {
                            is KSPropertyDeclaration -> {
                                appendLine("\t\t\t\"${buildPropertyUniqueKey(ksDeclaration)}\" -> ${buildPropertyInvocationCode(ksDeclaration)}")
                            }
                            is KSFunctionDeclaration -> {
                                appendLine("\t\t\t\"${buildFunctionUniqueKey(ksDeclaration)}\" -> ${buildFunctionInvocationCode(ksDeclaration)}")
                            }
                        }
                    }
                    appendLine("\t\t\telse -> null")
                    appendLine("\t\t}")
                } else appendLine("\t\treturn null")
            }
            .appendLine("\t}")
            .appendLine()
            .appendLine("\toverride suspend fun invokeSuspendFunction(functionName: String, functionParameter: List<Any?>): Any? {")
            .apply {
                val suspendFunctionDeclarations = interfaceClassDeclaration.getDeclaredFunctions().filter { it.modifiers.contains(Modifier.SUSPEND) }.toList()
                if (suspendFunctionDeclarations.isNotEmpty()) {
                    appendLine("\t\treturn when (functionName) {")
                    interfaceClassDeclaration.getDeclaredFunctions().filter { it.modifiers.contains(Modifier.SUSPEND) }
                        .forEach { ksFunctionDeclaration ->
                            appendLine("\t\t\t\"${buildFunctionUniqueKey(ksFunctionDeclaration)}\" -> ${buildFunctionInvocationCode(ksFunctionDeclaration)}")
                        }
                    appendLine("\t\t\telse -> null")
                    appendLine("\t\t}")
                } else appendLine("\t\treturn null")
            }
            .appendLine("\t}")
    }

    @JvmStatic
    private fun buildPropertyInvocationCode(
        ksPropertyDeclaration: KSPropertyDeclaration
    ): String {
        val stringBuilder = StringBuilder()
        if (ksPropertyDeclaration.extensionReceiver != null) {
            val receiverType = requireNotNull(ksPropertyDeclaration.extensionReceiver?.resolve())
            val isExtensionReceiverNullable = requireNotNull(ksPropertyDeclaration.extensionReceiver?.resolve()).isMarkedNullable
            stringBuilder.append("this.implementationInstance.run { (functionParameter[0]")
            if (isExtensionReceiverNullable) {
                stringBuilder.append(" as? ")
            } else stringBuilder.append(" as ")
            stringBuilder.append(buildType(receiverType))
            stringBuilder.append(")")
            stringBuilder.append(".")
            stringBuilder.append(ksPropertyDeclaration.simpleName.asString())
            stringBuilder.append(" }")
        } else {
            stringBuilder.append("this.implementationInstance.${ksPropertyDeclaration.simpleName.asString()}")
        }
        return stringBuilder.toString()
    }

    @JvmStatic
    private fun buildFunctionInvocationCode(
        ksFunctionDeclaration: KSFunctionDeclaration
    ): String {
        val stringBuilder = StringBuilder()
        var parameterOffset = 0
        if (ksFunctionDeclaration.extensionReceiver != null) {
            parameterOffset = 1
            val receiverType = requireNotNull(ksFunctionDeclaration.extensionReceiver?.resolve())
            val isExtensionReceiverNullable = requireNotNull(ksFunctionDeclaration.extensionReceiver?.resolve()).isMarkedNullable
            stringBuilder.append("this.implementationInstance.run { (functionParameter[0]")
            if (isExtensionReceiverNullable) {
                stringBuilder.append(" as? ")
            } else stringBuilder.append(" as ")
                .append(buildType(receiverType))
                .append(")")
                .append(".")
                .append(ksFunctionDeclaration.simpleName.asString())
                .append("(")
                .append(buildFunctionInvocationParameter(parameterOffset, ksFunctionDeclaration))
                .append(")")
                .append(" }")
        } else {
            parameterOffset = 0
            stringBuilder.append("this.implementationInstance.${ksFunctionDeclaration.simpleName.asString()}")
                .append("(")
                .append(buildFunctionInvocationParameter(parameterOffset, ksFunctionDeclaration))
                .append(")")
        }
        return stringBuilder.toString()
    }

    @JvmStatic
    private fun buildFunctionInvocationParameter(
        parameterOffset: Int,
        ksFunctionDeclaration: KSFunctionDeclaration
    ): String {
        return StringBuilder().also { builder ->
            ksFunctionDeclaration.parameters.forEachIndexed { index, ksValueParameter ->
                val parameterType = ksValueParameter.type.resolve()
                if (index != 0) {
                    builder.append(", ")
                }
                builder.append("functionParameter[${index + parameterOffset}]")
                if (parameterType.isMarkedNullable) {
                    builder.append(" as? ")
                } else builder.append(" as ")
                builder.append(buildType(parameterType))
            }
        }.toString()
    }

}