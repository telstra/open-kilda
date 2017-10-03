package org.bitbucket.openkilda.messaging.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.MoreObjects;

import java.io.Serializable;
import java.util.Objects;

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ImmutablePair<L, R> implements Serializable {
    static final long serialVersionUID = 1L;

    @JsonProperty("forward")
    public final L left;

    @JsonProperty("reverse")
    public final R right;

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("forward", left)
                .add("reverse", right)
                .toString();
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }

        ImmutablePair flowPair = (ImmutablePair) object;
        return Objects.equals(getLeft(), flowPair.getLeft()) &&
                Objects.equals(getRight(), flowPair.getRight());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getLeft(), getRight());
    }

    @JsonCreator
    public ImmutablePair(@JsonProperty("forward") L left, @JsonProperty("reverse") R right) {
        this.left = left;
        this.right = right;
    }

    public L getLeft() {
        return left;
    }

    public R getRight() {
        return right;
    }
}
