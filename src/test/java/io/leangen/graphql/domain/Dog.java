package io.leangen.graphql.domain;

import io.leangen.graphql.annotations.GraphQLComplexity;
import io.leangen.graphql.annotations.GraphQLId;
import io.leangen.graphql.annotations.GraphQLQuery;

public class Dog implements Pet {

    private final Number dogTagNum;
    private final String sound;
    private final int boneCount;
    private final Human owner;

    public Dog() {
        this("woof");
    }

    public Dog(String sound) {
        this(123, sound, 5, new Human("John", "Dawg"));
    }

    public Dog(Number dogTagNum, String sound, int boneCount, Human owner) {
        this.dogTagNum = dogTagNum;
        this.sound = sound;
        this.boneCount = boneCount;
        this.owner = owner;
    }

    @Override
    public String getSound() {
        return sound;
    }

    @GraphQLComplexity("2")
    public int getBoneCount() {
        return boneCount;
    }

    @Override
    public Human getOwner() {
        return owner;
    }

    @GraphQLQuery(name = "id")
    public @GraphQLId(relayId = true) Number getDogTagNum() {
        return dogTagNum;
    }
}
