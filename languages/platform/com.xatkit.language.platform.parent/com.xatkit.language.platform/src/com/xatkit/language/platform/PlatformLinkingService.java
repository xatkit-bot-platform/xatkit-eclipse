package com.xatkit.language.platform;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.apache.log4j.Logger;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.xtext.linking.impl.DefaultLinkingService;
import org.eclipse.xtext.linking.impl.IllegalNodeException;
import org.eclipse.xtext.nodemodel.INode;

import com.xatkit.platform.PlatformDefinition;
import com.xatkit.platform.PlatformPackage;
import com.xatkit.utils.XatkitImportHelper;

import static java.text.MessageFormat.format;

public class PlatformLinkingService extends DefaultLinkingService {

	private static final Logger log = Logger.getLogger(PlatformLinkingService.class);
	
	public PlatformLinkingService() {
		super();
		log.debug(format("{0} started", this.getClass().getSimpleName()));
	}

	@Override
	public List<EObject> getLinkedObjects(EObject context, EReference ref, INode node) throws IllegalNodeException {
		if (context instanceof PlatformDefinition) {
			PlatformDefinition platformDefinition = (PlatformDefinition) context;
			if (ref.equals(PlatformPackage.eINSTANCE.getPlatformDefinition_Extends())) {
				log.debug(format("{0} linking super-platforms of {1}", this.getClass().getSimpleName(), platformDefinition.getName()));
				Collection<PlatformDefinition> importedPlatformDefinitions = XatkitImportHelper.getInstance()
						.getImportedPlatforms(platformDefinition);
				log.debug(format("{0} found {1} registered platforms", this.getClass().getSimpleName(), importedPlatformDefinitions.size()));
				for(PlatformDefinition importedPlatform : importedPlatformDefinitions) {
					if(importedPlatform.getName().equals(node.getText())) {
						return Arrays.asList(importedPlatform);
					}
				}
			}
		}
		return super.getLinkedObjects(context, ref, node);
	}

}
