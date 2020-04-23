package com.xatkit.language.execution.ui.highlights

import com.xatkit.execution.ExecutionPackage
import com.xatkit.execution.State
import com.xatkit.execution.Transition
import com.xatkit.language.execution.ExecutionUtils
import org.eclipse.emf.ecore.EObject
import org.eclipse.xtext.ide.editor.syntaxcoloring.IHighlightedPositionAcceptor
import org.eclipse.xtext.ide.editor.syntaxcoloring.ISemanticHighlightingCalculator
import org.eclipse.xtext.util.CancelIndicator
import org.eclipse.xtext.xbase.XFeatureCall
import org.eclipse.xtext.xbase.ide.highlighting.XbaseHighlightingCalculator

class ExecutionSemanticHighlightingCalculator extends XbaseHighlightingCalculator implements ISemanticHighlightingCalculator {

	override protected boolean highlightElement(EObject object, IHighlightedPositionAcceptor acceptor,
		CancelIndicator cancelIndicator) {
		if (object instanceof State) {
			this.highlightFeature(acceptor, object, ExecutionPackage.Literals.STATE__NAME,
				ExecutionHighlightingConfiguration.STATE_ID)
		} else if (object instanceof XFeatureCall) {
			this.highlightXatkitFeatureCall(object as XFeatureCall, acceptor, cancelIndicator)
		} else if (object instanceof Transition) {
			this.highlightFeature(acceptor, object, ExecutionPackage.Literals.TRANSITION__STATE,
				ExecutionHighlightingConfiguration.STATE_ID)
		} else {
			super.highlightElement(object, acceptor, cancelIndicator)
		}
		return false;
	}

	/**
	 * Highlights the provided XFeature {@link XFeatureCall}.
	 * <p>
	 * This method looks if the provided {@code featureCall} is a reference to an {@link IntenDefinition} or 
	 * {@link EventDefinition}. If this is the case a Xatkit-specific highlighting is computed. If it is not the 
	 * {@link XbaseHighlightingCalculator#highlightElement} generic method is called to apply Xbase-specific highlight. 
	 * 
	 * @param featureCall the {@link XFeatureCall} to compute the highlights of
	 * @param acceptor the acceptor
	 * @param cancelIndicator the {@link CancelIndicator}
	 */
	def protected void highlightXatkitFeatureCall(XFeatureCall featureCall, IHighlightedPositionAcceptor acceptor,
		CancelIndicator cancelIndicator) {
		val feature = featureCall.feature
		val importedIntents = ExecutionUtils.getEventDefinitionsFromImportedLibraries(
			ExecutionUtils.getContainingExecutionModel(featureCall)).map[name].toList
		/*
		 * We differenciate intents from events even if for now they use the same style, this may not be the case later.
		 */
		if (importedIntents.contains(feature.identifier)) {
			this.highlightFeatureCall(featureCall, acceptor, ExecutionHighlightingConfiguration.INTENT_ID)
		} else {
			val importedEvents = ExecutionUtils.getEventDefinitionsFromImportedPlatforms(
				ExecutionUtils.getContainingExecutionModel(featureCall)).map[name].toList
			if (importedEvents.contains(feature.identifier)) {
				this.highlightFeatureCall(featureCall, acceptor, ExecutionHighlightingConfiguration.EVENT_ID)
			} else {
				super.highlightElement(featureCall, acceptor, cancelIndicator)
			}
		}
	}

}
