package com.xatkit.language.intent;

import java.util.List;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.xtext.nodemodel.INode;
import org.eclipse.xtext.xbase.linking.XbaseLazyLinker;

import com.google.inject.Inject;
import com.xatkit.intent.IntentPackage;

public class IntentLazyLinker extends XbaseLazyLinker {

	@Inject
	IntentLinkingService intentLinkingService;

	@Override
	protected EObject createProxy(EObject obj, INode node, EReference eRef) {
		if (eRef.equals(IntentPackage.eINSTANCE.getIntentDefinition_Follows())) {
			/*
			 * Disable proxy resolution for bi-directional references within the same resource, they are not set by the
			 * default linking policy (see https://bugs.eclipse.org/bugs/show_bug.cgi?id=282486). With this fix the
			 * resolved object is directly returned, avoiding proxy resolution issues. As emphasized in the bug report
			 * this is a quick fix, and should not be generalized, that's why only the problematic EReference is
			 * checked.
			 */
			EReference eOpposite = eRef.getEOpposite();
			if (eOpposite != null) {
				List<EObject> linkedObjects = intentLinkingService.getLinkedObjects(obj, eRef, node);
				if (linkedObjects.size() == 1)
					return linkedObjects.get(0);
			}
		}
		return super.createProxy(obj, node, eRef);
	}

}
