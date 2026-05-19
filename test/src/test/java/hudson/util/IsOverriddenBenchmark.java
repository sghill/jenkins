package hudson.util;

import hudson.Util;
import hudson.model.Queue;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import jenkins.benchmark.jmh.JmhBenchmark;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Benchmarks {@link Util#isOverridden} as called by {@link Queue.Task#getDefaultAuthentication2()}
 * and {@link Queue.Task#getDefaultAuthentication2(Queue.Item)}.
 *
 * <p>Models the scenario of 1000 freestyle projects in the queue. Each {@code maintain()} cycle
 * calls both {@code isOverridden} overloads on every task. The class hierarchy depth matches
 * {@code FreeStyleProject}: three concrete levels above the {@code Queue.Task} interface
 * ({@code FreeStyleProject → Project → AbstractProject}), with interface scanning at each level.
 *
 * <p>Run via {@link IsOverriddenBenchmarkTest}.
 */
@JmhBenchmark
public class IsOverriddenBenchmark {

    // ---------------------------------------------------------------------------
    // Class hierarchy mirroring FreeStyleProject's depth above Queue.Task
    //
    // Real chain: FreeStyleProject → Project → AbstractProject → Job → ...
    // getMethod walks derived classes until the superclass no longer implements
    // Queue.Task. AbstractProject is the deepest class that does, so the walk
    // covers three levels: FreeStyleProject, Project, AbstractProject.
    //
    // Each level also triggers interface scanning before recursing to the
    // superclass, matching the real per-level cost.
    // ---------------------------------------------------------------------------

    /** Mirrors AbstractProject: deepest class still assignable to Queue.Task. */
    static abstract class Level3Task implements Queue.Task {
        @Override public String getDisplayName() { return "task"; }
        @Override public String getName() { return "task"; }
        @Override public Queue.Executable createExecutable() throws IOException { return null; }
    }

    /** Mirrors Project: one level above AbstractProject. */
    static abstract class Level2Task extends Level3Task {}

    /**
     * Mirrors FreeStyleProject: the concrete leaf class. Does not override either
     * deprecated {@code getDefaultAuthentication} overload — the common case.
     */
    static class ModernTask extends Level2Task {}

    /**
     * Mirrors a legacy FreeStyleProject subclass that overrides the no-arg deprecated
     * {@code getDefaultAuthentication()}, simulating a plugin compiled against an older Jenkins.
     */
    @SuppressWarnings("deprecation")
    static class LegacyNoArgTask extends Level2Task {
        @Override
        public org.acegisecurity.Authentication getDefaultAuthentication() {
            return org.acegisecurity.Authentication.fromSpring(hudson.security.ACL.SYSTEM2);
        }
    }

    /**
     * Mirrors a legacy FreeStyleProject subclass that overrides the item-arg deprecated
     * {@code getDefaultAuthentication(Queue.Item)}.
     */
    @SuppressWarnings("deprecation")
    static class LegacyItemArgTask extends Level2Task {
        @Override
        public org.acegisecurity.Authentication getDefaultAuthentication(Queue.Item item) {
            return org.acegisecurity.Authentication.fromSpring(hudson.security.ACL.SYSTEM2);
        }
    }

    // ---------------------------------------------------------------------------
    // JMH state — plain @State, no Jenkins instance required
    // ---------------------------------------------------------------------------

    @State(Scope.Benchmark)
    public static class BenchmarkState {

        @Param({"1000"})
        public int taskCount;

        List<Queue.Task> modernTasks;
        List<Queue.Task> legacyNoArgTasks;
        List<Queue.Task> legacyItemArgTasks;

        @Setup
        public void setup() {
            modernTasks = new ArrayList<>(taskCount);
            legacyNoArgTasks = new ArrayList<>(taskCount);
            legacyItemArgTasks = new ArrayList<>(taskCount);
            for (int i = 0; i < taskCount; i++) {
                modernTasks.add(new ModernTask());
                legacyNoArgTasks.add(new LegacyNoArgTask());
                legacyItemArgTasks.add(new LegacyItemArgTask());
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Benchmarks — iterate over all tasks as maintain() does
    // ---------------------------------------------------------------------------

    /**
     * Common case: 1000 modern tasks, no override of either deprecated method.
     *
     * <p>This is the dominant hot path in {@code maintain()} with a queue full of freestyle
     * projects. Both calls return {@code false}. Without caching, each task requires a
     * three-level superclass walk plus interface scanning per call. With caching, each
     * task after the first is a single {@code ConcurrentHashMap} lookup.
     */
    @Benchmark
    public void noOverride(BenchmarkState state, Blackhole bh) {
        for (Queue.Task task : state.modernTasks) {
            Class<?> cls = task.getClass();
            bh.consume(Util.isOverridden(Queue.Task.class, cls, "getDefaultAuthentication"));
            bh.consume(Util.isOverridden(Queue.Task.class, cls, "getDefaultAuthentication", Queue.Item.class));
        }
    }

    /**
     * Legacy case: 1000 tasks that override the no-arg deprecated method.
     *
     * <p>First call returns {@code true} (found at {@code Level2Task}), second returns
     * {@code false}. Models a queue full of plugins compiled against an older Jenkins.
     */
    @Benchmark
    public void overrideNoArg(BenchmarkState state, Blackhole bh) {
        for (Queue.Task task : state.legacyNoArgTasks) {
            Class<?> cls = task.getClass();
            bh.consume(Util.isOverridden(Queue.Task.class, cls, "getDefaultAuthentication"));
            bh.consume(Util.isOverridden(Queue.Task.class, cls, "getDefaultAuthentication", Queue.Item.class));
        }
    }

    /**
     * Legacy case: 1000 tasks that override the item-arg deprecated method.
     *
     * <p>First call returns {@code false}, second returns {@code true}.
     */
    @Benchmark
    public void overrideItemArg(BenchmarkState state, Blackhole bh) {
        for (Queue.Task task : state.legacyItemArgTasks) {
            Class<?> cls = task.getClass();
            bh.consume(Util.isOverridden(Queue.Task.class, cls, "getDefaultAuthentication"));
            bh.consume(Util.isOverridden(Queue.Task.class, cls, "getDefaultAuthentication", Queue.Item.class));
        }
    }
}
