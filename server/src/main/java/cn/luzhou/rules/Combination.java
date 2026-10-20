package cn.luzhou.rules;

import java.util.*;

public record Combination(
    Type type,
    List<Integer> cardIds,
    Map<Integer, Integer> wildRanks,
    int value,
    int groups,
    int bodyRank,
    String basis) {
  public Combination {
    cardIds = cardIds.stream().sorted().toList();
    wildRanks = Collections.unmodifiableMap(new TreeMap<>(wildRanks));
  }

  public String key() {
    return type + ":" + cardIds + ":" + wildRanks + ":" + value + ":" + groups + ":" + bodyRank;
  }

  public enum Type {
    SINGLE,
    PAIR,
    TRIPLE,
    TRIPLE_SINGLES,
    TRIPLE_PAIR,
    STRAIGHT,
    PAIR_RUN,
    PLANE,
    PLANE_SINGLES,
    PLANE_PAIRS,
    BOMB
  }
}
