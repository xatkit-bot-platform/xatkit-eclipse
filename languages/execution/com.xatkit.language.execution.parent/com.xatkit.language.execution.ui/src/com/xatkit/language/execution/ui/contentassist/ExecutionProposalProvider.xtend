/*
 * ge
 * nerated by Xtext 2.12.0
 */
package com.xatkit.language.execution.ui.contentassist

import com.xatkit.execution.ExecutionModel
import com.xatkit.platform.PlatformDefinition
import com.xatkit.utils.ImportRegistry
import org.eclipse.emf.ecore.EObject
import org.eclipse.xtext.Assignment
import org.eclipse.xtext.ui.editor.contentassist.ContentAssistContext
import org.eclipse.xtext.ui.editor.contentassist.ICompletionProposalAcceptor

/**
 * See https://www.eclipse.org/Xtext/documentation/304_ide_concepts.html#content-assist
 * on how to customize the content assistant.
 */
class ExecutionProposalProvider extends AbstractExecutionProposalProvider {

	override completeExecutionModel_EventProviderDefinitions(EObject model, Assignment assignment,
		ContentAssistContext context, ICompletionProposalAcceptor acceptor) {
		var platforms = ImportRegistry.getInstance.getImportedPlatforms(model as ExecutionModel)
		for (PlatformDefinition platform : platforms) {
			platform.eventProviderDefinitions.map[i|i.name].forEach [ iName |
				acceptor.accept(createCompletionProposal(platform.name + '.' + iName, context))
			]
		}
	}
	
	override completeExecutionRule_Event(EObject model, Assignment assignment, ContentAssistContext context,
		ICompletionProposalAcceptor acceptor) {
		/*
		 * Intents from libraries.
		 */
		var libraries = ImportRegistry.getInstance.getImportedLibraries(model.eContainer as ExecutionModel)
		libraries.map[m|m.eventDefinitions.map[e|e.name]].flatten.forEach [ eName |
			acceptor.accept(createCompletionProposal(eName, context))
		]
		/*
		 * Intents stored in used EventProviders
		 */
		var executionModel = model.eContainer as ExecutionModel
		executionModel.eventProviderDefinitions.map[e|e.eventDefinitions.map[ed|ed.name]].flatten.forEach [ edName |
			acceptor.accept(createCompletionProposal(edName, context))
		];
		super.completeExecutionRule_Event(model, assignment, context, acceptor)
	}
	
}
