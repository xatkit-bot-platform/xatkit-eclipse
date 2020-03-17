/*
 * generated by Xtext 2.12.0
 */
package com.xatkit.language.execution.validation

import com.xatkit.common.CommonPackage
import com.xatkit.common.ImportDeclaration
import com.xatkit.utils.XatkitImportHelper
import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.resource.Resource
import org.eclipse.xtext.validation.Check

import static java.util.Objects.isNull
import org.eclipse.xtext.xbase.XMemberFeatureCall
import org.eclipse.xtext.xbase.XFeatureCall
import com.xatkit.language.execution.ExecutionUtils
import org.eclipse.xtext.xbase.XStringLiteral
import org.eclipse.xtext.xbase.XbasePackage
import org.eclipse.xtext.EcoreUtil2
import com.xatkit.execution.Transition
import com.xatkit.execution.ExecutionPackage
import com.xatkit.execution.ExecutionModel

/**
 * This class contains custom validation rules. 
 * 
 * See https://www.eclipse.org/Xtext/documentation/303_runtime_concepts.html#validation
 */
class ExecutionValidator extends AbstractExecutionValidator {

	public static val String CUSTOM_TRANSITION_SIBLING_IS_WILDCARD = "custom.transition.sibling.is.wildcard"

	public static val String WILDCARD_TRANSITION_HAS_SIBLINGS = "wildcard.transition.has.siblings"

	public static val String FALLBACK_SHOULD_NOT_EXIST = "fallback.should.not.exist"

	public static val String TRANSITIONS_SHOULD_NOT_EXIST = "transitions.should.not.exist"

	public static val String INIT_STATE_DOES_NOT_EXIST = "init.state.does.not.exist"

	public static val String INIT_STATE_DOES_NOT_HAVE_TRANSITION = "init.state.does.not.have.transition"

	public static val String FALLBACK_STATE_DOES_NOT_EXIST = "fallback.state.does.not.exist"

	public static val String FALLBACK_DOES_NOT_HAVE_BODY = "fallback.does.not.have.body"

	@Check
	def checkImportDefinition(ImportDeclaration i) {
		val Resource importedResource = XatkitImportHelper.getInstance.getResourceFromImport(i)
		if (isNull(importedResource)) {
			error("Cannot resolve the import " + i.path, CommonPackage.Literals.IMPORT_DECLARATION__PATH)
		}
	}

	@Check
	def checkGetContext(XMemberFeatureCall f) {
		if (f.isStringGet) {
			if (f.targetIsContext) {
				val getKey = (f.memberCallArguments.get(0) as XStringLiteral).value
				val declaredContexts = ExecutionUtils.getEventDefinitionsFromImports(
					ExecutionUtils.getContainingExecutionModel(f)).flatMap[outContexts].map[name].toList
				if (!declaredContexts.contains(getKey)) {
					warning("Cannot find context " + getKey + " from the imported libraries/platforms",
						XbasePackage.Literals.XMEMBER_FEATURE_CALL__MEMBER_CALL_ARGUMENTS)
				}
			}
		}
	}

	@Check
	def checkGetParameterOnContext(XMemberFeatureCall f) {
		if (f.isStringGet) {
			if (f.memberCallTarget instanceof XMemberFeatureCall) {
				val memberFeatureCallTarget = f.memberCallTarget as XMemberFeatureCall
				if (memberFeatureCallTarget.isStringGet && memberFeatureCallTarget.targetIsContext) {
					/*
					 * We are dealing with context.get.get here, we want to check that the parameter associated to the get key exists in the context.
					 */
					val getContextKey = (memberFeatureCallTarget.memberCallArguments.get(0) as XStringLiteral).value
					val getParameterKey = (f.memberCallArguments.get(0) as XStringLiteral).value
					if (ExecutionUtils.getEventDefinitionsFromImports(ExecutionUtils.getContainingExecutionModel(f)).
						flatMap[outContexts].filter [ c |
							c.name == getContextKey
						].flatMap[parameters].filter[p|p.name == getParameterKey].isEmpty) {
						/*
						 * Cannot find the parameter in the imported EventDefinition's contexts.
						 */
						warning("Cannot find the parameter " + getParameterKey + " in context " + getContextKey,
							XbasePackage.Literals.XMEMBER_FEATURE_CALL__MEMBER_CALL_ARGUMENTS)
					}
				}
			}
		}
	}

	@Check
	def checkGetSession(XMemberFeatureCall f) {
		if (f.isStringGet && f.targetIsSession) {
			val getKey = (f.memberCallArguments.get(0) as XStringLiteral).value
			val executionModel = ExecutionUtils.getContainingExecutionModel(f)
			val allMemberFeatureCalls = EcoreUtil2.eAllOfType(executionModel, XMemberFeatureCall)
			val putCallsWithSameKey = allMemberFeatureCalls.filter [ fCall |
				/*
				 * Look for a session.put call with the same key
				 */
				fCall.isPutWithStringKey && fCall.targetIsSession &&
					(fCall.memberCallArguments.get(0) as XStringLiteral).value == getKey
			]
			if (putCallsWithSameKey.empty) {
				warning("The session key " + getKey + " is not set in the execution model",
					XbasePackage.Literals.XMEMBER_FEATURE_CALL__MEMBER_CALL_ARGUMENTS)
			}
		}
	}

	@Check
	def checkCustomTransitionSiblingIsNotWildcard(Transition t) {
		if (!t.isIsWildcard) {
			val state = t.eContainer as com.xatkit.execution.State
			if (!state.transitions.filter[isIsWildcard].empty) {
				error("Custom transitions are not allowed if a wildcard transition already exists",
					ExecutionPackage.Literals.TRANSITION__CONDITION, CUSTOM_TRANSITION_SIBLING_IS_WILDCARD)
			}
		}
	}

	@Check
	def checkWildcardTransitionHasNoSiblings(Transition t) {
		if (t.isIsWildcard) {
			val state = t.eContainer as com.xatkit.execution.State
			if (state.transitions.size > 1) {
				error("A wildcard transition cannot be defined with other transitions",
					ExecutionPackage.Literals.TRANSITION__IS_WILDCARD, WILDCARD_TRANSITION_HAS_SIBLINGS)
			}
		}
	}

	@Check
	def checkStateNameIsUnique(com.xatkit.execution.State s) {
		val executionModel = ExecutionUtils.getContainingExecutionModel(s)
		if (executionModel.states.filter[exState|exState.name == s.name].size > 1) {
			error("State names must be unique", ExecutionPackage.Literals.STATE__NAME)
		}
	}

	@Check
	def checkStateDoesNotDefineFallbackIfItContainsAWildcardTransition(com.xatkit.execution.State s) {
		val containsWildcardTransition = s.transitions.filter[it.isIsWildcard].iterator.hasNext
		if (containsWildcardTransition && s.fallback !== null) {
			error("States with a wildcard transition cannot define a custom fallback",
				ExecutionPackage.Literals.STATE__FALLBACK, FALLBACK_SHOULD_NOT_EXIST)
		}
	}

	@Check
	def checkFallbackStateDoesNotDefineFallback(com.xatkit.execution.State s) {
		if (s.name == "Default_Fallback") {
			if (s.fallback !== null) {
				error("Default_Fallback state cannot define a fallback", ExecutionPackage.Literals.STATE__FALLBACK,
					FALLBACK_SHOULD_NOT_EXIST)
			}
		}
	}

	@Check
	def checkFallbackStateDoesNotDefineNext(com.xatkit.execution.State s) {
		if (s.name == "Default_Fallback") {
			if (!s.transitions.isNullOrEmpty) {
				for (var i = 0; i < s.transitions.length; i++) {
					error("Default_Fallback state cannot define transitions",
						ExecutionPackage.Literals.STATE__TRANSITIONS, i, TRANSITIONS_SHOULD_NOT_EXIST)
				}
			}
		}
	}

	@Check
	def checkDefaultFallbackContainsBody(com.xatkit.execution.State s) {
		if (s.name == "Default_Fallback") {
			if (s.body === null) {
				warning("Default_Fallback state should have a non-empty body", ExecutionPackage.Literals.STATE__NAME,
					FALLBACK_DOES_NOT_HAVE_BODY)
			}
		}
	}

	@Check
	def checkInitStateExists(ExecutionModel m) {
		if (m.states.filter[it.name == "Init"].empty) {
			for (var i = 0; i < m.states.length; i++) {
				error("The execution model must contain an init state",
					ExecutionPackage.Literals.EXECUTION_MODEL__STATES, i, INIT_STATE_DOES_NOT_EXIST)
			}
		}
	}

	@Check
	def checkInitStateContainsATransition(com.xatkit.execution.State s) {
		if (s.name == "Init") {
			if (s.transitions.isNullOrEmpty) {
				warning("Init state should contain at least one transition", ExecutionPackage.Literals.STATE__NAME,
					INIT_STATE_DOES_NOT_HAVE_TRANSITION)
			}
		}
	}

	@Check
	def checkDefaultFallbackStateExists(ExecutionModel m) {
		if (m.states.filter[it.name == "Default_Fallback"].empty) {
			for (var i = 0; i < m.states.length; i++) {
				error("The execution model must contain a Default_Fallback state",
					ExecutionPackage.Literals.EXECUTION_MODEL__STATES, i, FALLBACK_STATE_DOES_NOT_EXIST)
			}
		}
	}

	private def boolean isStringGet(XMemberFeatureCall f) {
		return f.feature.simpleName == "get" && f.memberCallArguments.size == 1 &&
			f.memberCallArguments.get(0) instanceof XStringLiteral
	}

	private def boolean isPutWithStringKey(XMemberFeatureCall f) {
		return f.feature.simpleName == "put" && f.memberCallArguments.size == 2 &&
			f.memberCallArguments.get(0) instanceof XStringLiteral
	}

	private def boolean targetIsContext(XMemberFeatureCall f) {
		return f.memberCallTarget instanceof XFeatureCall &&
			(f.memberCallTarget as XFeatureCall).feature.simpleName == "context"
	}

	private def boolean targetIsSession(XMemberFeatureCall f) {
		return f.memberCallTarget instanceof XFeatureCall &&
			(f.memberCallTarget as XFeatureCall).feature.simpleName == "session"
	}

}
