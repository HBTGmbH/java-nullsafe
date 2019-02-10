package de.hbt.nullsafe;

import static de.hbt.nullsafe.Nullsafe.*;

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.*;
import org.openjdk.jmh.runner.options.*;

@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 4, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class Bench {

	static class A {
		private volatile B b;
		private volatile boolean isNull;

		A(boolean isNull) {
			this.isNull = isNull;
			this.b = new B(isNull);
		}

		B getB() {
			return isNull ? null : b;
		}
	}

	static class B {
		private volatile C c = new C();
		private volatile boolean isNull;

		B(boolean isNull) {
			this.isNull = isNull;
		}

		C getC() {
			return isNull ? null : c;
		}
	}

	static class C {
		String getString() {
			return "";
		}
	}

	private volatile A nullA = new A(System.currentTimeMillis() > 1000);
	private volatile A a = new A(System.currentTimeMillis() < 1000);

	@Benchmark
	public String normalNonNull() {
		return a.getB().getC().getString();
	}

	@Benchmark
	public String nullsafeNonNull() {
		return __nullsafe(a.getB().getC().getString());
	}

	@Benchmark
	public String nullsafeNull() {
		return __nullsafe(nullA.getB().getC().getString());
	}

	public static void main(String[] args) throws RunnerException {
		boolean withTrace = false;
		ChainedOptionsBuilder opt = new OptionsBuilder()
				.include(Bench.class.getSimpleName())
				.forks(1);
		if (withTrace) {
			opt.jvmArgsAppend(
					"-XX:+UnlockDiagnosticVMOptions",
					"-XX:+TraceClassLoading",
					"-XX:+LogCompilation",
					"-XX:+PrintAssembly");
		}
		new Runner(opt.build()).run();
	}
}
