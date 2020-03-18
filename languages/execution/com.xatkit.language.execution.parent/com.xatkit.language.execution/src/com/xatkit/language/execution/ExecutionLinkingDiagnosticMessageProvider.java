package com.xatkit.language.execution;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.diagnostics.DiagnosticMessage;
import org.eclipse.xtext.diagnostics.Severity;
import org.eclipse.xtext.linking.impl.LinkingDiagnosticMessageProvider;

import com.xatkit.execution.ExecutionPackage;
import com.xatkit.execution.Transition;

import static java.util.Objects.isNull;

public class ExecutionLinkingDiagnosticMessageProvider extends LinkingDiagnosticMessageProvider {

	public static final String STATE_NOT_RESOLVED = "state.not.resolved";

	@Override
	public DiagnosticMessage getUnresolvedProxyMessage(ILinkingDiagnosticContext context) {
		DiagnosticMessage diagnosticMessage = null;
		EObject contextEObject = context.getContext();
		if (contextEObject instanceof Transition) {
			if (context.getReference().equals(ExecutionPackage.Literals.TRANSITION__STATE)) {
				diagnosticMessage = new DiagnosticMessage(context.getLinkText() + " cannot be resolved to a State",
						Severity.ERROR, STATE_NOT_RESOLVED, context.getLinkText());
			}
		}
		if(isNull(diagnosticMessage)) {
			diagnosticMessage = super.getUnresolvedProxyMessage(context);
		}
		return diagnosticMessage;
	}

}
