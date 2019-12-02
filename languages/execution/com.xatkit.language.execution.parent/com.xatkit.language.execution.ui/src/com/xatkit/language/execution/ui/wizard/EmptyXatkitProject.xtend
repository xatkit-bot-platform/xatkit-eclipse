package com.xatkit.language.execution.ui.wizard

import org.eclipse.core.runtime.Status
import org.eclipse.jdt.core.JavaCore
import org.eclipse.xtext.ui.XtextProjectHelper
import org.eclipse.xtext.ui.util.PluginProjectFactory
import org.eclipse.xtext.ui.wizard.template.IProjectGenerator
import org.eclipse.xtext.ui.wizard.template.ProjectTemplate

import static org.eclipse.core.runtime.IStatus.*

@ProjectTemplate(label="Empty Xatkit Project", icon="project_template.png", description="<p><b>Empty project</b></p>
<p>This an empty project to start designing with Xatkit.</p>")
class EmptyXatkitProject {

	override generateProjects(IProjectGenerator generator) {
		generator.generate(new PluginProjectFactory => [
			projectName = projectInfo.projectName
			location = projectInfo.locationPath
			projectNatures += #[JavaCore.NATURE_ID, "org.eclipse.pde.PluginNature", XtextProjectHelper.NATURE_ID]
			builderIds += #[JavaCore.BUILDER_ID, XtextProjectHelper.BUILDER_ID]
			folders += "src"
			addFile('''src/«projectInfo.projectName».intent''', '''
				Library «projectInfo.projectName»
				
				intent HelloWorld {
					inputs {
						"Hello World"
					}
					creates context Hello {
						sets parameter helloTo from fragment "World" (entity any)
					}
				}
				
			''')
			addFile('''src/«projectInfo.projectName».execution''', '''
				import library "«projectInfo.projectName»/src/«projectInfo.projectName».intent" as «projectInfo.projectName»Lib
				import library "CoreLibrary"
				import platform "ReactPlatform"
				
				
				use provider ReactPlatform.ReactIntentProvider
				
				on intent HelloWorld do
					ReactPlatform.Reply(context.get("Hello").get("helloTo") + " says hello to you!")
					
				on intent Default_Fallback_Intent do
					ReactPlatform.Reply("Sorry I didn't get it")
					
			''')
			addFile('''«projectInfo.projectName».properties''', '''
				# Execution file containing the logic of the bot
				xatkit.execution.model = src/«projectInfo.projectName».execution
				
				# Resolve alias imports in Execution model
				xatkit.libraries.custom.«projectInfo.projectName»Lib = src/«projectInfo.projectName».intent
				
				# Set the server port to use (defaults to 5000)
				# xatkit.server.port = 5000
				
				# Xatkit server location, i.e. public URL (defaults to "http://localhost")
				# xatkit.server.public_url = http://localhost
				
			''')
			addRequiredBundles(
				#["org.eclipse.xtext.xbase.lib;bundle-version=\"2.15.0\"",
					"com.xatkit.metamodels-utils;bundle-version=\"2.0.0\""])
		])
	}
}
