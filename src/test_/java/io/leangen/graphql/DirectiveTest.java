package io.leangen.graphql;

import graphql.DirectivesUtil;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.Scalars;
import graphql.introspection.Introspection;
import graphql.schema.GraphQLDirectiveContainer;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import io.leangen.graphql.RelayTest.Book;
import io.leangen.graphql.annotations.Context;
import io.leangen.graphql.annotations.GraphQLNonNull;
import io.leangen.graphql.annotations.GraphQLScalar;
import io.leangen.graphql.annotations.types.GraphQLDirective;
import io.leangen.graphql.util.GraphQLUtils;
import org.eclipse.microprofile.graphql.InputField;
import org.eclipse.microprofile.graphql.Query;
import org.eclipse.microprofile.graphql.Source;
import org.junit.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class DirectiveTest {

    @Test
    public void testSchemaDirectives() {
        GraphQLSchema schema = new TestSchemaGenerator()
                .withOperationsFromSingleton(new ServiceWithDirectives())
                .generate();

        GraphQLFieldDefinition scalarField = schema.getQueryType().getFieldDefinition("scalar");
        assertDirective(scalarField, "fieldDef", "fieldDef");

        GraphQLScalarType scalarResult = (GraphQLScalarType) scalarField.getType();
        assertDirective(scalarResult, "scalar", "scalar");

        graphql.schema.GraphQLArgument argument = scalarField.getArgument("in");
        assertDirective(argument, "argDef", "argument");

        GraphQLInputObjectType inputType = (GraphQLInputObjectType) argument.getType();
        assertDirective(inputType, "inputObjectType", "input");
        graphql.schema.GraphQLArgument directiveArg = DirectivesUtil.directiveWithArg(inputType.getDirectives(), "inputObjectType", "value").get();
        Optional<graphql.schema.GraphQLArgument> metaArg = DirectivesUtil.directiveWithArg(directiveArg.getDirectives(), "meta", "value");
        assertTrue(metaArg.isPresent());
        assertEquals("meta", metaArg.get().getValue());

        GraphQLInputObjectField inputField = inputType.getField("value");
        assertDirective(inputField, "inputFieldDef", "inputField");

        GraphQLFieldDefinition objField = schema.getQueryType().getFieldDefinition("obj");
        GraphQLObjectType objResult = (GraphQLObjectType) objField.getType();
        assertDirective(objResult, "objectType", "object");

        GraphQLFieldDefinition innerField = objResult.getFieldDefinition("value");
        assertDirective(innerField, "fieldDef", "field");
    }

    @Test
    public void testClientDirectiveMapping() {
        GraphQLSchema schema = new GraphQLSchemaGenerator()
                .withOperationsFromSingleton(new BookService())
                .withAdditionalDirectives(Field.class, Frags.class, Operation.class)
                .generate();

        assertClientDirectiveMapping(schema, "field", "enabled", Introspection.DirectiveLocation.FIELD);
        assertClientDirectiveMapping(schema, "frags", "enabled", Introspection.DirectiveLocation.FRAGMENT_DEFINITION,
                Introspection.DirectiveLocation.FRAGMENT_SPREAD, Introspection.DirectiveLocation.INLINE_FRAGMENT);
        assertClientDirectiveMapping(schema, "operation", "enabled",
                Introspection.DirectiveLocation.QUERY, Introspection.DirectiveLocation.MUTATION);
    }

    @Test
    public void testClientDirectiveInjection() {
        GraphQLSchema schema = new GraphQLSchemaGenerator()
                .withOperationsFromSingleton(new BookService())
                .withAdditionalDirectives(Interrupt.class)
                .generate();

        assertClientDirectiveMapping(schema, "timeout", "afterMillis", GraphQLDirective.ALL_CLIENT);

        GraphQL graphQL = GraphQL.newGraphQL(schema).build();

        assertClientDirectiveValues(graphQL, "query Books {books(searchString: \"monkey\") {id review @timeout(afterMillis: 10)}}", 10);
        assertClientDirectiveValues(graphQL, "query Books {books(searchString: \"monkey\") {id review @timeout(afterMillis: 10) review}}", 10);
        assertClientDirectiveValues(graphQL, "query Books @timeout(afterMillis: 10) {books(searchString: \"monkey\") {id review}}", 10);
        assertClientDirectiveValues(graphQL, "query Books @timeout(afterMillis: 10) {books(searchString: \"monkey\") {id review @timeout(afterMillis: 5)}}", 5, 10);
        assertClientDirectiveValues(graphQL,"fragment Details on Book @timeout(afterMillis: 25) {\n" +
                "  title" +
                "  review @timeout(afterMillis: 5)" +
                "}" +
                "query Books @timeout(afterMillis: 30) {" +
                "  books(searchString: \"monkey\") {" +
                "    ...Details @timeout(afterMillis: 20)" +
                "    ...on Book @timeout(afterMillis: 15) {review @timeout(afterMillis: 10)} id}}",
                5, 10, 15, 20, 25, 30);
        assertClientDirectiveValues(graphQL,"fragment Details on Book @timeout(afterMillis: 30) {\n" +
                "  review @timeout(afterMillis: 5)" +
                "  ... Review @timeout(afterMillis: 15)" +
                "}" +
                "fragment Review on Book @timeout(afterMillis: 25) {\n" +
                "  review @timeout(afterMillis: 10)" +
                "}" +
                "query Books @timeout(afterMillis: 35) {" +
                "  books(searchString: \"monkey\") @timeout(afterMillis: 40) {" +
                "    ...Details @timeout(afterMillis: 20)" +
                "  }}",
                5, 10, 15, 20, 25, 30, 35);
    }

    private void assertDirective(GraphQLDirectiveContainer container, String directiveName, String innerName) {
        Optional<graphql.schema.GraphQLArgument> argument = DirectivesUtil.directiveWithArg(container.getDirectives(), directiveName, "value");
        assertTrue(argument.isPresent());
        GraphQLInputObjectType argType = (GraphQLInputObjectType) GraphQLUtils.unwrapNonNull(argument.get().getType());
        assertEquals("WrapperInput", argType.getName());
        assertSame(Scalars.GraphQLString, argType.getFieldDefinition("name").getType());
        assertSame(Scalars.GraphQLString, argType.getFieldDefinition("value").getType());
        Wrapper wrapper = (Wrapper) argument.get().getValue();
        assertEquals(innerName, wrapper.name());
        assertEquals("test", wrapper.value());
    }

    private void assertClientDirectiveMapping(GraphQLSchema schema, String directiveName, String argumentName, Introspection.DirectiveLocation... validLocations) {
        graphql.schema.GraphQLDirective directive = schema.getDirective(directiveName);
        assertNotNull(directive);
        assertEquals(validLocations.length, directive.validLocations().size());
        Arrays.stream(validLocations).forEach(loc -> assertTrue(directive.validLocations().contains(loc)));
        assertEquals(1, directive.getArguments().size());
        assertNotNull(directive.getArgument(argumentName));
    }

    private void assertClientDirectiveValues(GraphQL graphQL, String query, int... timeouts) {
        AtomicReference<List<Interrupt>> interrupts = new AtomicReference<>();
        ExecutionResult result = graphQL.execute(ExecutionInput.newExecutionInput()
                .query(query)
                .context(interrupts)
                .build());

        assertTrue(result.getErrors().isEmpty());
        assertNotNull(interrupts.get());
        assertEquals(timeouts.length, interrupts.get().size());
        assertArrayEquals(timeouts, interrupts.get().stream().mapToInt(timeout -> timeout.after).toArray());
    }

    public static class BookService {

        @Query
        public List<@GraphQLNonNull Book> books(String searchString) {
            return Collections.singletonList(new Book(searchString, "x123"));
        }

        @Query
        public String review(@Source Book book,
                             @Context AtomicReference<List<Interrupt>> context,
                             @io.leangen.graphql.annotations.GraphQLDirective List<Interrupt> timeouts) {
            context.set(timeouts);
            return "Wholesome";
        }
    }

    private static class ServiceWithDirectives {

        @Query
        @FieldDef(@Wrapper(name = "fieldDef", value = "test"))
        public @GraphQLScalar ScalarResult scalar(@ArgDef(@Wrapper(name = "argument", value = "test")) Input in) {
            return null;
        }

        @Query
        @FieldDef(@Wrapper(name = "fieldDef", value = "test"))
        public ObjectResult obj(@ArgDef(@Wrapper(name = "argument", value = "test")) String in) {
            return null;
        }
    }

    @Scalar(@Wrapper(name = "scalar", value = "test"))
    private static class ScalarResult {
        public String value;
    }

    @ObjectType(@Wrapper(name = "object", value = "test"))
    private static class ObjectResult {
        @FieldDef(@Wrapper(name = "field", value = "test"))
        @Query
        public String getValue() {
            return null;
        }
    }

    @InputObjectType(@Wrapper(name = "input", value = "test"))
    private static class Input {
        @InputFieldDef(@Wrapper(name = "inputField", value = "test"))
        public String value;
    }

    @GraphQLDirective
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE})
    public @interface Scalar {
        Wrapper value();
    }

    @GraphQLDirective
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE})
    public @interface ObjectType {
        Wrapper value();
    }

    @GraphQLDirective
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.FIELD})
    public @interface FieldDef {
        Wrapper value();
    }

    @GraphQLDirective
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.PARAMETER})
    public @interface ArgDef {
        Wrapper value();
    }

    @GraphQLDirective
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE})
    public @interface InterfaceType {
        Wrapper value();
    }

    @GraphQLDirective
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE})
    public @interface UnionType {
        Wrapper value();
    }

    @GraphQLDirective
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE})
    public @interface EnumType {
        Wrapper value();
    }

    @GraphQLDirective
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD})
    public @interface EnumValue {
        Wrapper value();
    }

    @GraphQLDirective
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE})
    public @interface InputObjectType {
        @Meta("meta") Wrapper value();
    }

    @GraphQLDirective
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.FIELD})
    public @interface InputFieldDef {
        Wrapper value();
    }

    @GraphQLDirective
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD})
    public @interface Meta {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.ANNOTATION_TYPE})
    public @interface Wrapper {
        String name();
        String value();
    }

    @GraphQLDirective(name = "timeout")
    public static class Interrupt {
        @InputField(value = "afterMillis")
        @SuppressWarnings("WeakerAccess")
        public int after;
    }

    @GraphQLDirective(locations = Introspection.DirectiveLocation.FIELD)
    public static class Field {
        public boolean enabled;
    }

    @GraphQLDirective(locations = {Introspection.DirectiveLocation.FRAGMENT_DEFINITION, Introspection.DirectiveLocation.FRAGMENT_SPREAD, Introspection.DirectiveLocation.INLINE_FRAGMENT})
    public static class Frags {
        public boolean enabled;
    }

    @GraphQLDirective(locations = {Introspection.DirectiveLocation.QUERY, Introspection.DirectiveLocation.MUTATION})
    public static class Operation {
        public boolean enabled;
    }
}
