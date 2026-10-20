package cn.luzhou.rules;

import java.util.*;

/** Boundary and attachment comparison are isolated from ordinary rank strength. */
public record SequencePolicy(List<Integer> order) {
  public SequencePolicy {
    order = List.copyOf(order);
    if (order.size() != new HashSet<>(order).size()) throw new IllegalArgumentException();
  }

  public static SequencePolicy demo() {
    return new SequencePolicy(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13));
  }
}
