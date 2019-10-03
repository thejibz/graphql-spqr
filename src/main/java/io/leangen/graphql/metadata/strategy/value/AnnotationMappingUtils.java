package io.leangen.graphql.metadata.strategy.value;

import io.leangen.graphql.annotations.GraphQLInputField;
import io.leangen.graphql.util.Utils;

import org.eclipse.microprofile.graphql.InputField;
import org.eclipse.microprofile.graphql.Description;

import java.lang.reflect.Method;

public class AnnotationMappingUtils {

    public static String inputFieldName(Method method) {
        if (method.isAnnotationPresent(InputField.class)) {
            return Utils.coalesce(method.getAnnotation(InputField.class).value(), method.getName());
        }
        if (method.isAnnotationPresent(GraphQLInputField.class)) {
            return Utils.coalesce(method.getAnnotation(GraphQLInputField.class).name(), method.getName());
        }
        return method.getName();
    }

    public static String inputFieldDescription(Method method) {
        if(method.isAnnotationPresent(Description.class) && method.getAnnotation(Description.class).value()!=null){
            return method.getAnnotation(Description.class).value();
        }
        if(method.isAnnotationPresent(GraphQLInputField.class) && method.getAnnotation(GraphQLInputField.class).description()!=null){
            return method.getAnnotation(GraphQLInputField.class).description();
        }
        return "";
    }
}
