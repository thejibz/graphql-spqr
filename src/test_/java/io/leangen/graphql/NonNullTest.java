package io.leangen.graphql;

import graphql.Scalars;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLSchema;
import io.leangen.graphql.annotations.GraphQLNonNull;
import io.leangen.graphql.generator.mapping.common.NonNullMapper;
import io.leangen.graphql.support.TestLog;
import org.eclipse.microprofile.graphql.Argument;
import org.eclipse.microprofile.graphql.DefaultValue;
import org.eclipse.microprofile.graphql.InputField;
import org.eclipse.microprofile.graphql.Query;
import org.junit.Test;

import javax.annotation.Nonnull;
import javax.validation.constraints.NotNull;

import static io.leangen.graphql.support.GraphQLTypeAssertions.assertNonNull;
import static io.leangen.graphql.support.LogAssertions.assertWarningsLogged;

public class NonNullTest {

    @Test
    public void testNonNullWithDefaultValueWarning() {
        try (TestLog log = new TestLog(NonNullMapper.class)) {
            new TestSchemaGenerator().withOperationsFromSingleton(new Service()).generate();
            assertWarningsLogged(log.getEvents(), "Non-null argument");
        }
    }

    @Test
    public void testJsr305NonNull() {
        GraphQLSchema schema = new TestSchemaGenerator().withOperationsFromSingleton(new Jsr305()).generate();
        GraphQLFieldDefinition field = schema.getQueryType().getFieldDefinition("nonNull");
        assertNonNull(field.getType(), Scalars.GraphQLString);
        assertNonNull(field.getArgument("in").getType(), Scalars.GraphQLString);
    }

    @Test
    public void testJsr380NonNull() {
        GraphQLSchema schema = new TestSchemaGenerator().withOperationsFromSingleton(new Jsr380()).generate();
        GraphQLFieldDefinition field = schema.getQueryType().getFieldDefinition("nonNull");
        assertNonNull(field.getType(), Scalars.GraphQLString);
        assertNonNull(field.getArgument("in").getType(), Scalars.GraphQLString);
    }

    private static class Service {
        @Query
        public Integer integerWithDefault(@Argument(value = "in") @DefaultValue("3") @GraphQLNonNull Integer in) {
            return in;
        }

        @Query
        public Item fieldWithDefault(Item in) {
            return in;
        }
    }

    private static class Jsr305 {
        @Query
        @Nonnull
        public String nonNull(@Nonnull String in) {
            return in;
        }
    }

    private static class Jsr380 {
        @Query
        @NotNull
        public String nonNull(@NotNull String in) {
            return in;
        }
    }

    private static class Item {
        @InputField(value = "title")
        public @GraphQLNonNull String name;
    }
}
