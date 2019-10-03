package io.leangen.graphql;

import graphql.language.IntValue;
import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import io.leangen.geantyref.TypeFactory;
import io.leangen.graphql.annotations.GraphQLArgument;
import io.leangen.graphql.annotations.GraphQLQuery;
import io.leangen.graphql.util.Scalars;
import org.junit.Test;

import java.math.BigInteger;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TemporalScalarsTest {

    @Test
    public void testDate() {
        testTemporal(Date.class, "2017-06-24T23:22:34.120Z", 1498346554120L);
    }

    @Test
    public void testSqlDate() {
        testTemporal(java.sql.Date.class, "2017-06-24", 1498346554120L);
    }

    @Test
    public void testSqlTime() {
        testTemporal(Time.class, "23:15:44", 1498346144505L);
    }

    @Test
    public void testSqlTimestamp() {
        testTemporal(Timestamp.class, "2017-06-24T23:15:44.500Z", 1498346144500L);
    }

    @Test
    public void testSqlTypesAsDates() {
        Coercing dateCoercing = Scalars.toGraphQLScalarType(Date.class).getCoercing();
        Coercing sqlDateCoercing = Scalars.toGraphQLScalarType(java.sql.Date.class).getCoercing();
        Coercing sqlTimeCoercing = Scalars.toGraphQLScalarType(Time.class).getCoercing();
        Coercing sqlTimestampCoercing = Scalars.toGraphQLScalarType(Timestamp.class).getCoercing();
        java.sql.Date sqlDate = java.sql.Date.valueOf(LocalDate.parse("2017-06-24"));
        Time sqlTime = Time.valueOf(LocalTime.parse("23:15:44"));
        Timestamp sqlTimestamp = Timestamp.valueOf(LocalDateTime.parse("2017-06-24T23:15:44.500"));
        assertEquals(dateCoercing.serialize(sqlDate), sqlDateCoercing.serialize(sqlDate));
        assertEquals(dateCoercing.serialize(sqlTime), sqlTimeCoercing.serialize(sqlTime));
        assertEquals(dateCoercing.serialize(sqlTimestamp), sqlTimestampCoercing.serialize(sqlTimestamp));
    }

    @Test
    public void testCalendar() {
        testTemporal(Calendar.class, "2017-06-24T23:22:34.120Z", 1498346554120L);
    }

    @Test
    public void testInstant() {
        testTemporal(Instant.class, "2017-06-24T23:22:34.120Z", 1498346554120L);
    }

    @Test
    public void testLocalDate() {
        testTemporal(LocalDate.class, "2017-06-24", 1498346144503L);
    }

    @Test
    public void testLocalTime() {
        testTemporal(LocalTime.class, "23:15:44.505", 1498346144505L);
    }

    @Test
    public void testLocalDateTime() {
        testTemporal(LocalDateTime.class, "2017-06-24T23:15:44.500", 1498346144500L);
    }

    @Test
    public void testZonedDateTime() {
        testTemporal(ZonedDateTime.class, "2017-06-24T23:15:44.510Z", 1498346144510L);
    }

    @Test
    public void testOffsetTime() {
        testTemporal(OffsetTime.class, "09:15:30Z", 33330000L);
    }

    @Test
    public void testOffsetDateTime() {
        testTemporal(OffsetDateTime.class, "2017-06-24T23:15:44.510Z", "2017-06-24T22:15:44.510-01:00", 1498346144510L);
    }

    private void testTemporal(Class type, String expected, long literal) {
        testTemporal(type, expected, expected, literal);
    }

    private void testTemporal(Class type, String expected, String stringLiteral, long literal) {
        GraphQLScalarType scalar = Scalars.toGraphQLScalarType(type);
        testStringTemporal(type, scalar.getCoercing(), stringLiteral, stringLiteral);
        testEpochMilliTemporal(type, scalar.getCoercing(), expected, literal);
        testTemporalMapping(type, scalar);
    }

    private void testStringTemporal(Class type, Coercing coercing, String expected, String stringLiteral) {
        Object parsed = coercing.parseLiteral(new StringValue(stringLiteral));
        assertTrue(type.isInstance(parsed));
        assertEquals(expected, coercing.serialize(parsed));

        parsed = coercing.parseValue(stringLiteral);
        assertTrue(type.isInstance(parsed));
        assertEquals(expected, coercing.serialize(parsed));
        
        Object same = coercing.parseValue(parsed);
        assertEquals(parsed, same);
    }
    
    private void testEpochMilliTemporal(Class type, Coercing coercing, String expected, long literal) {
        Object parsed = coercing.parseLiteral(new IntValue(new BigInteger(Long.toString(literal))));
        assertTrue(type.isInstance(parsed));
        assertEquals(expected, coercing.serialize(parsed));
        
        parsed = coercing.parseValue(literal);
        assertTrue(type.isInstance(parsed));
        assertEquals(expected, coercing.serialize(parsed));
    }
    
    private void testTemporalMapping(Class type, GraphQLScalarType scalar) {
        GraphQLSchema schema = new GraphQLSchemaGenerator()
                .withOperationsFromSingleton(new ScalarService(), TypeFactory.parameterizedClass(ScalarService.class, type))
                .generate();
        GraphQLFieldDefinition query = schema.getQueryType().getFieldDefinition("identity");
        assertEquals(scalar, query.getType());
        assertEquals(scalar, query.getArgument("input").getType());
    }
    
    public static class ScalarService<T> {
        
        @GraphQLQuery(name = "identity")
        public T identity(@GraphQLArgument(name = "input") T input) {
            return input;
        }
    }
}
