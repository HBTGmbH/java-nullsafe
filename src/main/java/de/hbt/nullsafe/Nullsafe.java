package de.hbt.nullsafe;

public class Nullsafe {
	private Nullsafe() {
	}

	/**
	 * Expressions used as argument to a call of this method will not throw a {@link NullPointerException} but only ever
	 * produce <code>null</code> as the result of the whole expression if any intermediate sub-expression produces
	 * <code>null</code> itself. Otherwise, this method will return the non-null result of the expression.
	 * 
	 * @param v an argument expression
	 * @param   <T> the type of the expression
	 * @return the expression's value (if not <code>null</code>) or <code>null</code>
	 */
	public static <T> T __nullsafe(T v) {
		throw new AssertionError("JVM was not started with '-javaagent:nullsafe-1.0.0-SNAPSHOT.jar'");
	}
}
