package io.leangen.graphql.metadata.strategy.value;

import io.leangen.graphql.annotations.GraphQLInputField;
import io.leangen.graphql.annotations.GraphQLQuery;
import io.leangen.graphql.execution.GlobalEnvironment;
import io.leangen.graphql.metadata.messages.MessageBundle;
import io.leangen.graphql.util.ReservedStrings;
import io.leangen.graphql.util.Utils;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.AnnotatedType;
import java.util.List;
import java.util.Optional;

import org.eclipse.microprofile.graphql.DefaultValue;
import org.eclipse.microprofile.graphql.InputField;
import org.eclipse.microprofile.graphql.Query;
import org.eclipse.microprofile.graphql.Description;

public class InputFieldInfoGenerator {

    public Optional<String> getName(List<AnnotatedElement> candidates, MessageBundle messageBundle) {
        return Utils.or(getMicroProfileName(candidates, messageBundle), getSPQRName(candidates, messageBundle));
    }
    
    private Optional<String> getMicroProfileName(List<AnnotatedElement> candidates, MessageBundle messageBundle) {
        Optional<String> explicit = candidates.stream()
                .filter(element -> element.isAnnotationPresent(InputField.class))
                .findFirst()
                .map(element -> element.getAnnotation(InputField.class).value());
        Optional<String> implicit = candidates.stream()
                .filter(element -> element.isAnnotationPresent(Query.class))
                .findFirst()
                .map(element -> element.getAnnotation(Query.class).value());
        return Utils.or(explicit, implicit).filter(Utils::isNotEmpty).map(messageBundle::interpolate);
    }

    private Optional<String> getSPQRName(List<AnnotatedElement> candidates, MessageBundle messageBundle) {
        Optional<String> explicit = candidates.stream()
                .filter(element -> element.isAnnotationPresent(GraphQLInputField.class))
                .findFirst()
                .map(element -> element.getAnnotation(GraphQLInputField.class).name());
        Optional<String> implicit = candidates.stream()
                .filter(element -> element.isAnnotationPresent(GraphQLQuery.class))
                .findFirst()
                .map(element -> element.getAnnotation(GraphQLQuery.class).name());
        return Utils.or(explicit, implicit).filter(Utils::isNotEmpty).map(messageBundle::interpolate);
    }
    
    public Optional<String> getDescription(List<AnnotatedElement> candidates, MessageBundle messageBundle) {
        return Utils.or(getMicroProfileDescription(candidates, messageBundle),getSPQRDescription(candidates, messageBundle));
    }
    
    private Optional<String> getMicroProfileDescription(List<AnnotatedElement> candidates, MessageBundle messageBundle) {
        Optional<String> description = candidates.stream()
                .filter(element -> element.isAnnotationPresent(Description.class))
                .findFirst()
                .map(element -> element.getAnnotation(Description.class).value());
        
        return description.filter(Utils::isNotEmpty).map(messageBundle::interpolate);
    }

    private Optional<String> getSPQRDescription(List<AnnotatedElement> candidates, MessageBundle messageBundle) {
        Optional<String> explicit = candidates.stream()
                .filter(element -> element.isAnnotationPresent(GraphQLInputField.class))
                .findFirst()
                .map(element -> element.getAnnotation(GraphQLInputField.class).description());
        Optional<String> implicit = candidates.stream()
                .filter(element -> element.isAnnotationPresent(GraphQLQuery.class))
                .findFirst()
                .map(element -> element.getAnnotation(GraphQLQuery.class).description());
        return Utils.or(explicit, implicit).filter(Utils::isNotEmpty).map(messageBundle::interpolate);
    }
    
    public Optional<Object> defaultValue(List<? extends AnnotatedElement> candidates, AnnotatedType type, GlobalEnvironment environment) {
        return Utils.or(defaultMicroProfileValue(candidates, type, environment),defaultSPQRValue(candidates, type, environment));
    }
    
    private Optional<Object> defaultMicroProfileValue(List<? extends AnnotatedElement> candidates, AnnotatedType type, GlobalEnvironment environment) {
        return candidates.stream()
                .filter(element -> element.isAnnotationPresent(DefaultValue.class))
                .findFirst()
                .map(element -> {
                    DefaultValue ann = element.getAnnotation(DefaultValue.class);
                    try {
                        return defaultValueProvider(JsonDefaultValueProvider.class, environment)
                                .getDefaultValue(element, type, environment.messageBundle.interpolate(ReservedStrings.decode(ann.value())));
                    } catch (ReflectiveOperationException e) {
                        throw new IllegalArgumentException(
                                JsonDefaultValueProvider.class.getName() + " must expose a public default constructor, or a constructor accepting " + GlobalEnvironment.class.getName(), e);
                    }
                });
    }

    public Optional<Object> defaultSPQRValue(List<? extends AnnotatedElement> candidates, AnnotatedType type, GlobalEnvironment environment) {
        return candidates.stream()
                .filter(element -> element.isAnnotationPresent(GraphQLInputField.class))
                .findFirst()
                .map(element -> {
                    GraphQLInputField ann = element.getAnnotation(GraphQLInputField.class);
                    try {
                        return defaultValueProvider(ann.defaultValueProvider(), environment)
                                .getDefaultValue(element, type, environment.messageBundle.interpolate(ReservedStrings.decode(ann.defaultValue())));
                    } catch (ReflectiveOperationException e) {
                        throw new IllegalArgumentException(
                                ann.defaultValueProvider().getName() + " must expose a public default constructor, or a constructor accepting " + GlobalEnvironment.class.getName(), e);
                    }
                });
    }
    
    @SuppressWarnings("WeakerAccess")
    protected <T extends DefaultValueProvider> T defaultValueProvider(Class<T> type, GlobalEnvironment environment) throws ReflectiveOperationException {
        try {
            return type.getConstructor(GlobalEnvironment.class).newInstance(environment);
        } catch (NoSuchMethodException e) {
            return type.getConstructor().newInstance();
        }
    }
}
