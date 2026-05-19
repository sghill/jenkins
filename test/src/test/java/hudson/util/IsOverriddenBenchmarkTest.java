package hudson.util;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * Runs {@link IsOverriddenBenchmark} as a test to verify it executes without error.
 *
 * <p>For meaningful performance numbers, increase forks, warmup iterations, and measurement
 * iterations. The defaults here are the minimum needed for CI correctness checks.
 */
class IsOverriddenBenchmarkTest {

    @Test
    void runBenchmark() throws Exception {
        ChainedOptionsBuilder options = new OptionsBuilder()
                .mode(Mode.AverageTime)
                .forks(3)
                .result("jmh-report.json")
                .resultFormat(ResultFormatType.JSON)
                .operationsPerInvocation(1)
                .threads(1)
                .warmupForks(0)
                .warmupIterations(5)
                .measurementBatchSize(1)
                .measurementIterations(10)
                .timeUnit(TimeUnit.NANOSECONDS)
                .shouldFailOnError(true)
                .include(IsOverriddenBenchmark.class.getName() + ".*");
        new Runner(options.build()).run();
        assertTrue(Files.exists(Paths.get("jmh-report.json")));
    }
}
