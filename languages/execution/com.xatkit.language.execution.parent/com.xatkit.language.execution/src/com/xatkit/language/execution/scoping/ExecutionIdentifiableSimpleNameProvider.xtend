package com.xatkit.language.execution.scoping

import com.xatkit.language.execution.jvmmodel.ExecutionJvmModelInferrer
import org.eclipse.xtext.common.types.JvmIdentifiableElement
import org.eclipse.xtext.xbase.featurecalls.IdentifiableSimpleNameProvider

class ExecutionIdentifiableSimpleNameProvider extends IdentifiableSimpleNameProvider {

	/**
	 * Returns the simple name of the provided {@code element}.
	 * <p>
	 * This method is a quick fix to return {@code "this"} when the accessed class is the one inferred from the 
	 * {@link ExecutionJvmModelInferrer}.
	 * 
	 * @return the simple name of the provided {@code element}
	 */
	override getSimpleName(JvmIdentifiableElement element) {
		if (element.simpleName == ExecutionJvmModelInferrer.INFERRED_CLASS_NAME) {
			return 'this'
		} else {
			super.getSimpleName(element)
		}
	}
}
