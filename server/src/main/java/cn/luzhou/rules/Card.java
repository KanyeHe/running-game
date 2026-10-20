package cn.luzhou.rules;

import java.util.*;

/** IDs 0..51 = suit * 13 + rank-1 (A=1, 2=2); 52,53 are jokers. */
public record Card(int id) {
  public static final int SPADE_THREE = 2;

  public Card {
    if (id < 0 || id > 53) throw new IllegalArgumentException("INVALID_CARD");
  }

  public static int strength(int rank) {
    return rank == 1 ? 14 : rank == 2 ? 15 : rank;
  }

  public static List<Card> deck() {
    return java.util.stream.IntStream.range(0, 54).mapToObj(Card::new).toList();
  }

  public boolean wild() {
    return id >= 52;
  }

  public int rank() {
    return wild() ? 0 : id % 13 + 1;
  }

  public int suit() {
    return wild() ? -1 : id / 13;
  }
}
