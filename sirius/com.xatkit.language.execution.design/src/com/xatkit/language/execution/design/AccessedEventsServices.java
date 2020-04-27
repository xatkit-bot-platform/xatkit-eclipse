package com.xatkit.language.execution.design;

import com.xatkit.intent.EventDefinition;
import com.xatkit.execution.Transition;

import java.text.MessageFormat;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.common.types.JvmField;
import org.eclipse.xtext.common.types.JvmGenericType;
import org.eclipse.xtext.common.types.JvmIdentifiableElement;
import org.eclipse.xtext.common.types.JvmMember;
import org.eclipse.xtext.xbase.XFeatureCall;

/**
 * The services class used by VSM to retrieve the {@link EventDefinition}s accessed in the provided {@link Transition}
 * Taken from <a href="https://github.com/xatkit-bot-platform/xatkit-runtime/blob/e45161b90eb5763f7fda6942e21e1c22c0eb54e5/core/src/main/java/com/xatkit/util/ExecutionModelUtils.java">ExecutionModelUtil</a>
 */
public class AccessedEventsServices {
    
 
    /**
     * Returns the {@link EventDefinition}s accessed from the provided {@link Transition}.
     * <p>
     * This method looks in the content tree of the provided {@link Transition} to retrieve the
     * {@link EventDefinition} accesses. This means that {@link EventDefinition} accesses defined as part of complex
     * conditions will be retrieved by this method.
     *
     * @param transition the {@link Transition} to retrieve the {@link EventDefinition} accesses from
     * @return the {@link EventDefinition}s accessed in the provided {@link Transition}
     */
    public Set<EventDefinition> getAccessedEvents(Transition transition) {
        Iterable<EObject> transitionContents = transition::eAllContents;
        return getAccessedEvents(transitionContents);
    }

    /**
     * Returns all the {@link EventDefinition} accesses from the provided {@link EObject}s.
     * <p>
     * This method does not iterate the content tree of the provided {@link EObject}.
     *
     * @param eObjects the {@link EObject}s to retrieve the {@link EventDefinition} accesses from
     * @return the accessed {@link EventDefinition}s
     */
    public Set<EventDefinition> getAccessedEvents(Iterable<EObject> eObjects) {
        Set<EventDefinition> result = new HashSet<>();
        for (EObject e : eObjects) {
            if (e instanceof XFeatureCall) {
                XFeatureCall featureCall = (XFeatureCall) e;
                if (isEventDefinitionAccess(featureCall.getFeature())) {
                    EventDefinition eventDefinition = getAccessedEventDefinition(featureCall.getFeature());
                    if (eventDefinition != null) {
                        result.add(eventDefinition);
                    } else {
                        throw new RuntimeException(MessageFormat.format("Cannot retrieve the {0} from the provided " +
                                        "{1} {2}", EventDefinition.class.getSimpleName(),
                                featureCall.getFeature().getClass().getSimpleName(), featureCall.getFeature()));
                    }
                }
            }
        }
        return result;
    }
    
  
    public EventDefinition getAccessedEventDefinition(JvmIdentifiableElement element) {
        if (element instanceof JvmGenericType) {
            JvmGenericType typeFeature = (JvmGenericType) element;
            if (typeFeature.getSuperTypes().stream().anyMatch(t -> t.getIdentifier().equals(EventDefinition.class.getName()))) {
                Optional<JvmMember> field =
                        typeFeature.getMembers().stream().filter(m -> m instanceof JvmField && m.getSimpleName().equals("base")).findAny();
                if (field.isPresent()) {
                    return (EventDefinition) ((JvmField) field.get()).getConstantValue();
                } else {
                    throw new RuntimeException(MessageFormat.format("Cannot find the static field {0}.{1}, this field" +
                            " should have been set during Xtext parsing", element.getSimpleName(), "base"));
                }
            }
        }
        return null;
    }
    public boolean isEventDefinitionAccess(JvmIdentifiableElement element) {
        if (element instanceof JvmGenericType) {
            JvmGenericType typeFeature = (JvmGenericType) element;
            if (typeFeature.getSuperTypes().stream().anyMatch(t -> t.getIdentifier().equals(EventDefinition.class.getName()))) {
                return true;
            }
        }
        return false;
    }
}
