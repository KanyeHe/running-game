package cn.luzhou.rules;

import java.util.*;
public interface WildcardPolicy {
    boolean allows(Combination.Type type, Map<Integer,Integer> substitutions);
    /** Explicit development placeholder: rank-only substitutions, both jokers allowed. No suit identity. */
    static WildcardPolicy demo() { return (type, ranks) -> ranks.size() <= 2 && ranks.entrySet().stream()
        .allMatch(e -> e.getKey() >= 52 && e.getKey() <= 53 && e.getValue() >= 1 && e.getValue() <= 13); }
    static WildcardPolicy unconfirmed() { return (type, ranks) -> ranks.isEmpty(); }
}
