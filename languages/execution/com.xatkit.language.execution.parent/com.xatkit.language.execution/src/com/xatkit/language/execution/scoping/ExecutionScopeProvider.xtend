/*
 * generated by Xtext 2.12.0
 */
package com.xatkit.language.execution.scoping

import com.xatkit.execution.ExecutionModel
import java.util.ArrayList
import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.EReference
import org.eclipse.xtext.resource.IEObjectDescription
import org.eclipse.xtext.scoping.IScope
import org.eclipse.xtext.scoping.impl.SimpleScope
import org.eclipse.xtext.resource.EObjectDescription
import org.eclipse.xtext.naming.QualifiedName
import com.xatkit.platform.PlatformDefinition
import com.xatkit.execution.ExecutionPackage

/**
 * This class contains custom scoping description.
 * 
 * See https://www.eclipse.org/Xtext/documentation/303_runtime_concepts.html#scoping
 * on how and when to use it.
 */
class ExecutionScopeProvider extends AbstractExecutionScopeProvider {

	override getScope(EObject context, EReference reference) {
		if (context instanceof ExecutionModel) {
			return getScope(context as ExecutionModel, reference)
		} else {
			super.getScope(context, reference)
		}
	}

	/**
	 * Returns the {@link IScope} associated to the provided {@link ExecutionModel}.
	 * <p>
	 * When the provided {@code reference} matches {@code ExecutionModel.EventProviderDefinitions} this method sets the 
	 * maps the imported providers to their qualified names in the returned scope. This is required when operating at 
	 * the semantic model level, otherwise the name of the imported provider cannot be retrieved and operations like 
	 * quickfixes won't work.
	 * 
	 * @param context the {@link ExecutionModel} to compute the scope of
	 * @param reference the {@link ExecutionModel}'s {@link EReference} to compute the scope of
	 * 
	 * @return the created {@link IScope}
	 */
	private def IScope getScope(ExecutionModel context, EReference reference) {
		if (reference == ExecutionPackage.Literals.EXECUTION_MODEL__EVENT_PROVIDER_DEFINITIONS) {
			var result = new ArrayList<IEObjectDescription>()
			for (eventProvider : context.eventProviderDefinitions) {
				val platformName = (eventProvider.eContainer as PlatformDefinition).name
				val eventProviderName = eventProvider.name
				result.add(
					EObjectDescription.create(QualifiedName.create(platformName, eventProviderName), eventProvider))
			}
			new SimpleScope(IScope.NULLSCOPE, result)
		} else {
			super.getScope(context, reference)
		}
	}

}
