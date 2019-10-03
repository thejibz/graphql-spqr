package io.leangen.graphql.metadata.strategy.query;

import io.leangen.graphql.annotations.GraphQLArgument;
import io.leangen.graphql.annotations.GraphQLContext;
import io.leangen.graphql.annotations.GraphQLId;
import io.leangen.graphql.execution.GlobalEnvironment;
import io.leangen.graphql.metadata.OperationArgument;
import io.leangen.graphql.metadata.exceptions.TypeMappingException;
import io.leangen.graphql.metadata.messages.MessageBundle;
import io.leangen.graphql.metadata.strategy.InclusionStrategy;
import io.leangen.graphql.metadata.strategy.value.DefaultValueProvider;
import io.leangen.graphql.metadata.strategy.value.JsonDefaultValueProvider;
import io.leangen.graphql.util.ClassUtils;
import io.leangen.graphql.util.ReservedStrings;
import io.leangen.graphql.util.Urls;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.microprofile.graphql.Argument;
import org.eclipse.microprofile.graphql.DefaultValue;
import org.eclipse.microprofile.graphql.Source;
import org.eclipse.microprofile.graphql.Description;

@SuppressWarnings("WeakerAccess")
public class AnnotatedArgumentBuilder implements ResolverArgumentBuilder {

    private static final Logger log = LoggerFactory.getLogger(AnnotatedArgumentBuilder.class);

    @Override
    public List<OperationArgument> buildResolverArguments(ArgumentBuilderParams params) {
        Method resolverMethod = params.getResolverMethod();
        List<OperationArgument> operationArguments = new ArrayList<>(resolverMethod.getParameterCount());
        AnnotatedType[] parameterTypes = ClassUtils.getParameterTypes(resolverMethod, params.getDeclaringType());
        for (int i = 0; i < resolverMethod.getParameterCount(); i++) {
            Parameter parameter = resolverMethod.getParameters()[i];
            if (parameter.isSynthetic() || parameter.isImplicit()) continue;
            AnnotatedType parameterType;
            try {
                parameterType = params.getTypeTransformer().transform(parameterTypes[i]);
            } catch (TypeMappingException e) {
                throw TypeMappingException.ambiguousParameterType(resolverMethod, parameter, e);
            }
            operationArguments.add(buildResolverArgument(parameter, parameterType, params.getInclusionStrategy(), params.getEnvironment()));
        }
        return operationArguments;
    }

    protected OperationArgument buildResolverArgument(Parameter parameter, AnnotatedType parameterType,
                                                      InclusionStrategy inclusionStrategy, GlobalEnvironment environment) {
        return new OperationArgument(
                parameterType,
                getArgumentName(parameter, parameterType, inclusionStrategy, environment.messageBundle),
                getArgumentDescription(parameter, parameterType, environment.messageBundle),
                defaultValue(parameter, parameterType, environment),
                parameter,
                (parameter.isAnnotationPresent(GraphQLContext.class) || parameter.isAnnotationPresent(Source.class)),
                inclusionStrategy.includeArgument(parameter, parameterType)
        );
    }

    protected String getArgumentName(Parameter parameter, AnnotatedType parameterType, InclusionStrategy inclusionStrategy, MessageBundle messageBundle) {
        if (Optional.ofNullable(parameterType.getAnnotation(GraphQLId.class)).filter(GraphQLId::relayId).isPresent()) {
            return GraphQLId.RELAY_ID_FIELD_NAME;
        }
        Argument argument = parameter.getAnnotation(Argument.class);
        if (argument != null && !argument.value().isEmpty()) {
            return messageBundle.interpolate(argument.value());
        }
        GraphQLArgument meta = parameter.getAnnotation(GraphQLArgument.class);
        if (meta != null && !meta.name().isEmpty()) {
            return messageBundle.interpolate(meta.name());
        } else {
            if (!parameter.isNamePresent() && inclusionStrategy.includeArgument(parameter, parameterType)) {
                log.warn("No explicit argument name given and the parameter name lost in compilation: "
                        + parameter.getDeclaringExecutable().toGenericString() + "#" + parameter.toString()
                        + ". For details and possible solutions see " + Urls.Errors.MISSING_ARGUMENT_NAME);
            }
            return parameter.getName();
        }
    }

    protected String getArgumentDescription(Parameter parameter, AnnotatedType parameterType, MessageBundle messageBundle) {
        Description description = parameter.getAnnotation(Description.class);
        if(description!=null && description.value()!=null && !description.value().isEmpty())return messageBundle.interpolate(description.value());
        
        GraphQLArgument meta = parameter.getAnnotation(GraphQLArgument.class);
        return meta != null ? messageBundle.interpolate(meta.description()) : null;
    }

    protected Object defaultValue(Parameter parameter, AnnotatedType parameterType, GlobalEnvironment environment) {

        DefaultValue defaultValue = parameter.getAnnotation(DefaultValue.class);
        if(defaultValue!=null && defaultValue.value()!=null && !defaultValue.value().isEmpty()){
            try {
                return defaultValueProvider(JsonDefaultValueProvider.class, environment)
                        .getDefaultValue(parameter, environment.getMappableInputType(parameterType), ReservedStrings.decode(environment.messageBundle.interpolate(defaultValue.value())));
            } catch (ReflectiveOperationException e) {
                throw new IllegalArgumentException(
                        JsonDefaultValueProvider.class + " must expose a public default constructor, or a constructor accepting " + GlobalEnvironment.class.getName(), e);
            }
        }
        
        GraphQLArgument meta = parameter.getAnnotation(GraphQLArgument.class);
        if (meta == null) return null;
        try {
            return defaultValueProvider(meta.defaultValueProvider(), environment)
                    .getDefaultValue(parameter, environment.getMappableInputType(parameterType), ReservedStrings.decode(environment.messageBundle.interpolate(meta.defaultValue())));
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException(
                    meta.defaultValueProvider().getName() + " must expose a public default constructor, or a constructor accepting " + GlobalEnvironment.class.getName(), e);
        }
    }

    protected <T extends DefaultValueProvider> T defaultValueProvider(Class<T> type, GlobalEnvironment environment) throws ReflectiveOperationException {
        try {
            return type.getConstructor(GlobalEnvironment.class).newInstance(environment);
        } catch (NoSuchMethodException e) {
            return type.getConstructor().newInstance();
        }
    }
}
