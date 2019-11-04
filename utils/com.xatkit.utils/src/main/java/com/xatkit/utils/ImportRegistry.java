package com.xatkit.utils;

import static java.text.MessageFormat.format;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;
import org.eclipse.core.runtime.Platform;
import org.eclipse.emf.common.CommonPlugin;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;

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
public class ImportRegistry {

	private static final Logger log = Logger.getLogger(ImportRegistry.class);

	/**
	 * The singleton {@link ImportRegistry} instance.
	 */
	private static ImportRegistry INSTANCE;

	/**
	 * Returns the singleton {@link ImportRegistry} instance.
	 * 
	 * @return the singleton {@link ImportRegistry} instance
	 */
	public static ImportRegistry getInstance() {
		if (isNull(INSTANCE)) {
			INSTANCE = new ImportRegistry();
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
		log.info(format("# File loaded: {0}", FILE_LOADED_COUNT));
	}

	/**
	 * The {@link ResourceSet} used to load imported {@link Resource}s.
	 * <p>
	 * All the imported {@link Resource}s are stored in the same {@link ResourceSet} to allow proxy resolution between
	 * models (this is typically the case when an execution model imports a Platform and uses its actions).
	 */
	private ResourceSet rSet;

	/**
	 * Caches the loaded {@link Platform} instances.
	 * 
	 * @see #getImport(ImportDeclaration)
	 * @see ImportEntry
	 */
	private ConcurrentHashMap<ImportEntry, PlatformDefinition> platforms;

	/**
	 * Caches the loaded {@link Library} instances.
	 * 
	 * @see #getImport(ImportDeclaration)
	 * @see ImportEntry
	 */
	private ConcurrentHashMap<ImportEntry, Library> libraries;

	/**
	 * Constructs a new {@link ImportRegistry}.
	 * <p>
	 * This method is private, use {@link #getInstance()} to retrieve the singleton instance of this class.
	 */
	private ImportRegistry() {
		this.rSet = new ResourceSetImpl();
		this.rSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("xmi", new XMIResourceFactoryImpl());
		this.rSet.getPackageRegistry().put(ExecutionPackage.eINSTANCE.getNsURI(), ExecutionPackage.eINSTANCE);
		this.rSet.getPackageRegistry().put(PlatformPackage.eINSTANCE.getNsURI(), PlatformPackage.eINSTANCE);
		this.rSet.getPackageRegistry().put(IntentPackage.eINSTANCE.getNsURI(), IntentPackage.eINSTANCE);
		/*
		 * Load the core Platforms and Library in the constructor, they should not be reloaded later.
		 */
		loadXatkitCore();
		ExecutionPackage.eINSTANCE.eClass();
		PlatformPackage.eINSTANCE.eClass();
		IntentPackage.eINSTANCE.eClass();
		this.platforms = new ConcurrentHashMap<>();
		this.libraries = new ConcurrentHashMap<>();
	}

	/**
	 * Reset the internal data structures and sets the provided {@link ResourceSet} to load resources.
	 * <p>
	 * This method is used by Xatkit core component to share the runtime {@link ResourceSet} with the one from the
	 * {@link ImportRegistry}. Xatkit Eclipse editors should not call it directly.
	 * 
	 * @param rSet the {@link ResourceSet} to use to load resources.
	 */
	public void setResourceSet(ResourceSet rSet) {
		this.internalLibraryAliases.clear();
		this.internalPlatformAliases.clear();
		this.libraries.clear();
		this.platforms.clear();
		FILE_LOADED_COUNT = 0;
		this.rSet = rSet;
	}

	private Map<String, PlatformDefinition> internalPlatformAliases = new HashMap<>();

	private Map<String, Library> internalLibraryAliases = new HashMap<>();

	// Called by xatkit internals
	public void internalRegisterAlias(String alias, PlatformDefinition platformDefinition) {
		if (!rSet.getResources().contains(platformDefinition.eResource())) {
			this.rSet.getResources().add(platformDefinition.eResource());
		}
		this.internalPlatformAliases.put(alias, platformDefinition);
	}

	// Called by xatkit internals
	public void internalRegisterAlias(String alias, Library library) {
		if (!rSet.getResources().contains(library.eResource())) {
			this.rSet.getResources().add(library.eResource());
		}
		this.internalLibraryAliases.put(alias, library);
	}

	// Called by xatkit internals
	public void internalRegisterPlatform(String path, String alias, PlatformDefinition platformDefinition) {
		this.rSet.getResources().add(platformDefinition.eResource());
		this.platforms.put(ImportEntry.from(path, alias), platformDefinition);
	}

	// Called by xatkit internals
	public void internalRegisterLibrary(String path, String alias, Library library) {
		this.rSet.getResources().add(library.eResource());
		this.libraries.put(ImportEntry.from(path, alias), library);
	}

	/**
	 * Updates the {@link Library} cache with the provided {@code library}.
	 * <p>
	 * This method ensures that the imports referring to the provided {@code library} are always associated to the
	 * latest version of the {@link Library}. This is for example the case when updating an intent library along with an
	 * execution model.
	 * <p>
	 * This method is typically called by the <i>intent language</i> generator once the {@code .xmi} model associated to
	 * the provided {@code library} has been created.
	 * 
	 * @param library the {@link Library} to update the imports for
	 * @see #updateImport(PlatformDefinition)
	 */
	public void updateImport(Library library) {
		for (Entry<ImportEntry, Library> librariesEntry : libraries.entrySet()) {
			Library storedLibrary = librariesEntry.getValue();
			if (library.getName().equals(storedLibrary.getName())) {
				log.info(format("Updating library entry for {0}", library.getName()));
				librariesEntry.setValue(library);
			}
		}
	}

	/**
	 * Updates the {@link Platform} cache with the provided {@code platform}.
	 * <p>
	 * This method ensures that the imports referring to the provided {@code platform} are always associated to the
	 * latest version of the {@link Platform}. This is for example the case when updating a platform along with an
	 * execution model.
	 * <p>
	 * This method is typically called by the <i>platform language</i> generator once the {@code .xmi} model associated
	 * to the provided {@code platform} has been created.
	 * 
	 * @param platform the {@link Platform} to update the imports for
	 * @see #updateImport(Library)
	 */
	public void updateImport(PlatformDefinition platform) {
		for (Entry<ImportEntry, PlatformDefinition> platformsEntry : platforms.entrySet()) {
			PlatformDefinition storedPlatform = platformsEntry.getValue();
			if (platform.getName().equals(storedPlatform.getName())) {
				log.info(format("Updating platform entry for {0}", platform.getName()));
				platformsEntry.setValue(platform);
			}
		}
	}

	/**
	 * Retrieves the {@link Resource} associated to the provided {@code importDeclaration}.
	 * <p>
	 * This method checks whether the provided {@code importDeclaration} is already cached and returns it. If it is not
	 * the case the registry attempts to load the {@link Resource} corresponding to the provided {@code import} and
	 * caches it.
	 * 
	 * @param importDeclaration the {@link ImportDeclaration} to retrieve the {@link Resource} from
	 * @return the retrieved {@link Resource} if it exists, {@code null} otherwise
	 * 
	 * @see #getImport(ImportDeclaration)
	 * @see #loadImport(ImportDeclaration)
	 */
	public Resource getOrLoadImport(ImportDeclaration importDeclaration) {
		Resource resource = this.getImport(importDeclaration);
		if (resource == null) {
			log.info(format("The import {0} (alias={1}) is not in the cache, loading the corresponding resource",
					importDeclaration.getPath(), importDeclaration.getAlias()));
			resource = this.loadImport(importDeclaration);
		}
		/*
		 * If the resource is null this means that an error occurred when loading the import (e.g. the file doesn't
		 * exist, or the resource contains invalid content)
		 */
		return resource;
	}

	/**
	 * Returns the {@link PlatformDefinition}s imported by the provided {@code platform}.
	 * 
	 * @param platform the {@link PlatformDefinition} to retrieve the imported platforms of
	 * @return the {@link PlatformDefinition}s imported by the provided {@code platform}
	 */
	public Collection<PlatformDefinition> getImportedPlatforms(PlatformDefinition platform) {
		this.refreshRegisteredImports(platform.getImports());
		List<PlatformDefinition> platformDefinitions = new ArrayList<PlatformDefinition>(this.platforms.values());
		platformDefinitions.addAll(this.internalPlatformAliases.values());
		return platformDefinitions;
	}

	/**
	 * Returns the {@link PlatformDefinition}s imported by the provided {@code executionModel}
	 * 
	 * @param executionModel the {@link ExecutionModel} to retrieve the imported platforms of
	 * @return the {@link PlatformDefinition}s imported by the provided {@code executionModel}
	 */
	public Collection<PlatformDefinition> getImportedPlatforms(ExecutionModel executionModel) {
		this.refreshRegisteredImports(executionModel.getImports());
		List<PlatformDefinition> platformDefinitions = new ArrayList<PlatformDefinition>(this.platforms.values());
		platformDefinitions.addAll(this.internalPlatformAliases.values());
		return platformDefinitions;
	}

	/**
	 * Returns the {@link PlatformDefinition} imported by the provided {@code model} with the given
	 * {@code platformName}.
	 * 
	 * @param model        the {@link ExecutionModel} to retrieve the imported {@link PlatformDefinition} from
	 * @param platformName the name of the {@link PlatformDefinition} to retrieve
	 * @return the imported {@link PlatformDefinition}, or {@code null} if there is no imported
	 *         {@link PlatformDefinition} matching the provided {@code platformName}
	 */
	public PlatformDefinition getImportedPlatform(ExecutionModel model, String platformName) {
		Optional<PlatformDefinition> result = this.getImportedPlatforms(model).stream()
				.filter(p -> p.getName().equals(platformName)).findFirst();
		if (result.isPresent()) {
			return result.get();
		} else {
			return null;
		}
	}

	/**
	 * Returns the {@link Library} instances imported by the provided {@code executionModel}
	 * 
	 * @param executionModel the {@link ExecutionModel} to retrieve the imported libraries of
	 * @return the {@link Library} instances imported by the provided {@code executionModel}
	 */
	public Collection<Library> getImportedLibraries(ExecutionModel executionModel) {
		this.refreshRegisteredImports(executionModel.getImports());
		List<Library> libraries = new ArrayList<Library>(this.libraries.values());
		libraries.addAll(this.internalLibraryAliases.values());
		return libraries;
	}

	/**
	 * Loads the core {@link Platform}s and {@link Library} instances.
	 * <p>
	 * The {@link Platform}s and {@link Library} instances are retrieved from the {@code xatkit.jar} file in the
	 * classpath. Note that this method should only be called once: the classpath is not supposed to change during the
	 * execution.
	 * 
	 * @see #loadXatkitCorePlatforms()
	 * @see #loadXatkitCoreLibraries()
	 */
	private void loadXatkitCore() {
		try {
			loadXatkitCorePlatforms();
		} catch (IOException e) {
			log.error("An error occurred when loading core platforms");
			e.printStackTrace();
		}
		try {
			loadXatkitCoreLibraries();
		} catch (IOException e) {
			log.error("An error occurred when loading core libraries");
			e.printStackTrace();
		}
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
	private Collection<Resource> refreshRegisteredImports(Collection<? extends ImportDeclaration> newImports) {
		removeUnusedImports(newImports);
		List<Resource> resources = new ArrayList<>();
		for (ImportDeclaration importDeclaration : newImports) {
			resources.add(getOrLoadImport(importDeclaration));
		}
		return resources;
	}

	/**
	 * Removes the registered {@link ImportDeclaration} that are not part of the provided {@code imports}.
	 * <p>
	 * This method ensure that deleted imports in the editors are reflected in the registry.
	 * <p>
	 * Note that this method can be called by different editors containing different sets of imports. Removing unused
	 * imports also ensures that imports are not shared between editors.
	 * 
	 * @param imports the {@link ImportDeclaration} to keep in the registry
	 */
	private void removeUnusedImports(Collection<? extends ImportDeclaration> imports) {
		List<ImportEntry> entries = imports.stream().map(i -> ImportEntry.from(i)).collect(Collectors.toList());
		for (ImportEntry importEntry : platforms.keySet()) {
			if (!entries.contains(importEntry)) {
				log.info(format("Removing unused import {0} (alias={1})", importEntry.getPath(),
						importEntry.getAlias()));
				platforms.remove(importEntry);
			}
		}

		for (ImportEntry importEntry : libraries.keySet()) {
			if (!entries.contains(importEntry)) {
				log.info(format("Removing unused import {0} (alias={1})", importEntry.getPath(),
						importEntry.getAlias()));
				libraries.remove(importEntry);
			}
		}
	}

	/**
	 * Retrieves the cached {@link Resource} associated to the provided {@code importDeclaration}.
	 * 
	 * @param importDeclaration the {@link ImportDeclaration} to retrieve the {@link Resource} from
	 * @return the retrieved {@link Resource} if it exists, {@code null} otherwise
	 * 
	 * @see #getOrLoadImport(ImportDeclaration)
	 */
	private Resource getImport(ImportDeclaration importDeclaration) {
		if (importDeclaration instanceof LibraryImportDeclaration) {
			Library library;
			if (nonNull(importDeclaration.getAlias())) {
				/*
				 * If there is an alias it may have been set by the core component (see #internalRegisterAlias)
				 */
				library = this.internalLibraryAliases.get(importDeclaration.getAlias());
				if (nonNull(library)) {
					return library.eResource();
				}
			}
			library = this.libraries.get(ImportEntry.from(importDeclaration));
			if (nonNull(library)) {
				return library.eResource();
			} else {
				log.error(format("Cannot find the library {0}", importDeclaration.getPath()));
				return null;
			}
		} else if (importDeclaration instanceof PlatformImportDeclaration) {
			PlatformDefinition platform;
			if (nonNull(importDeclaration.getAlias())) {
				/*
				 * If there is an alias it may have been set by the core component (see #internalRegisterAlias)
				 */
				platform = this.internalPlatformAliases.get(importDeclaration.getAlias());
				if (nonNull(platform)) {
					return platform.eResource();
				}
			}
			platform = this.platforms.get(ImportEntry.from(importDeclaration));
			if (nonNull(platform)) {
				return platform.eResource();
			} else {
				log.error(format("Cannot find the platform {0}", importDeclaration.getPath()));
				return null;
			}
		} else {
			throw new IllegalArgumentException(MessageFormat.format("Unknown {0} type {1}",
					ImportDeclaration.class.getSimpleName(), importDeclaration.getClass().getSimpleName()));
		}
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
	private Resource loadImport(ImportDeclaration importDeclaration) {
		incrementLoadCalls();
		String path = importDeclaration.getPath();
		String alias = importDeclaration.getAlias();
		/*
		 * Prepend the file extension to the alias to ensure the correct resource factory is loaded.
		 */
		if(importDeclaration instanceof PlatformImportDeclaration) {
			alias += ".platform";
		} else if(importDeclaration instanceof LibraryImportDeclaration) {
			alias += ".intent";
		}
		log.info(format("Loading import from path {0} (alias={1})", importDeclaration.getPath(),
				importDeclaration.getAlias()));
		/*
		 * Try to load it as a core platform/library
		 */
		Resource resource = null;
		try {
			String uriPrefix;
			String uriSuffix;
			if (importDeclaration instanceof PlatformImportDeclaration) {
				uriPrefix = PlatformLoaderUtils.CORE_PLATFORM_PATHMAP;
				uriSuffix = ".platform";
			} else {
				uriPrefix = LibraryLoaderUtils.CORE_LIBRARY_PATHMAP;
				uriSuffix = ".intent";
			}
			resource = rSet.getResource(URI.createURI(uriPrefix + path + uriSuffix), false);
			if (isNull(resource)) {
				/*
				 * In case .xmi has been specified within the import
				 */
				resource = rSet.getResource(URI.createURI(uriPrefix + path), false);
			}
		} catch (Exception e) {
			log.info("Cannot load the import as a core platform/library");
		}
		/*
		 * The import is not a core platform, try to load it from its path, and register the resource using its alias.
		 * If the import declaration does not define an alias load the resource using its absolute path, meaning that
		 * the generated XMI will not be portable.
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
				URI importResourceAliasURI;
				/*
				 * Add the extension to be sure the correct resource factory will be used.
				 */
				if (importDeclaration instanceof PlatformImportDeclaration) {
					importResourceAliasURI = URI.createURI(PlatformLoaderUtils.CUSTOM_PLATFORM_PATHMAP + alias);
				} else if (importDeclaration instanceof LibraryImportDeclaration) {
					importResourceAliasURI = URI.createURI(LibraryLoaderUtils.CUSTOM_LIBRARY_PATHMAP + alias);
				} else {
					log.error(format("Cannot load the provided import, unknown import type {0}",
							importDeclaration.eClass().getName()));
					return null;
				}

				/*
				 * Remove the existing alias if there is one, this allows to update the name of the alias.
				 */
				removeAliasForURI(importResourceURI);

				rSet.getURIConverter().getURIMap().put(importResourceAliasURI, importResourceFileURI);
				Iterator<Resource> registeredResources = rSet.getResources().iterator();
				/*
				 * Removes the Resource from the resource set that matches either the alias URI or the base URI. The
				 * alias URI needs to be removed if the resource URI to load has changed, otherwise the old resource is
				 * returned. The base URI needs to be removed if the base URI was used to register the resource without
				 * an alias before.
				 */
				while (registeredResources.hasNext()) {
					Resource registeredResource = registeredResources.next();
					/*
					 * Check the last segment, the URI may contained either CUSTOM_PLATFORM_PATHMAP or
					 * CUSTOM_LIBRARY_PATHMAP if it was previously registered as a Platform/Library
					 */
					if (nonNull(registeredResource.getURI().lastSegment())
							&& registeredResource.getURI().lastSegment().equals(alias)) {
						log.info(format("Unregistering resource {0} from the {1}", importResourceAliasURI,
								ResourceSet.class.getSimpleName()));
						registeredResources.remove();
					}
					if (registeredResource.getURI().equals(importResourceURI)) {
						log.info(format("Unregistering resource {0} from the {1}", importResourceURI,
								ResourceSet.class.getSimpleName()));
						registeredResources.remove();
					}
				}
				importResourceURI = importResourceAliasURI;
			} else {
				/*
				 * If there is an alias we need to remove the URIMap entry previously associated to the base URI. This
				 * allows to update aliases and remove them.
				 */
				removeAliasForURI(importResourceURI);
			}
			resource = rSet.getResource(importResourceURI, false);
			if (isNull(resource)) {
				resource = rSet.createResource(importResourceURI);
			}
		}
		if (nonNull(resource)) {
			try {
				resource.load(Collections.emptyMap());
			} catch (IOException e) {
				log.error("An error occurred when loading the resource");
				return null;
			} catch (IllegalArgumentException e) {
				log.error("An error occurred when loading the resource, invalid platform URI provided");
				return null;
			}
		} else {
			log.error(format("Cannot find the resource associated to the import {0} (alias={1})",
					importDeclaration.getPath(), importDeclaration.getAlias()));
			return null;
		}
		log.info(format("Resource with URI {0} loaded", resource.getURI()));
		if (resource.getContents().isEmpty()) {
			log.error("The loaded resource is empty");
			return null;
		}
		for (EObject e : resource.getContents()) {
			if (e instanceof PlatformDefinition) {
				PlatformDefinition platformDefinition = (PlatformDefinition) e;
				if (importDeclaration instanceof PlatformImportDeclaration) {
					if (this.platforms.containsKey(ImportEntry.from(importDeclaration))) {
						log.info(
								format("The platform {0} is already loaded, erasing it", platformDefinition.getName()));
					}
					log.info(format("Registering platform {0}", platformDefinition.getName()));
					this.platforms.put(ImportEntry.from(importDeclaration), platformDefinition);
				} else {
					log.error(format("Trying to load a {0} using a {1}, please use a {2} instead", e.eClass().getName(),
							importDeclaration.getClass().getSimpleName(),
							PlatformImportDeclaration.class.getSimpleName()));
					return null;
				}
			} else if (e instanceof Library) {
				Library library = (Library) e;
				if (importDeclaration instanceof LibraryImportDeclaration) {
					if (this.libraries.containsKey(ImportEntry.from(importDeclaration))) {
						log.info(format("The library {0} is already loaded, erasing it", library.getName()));
					}
					log.info(format("Registering library {0}", library.getName()));
					this.libraries.put(ImportEntry.from(importDeclaration), library);
				} else {
					log.error(format("Trying to load a {0} using a {1}, please use a {2} instead", e.eClass().getName(),
							importDeclaration.getClass().getSimpleName(),
							LibraryImportDeclaration.class.getSimpleName()));
					return null;
				}
			} else {
				/*
				 * The top level element is not a platform, we are not loading a valid Platform resource
				 */
				log.error(
						format("The loaded resource contains the unknown top-level element {0}", e.eClass().getName()));
				return null;
			}
		}
		return resource;
	}

	/**
	 * Removes the alias associated to the provided {@code uri}.
	 * <p>
	 * This method looks at the {@link ResourceSet}'s URI map for entries containing the {@code uri} as a value and
	 * remove them. Note that the comparison must be performed on the value since the registered alias for the provided
	 * {@code uri} is not known.
	 * 
	 * @param uri the {@link URI} to remove the alias for
	 */
	private void removeAliasForURI(URI uri) {
		if(nonNull(uri)) {
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
	 * Loads the core {@link Library}s.
	 * <p>
	 * These {@link Library}s are retrieved from the {@code XATKIT} environment variable. If this variable is not set
	 * check <a href="https://github.com/xatkit-bot-platform/xatkit-releases/wiki/Installation">this tutorial</a> to
	 * setup your Xatkit environment.
	 * 
	 * @throws IOException if an error occurred when retrieving the installed Xatkit libraries
	 */
	private void loadXatkitCoreLibraries() throws IOException {
		incrementLoadCalls();
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
						Resource modelResource = this.rSet.createResource(URI.createURI(
								LibraryLoaderUtils.CORE_LIBRARY_PATHMAP + modelPath.getFileName().toString()));
						modelResource.load(is, Collections.emptyMap());
						log.info(format("Library resource {0} loaded (uri={1})", modelPath.getFileName(),
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
	private void loadXatkitCorePlatforms() throws IOException {
		incrementLoadCalls();
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
						Resource modelResource = this.rSet.createResource(URI.createURI(
								PlatformLoaderUtils.CORE_PLATFORM_PATHMAP + modelPath.getFileName().toString()));
						modelResource.load(is, Collections.emptyMap());
						log.info(format("Platform resource {0} loaded (uri={1})", modelPath.getFileName(),
								modelResource.getURI()));
						is.close();
					} catch (IOException e) {
						// TODO check why this exception cannot be thrown back to the caller (probably some lambda
						// shenanigans)
						log.error(format("An error occurred when loading the platform resource {0}",
								modelPath.getFileName()));
					}
				});
	}

	/**
	 * A {@link Map} entry used to uniquely identify imported {@link Platform} and {@link Library} instances.
	 * <p>
	 * {@link ImportDeclaration} instance cannot be used as {@link Map} entries because they do not provide a
	 * field-based implementation of {@code equals}, and multiple instances of the same import can be created from the
	 * same editor. Overriding {@link EObject#equals(Object)} is considered as a bad practice since some of the core
	 * components of the EMF framework rely on object equality (see
	 * <a href="https://www.eclipse.org/forums/index.php/t/663829/">this link</a> for more information).
	 * <p>
	 * Note that there is no public constructor for this class. {@link ImportEntry} instances can be created using the
	 * {{@link #from(ImportDeclaration)} method.
	 * 
	 * @see ImportDeclaration
	 */
	private static class ImportEntry {

		/**
		 * Creates an {@link ImportEntry} from the provided {@code importDeclaration}.
		 * 
		 * @param importDeclaration the {@link ImportDeclaration} to create an entry from
		 * @return the created {@link ImportEntry}
		 */
		public static ImportEntry from(ImportDeclaration importDeclaration) {
			return new ImportEntry(importDeclaration.getPath(), importDeclaration.getAlias());
		}

		public static ImportEntry from(String path, String alias) {
			return new ImportEntry(path, alias);
		}

		/**
		 * The path of the {@link ImportDeclaration} used to create the entry.
		 */
		private String path;

		/**
		 * The alias of the {@link ImportDeclaration} used to create the entry.
		 */
		private String alias;

		/**
		 * Constructs an {@link ImportEntry} with the provided {@code path} and {@code alias}.
		 * <p>
		 * This method is private, use {@link #from(ImportDeclaration)} to create {@link ImportEntry} instances from
		 * {@link ImportDeclaration}s.
		 * 
		 * @param path  the path of the {@link ImportDeclaration} used to create the entry
		 * @param alias the alias of the {@link ImportDeclaration} used to create the entry
		 * 
		 * @see #from(ImportDeclaration)
		 */
		private ImportEntry(String path, String alias) {
			this.path = path;
			this.alias = alias;
		}

		/**
		 * Returns the path of the entry.
		 * 
		 * @return the path of the entry
		 */
		public String getPath() {
			return this.path;
		}

		/**
		 * Returns the alias of the entry.
		 * 
		 * @return the alias of the entry
		 */
		public String getAlias() {
			return this.alias;
		}

		/**
		 * Returns whether the {@link ImportEntry} and the provided {@code obj} are equal.
		 * <p>
		 * This method checks whether the {@code path} and {@code alias} values of the objects are equal. This allows to
		 * compare different {@link ImportEntry} instances representing the same {@link ImportDeclaration}.
		 * 
		 * @return {@code true} if the provided {@code obj} is equal to the {@link ImportEntry}, {@code false} otherwise
		 */
		@Override
		public boolean equals(Object obj) {
			if (obj instanceof ImportEntry) {
				ImportEntry otherEntry = (ImportEntry) obj;
				/*
				 * Use Objects.equals, the path or the alias can be null
				 */
				return Objects.equals(this.path, otherEntry.path) && Objects.equals(this.alias, otherEntry.alias);
			} else {
				return super.equals(obj);
			}
		}

		@Override
		public int hashCode() {
			/*
			 * Use Objects.hashCode, the path or the alias can be null
			 */
			return Objects.hashCode(this.path) + Objects.hashCode(this.alias);
		}

	}

}
