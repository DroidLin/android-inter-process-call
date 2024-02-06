package com.lza.android.inter.process.compiler

import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier

/**
 * @author liuzhongao
 * @since 2024/2/6 10:58
 */

fun buildFunctionParameters(
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
                .append(buildType(parameterTypeClassDeclaration))
        } else {
            stringBuilder.append(requireNotNull(ksValueParameter.name).asString()).append(": ")
                .append(buildType(parameterTypeClassDeclaration))
        }
    }
    return stringBuilder.toString()
}

fun buildPropertyUniqueKey(propertyDeclaration: KSPropertyDeclaration): String {
    val stringBuilder = StringBuilder()
    if (propertyDeclaration.isMutable) {
        stringBuilder.append("var ")
    } else stringBuilder.append("val ")
    val receiverType = propertyDeclaration.extensionReceiver?.resolve()
    if (receiverType != null) {
        stringBuilder.append(buildType(receiverType)).append(".")
    }
    stringBuilder.append(propertyDeclaration.simpleName.asString())
    stringBuilder.append(": ${buildType(propertyDeclaration.type.resolve())}")
    return stringBuilder.toString()
}

fun buildFunctionUniqueKey(functionDeclaration: KSFunctionDeclaration): String {
    val stringBuilder = StringBuilder()
    if (functionDeclaration.modifiers.contains(Modifier.SUSPEND)) {
        stringBuilder.append("suspend ")
    }
    stringBuilder.append("fun ")
    val receiverType = functionDeclaration.extensionReceiver?.resolve()
    if (receiverType != null) {
        stringBuilder.append(buildType(receiverType)).append(".")
    }
    stringBuilder.append(functionDeclaration.simpleName.asString())
    stringBuilder.append("(")
    stringBuilder.append(buildFunctionParameters(functionDeclaration))
    stringBuilder.append(")")
    val returnKSType = functionDeclaration.returnType?.resolve()
    if (returnKSType != null) {
        stringBuilder.append(": ${buildType(returnKSType)}")
    }
    return stringBuilder.toString()
}

fun buildFunctionCallParameter(
    functionDeclaration: KSFunctionDeclaration
): String {
    return StringBuilder().also { builder ->
        functionDeclaration.parameters.forEachIndexed { index, ksValueParameter ->
            if (index != 0) {
                builder.append(", ")
            }
            if (ksValueParameter.isVararg) {
                builder.append("*").append(requireNotNull(ksValueParameter.name).asString())
            } else builder.append(requireNotNull(ksValueParameter.name).asString())
        }
    }
        .toString()
}

fun buildType(ksType: KSType, nullableEnabled: Boolean = true): String {
    return StringBuilder()
        .append(requireNotNull(ksType.declaration.qualifiedName).asString())
        .also { builder ->
            val typeDeclaration = ksType.arguments
            if (typeDeclaration.isNotEmpty()) {
                builder.append("<")
                typeDeclaration.forEachIndexed { index, ksTypeParameter ->
                    if (index != 0) {
                        builder.append(", ")
                    }
                    val parameterType = requireNotNull(ksTypeParameter.type).resolve()
                    val parameterTypeString = buildType(parameterType)
                    builder.append(parameterTypeString)
                }
                builder.append(">")
            }
            if (nullableEnabled && ksType.isMarkedNullable) {
                builder.append("?")
            }
        }
        .toString()
}