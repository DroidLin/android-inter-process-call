package com.lza.android.inter.process.compiler

import java.io.Writer
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.Element
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.PackageElement
import javax.lang.model.element.TypeElement

/**
 * @author liuzhongao
 * @since 2024/2/16 17:17
 */
internal object InterfaceStubClassGenerator {

    @JvmStatic
    fun buildInterfaceProxyImplementationClass(
        environment: ProcessingEnvironment,
        rootElement: Element,
    ) {
        buildInterfaceCalleeStubImplementationClass(
            environment = environment,
            rootElement = rootElement,
        )
    }

    @JvmStatic
    private fun buildInterfaceCalleeStubImplementationClass(
        environment: ProcessingEnvironment,
        rootElement: Element,
    ) {
        if (rootElement !is TypeElement) {
            return
        }

        val packageName = (rootElement.enclosingElement as? PackageElement)?.qualifiedName?.toString() ?: return
        val simpleName = rootElement.simpleName.toString()
        val newClassName = "${simpleName}_Generated_Stub"
        val writer = environment.filer.createSourceFile("${packageName}.${newClassName}").openWriter().buffered()
        val declaringClassName = rootElement.qualifiedName.toString()

        writer.appendLine("package ${packageName};")
            .appendLine()
        this.buildClassIdentifier(
            writer = writer,
            newClassName = newClassName,
            rootElement = rootElement
        ) {
            writer.appendLine()
                .appendLine("\t@androidx.annotation.NonNull")
                .appendLine("\tprivate final $declaringClassName implementationInstance;")
                .appendLine()
                .appendLine("\tpublic ${newClassName}(@androidx.annotation.NonNull $declaringClassName implementationInstance) {")
                .appendLine("\t\tthis.implementationInstance = implementationInstance;")
                .appendLine("\t}")

            writer.appendLine()
                .appendLine("\t@androidx.annotation.Nullable")
                .appendLine("\t@java.lang.Override")
                .appendLine("\tpublic Object invokeNonSuspendFunction(@androidx.annotation.NonNull java.lang.String functionName, @androidx.annotation.NonNull java.util.List<?> functionParameter) {")
                .apply {
                    val functionList = rootElement.enclosedElements.filterIsInstance<ExecutableElement>().filter { !it.isSuspendExecutable }
                    if (functionList.isNotEmpty()) {
                        appendLine("\t\tObject result = null;")
                        appendLine("\t\tswitch (functionName) {")
                        functionList.forEach { element ->
                            appendLine("\t\t\tcase \"${buildFunctionUniqueKey(element)}\":")
                            if (element.returnType.toString() == "void" || element.returnType.toString() == "java.lang.Void") {
                                append("\t\t\t\tthis.implementationInstance.${element.simpleName}(")
                            } else append("\t\t\t\tresult = this.implementationInstance.${element.simpleName}(")
                            element.parameters.forEachIndexed { index, variableElement ->
                                if (index != 0) {
                                    append(", ")
                                }
                                append("(${buildType(variableElement)}) functionParameter.get(${index})")
                            }
                            appendLine(");")
                            appendLine("\t\t\t\tbreak;")
                        }
                        appendLine("\t\t\tdefault:")
                        appendLine("\t\t\t\tbreak;")
                        appendLine("\t\t}")
                        appendLine("\t\treturn result;")
                    } else appendLine("return null;")
                }
                .appendLine("\t}")

            writer.appendLine()
                .appendLine("\t@androidx.annotation.Nullable")
                .appendLine("\t@java.lang.Override")
                .appendLine("\tpublic java.lang.Object invokeSuspendFunction(@androidx.annotation.NonNull String functionName, @androidx.annotation.NonNull java.util.List<?> functionParameter, @androidx.annotation.NonNull kotlin.coroutines.Continuation<? super Object> \$completion) {")
                .apply {
                    val functionList = rootElement.enclosedElements.filterIsInstance<ExecutableElement>().filter { it.isSuspendExecutable }
                    if (functionList.isNotEmpty()) {
                        appendLine("\t\tObject result = null;")
                        appendLine("\t\tswitch (functionName) {")
                        functionList.forEach { element ->
                            appendLine("\t\t\tcase \"${buildFunctionUniqueKey(element)}\":")
                            if (element.returnType.toString() == "void" || element.returnType.toString() == "java.lang.Void") {
                                append("\t\t\t\tthis.implementationInstance.${element.simpleName}(")
                            } else append("\t\t\t\tresult = this.implementationInstance.${element.simpleName}(")
                            val functionParameterList = element.parameters
                            for (index in 0 until functionParameterList.size - 1) {
                                if (index != 0) {
                                    append(", ")
                                }
                                append("(${buildType(functionParameterList[index])}) functionParameter.get(${index})")
                            }
                            if (functionParameterList.size > 1) {
                                append(", ")
                            }
                            append("\$completion")
                            appendLine(");")
                            appendLine("\t\t\t\tbreak;")
                        }
                        appendLine("\t\t\tdefault:")
                        appendLine("\t\t\t\tbreak;")
                        appendLine("\t\t}")
                        appendLine("\t\treturn result;")
                    } else appendLine("return null;")
                }
                .appendLine("\t}")
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
        writer.appendLine("public final class $newClassName implements com.lza.android.inter.process.library.interfaces.GeneratedStubFunction {")
        classBody()
        writer.appendLine("}")
    }

}