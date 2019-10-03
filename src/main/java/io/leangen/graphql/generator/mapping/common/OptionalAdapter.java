package io.leangen.graphql.generator.mapping.common;

import io.leangen.geantyref.GenericTypeReflector;
import io.leangen.graphql.execution.GlobalEnvironment;
import io.leangen.graphql.execution.ResolutionEnvironment;
import io.leangen.graphql.generator.mapping.AbstractSimpleTypeAdapter;
import io.leangen.graphql.generator.mapping.DelegatingOutputConverter;
import io.leangen.graphql.metadata.strategy.value.ValueMapper;
import io.leangen.graphql.util.ClassUtils;

import java.lang.reflect.AnnotatedType;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * @author Bojan Tomic (kaqqao)
 */
public class OptionalAdapter extends AbstractSimpleTypeAdapter<Optional<?>, Object> implements DelegatingOutputConverter<Optional<?>, Object> {

    @Override
    public Object convertOutput(Optional<?> original, AnnotatedType type, ResolutionEnvironment env) {
        return original.map(inner -> env.convertOutput(inner, env.getDerived(type, 0))).orElse(null);
    }

    @Override
    public Optional<?> convertInput(Object substitute, AnnotatedType type, GlobalEnvironment environment, ValueMapper valueMapper) {
        return Optional.ofNullable(environment.convertInput(substitute, getSubstituteType(type), valueMapper));
    }

    @Override
    public AnnotatedType getSubstituteType(AnnotatedType original) {
        AnnotatedType innerType = GenericTypeReflector.getTypeParameter(original, Optional.class.getTypeParameters()[0]);
        return ClassUtils.addAnnotations(innerType, original.getAnnotations());
    }

    @Override
    public List<AnnotatedType> getDerivedTypes(AnnotatedType type) {
        return Collections.singletonList(getSubstituteType(type));
    }
}
