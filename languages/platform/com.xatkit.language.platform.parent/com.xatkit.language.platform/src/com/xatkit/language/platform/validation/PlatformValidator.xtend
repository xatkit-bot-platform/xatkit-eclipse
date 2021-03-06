/*
 * generated by Xtext 2.12.0
 */
package com.xatkit.language.platform.validation

import com.xatkit.common.CommonPackage
import com.xatkit.common.ImportDeclaration
import com.xatkit.platform.PlatformDefinition
import com.xatkit.platform.PlatformPackage
import com.xatkit.utils.XatkitImportHelper
import org.eclipse.emf.ecore.resource.Resource
import org.eclipse.xtext.validation.Check

import static java.util.Objects.isNull
import static java.util.Objects.nonNull

/**
 * This class contains custom validation rules. 
 * 
 * See https://www.eclipse.org/Xtext/documentation/303_runtime_concepts.html#validation
 */
class PlatformValidator extends AbstractPlatformValidator {

	@Check
	def checkImportDefinition(ImportDeclaration i) {
		val Resource importedResource = XatkitImportHelper.getInstance.getResourceFromImport(i)
		if(isNull(importedResource)) {
			error("Cannot resolve the import " + i.path, CommonPackage.Literals.IMPORT_DECLARATION__PATH)
		}
	}
	
	@Check
	def checkDuplicatedAliases(ImportDeclaration i) {
		val PlatformDefinition platformDefinition = i.eContainer as PlatformDefinition
		platformDefinition.imports.forEach[platformDefinitionImport | 
			if(!platformDefinitionImport.path.equals(i.path) && platformDefinitionImport.alias.equals(i.alias)) {
				error("Duplicated alias " + i.alias, CommonPackage.Literals.IMPORT_DECLARATION__ALIAS)
			}
		]
	}

	@Check
	def checkPlatformExtendsAbstractPlatform(PlatformDefinition platform) {
		if (nonNull(platform.extends)) {
			if (!platform.extends.abstract) {
				error('Platform ' + platform.name + ' extends ' + platform.extends.name + ' which is not abstract',
					PlatformPackage.Literals.PLATFORM_DEFINITION__EXTENDS)
			}
		}
	}

	@Check
	def checkAbstractPlatformDoesNotDefinePath(PlatformDefinition platform) {
		if (platform.abstract) {
			if (nonNull(platform.runtimePath)) {
				error("Abstract platforms should not declare a path",
					PlatformPackage.Literals.PLATFORM_DEFINITION__RUNTIME_PATH)
			}
		}
	}

	@Check
	def checkConcretePlatformDefinesPath(PlatformDefinition platform) {
		if (!platform.abstract) {
			if (isNull(platform.runtimePath) || platform.runtimePath.isEmpty) {
				error("Not abstract platforms should declare a not empty path",
					PlatformPackage.Literals.PLATFORM_DEFINITION__RUNTIME_PATH)
			}
		}
	}

}
