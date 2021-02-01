package io.reflekt.plugin.analysis.processor.uses

import io.reflekt.plugin.analysis.ClassOrObjectUses
import io.reflekt.plugin.analysis.ReflektInvokes
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.psiUtil.isPublic
import org.jetbrains.kotlin.resolve.BindingContext

class ObjectUsesProcessor(override val binding: BindingContext, private val reflektInvokes: ReflektInvokes) : BaseUsesProcessor<ClassOrObjectUses>(binding) {
    override val uses: ClassOrObjectUses = initClassOrObjectUses(reflektInvokes.objects)

    override fun process(element: KtElement): ClassOrObjectUses = processClassOrObjectUses(element, reflektInvokes.objects, uses)

    override fun shouldRunOn(element: KtElement) = element is KtObjectDeclaration && element.isPublic
}