package de.hbt.nullsafe;

import static de.hbt.nullsafe.Nullsafe.*;
import static org.junit.Assert.*;

import java.util.*;

import org.junit.*;

public class NullsafeIT {
	static class A {
		B nullB;
		B b = new B();

		B getNullB() {
			return null;
		}

		B getB() {
			return b;
		}
	}

	static class B {
		C c = new C();

		C getNullC() {
			return null;
		}

		C getC() {
			return c;
		}
	}

	static class C {
		String getNullName() {
			return null;
		}

		String getName() {
			return "C";
		}
	}

	private A nullA;
	private A a = new A();

	@Test
	public void testNullObject() {
		assertNull(__nullsafe((Object) null));
	}

	@Test
	public void testNullA() {
		assertNull(__nullsafe((A) null));
	}

	@Test
	public void testNullAgetNullB() {
		assertNull(__nullsafe(((A) null).getNullB()));
	}

	@Test
	public void testNullAnullBfield() {
		assertNull(__nullsafe(((A) null).nullB));
	}

	@Test
	public void testNullAlocalGetNullB() {
		A nullA = Math.sqrt(9) == 3.0 ? null : new A();
		assertNull(__nullsafe(nullA.getNullB()));
	}

	@Test
	public void testAlocalGetNullB() {
		A a = Math.sqrt(9) != 3.0 ? null : new A();
		assertNull(__nullsafe(a.getNullB()));
	}

	@Test
	public void testAlocalGetB() {
		A a = Math.sqrt(9) != 3.0 ? null : new A();
		assertNotNull(__nullsafe(a.getB()));
	}

	@Test
	public void testNullAlocalGetNullCmustThrowNPE() {
		A nullA = Math.sqrt(9) == 3.0 ? null : new A();
		B nullB = null;
		try {
			nullB = nullA.getB();
		} catch (NullPointerException e) {
			/* Expected! */
		}
		assertNull(__nullsafe(nullB.getNullC()));
	}

	@Test
	public void testNullAFieldGetNullB() {
		assertNull(__nullsafe(nullA.getNullB()));
	}

	@Test
	public void testAFieldGetNullB() {
		assertNull(__nullsafe(a.getNullB()));
	}

	@Test
	public void testAFieldGetB() {
		assertNotNull(__nullsafe(a.getB()));
	}

	@Test
	public void testNullAgetB() {
		assertNull(__nullsafe(((A) null).getB()));
	}

	@Test
	public void testAgetNullB() {
		assertNull(__nullsafe((new A()).getNullB()));
	}

	@Test
	public void testNullAgetBgetC() {
		assertNull(__nullsafe(((A) null).getB().getC()));
	}

	@Test
	public void testAgetB() {
		assertNotNull(__nullsafe((new A()).getB()));
	}

	@Test
	public void testAgetBgetNullC() {
		assertNull(__nullsafe((new A()).getB().getNullC()));
	}

	@Test
	public void testAgetBgetC() {
		assertNotNull(__nullsafe((new A()).getB().getC()));
	}

	@Test
	public void testAgetBgetCgetNullName() {
		assertNull(__nullsafe((new A()).getB().getC().getNullName()));
	}

	@Test
	public void testAgetBgetCgetName() {
		assertEquals("C", __nullsafe((new A()).getB().getC().getName()));
	}

	@Test
	public void testNullAgetBgetCgetName() {
		assertNull(__nullsafe(((A) null).getB().getC().getName()));
	}

	@Test
	public void testNullStringListGet() {
		assertNull(__nullsafe(((List<String>) null).get(0)));
	}

	@Test
	public void testNullListSize() {
		assertNull(__nullsafe(((List<String>) null).size()));
	}

	@Test
	public void testStringListGet() {
		assertNotNull(__nullsafe(Collections.singletonList("Hello").get(0)));
	}

	@Test
	public void testStaticMethodCallWithNullA() {
		assertNull(withNullA((A) null));
	}

	@Test
	public void testStaticMethodCallWithNullAfield() {
		assertNull(withNullA(nullA));
	}

	private static String withNullA(A nullA) {
		return __nullsafe(nullA.getB().getC().getName());
	}

	@Test
	public void testMethodCallWithNullAfield() {
		assertNull(__nullsafe(justReturnArg(nullA).getB().getC().getNullName()));
	}

	private A justReturnArg(A nullA) {
		return nullA;
	}

	@Test
	public void testMethodWithMultipleNullsafe() {
		assertNull(__nullsafe((Object) null));
		assertNull(__nullsafe(((List<String>) null).get(0)));
		assertNull(__nullsafe(((A) null).getB().getC().getName()));
	}

	@Test
	@SuppressWarnings("null")
	public void testComplexMethod() {
		A a = nullA;
		B b;
		if (a != null) {
			b = a.getNullB();
		} else {
			b = __nullsafe(a.getNullB());
		}
		assertNull(b);
	}

}
