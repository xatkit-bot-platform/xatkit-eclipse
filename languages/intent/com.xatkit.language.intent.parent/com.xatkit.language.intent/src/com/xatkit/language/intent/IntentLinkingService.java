package com.xatkit.language.intent;

import static java.text.MessageFormat.format;

import java.util.List;

import org.apache.log4j.Logger;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.xtext.linking.ILinkingService;
import org.eclipse.xtext.linking.impl.DefaultLinkingService;
import org.eclipse.xtext.linking.impl.IllegalNodeException;
import org.eclipse.xtext.nodemodel.INode;

/**
 * The intent {@link ILinkingService}.
 * <p>
 * This service is required by the {@link IntentLazyLinker} to automatically resolve proxies of bi-directional
 * references.
 */
public class IntentLinkingService extends DefaultLinkingService {

	private static final Logger log = Logger.getLogger(IntentLinkingService.class);

	public IntentLinkingService() {
		super();
		log.debug(format("{0} started", this.getClass().getSimpleName()));
	}

	@Override
	public List<EObject> getLinkedObjects(EObject context, EReference ref, INode node) throws IllegalNodeException {
		log.debug(format("{0} linking context: {1}", this.getClass().getSimpleName(), context));
		log.debug(format("{0} linking reference: {1}", this.getClass().getSimpleName(), ref));
		return super.getLinkedObjects(context, ref, node);
	}
}
