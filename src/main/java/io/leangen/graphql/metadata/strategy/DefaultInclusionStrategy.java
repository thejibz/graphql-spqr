package io.leangen.graphql.metadata.strategy;

import io.leangen.graphql.annotations.GraphQLContext;
import io.leangen.graphql.annotations.GraphQLIgnore;
import io.leangen.graphql.util.ClassUtils;
import io.leangen.graphql.util.Utils;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import javax.json.bind.annotation.JsonbTransient;
import org.eclipse.microprofile.graphql.Ignore;
import org.eclipse.microprofile.graphql.Source;

public class DefaultInclusionStrategy implements InclusionStrategy {

    private final String[] basePackages;

    public DefaultInclusionStrategy(String... basePackages) {
        this.basePackages = basePackages;
    }

    @Override
    public boolean includeOperation(AnnotatedElement element, AnnotatedType type) {
        return (!ClassUtils.hasAnnotation(element, GraphQLIgnore.class, Ignore.class, JsonbTransient.class) &&
                !ClassUtils.hasAnnotationOnGetter(element, GraphQLIgnore.class, Ignore.class, JsonbTransient.class));
    }

    @Override
    public boolean includeArgument(Parameter parameter, AnnotatedType type) {
        return (!ClassUtils.hasAnnotation(parameter, GraphQLIgnore.class, Ignore.class, JsonbTransient.class, Source.class, GraphQLContext.class));
    }

    @Override
    public boolean includeInputField(InputFieldInclusionParams params) {
        return params.getElements().stream().noneMatch(element -> (ClassUtils.hasAnnotation(element, GraphQLIgnore.class, Ignore.class, JsonbTransient.class) || 
                ClassUtils.hasAnnotationOnSetter(element, GraphQLIgnore.class, Ignore.class, JsonbTransient.class)))
                && (params.isDirectlyDeserializable() || params.isDeserializableInSubType()) //is ever deserializable
                && isPackageAcceptable(params.getType(), params.getElementDeclaringClass());
    }

    protected boolean isPackageAcceptable(AnnotatedType type, Class<?> elementDeclaringClass) {
        Class<?> rawType = ClassUtils.getRawType(type.getType());
        String[] packages = new String[0];
        if (Utils.isArrayNotEmpty(this.basePackages)) {
            packages = this.basePackages;
        } else if (rawType.getPackage() != null) {
            packages = new String[] {rawType.getPackage().getName()};
        }
        packages = Arrays.stream(packages).filter(Utils::isNotEmpty).toArray(String[]::new); //remove the default package
        return elementDeclaringClass.equals(rawType)
                || Arrays.stream(packages).anyMatch(basePackage -> ClassUtils.isSubPackage(elementDeclaringClass.getPackage(), basePackage));
    }
}
