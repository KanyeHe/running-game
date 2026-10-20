package cn.luzhou.rules;

import java.util.*;

public final class HintComparator implements Comparator<Combination> {
  private final List<Combination.Type> order;

  public HintComparator(List<Combination.Type> order) {
    this.order = List.copyOf(order);
  }

  public static HintComparator demo() {
    return new HintComparator(List.of(Combination.Type.values()));
  }

  public int compare(Combination a, Combination b) {
    int c = Integer.compare(order.indexOf(a.type()), order.indexOf(b.type()));
    if (c == 0) c = Integer.compare(a.cardIds().size(), b.cardIds().size());
    if (c == 0) c = Integer.compare(a.value(), b.value());
    return c == 0 ? a.key().compareTo(b.key()) : c;
  }
}
