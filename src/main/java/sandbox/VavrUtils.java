package sandbox;

import io.vavr.collection.Seq;

public class VavrUtils {
  /** Shortcut since API.SeqOfAll does not exist. */
  public static <T> Seq<T> Seq(Iterable<T> list) {
    return io.vavr.collection.List.ofAll(list);
  }
}
