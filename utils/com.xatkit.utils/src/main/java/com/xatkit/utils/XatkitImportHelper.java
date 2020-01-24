package com.xatkit.utils;

import static java.text.MessageFormat.format;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.log4j.Logger;
import org.eclipse.core.runtime.Platform;
import org.eclipse.emf.common.CommonPlugin;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.xtext.xbase.XbasePackage;

import com.xatkit.common.CommonPackage;
import com.xatkit.common.ImportDeclaration;
import com.xatkit.common.LibraryImportDeclaration;
import com.xatkit.common.PlatformImportDeclaration;
import com.xatkit.execution.ExecutionModel;
import com.xatkit.execution.ExecutionPackage;
import com.xatkit.intent.IntentPackage;
import com.xatkit.intent.Library;
import com.xatkit.metamodels.utils.LibraryLoaderUtils;
import com.xatkit.metamodels.utils.PlatformLoaderUtils;
import com.xatkit.platform.PlatformDefinition;
import com.xatkit.platform.PlatformPackage;

/**
 * A registry managing Platform and Library imports.
 * <p>
 * This class provides utility methods to load EMF {@link Resource}s from imports. Imports can point to workspace files,
 * absolute files on the file system, as well as files stored in imported {@code jars}.
 * <p>
 * This class also loads the {@link Platform} and {@link Library} models stored in the <i>core component</i> allowing to
 * use unqualified imports in execution models such as {@code import "CorePlatform"}.
 */
public class XatkitImportHelper {

	private static final Logger log = Logger.getLogger(XatkitImportHelper.class);

	/**
	 * The singleton {@link XatkitImportHelper} instance.
	 */
	private static XatkitImportHelper INSTANCE;

	/**
	 * Returns the singleton {@link XatkitImportHelper} instance.
	 * 
	 * @return the singleton {@link XatkitImportHelper} instance
	 */
	public static XatkitImportHelper getInstance() {
		if (isNull(INSTANCE)) {
			INSTANCE = new XatkitImportHelper();
		}
		return INSTANCE;
	}

	/**
	 * Tracks the number of files loaded by the registry.
	 * <p>
	 * This field is used for debugging purposes.
	 */
	private static int FILE_LOADED_COUNT = 0;

	/**
	 * Increments and prints the {@link #FILE_LOADED_COUNT} value.
	 * <p>
	 * This method is used for debugging purposes.
	 * <p>
	 */
	private static void incrementLoadCalls() {
		FILE_LOADED_COUNT++;
		log.debug(format("# File loaded: {0}", FILE_LOADED_COUNT));
	}

	/**
	 * The {@link Map} used to store the ignored aliases for each {@link ResourceSet}.
	 * <p>
	 * This {@link Map} is typically populated by applications loading Xatkit files from outside eclipse with a custom
	 * loading mechanism. In this context the {@link XatkitImportHelper} should not erase the content of the
	 * {@link ResourceSet} for the aliases representing the custom loaded resources.
	 * <p>
	 * This {@link Map} is not populated when loading Xatkit files from within eclipse (e.g. the Xatkit editors).
	 * 
	 * @see #ignoreAlias(ResourceSet, String)
	 */
	private Map<ResourceSet, List<String>> ignoredAliases;

	/**
	 * Constructs a new {@link XatkitImportHelper}.
	 * <p>
	 * This method is private, use {@link #getInstance()} to retrieve the singleton instance of this class.
	 */
	private XatkitImportHelper() {
		EPackage.Registry.INSTANCE.put(XbasePackage.eINSTANCE.getNsURI(), XbasePackage.eINSTANCE);
		EPackage.Registry.INSTANCE.put(CommonPackage.eINSTANCE.getNsURI(), CommonPackage.eINSTANCE);
		EPackage.Registry.INSTANCE.put(IntentPackage.eINSTANCE.getNsURI(), IntentPackage.eINSTANCE);
		EPackage.Registry.INSTANCE.put(PlatformPackage.eINSTANCE.getNsURI(), PlatformPackage.eINSTANCE);
		EPackage.Registry.INSTANCE.put(ExecutionPackage.eINSTANCE.getNsURI(), ExecutionPackage.eINSTANCE);
		this.ignoredAliases = new HashMap<>();
	}

	/**
	 * Tells the registry to ignore reloading of {@link Resource}s associated to the provided {@code alias}.
	 * <p>
	 * This method is typically used by application loading Xatkit files from outside eclipse with a custom loading
	 * mechanism. In this context the {@link XatkitImportHelper} should not erase the content of the provided
	 * {@link ResourceSet} for the aliases representing the custom loaded resources.
	 * <p>
	 * The typical sequence of API calls from such external applications would be:
	 * 
	 * <pre>
	 * {@code
	 * String resourceAlias = "myAlias" // alias from the execution/platform file
	 * URI resourceUri = // custom URI
	 * Resource resource = resourceSet.getResource(resourceUri, true);
	 * ImportRegistry.getInstance().ignoreAlias(rSet, "myAlias");
	 * }
	 * </pre>
	 * 
	 * @param rSet  the {@link ResourceSet} where the provided {@code alias} must be ignored
	 * @param alias the alias to ignore
	 * 
	 * @see #isIgnored(ImportDeclaration)
	 */
	public void ignoreAlias(ResourceSet rSet, String alias) {
		List<String> ignoredAliasesForRset = ignoredAliases.get(rSet);
		if (isNull(ignoredAliasesForRset)) {
			List<String> aliasList = new ArrayList<>();
			aliasList.add(alias);
			ignoredAliases.put(rSet, aliasList);
		} else {
			ignoredAliasesForRset.add(alias);
		}
	}

	/**
	 * Returns whether the provided {@code ImportDeclaration}'s alias is ignored.
	 * <p>
	 * This method returns {@code true} if the {@code importDeclaration.getAlias()} value has been ignored for the
	 * {@code importDeclaration.eResource().getResourceSet()}.
	 * 
	 * @param importDeclaration the {@link ImportDeclaration} to check
	 * @return {@code true} if the {@code ImportDeclaration}'s alias is ignored, {@code false} otherwise
	 * 
	 * @see #ignoreAlias(ResourceSet, String)
	 */
	public boolean isIgnored(ImportDeclaration importDeclaration) {
		ResourceSet rSet = importDeclaration.eResource().getResourceSet();
		List<String> ignoredAliasesForRset = this.ignoredAliases.get(rSet);
		return nonNull(ignoredAliasesForRset) && ignoredAliasesForRset.contains(importDeclaration.getAlias());
	}

	/**
	 * Returns the {@link PlatformDefinition}s imported by the provided {@code platform}.
	 * <p>
	 * This method looks for the {@link PlatformDefinition} instances from the provided {@code platform}'s
	 * {@link ResourceSet}.
	 * 
	 * @param platform the {@link PlatformDefinition} to retrieve the imported platforms of
	 * @return the {@link PlatformDefinition}s imported by the provided {@code platform}
	 */
	public Collection<PlatformDefinition> getImportedPlatforms(PlatformDefinition platform) {
		Collection<Resource> refreshedImports = this.reloadImports(platform.getImports());
		return getLoadedElements(PlatformDefinition.class, refreshedImports);
	}

	/**
	 * Returns the {@link PlatformDefinition}s imported by the provided {@code executionModel}.
	 * <p>
	 * This method looks for the {@link PlatformDefinition} instances from the provided {@code executionModel}'s
	 * {@link ResourceSet}.
	 * 
	 * @param executionModel the {@link ExecutionModel} to retrieve the imported platforms of
	 * @return the {@link PlatformDefinition}s imported by the provided {@code executionModel}
	 */
	public Collection<PlatformDefinition> getImportedPlatforms(ExecutionModel executionModel) {
		Collection<Resource> refreshedImports = this.reloadImports(executionModel.getImports());
		return getLoadedElements(PlatformDefinition.class, refreshedImports);
	}

	/**
	 * Returns the {@link PlatformDefinition} imported by the provided {@code model} with the given
	 * {@code platformName}.
	 * <p>
	 * This method looks for the {@link PlatformDefinition} instance matching the provided {@code platformName} in the
	 * given {@code executionModel}'s {@link ResourceSet}.
	 * 
	 * @param executionModel the {@link ExecutionModel} to retrieve the imported {@link PlatformDefinition} from
	 * @param platformName   the name of the {@link PlatformDefinition} to retrieve
	 * @return the imported {@link PlatformDefinition}, or {@code null} if there is no imported
	 *         {@link PlatformDefinition} matching the provided {@code platformName}
	 */
	public PlatformDefinition getImportedPlatform(ExecutionModel executionModel, String platformName) {
		Optional<PlatformDefinition> result = this.getImportedPlatforms(executionModel).stream()
				.filter(p -> p.getName().equals(platformName)).findFirst();
		if (result.isPresent()) {
			return result.get();
		} else {
			return null;
		}
	}

	/**
	 * Returns the {@link Library} instances imported by the provided {@code executionModel}.
	 * <p>
	 * This method lookds for the {@link Library} instances from the provided {@code executionModel}'s
	 * {@link ResourceSet}.
	 * 
	 * @param executionModel the {@link ExecutionModel} to retrieve the imported libraries of
	 * @return the {@link Library} instances imported by the provided {@code executionModel}
	 */
	public Collection<Library> getImportedLibraries(ExecutionModel executionModel) {
		Collection<Resource> refreshedImports = this.reloadImports(executionModel.getImports());
		return getLoadedElements(Library.class, refreshedImports);
	}

	/**
	 * Get the top-level elements of the provided {@code resources} matching the given {@code elementClazz}.
	 * <p>
	 * This method is used to retrieve the imported {@link PlatformDefinition}s and {@link Library} instances for a
	 * given {@link ResourceSet}. Note that the method doesn't manipulate directly the
	 * {@link ResourceSet#getResources()} collection because it may contain non-imported instances of the provided
	 * {@code elementClazz}.
	 * 
	 * @param elementClazz the {@link Class} of the top-level elements to retrieve
	 * @param resources    the {@link Resource}s to retrieve the elements from
	 * @return a {@link Collection} of top-level instances of the provided {@code elementClazz}.
	 * 
	 * @see #getImportedLibraries(ExecutionModel)
	 * @see #getImportedPlatforms(ExecutionModel)
	 * @see #reloadImports(Collection)
	 */
	@SuppressWarnings("unchecked")
	private <T> Collection<T> getLoadedElements(Class<T> elementClazz, Collection<Resource> resources) {
		List<T> result = new ArrayList<>();
		resources.forEach(r -> {
			EObject topLevelElement = r.getContents().get(0);
			if (elementClazz.isInstance(topLevelElement)) {
				result.add((T) topLevelElement);
			}
		});
		return result;
	}

	/**
	 * Refreshes the {@link ImportRegistry} with the provided {@code newImports}.
	 * <p>
	 * This method ensures that the registry does not contain imports that are not used anymore, and that all the
	 * provided imports correspond to loadable EMF {@link Resource}s.
	 * <p>
	 * Note that this method can be called by different editors containing different sets of imports. Removing unused
	 * imports also ensures that imports are not shared between editors.
	 * 
	 * @param newImports the new {@link ImportDeclaration} to register
	 * @return the {@link Resource}s loaded from the provided {@code newImports}.
	 * 
	 * @see #removeUnusedImports(Collection)
	 */

	/**
	 * Reloads the provided {@code newImports} in the {@link ResourceSet} and returns their {@link Resource}.
	 * <p>
	 * This method is expansive, and should only be called when a complete and up-to-date version of the
	 * {@link ResourceSet} is required (e.g. when calling {@link #getImportedLibraries(ExecutionModel)} or
	 * {@link #getImportedPlatforms(ExecutionModel)}).
	 * 
	 * @param newImports the {@link ImportDeclaration} to reload
	 * @return the {@link Resource}s containing the provided {@code newImports}
	 */
	private Collection<Resource> reloadImports(Collection<? extends ImportDeclaration> newImports) {
		List<Resource> resources = new ArrayList<>();
		for (ImportDeclaration importDeclaration : newImports) {
			Resource resource = getResourceFromImport(importDeclaration);
			if (nonNull(resource)) {
				resources.add(resource);
			}
			/*
			 * Ignore the resource if it is null, this means that an error occurred while loading it and it should be
			 * visible in the logs.
			 */
		}
		return resources;
	}

	/**
	 * Returns the formatted alias from the provided {@code importDeclaration}.
	 * <p>
	 * This method appends the correct extension to the provided alias ({@code .platform} for platform imports and
	 * {@code .intent} for library imports}. This extension is needed to ensure that the right resource factory is used
	 * to load aliases (otherwise it may default to the {@code xmi} implementation).
	 * 
	 * @param importDeclaration the {@link ImportDeclaration} to format the alias from
	 * @return the formatted alias, or {@code null} if the provided {@code importDeclaration.getAlias()} is {@code null}
	 * 
	 * @throws IllegalArgumentException if the provided {@code importDeclaration} is neither a
	 *                                  {@link PlatformImportDeclaration} nor a {@link LibraryImportDeclaration}.
	 */
	private String formatAlias(ImportDeclaration importDeclaration) {
		String alias = importDeclaration.getAlias();
		if (isNull(alias) || alias.isEmpty()) {
			return null;
		} else {
			if (importDeclaration instanceof PlatformImportDeclaration) {
				alias += ".platform";
			} else if (importDeclaration instanceof LibraryImportDeclaration) {
				alias += ".intent";
			} else {
				throw new IllegalArgumentException(format("Unknown {0} type {1}",
						ImportDeclaration.class.getSimpleName(), importDeclaration.getClass()));
			}
			return alias;
		}
	}

	/**
	 * Tries to load the provided {@code importDeclaration} as a Xatkit core {@link Resource}.
	 * 
	 * @param importDeclaration the {@link ImportDeclaration} to load
	 * @return the loaded {@link Resource}, or {@code null} if the provided {@code importDeclaration} doesn't correspond
	 *         to a Xatkit core resource
	 * 
	 * @throws IllegalArgumentException if the provided {@code importDeclaration} is neither a
	 *                                  {@link PlatformImportDeclaration} nor a {@link LibraryImportDeclaration}
	 * @see #loadImportAsCoreLibrary(LibraryImportDeclaration)
	 * @see #loadImportAsCorePlatform(PlatformImportDeclaration)
	 */
	private /* @Nullable */ Resource loadImportAsCoreResource(ImportDeclaration importDeclaration) {
		Resource result = null;
		if (importDeclaration instanceof PlatformImportDeclaration) {
			result = loadImportAsCorePlatform((PlatformImportDeclaration) importDeclaration);
		} else if (importDeclaration instanceof LibraryImportDeclaration) {
			result = loadImportAsCoreLibrary((LibraryImportDeclaration) importDeclaration);
		} else {
			throw new IllegalArgumentException(format("Unknown {0} type {1}", ImportDeclaration.class.getSimpleName(),
					importDeclaration.getClass()));
		}
		return result;
	}

	/**
	 * Tries to load the provided {@code importDeclaration} as a Xatkit core platform {@link Resource}.
	 * 
	 * @param importDeclaration the {@link PlatformImportDeclaration} to load
	 * @return the loaded {@link Resource}, or {@code null} if the provided {@code importDeclaration} doesn't correspond
	 *         to a Xatkit core platform resource.
	 */
	private /* @Nullable */ Resource loadImportAsCorePlatform(PlatformImportDeclaration importDeclaration) {
		ResourceSet rSet = importDeclaration.eResource().getResourceSet();
		String uriPrefix = PlatformLoaderUtils.CORE_PLATFORM_PATHMAP;
		String path = importDeclaration.getPath();
		String uriSuffix = path.endsWith(".platform") ? "" : ".platform";
		return rSet.getResource(URI.createURI(uriPrefix + importDeclaration.getPath() + uriSuffix), false);
	}

	/**
	 * Tries to load the provided {@code importDeclaration} as a Xatkit core library {@link Resource}.
	 * 
	 * @param importDeclaration the {@link LibraryImportDeclaration} to load
	 * @return the loaded {@link Resource}, or {@code null} if the provided {@code importDeclaration} doesn't correspond
	 *         to a Xatkit core library resource
	 */
	private /* @Nullable */ Resource loadImportAsCoreLibrary(LibraryImportDeclaration importDeclaration) {
		ResourceSet rSet = importDeclaration.eResource().getResourceSet();
		String uriPrefix = LibraryLoaderUtils.CORE_LIBRARY_PATHMAP;
		String path = importDeclaration.getPath();
		String uriSuffix = path.endsWith(".intent") ? "" : ".intent";
		return rSet.getResource(URI.createURI(uriPrefix + importDeclaration.getPath() + uriSuffix), false);
	}

	/**
	 * Creates a custom {@link Resource} {@link URI} from the provided {@code importDeclaration}.
	 * <p>
	 * Custom {@link URI}s are used to represent external platforms/libraries imported in Xatkit with an alias (i.e. not
	 * the ones bundled as core platforms/libraries). This method builds a normalized {@link URI} for such
	 * {@link Resource}s that can be used to load and retrieve them from the {@link ResourceSet}.
	 * 
	 * @param importDeclaration the {@link ImportDeclaration} to build a custom {@link URI} from
	 * @return the created {@link URI}
	 * @throws IllegalArgumentException if the provided {@code importDeclaration.getAlias()} is {@code null}, or if the
	 *                                  provided {@code importDeclaration} is neither a
	 *                                  {@link PlatformImportDeclaration} nor a {@link LibraryImportDeclaration}
	 */
	private URI createCustomURI(ImportDeclaration importDeclaration) {
		String formattedAlias = formatAlias(importDeclaration);
		if (isNull(formattedAlias)) {
			throw new IllegalArgumentException(
					format("Cannot create a custom URI from the provided {0} (path={1}, alias={2})",
							ImportDeclaration.class.getSimpleName(), importDeclaration.getPath(),
							importDeclaration.getAlias()));
		}
		URI uri = null;
		if (importDeclaration instanceof PlatformImportDeclaration) {
			uri = URI.createURI(PlatformLoaderUtils.CUSTOM_PLATFORM_PATHMAP + formattedAlias);
		} else if (importDeclaration instanceof LibraryImportDeclaration) {
			uri = URI.createURI(LibraryLoaderUtils.CUSTOM_LIBRARY_PATHMAP + formattedAlias);
		} else {
			throw new IllegalArgumentException(format("Unknown {0} type {1}", ImportDeclaration.class.getSimpleName(),
					importDeclaration.getClass()));
		}
		return uri;
	}

	/**
	 * Loads the {@link Resource} described by the provided {@code importDeclaration}.
	 * <p>
	 * The loaded {@link Resource} can be either a {@link Platform} {@link Resource}, or a {@link Library}
	 * {@link Resource}.
	 * 
	 * @param importDeclaration the {@link ImportDeclaration} to load
	 * @return the loaded {@link Resource}, or {@code null} if the {@link ImportRegistry} was not able to load it
	 */

	/**
	 * Retrieves the {@link Resource} associated to the provided {@code importDeclaration}.
	 * <p>
	 * This method handles core resources, general resources without alias, and custom resources with an alias.
	 * <p>
	 * This method doesn't guarantee that the returned {@link Resource} has been reloaded from scratch, and may return
	 * it from the {@link ResourceSet}'s cache.
	 * 
	 * @param importDeclaration the {@link ImportDeclaration} to get the {@link Resource} from
	 * @return the loaded {@link Resource}, or {@code null} if an error occurred while loading the {@link Resource}
	 */
	public Resource getResourceFromImport(ImportDeclaration importDeclaration) {
		ResourceSet rSet = importDeclaration.eResource().getResourceSet();
		this.loadXatkitCore(rSet);
		String path = importDeclaration.getPath();
		String alias = formatAlias(importDeclaration);
		/*
		 * Try to load it first as a core platform, if this succeeds we don't need additional URI processing, and there
		 * are good chances that the core resources are already loaded in the ResourceSet.
		 */
		Resource resource = loadImportAsCoreResource(importDeclaration);
		/*
		 * The import is not a core platform, try to load it from its path, and register the resource using its alias.
		 * If the import declaration does not define an alias load the resource using its absolute path, meaning that
		 * the model is not be portable.
		 */
		if (isNull(resource)) {
			File importResourceFile = new File(path);
			URI importResourceFileURI = null;
			if (importResourceFile.exists()) {
				importResourceFileURI = URI.createFileURI(importResourceFile.getAbsolutePath());
			} else {
				/*
				 * Try to load the resource as a platform resource
				 */
				URI platformURI = URI.createPlatformResourceURI(path, false);
				/*
				 * Convert the URI to an absolute URI, platform:/ is not handled by the runtime engine
				 */
				importResourceFileURI = CommonPlugin.asLocalURI(platformURI);
			}
			URI importResourceURI = importResourceFileURI;
			if (nonNull(alias)) {
				/*
				 * There is an alias, we need to get the custom resource URI associated to it
				 * (pathmap://XATKIT_CUSTOM_[...]_<alias>)
				 */
				URI importResourceAliasURI = createCustomURI(importDeclaration);
				if (!isIgnored(importDeclaration)) {
					/*
					 * Remove the existing alias if there is one, this allows to update the name of the alias.
					 */
					removeAliasForURI(importResourceURI, rSet);

					rSet.getURIConverter().getURIMap().put(importResourceAliasURI, importResourceFileURI);
					Iterator<Resource> registeredResources = rSet.getResources().iterator();
					/*
					 * Removes the Resource from the resource set that matches either the alias URI or the base URI. The
					 * alias URI needs to be removed if the resource URI to load has changed, otherwise the old resource
					 * is returned. The base URI needs to be removed if the base URI was used to register the resource
					 * without an alias before.
					 */
					while (registeredResources.hasNext()) {
						Resource registeredResource = registeredResources.next();
						/*
						 * Check the last segment, the URI may contained either CUSTOM_PLATFORM_PATHMAP or
						 * CUSTOM_LIBRARY_PATHMAP if it was previously registered as a Platform/Library
						 */
						if (nonNull(registeredResource.getURI().lastSegment())
								&& registeredResource.getURI().lastSegment().equals(alias)) {
							log.debug(format("Unregistering resource {0} from the {1}", importResourceAliasURI,
									ResourceSet.class.getSimpleName()));
							registeredResources.remove();
						}
						if (registeredResource.getURI().equals(importResourceURI)) {
							log.debug(format("Unregistering resource {0} from the {1}", importResourceURI,
									ResourceSet.class.getSimpleName()));
							registeredResources.remove();
						}
					}
				} else {
					/*
					 * The alias represent an externally loaded Resource that should be ignored by the ImportRegistry,
					 * otherwise the URI will get updated and corrupted w.r.t the one defined by the external
					 * application.
					 */
				}
				importResourceURI = importResourceAliasURI;
			} else {
				/*
				 * If the import doesn't define an alias we need to remove any previously registered alias associated to
				 * the base URI. This is required to update aliases and remove them.
				 * 
				 * Note: ignored aliases are not impacted by this since they always define an alias
				 */
				removeAliasForURI(importResourceURI, rSet);
			}
			/*
			 * First try to get the Resource from the ResourceSet cache. If the Resource doesn't exist we need to load
			 * it completely.
			 */
			resource = rSet.getResource(importResourceURI, false);
			if (isNull(resource)) {
				incrementLoadCalls();
				resource = rSet.createResource(importResourceURI);
			}
		}
		if (nonNull(resource)) {
			try {
				resource.load(Collections.emptyMap());
			} catch (IOException e) {
				log.error(format("An error occurred when loading the resource {0}", resource.getURI().toString()));
				/*
				 * Remove the Resource from the ResourceSet to avoid its corruption.
				 */
				rSet.getResources().remove(resource);
				return null;
			} catch (IllegalArgumentException e) {
				log.error(format("An error occurred when loading the resource, invalid platform URI provided: {0}",
						resource.getURI().toString()));
				/*
				 * Remove the Resource from the ResourceSet to avoid its corruption.
				 */
				rSet.getResources().remove(resource);
				return null;
			}
		} else {
			log.error(format("Cannot find the resource associated to the import {0} (alias={1})",
					importDeclaration.getPath(), importDeclaration.getAlias()));
			return null;
		}
		if (resource.getContents().isEmpty()) {
			log.error("The loaded resource is empty");
			return null;
		}
		log.debug(format("Resource with URI {0} loaded", resource.getURI()));
		return resource;
	}

	/**
	 * Removes the alias associated to the provided {@code uri} from the given {@code rSet}.
	 * <p>
	 * This method looks at the {@link ResourceSet}'s URI map for entries containing the {@code uri} as a value and
	 * remove them. Note that the comparison must be performed on the value since the registered alias for the provided
	 * {@code uri} is not known.
	 * 
	 * @param uri the {@link URI} to remove the alias for
	 */
	private void removeAliasForURI(URI uri, ResourceSet rSet) {
		if (nonNull(uri)) {
			Iterator<Map.Entry<URI, URI>> uriMapEntries = rSet.getURIConverter().getURIMap().entrySet().iterator();
			while (uriMapEntries.hasNext()) {
				Map.Entry<URI, URI> uriMapEntry = uriMapEntries.next();
				if (uri.equals(uriMapEntry.getValue())) {
					uriMapEntries.remove();
				}
			}
		} else {
			log.warn(format("Cannot remove the provided URI {0}", uri));
		}
	}

	/**
	 * Loads the core {@link Platform}s and {@link Library} instances.
	 * <p>
	 * The {@link Platform}s and {@link Library} instances are retrieved from the {@code XATKIT} environment variable.
	 * <p>
	 * This method won't load any core {@link Resource} if at least one {@link Resource} already loaded in the provided
	 * {@code rSet} corresponds to a core {@link Resource}. Yet the result of this method cannot be cached because it
	 * needs to be called for each {@code rSet} instance in order to avoid {@link ResourceSet} consistency issues (a
	 * {@link Resource} can be containined in only one {@link ResourceSet}).
	 * 
	 * @see #loadXatkitCorePlatforms()
	 * @see #loadXatkitCoreLibraries()
	 */
	private void loadXatkitCore(ResourceSet rSet) {
		if (rSet.getResources().stream()
				.anyMatch(r -> r.getURI().toString().contains(PlatformLoaderUtils.CORE_PLATFORM_PATHMAP))) {
			log.debug("Xatkit core components already loaded");
			return;
		}
		try {
			loadXatkitCorePlatforms(rSet);
		} catch (IOException e) {
			log.error("An error occurred when loading core platforms");
			e.printStackTrace();
		}
		try {
			loadXatkitCoreLibraries(rSet);
		} catch (IOException e) {
			log.error("An error occurred when loading core libraries");
			e.printStackTrace();
		}
	}

	/**
	 * Loads the core {@link Library}s.
	 * <p>
	 * These {@link Library}s are retrieved from the {@code XATKIT} environment variable. If this variable is not set
	 * check <a href="https://github.com/xatkit-bot-platform/xatkit-releases/wiki/Installation">this tutorial</a> to
	 * setup your Xatkit environment.
	 * 
	 * @throws IOException if an error occurred when retrieving the installed Xatkit libraries
	 */
	private void loadXatkitCoreLibraries(ResourceSet rSet) throws IOException {
		String xatkitPath = System.getenv("XATKIT");
		if (isNull(xatkitPath) || xatkitPath.isEmpty()) {
			log.error("XATKIT environment variable not set, no core libraries to import");
			return;
		}
		/*
		 * Create a File instance to uniformize trailing '/' between Linux and Windows installations.
		 */
		File xatkitFile = new File(xatkitPath);
		Files.walk(Paths.get(xatkitFile.getAbsolutePath() + File.separator + "plugins" + File.separator + "libraries"),
				Integer.MAX_VALUE)
				.filter(filePath -> !Files.isDirectory(filePath) && filePath.toString().endsWith(".intent"))
				.forEach(modelPath -> {
					try {
						InputStream is = Files.newInputStream(modelPath);
						rSet.getURIConverter().getURIMap().put(
								URI.createURI(LibraryLoaderUtils.CORE_LIBRARY_PATHMAP + modelPath.getFileName()),
								URI.createURI(modelPath.getFileName().toString()));
						Resource modelResource = rSet.getResource(
								URI.createURI(
										LibraryLoaderUtils.CORE_LIBRARY_PATHMAP + modelPath.getFileName().toString()),
								false);
						if (modelResource == null) {
							incrementLoadCalls();
							modelResource = rSet.createResource(URI.createURI(
									LibraryLoaderUtils.CORE_LIBRARY_PATHMAP + modelPath.getFileName().toString()));
							modelResource.load(is, Collections.emptyMap());
						}
						log.debug(format("Library resource {0} loaded (uri={1})", modelPath.getFileName(),
								modelResource.getURI()));
						is.close();
					} catch (IOException e) {
						// TODO check why this exception cannot be thrown back to the caller (probably some lambda
						// shenanigans)
						log.error(format("An error occurred when loading the library resource {0}",
								modelPath.getFileName()));
					}
				});
	}

	/**
	 * Loads the core {@link Platform}s.
	 * <p>
	 * These {@link Platform}s are retrieved from the {@code XATKIT} environment variable. If this variable is not set
	 * check <a href="https://github.com/xatkit-bot-platform/xatkit-releases/wiki/Installation">this tutorial</a> to
	 * setup your Xatkit environment.
	 * 
	 * @throws IOException if an error occurred when retrieving the installed Xatkit platforms
	 */
	private void loadXatkitCorePlatforms(ResourceSet rSet) throws IOException {
		String xatkitPath = System.getenv("XATKIT");
		if (isNull(xatkitPath) || xatkitPath.isEmpty()) {
			log.error("XATKIT environment variable not set, no core platforms to import");
			return;
		}
		/*
		 * Create a File instance to uniformize trailing '/' between Linux and Windows installations.
		 */
		File xatkitFile = new File(xatkitPath);
		Files.walk(Paths.get(xatkitFile.getAbsolutePath() + File.separator + "plugins" + File.separator + "platforms"),
				Integer.MAX_VALUE)
				.filter(filePath -> !Files.isDirectory(filePath) && filePath.toString().endsWith(".platform"))
				.forEach(modelPath -> {
					try {
						InputStream is = Files.newInputStream(modelPath);
						rSet.getURIConverter().getURIMap().put(
								URI.createURI(PlatformLoaderUtils.CORE_PLATFORM_PATHMAP + modelPath.getFileName()),
								URI.createURI(modelPath.getFileName().toString()));
						Resource modelResource = rSet.getResource(
								URI.createURI(
										PlatformLoaderUtils.CORE_PLATFORM_PATHMAP + modelPath.getFileName().toString()),
								false);
						if (modelResource == null) {
							incrementLoadCalls();
							modelResource = rSet.createResource(URI.createURI(
									PlatformLoaderUtils.CORE_PLATFORM_PATHMAP + modelPath.getFileName().toString()));
							modelResource.load(is, Collections.emptyMap());
						}
						log.debug(format("Platform resource {0} loaded (uri={1})", modelPath.getFileName(),
								modelResource.getURI()));
						is.close();
						EcoreUtil.resolveAll(modelResource);
					} catch (IOException e) {
						// TODO check why this exception cannot be thrown back to the caller (probably some lambda
						// shenanigans)
						log.error(format("An error occurred when loading the platform resource {0}",
								modelPath.getFileName()));
					}
				});
	}

}
