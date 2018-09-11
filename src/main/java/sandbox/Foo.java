package sandbox;

import com.github.threadcontext.Context;
import com.github.threadcontext.PropagatingExecutorService;
import com.github.threadcontext.control.TryFinallyContext;
import com.linkedin.parseq.Engine;
import com.linkedin.parseq.EngineBuilder;
import com.linkedin.parseq.Task;
import com.linkedin.parseq.httpclient.HttpClient;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;

public class Foo {

  private static ThreadLocal<String> requestId = new ThreadLocal<>();

  public static void main(String[] args) throws Exception {
    var scheduler = newScheduler();
    var engine = newEngine(scheduler);
    try {
      run(engine);
    } finally {
      engine.shutdown();
      scheduler.shutdownNow();
    }
  }

  private static ScheduledExecutorService newScheduler() {
    var numCores = Runtime.getRuntime().availableProcessors();
    return Executors.newScheduledThreadPool(numCores + 1);
  }

  private static Engine newEngine(ScheduledExecutorService scheduler) {
    var context = new ThreadLocalContextSupplier<>(requestId);
    var p = new PropagatingExecutorService(scheduler, context);
    var builder = new EngineBuilder().setTaskExecutor(p).setTimerScheduler(scheduler);
    return builder.build();
  }

  private static void run(Engine engine) throws Exception {
    requestId.set("REQUEST123");
    var task = Task.value("first value").flatMap(value -> {
      var get1 = HttpClient.get("http://www.google.com").task().map(response -> {
        System.out.println("REQUEST = " + requestId.get());
        return requestId.get() + " " + response.getStatusCode();
      });
      var get2 = HttpClient.get("http://www.google.com").task().map(response -> {
        System.out.println("REQUEST = " + requestId.get());
        return requestId.get() + " " + response.getStatusCode();
      });
      return Task.par(get1, get2);
    });
    engine.run(task);
    task.await();
    System.out.println("Google Page: " + task.get());
  }

  public static class ThreadLocalContextSupplier<T> implements Supplier<Context> {
    private final ThreadLocal<T> threadLocal;

    public ThreadLocalContextSupplier(ThreadLocal<T> threadLocal) {
      this.threadLocal = threadLocal;
    }

    public Context get() {
      System.out.println("CAPTURE ON THREAD " + Thread.currentThread().toString() + " value is " + threadLocal.get());
      T value = threadLocal.get();
      return new TryFinallyContext(() -> {
        T oldValue = threadLocal.get();
        System.out.println("RESTORE ON THREAD " + Thread.currentThread().toString() + " value is " + value);
        threadLocal.set(value);
        return () -> threadLocal.set(oldValue);
      });
    }

    // Output:
    // CAPTURE ON THREAD Thread[main,5,main] value is REQUEST123
    // RESTORE ON THREAD Thread[pool-1-thread-1,5,main] value is REQUEST123
    // CAPTURE ON THREAD Thread[New I/O worker #1,5,main] value is null
    // RESTORE ON THREAD Thread[pool-1-thread-1,5,main] value is null
    // REQUEST = null
    // REQUEST = null
    // Google Page: (null 200, null 200)
  }
}

