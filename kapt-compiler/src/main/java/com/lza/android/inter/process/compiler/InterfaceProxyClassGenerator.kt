package com.lza.android.inter.process.compiler

import java.io.Writer
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.Element
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.PackageElement
import javax.lang.model.element.TypeElement

/**
 * @author liuzhongao
 * @since 2024/2/16 14:43
 */
internal object InterfaceProxyClassGenerator {

    @JvmStatic
    fun buildInterfaceProxyImplementationClass(
        environment: ProcessingEnvironment,
        rootElement: Element,
    ) {
        if (rootElement !is TypeElement) {
            return
        }
        val packageName = (rootElement.enclosingElement as? PackageElement)?.qualifiedName?.toString() ?: return
        val simpleName = rootElement.simpleName.toString()
        val newClassName = "${simpleName}_Generated_Proxy"
        val writer = environment.filer.createSourceFile("${packageName}.${newClassName}").openWriter().buffered()

        val declaringClassName = rootElement.qualifiedName.toString()

        writer.appendLine("package ${packageName};")
            .appendLine()
        this.buildClassIdentifier(
            writer = writer,
            newClassName = newClassName,
            rootElement = rootElement
        ) {
            this.buildClassInternalField(
                writer = writer,
                newClassName = newClassName,
                rootElement = rootElement
            )
            val memberFunctionList = rootElement.enclosedElements
            if (memberFunctionList.isNotEmpty()) {
                memberFunctionList.forEach { function ->
                    if (function !is ExecutableElement) return@forEach
                    this.buildImplementationMethod(writer, function) {
                        val returnTypeString = function.returnType.toString()
                        val returnValueExists = returnTypeString != "void" && returnTypeString != "java.lang.Void"
                        if (returnValueExists) {
                            writer.appendLine("\t\tjava.lang.Object data = null;")
                        }
                        writer.appendLine("\t\ttry {")
                        if (function.isSuspendExecutable) {
                            if (returnValueExists) {
                                writer.appendLine("\t\t\tdata = com.lza.android.inter.process.library.KSPHelperKt.invokeDirectSuspendKotlinFunction(")
                            } else writer.appendLine("\t\t\tcom.lza.android.inter.process.library.KSPHelperKt.invokeDirectSuspendKotlinFunction(")
                            writer.appendLine("\t\t\t\tthis.context,")
                            writer.appendLine("\t\t\t\tthis.currentProcessKey,")
                            writer.appendLine("\t\t\t\tthis.destinationProcessKey,")
                            writer.appendLine("\t\t\t\t\"${declaringClassName}\",")
                            writer.appendLine("\t\t\t\t\"${buildFunctionUniqueKey(function)}\",")
                            writer.append("\t\t\t\tkotlin.collections.CollectionsKt.listOf(")
                            val continuationVariable = function.continuationVariable
                            val functionParameter = function.parametersWithoutContinuation
                            if (functionParameter.isNotEmpty()) {
                                functionParameter.forEachIndexed { index, variableElement ->
                                    if (index != 0) {
                                        writer.append(", ")
                                    }
                                    writer.append(variableElement.simpleName.toString())
                                }
                            }
                            writer.appendLine("), ")
                            if (continuationVariable != null) {
                                writer.appendLine("\t\t\t${continuationVariable.simpleName}")
                            }
                            writer.appendLine("\t\t\t);")
                        } else {
                            if (returnValueExists) {
                                writer.appendLine("\t\t\tdata = com.lza.android.inter.process.library.KSPHelperKt.invokeDirectKotlinFunction(")
                            } else writer.appendLine("\t\t\tcom.lza.android.inter.process.library.KSPHelperKt.invokeDirectKotlinFunction(")
                            writer.appendLine("\t\t\t\tthis.coroutineContext,")
                            writer.appendLine("\t\t\t\tthis.context,")
                            writer.appendLine("\t\t\t\tthis.currentProcessKey,")
                            writer.appendLine("\t\t\t\tthis.destinationProcessKey,")
                            writer.appendLine("\t\t\t\t\"${declaringClassName}\",")
                            writer.appendLine("\t\t\t\t\"${buildFunctionUniqueKey(function)}\",")
                            writer.append("\t\t\t\tkotlin.collections.CollectionsKt.listOf(")
                            val functionParameter = function.parameters
                            if (functionParameter.isNotEmpty()) {
                                functionParameter.forEachIndexed { index, variableElement ->
                                    if (index != 0) {
                                        writer.append(", ")
                                    }
                                    writer.append(variableElement.simpleName.toString())
                                }
                            }
                            writer.appendLine(")")
                            writer.appendLine("\t\t\t);")
                        }
                        writer.appendLine("\t\t} catch (Throwable throwable) {")
                            .appendLine("\t\t\tif (this.exceptionHandler != null && !this.exceptionHandler.handleException(throwable)) {")
                            .appendLine("\t\t\t\tthrow new com.lza.android.inter.process.library.kotlin.UnHandledRuntimeException(throwable);")
                            .appendLine("\t\t\t}")
                            .appendLine("\t\t}")
                            .apply {
                                if (returnValueExists) {
                                    appendLine("\t\tif (data == null) {")
                                        .appendLine("\t\t\tif (this.interfaceDefaultImpl != null) {")
                                        .appendLine("\t\t\t\tdata = this.interfaceDefaultImpl.${function.simpleName}(${
                                            function.parameters.let { functionParameter ->
                                                StringBuilder().also { stringBuilder ->
                                                    if (functionParameter.isNotEmpty()) {
                                                        functionParameter.forEachIndexed { index, variableElement ->
                                                            if (index != 0) {
                                                                stringBuilder.append(", ")
                                                            }
                                                            stringBuilder.append(variableElement.simpleName.toString())
                                                        }
                                                    }
                                                }.toString()
                                            }
                                        });")
                                        .appendLine("\t\t\t}")
                                        .appendLine("\t\t}")
                                        .appendLine("\t\treturn (${returnTypeString}) data;")
                                }/* else {
                                    appendLine("\t\tif (this.interfaceDefaultImpl != null) {")
                                        .appendLine("\t\t\tthis.interfaceDefaultImpl.${function.simpleName}(${
                                            function.parameters.let { functionParameter ->
                                                StringBuilder().also { stringBuilder ->
                                                    if (functionParameter.isNotEmpty()) {
                                                        functionParameter.forEachIndexed { index, variableElement ->
                                                            if (index != 0) {
                                                                stringBuilder.append(", ")
                                                            }
                                                            stringBuilder.append(variableElement.simpleName.toString())
                                                        }
                                                    }
                                                }.toString()
                                            }
                                        });")
                                        .appendLine("\t\t}")
                                }*/
                            }
                    }
                }
            }
        }
        writer.flush()
        writer.close()
    }

    @JvmStatic
    private fun buildClassIdentifier(
        writer: Writer,
        newClassName: String,
        rootElement: TypeElement,
        classBody: () -> Unit = {}
    ) {
        writer.appendLine("public final class $newClassName implements ${rootElement.qualifiedName} {")
        classBody()
        writer.appendLine("}")
    }

    @JvmStatic
    private fun buildClassInternalField(
        writer: Writer,
        newClassName: String,
        rootElement: TypeElement,
    ) {
        writer.appendLine()
            .appendLine("\t@androidx.annotation.NonNull")
            .appendLine("\tprivate final android.content.Context context;")
            .appendLine()
            .appendLine("\t@androidx.annotation.NonNull")
            .appendLine("\tprivate final java.lang.String currentProcessKey;")
            .appendLine()
            .appendLine("\t@androidx.annotation.NonNull")
            .appendLine("\tprivate final java.lang.String destinationProcessKey;")
            .appendLine()
            .appendLine("\t@androidx.annotation.NonNull")
            .appendLine("\tprivate final kotlin.coroutines.CoroutineContext coroutineContext;")
            .appendLine()
            .appendLine("\t@androidx.annotation.Nullable")
            .appendLine("\tprivate final ${rootElement.qualifiedName} interfaceDefaultImpl;")
            .appendLine()
            .appendLine("\t@androidx.annotation.Nullable")
            .appendLine("\tprivate final com.lza.android.inter.process.library.interfaces.ExceptionHandler exceptionHandler;")
            .appendLine()

        writer.appendLine()
            .append("\tpublic ${newClassName}(")
            .append("@androidx.annotation.NonNull android.content.Context context, ")
            .append("@androidx.annotation.NonNull java.lang.String currentProcessKey, ")
            .append("@androidx.annotation.NonNull java.lang.String destinationProcessKey, ")
            .append("@androidx.annotation.NonNull kotlin.coroutines.CoroutineContext coroutineContext, ")
            .append("@androidx.annotation.NonNull ${rootElement.qualifiedName} interfaceDefaultImpl, ")
            .append("@androidx.annotation.NonNull com.lza.android.inter.process.library.interfaces.ExceptionHandler exceptionHandler")
            .appendLine(") {")
        writer.appendLine("\t\tthis.context = context;")
        writer.appendLine("\t\tthis.currentProcessKey = currentProcessKey;")
        writer.appendLine("\t\tthis.destinationProcessKey = destinationProcessKey;")
        writer.appendLine("\t\tthis.coroutineContext = coroutineContext;")
        writer.appendLine("\t\tthis.interfaceDefaultImpl = interfaceDefaultImpl;")
        writer.appendLine("\t\tthis.exceptionHandler = exceptionHandler;")
        writer.appendLine("\t}")
    }

    @JvmStatic
    private fun buildImplementationMethod(writer: Writer, element: ExecutableElement, body: () -> Unit = {}) {
        writer.appendLine()
            .appendLine("\t@java.lang.Override")
            .append("\tpublic final ${element.returnType} ${element.simpleName}(")
        val methodParameter = element.parameters
        if (methodParameter.isNotEmpty()) {
            methodParameter.forEachIndexed { index, variableElement ->
                if (index != 0) {
                    writer.append(", ")
                }
                writer.append(buildType(variableElement))
                    .append(" ")
                    .append(variableElement.simpleName.toString())
            }
        }
        writer.appendLine(") {")
        body()
        writer.appendLine("\t}")
    }

    @JvmStatic
    private fun buildMethodBody(writer: Writer, element: ExecutableElement) {

    }
}