package org.jetbrains.reflekt.plugin.ir.type.util

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.jvm.codegen.psiElement
import org.jetbrains.kotlin.com.intellij.mock.MockProject
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI
import org.jetbrains.kotlin.ir.backend.js.utils.asString
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.impl.IrSimpleTypeImpl
import org.jetbrains.kotlin.ir.util.isFakeOverride
import org.jetbrains.kotlin.ir.visitors.IrElementVisitor
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.reflekt.plugin.analysis.ir.*
import org.jetbrains.reflekt.plugin.analysis.toPrettyString
import java.io.File

/**
 * @property visitors
 */
class IrTestComponentRegistrar(val visitors: List<IrElementVisitor<Unit, IrPluginContext>>) : ComponentRegistrar {
    @ObsoleteDescriptorBasedAPI
    override fun registerProjectComponents(project: MockProject, configuration: CompilerConfiguration) {
        IrGenerationExtension.registerExtension(project, object : IrGenerationExtension {
            override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
                println("$moduleFragment module fragment")
                visitors.forEach { moduleFragment.accept(it, pluginContext) }
            }
        })
    }
}

/**
 * Visits IrFunctions, filtered by name via [filterByName], for example, to avoid special methods like equals() or toString().
 */
abstract class FilteredIrFunctionVisitor(val filterByName: (String) -> Boolean) : IrElementVisitor<Unit, IrPluginContext> {

    abstract fun visitFilteredFunction(declaration: IrFunction, data: IrPluginContext)

    override fun visitFunction(declaration: IrFunction, data: IrPluginContext) {
        val name = declaration.name.asString()
        if (filterByName(name)) {
            visitFilteredFunction(declaration, data)
        }
        super.visitFunction(declaration, data)
    }

    override fun visitElement(element: IrElement, data: IrPluginContext) = element.acceptChildren(this, data)
}

/**
 * Checks IrFunctions transforming to IrType, stores the result and expected IrType which is written in functions' docs in their implementation.
 */
class IrFunctionTypeVisitor(filterByName: (String) -> Boolean) : FilteredIrFunctionVisitor(filterByName) {
    val functions = mutableListOf<Function>()

    override fun visitFilteredFunction(declaration: IrFunction, data: IrPluginContext) {
        val name = declaration.name.asString()
        val type = declaration.irType()
//          Todo: rename tag from kotlinType to irType once we delete obsolete tests
        val expectedType = (declaration.psiElement as? KtNamedFunction)?.getTagContent("kotlinType") ?: ("Expected ir type of function $name is null")
        functions.add(Function(name, type, expectedType))
    }

    /**
     * @property name
     * @property actualType
     * @property expectedType
     */
    data class Function(
        val name: String,
        val actualType: IrType,
        val expectedType: String
    )
}

/**
 * Checks IrType (from expression type arguments) transforming to ParametrizedType, simulating the behaviour of [ReflektFunctionInvokeArgumentsCollector].
 * The expected Kotlin Type is written in expression value arguments.
 */
class IrCallArgumentTypeVisitor : IrElementVisitor<Unit, IrPluginContext> {
    val typeArguments = mutableListOf<TypeArgument>()

    @ObsoleteDescriptorBasedAPI
    override fun visitCall(expression: IrCall, data: IrPluginContext) {
        val typeArgument = expression.getTypeArgument(0) ?: error("No arguments found in expression $expression")
        val a = (typeArgument as IrSimpleTypeImpl).arguments
        val type = typeArgument.toParameterizedType()
        val valueArgument = expression.getValueArgument(0) ?: error("No value passed as expected KotlinType in expression $expression")
        val expectedType = (valueArgument as IrConstImpl<*>).value.toString()
        typeArguments.add(TypeArgument(typeArgument.asString(), type, expectedType))
        println(type.toPrettyString())
        super.visitCall(expression, data)
    }

    override fun visitElement(element: IrElement, data: IrPluginContext) = element.acceptChildren(this, data)

    /**
     * @property name
     * @property actualType
     * @property expectedType
     */
    data class TypeArgument(
        val name: String,
        val actualType: KotlinType,
        val expectedType: String,
    )
}


class IrFunctionSubtypesVisitor(filterByName: (String) -> Boolean) : FilteredIrFunctionVisitor(filterByName) {
    val functionSubtypesList: MutableList<FunctionSubtypes> = mutableListOf()

    override fun visitFilteredFunction(declaration: IrFunction, data: IrPluginContext) {
        // We should miss have overridden functions since they can have supertypes,
        // but the tests files don't contain these functions and then have empty KDoc block
        if (declaration.isFakeOverride) {
            return
        }
        val declarationSubtypes = FunctionSubtypes(declaration)
        for (functionSubtypes in functionSubtypesList) {
            val builtIns = declaration.createIrBuiltIns(data)
            if (declarationSubtypes.function.isSubTypeOf(functionSubtypes.function, builtIns)) {
                functionSubtypes.actualSubtypes.add(declaration)
            }
            if (functionSubtypes.function.isSubTypeOf(declarationSubtypes.function, builtIns)) {
                declarationSubtypes.actualSubtypes.add(functionSubtypes.function)
            }
        }
        functionSubtypesList.add(declarationSubtypes)

    }


    data class FunctionSubtypes(val function: IrFunction, val actualSubtypes: MutableList<IrFunction> = mutableListOf()) {
        val expectedSubtypes = (function.psiElement as? KtNamedFunction)?.parseKdocLinks("subtypes") ?: emptyList()
    }
}

/**
 * We cannot access IR level when compilation is done, so to test it properly we can pass any visitor to it
 * and do any checks during compilation when IR code is generated.
 * After that, we can check the result by getting info from visitors.
 *
 * @param sourceFiles
 * @param visitors
 * @return
 */
fun visitIrElements(sourceFiles: List<File>, visitors: List<IrElementVisitor<Unit, IrPluginContext>>): KotlinCompilation.Result {
    val plugin = IrTestComponentRegistrar(visitors)
    return KotlinCompilation().apply {
        sources = sourceFiles.map { SourceFile.fromPath(it) }
        jvmTarget = "11"
        compilerPlugins = listOf(plugin)
        inheritClassPath = true
        messageOutputStream
        useIR = true
    }.compile()
}