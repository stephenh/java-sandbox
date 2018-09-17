package sandbox;

import io.engagingspaces.vertx.dataloader.BatchLoader;
import io.engagingspaces.vertx.dataloader.DataLoader;
import io.engagingspaces.vertx.dataloader.Dispatcher;
import io.trane.future.Promise;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.trane.future.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class Foo {

  public static void main(String[] args) {
    var userBatchLoader = new BatchLoader<Long, User>() {
      @Override public Future<List<User>> load(Collection<Long> userIds) {
        Promise<List<User>> future = Promise.apply();
        Vertx.currentContext().runOnContext((e) -> {
          System.out.println("Loading users " + userIds);
          future.setValue(VavrUtils.Seq(userIds).map(User::new).toJavaList());
        });
        return future;
      }
    };

    var userAccountBatchLoader = new BatchLoader<Long, List<Account>>() {
      @Override public Future<List<List<Account>>> load(Collection<Long> userIds) {
        Promise<List<List<Account>>> future = Promise.apply();
        Vertx.currentContext().runOnContext((e) -> {
          System.out.println("Loading accounts for " + userIds);
          future.setValue(VavrUtils.Seq(userIds).map(userId -> List.of(new Account(userId), new Account(userId))).toJavaList());
        });
        return future;
      }
    };

    var vertx = Vertx.vertx();
    var server = vertx.createHttpServer();
    var client = WebClient.create(vertx);

    server.requestHandler(request -> {

      var dispatchState = new Dispatcher();

      var userLoader = new DataLoader<>(userBatchLoader) {
        public Future<User> load(Long key) {
          dispatchState.queue(this, request);
          return super.load(key);
        }
      };
      var userAccountLoader = new DataLoader<>(userAccountBatchLoader) {
        public Future<List<Account>> load(Long key) {
          dispatchState.queue(this, request);
          return super.load(key);
        }
      };

      var response = request.response();
      response.putHeader("content-type", "text/plain");

      var f1 = userLoader.load(1L).flatMap(user -> {
        return userAccountLoader.load(user.id);
      });
      var f2 = userLoader.load(2L).flatMap(user -> {
        return userAccountLoader.load(user.id);
      });
      var f3 = join(f1, f2).map(r -> {
        return VavrUtils.Seq(r._1).appendAll(r._2).toJavaList();
      });
      f3.map(accounts -> {
        response.end("Accounts5 " + accounts.size());
        return null;
      });

      /*
      client.get(80, "www.google.com", "/").send(ar -> {
        if (ar.succeeded()) {
          var buffer = ar.result();
          response.end("Worked " + buffer.bodyAsString().length());
        } else {
          response.end("Failed " + ar.cause().getMessage());
        }
      });
      */

    });
    server.listen(8080);
  }

  public static class User {
    private final long id;

    public User(long id) {
      this.id = id;
    }        var a1 = f1.result();
        var a2 = f2.result();
  }

  public static class Account {
    private final long id;

    public Account(long id) {
      this.id = id;
    }
  }

  private static <T1, T2> Future<Tuple2<T1, T2>> join(Future<T1> f1, Future<T2> f2) {
    return Future.collect(Arrays.asList((Future<Object>) f1, (Future<Object>) f2)).map(list -> Tuple.of((T1) list.get(0), (T2) list.get(1)));
  }

}

