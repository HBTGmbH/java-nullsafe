package de.hbt.nullsafe;

import static org.objectweb.asm.Opcodes.*;

import java.io.*;
import java.lang.instrument.*;
import java.security.*;
import java.util.*;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.util.*;

class Agent implements ClassFileTransformer {

	private static final String Nullsafe_name = Type.getInternalName(Nullsafe.class);
	private static final String __nullsafe = "__nullsafe";

	private final boolean withTrace;

	Agent(boolean withTrace) {
		super();
		this.withTrace = withTrace;
	}

	static class DefUseBasicValue extends BasicValue {
		private final List<? extends BasicValue> values;
		private final AbstractInsnNode node;
		private final boolean needsNullCheck;
		private final boolean canProduceNull;

		DefUseBasicValue(AbstractInsnNode node, BasicValue delegate, List<? extends BasicValue> values,
				boolean needsNullCheck, boolean canProduceNull) {
			super(delegate.getType());
			this.values = values;
			this.node = node;
			this.needsNullCheck = needsNullCheck;
			this.canProduceNull = canProduceNull;
		}

		private void makeNullSafe(MethodNode mn, LabelNode label) {
			if (needsNullCheck) {
				DefUseBasicValue firstValue = (DefUseBasicValue) values.get(0);
				if (firstValue.canProduceNull) {
					mn.instructions.insert(firstValue.node, new JumpInsnNode(IFNULL, label));
					mn.instructions.insert(firstValue.node, new InsnNode(DUP));
				}
			}
			processPrevious(mn, label);
		}

		void makeNullSafe(MethodNode mn) {
			LabelNode label = new LabelNode();
			mn.instructions.insertBefore(node, label);
			processPrevious(mn, label);
			mn.instructions.remove(node);
		}

		private void processPrevious(MethodNode mn, LabelNode label) {
			BasicValue prev = values != null && values.size() >= 1 ? values.get(0) : null;
			if (prev instanceof DefUseBasicValue) {
				DefUseBasicValue mc = (DefUseBasicValue) prev;
				mc.makeNullSafe(mn, label);
			}
		}

		boolean isNullsafeCall() {
			if (!(node instanceof MethodInsnNode))
				return false;
			MethodInsnNode min = (MethodInsnNode) node;
			return __nullsafe.equals(min.name) && Nullsafe_name.equals(min.owner);
		}
	}

	static class DefUseAnalyzer extends Analyzer<BasicValue> {
		DefUseAnalyzer(boolean instanceMethod) {
			super(new DefUseInterpreter(instanceMethod));
		}
	}

	static class DefUseInterpreter extends BasicInterpreter {
		private final boolean instanceMethod;

		DefUseInterpreter(boolean instanceMethod) {
			super(ASM7);
			this.instanceMethod = instanceMethod;
		}

		private static BasicValue produce(AbstractInsnNode insn, BasicValue delegate, List<? extends BasicValue> values,
				boolean needsNullCheck, boolean canProduceNull) {
			if (delegate == null)
				return null;
			return new DefUseBasicValue(insn, delegate, values, needsNullCheck, canProduceNull);
		}

		@Override
		public BasicValue naryOperation(AbstractInsnNode insn, List<? extends BasicValue> values)
				throws AnalyzerException {
			return produce(insn, super.naryOperation(insn, values), values, true, insn instanceof MethodInsnNode
					&& typeIsReference(Type.getReturnType(((MethodInsnNode) insn).desc)));
		}

		@Override
		public BasicValue unaryOperation(AbstractInsnNode insn, BasicValue value) throws AnalyzerException {
			return produce(insn, super.unaryOperation(insn, value), Collections.singletonList(value),
					insn.getOpcode() == GETFIELD, true);
		}

		@Override
		public BasicValue newOperation(AbstractInsnNode insn) throws AnalyzerException {
			return produce(insn, super.newOperation(insn), Collections.<BasicValue>emptyList(), false,
					insn.getOpcode() == ACONST_NULL || (insn.getOpcode() == GETSTATIC
							&& typeIsReference(Type.getType(((FieldInsnNode) insn).desc))));
		}

		private static boolean typeIsReference(Type t) {
			return t.getSort() == Type.OBJECT || t.getSort() == Type.ARRAY;
		}

		@Override
		public BasicValue copyOperation(AbstractInsnNode insn, BasicValue value) throws AnalyzerException {
			/*
			 * Use empty source value to not leak null-checks out of the __nullsafe(...)
			 * call!
			 */
			return produce(insn, super.copyOperation(insn, value), Collections.<BasicValue>emptyList(), false,
					insn.getOpcode() == ALOAD && (!instanceMethod || ((VarInsnNode) insn).var != 0));
		}

		@Override
		public BasicValue binaryOperation(AbstractInsnNode insn, BasicValue value1, BasicValue value2)
				throws AnalyzerException {
			return produce(insn, super.binaryOperation(insn, value1, value2), Arrays.asList(value1, value2), false,
					false);
		}
	}

	public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
			ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
		/*
		 * Exclude some classes that are guaranteed not to contain __nullsafe(...) calls
		 * to speed up the transformation process.
		 */
		if (className == null || classfileBuffer == null || className.startsWith("java/")
				|| className.startsWith("javax/") || className.startsWith("com/sun/")
				|| className.startsWith("com/ibm/") || className.startsWith("sun/")
				|| className.startsWith("jdk/internal/") || className.startsWith("org/hibernate/")
				|| className.startsWith("org/apache/"))
			return null;
		try {
			return doTransform(className, classfileBuffer);
		} catch (Throwable t) {
			System.err.println("Exception while transforming class '" + className.replace('/', '.') + "'");
			t.printStackTrace();
			throw new IllegalClassFormatException(t.getMessage());
		}
	}

	private byte[] doTransform(String className, byte[] classfileBuffer) {
		/* Quickly scan for methods that contain __nullsafe(...) calls. */
		ClassReader cr = new ClassReader(classfileBuffer);
		final Set<String> methodsToTransform = new HashSet<String>();
		cr.accept(new ClassVisitor(ASM7) {
			public MethodVisitor visitMethod(int access, final String methodName, final String methodDescriptor,
					String signature, String[] exceptions) {
				return new MethodVisitor(ASM7) {
					public void visitMethodInsn(int opcode, String owner, String name, String descriptor,
							boolean isInterface) {
						if (opcode == INVOKESTATIC && Nullsafe_name.equals(owner) && __nullsafe.equals(name))
							methodsToTransform.add(methodName + methodDescriptor);
					}
				};
			}
		}, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

		/* If the set is empty, then no methods need to be transformed. */
		if (methodsToTransform.isEmpty())
			return null;

		/* Transform the scanned methods */
		return transformMethods(className, cr, methodsToTransform);
	}

	private byte[] transformMethods(final String className, ClassReader cr, final Set<String> methodsToTransform) {
		byte majorVersion = (byte) cr.readByte(7);
		/*
		 * Build ClassWriter based on ClassReader to quickly copy all untransformed
		 * methods and the constant pool.
		 */
		ClassWriter cw = new ClassWriter(cr,
				ClassWriter.COMPUTE_MAXS | (majorVersion >= 51 ? ClassWriter.COMPUTE_FRAMES : 0));
		cr.accept(new ClassVisitor(ASM7, cw) {
			@Override
			public MethodVisitor visitMethod(final int access, String methodName, String methodDescriptor,
					String signature, String[] exceptions) {
				final MethodVisitor original = super.visitMethod(access, methodName, methodDescriptor, signature,
						exceptions);
				/*
				 * If this method should not get transformed, return the original MethodVisitor
				 */
				if (!methodsToTransform.contains(methodName + methodDescriptor))
					return original;
				/* Build a MethodNode whose instructions we later modify */
				final MethodNode mn = new MethodNode(ASM7, access, methodName, methodDescriptor, signature, exceptions);
				return new MethodVisitor(ASM7, mn) {
					/**
					 * visitEnd() is the very last visitor method called. This is where we will
					 * transform the method.
					 */
					public void visitEnd() {
						/* Do the transformation */
						transformMethod(className, mn);
						/* Replay the transformed method into the original MethodVisitor */
						try {
							mn.accept(original);
							original.visitEnd();
						} catch (Exception e) {
							throw new RuntimeException("Failed to replay method instructions", e);
						}
					}

					/**
					 * Finds calls to __nullsafe(...) and injects null-checks in the definition-use
					 * instruction chain.
					 */
					private void transformMethod(String className, MethodNode mn) {
						Analyzer<BasicValue> analyzer = new DefUseAnalyzer((access & ACC_STATIC) == 0);
						Frame<BasicValue>[] frames;
						try {
							frames = analyzer.analyze(className, mn);
						} catch (AnalyzerException e) {
							throw new RuntimeException("Could not analyze method", e);
						}
						List<DefUseBasicValue> nullsafeCalls = new ArrayList<DefUseBasicValue>();
						for (int i = frames.length - 1; i >= 0; i--) {
							Frame<BasicValue> f = frames[i];
							if (f == null || f.getStackSize() == 0)
								continue;
							for (int s = 0; s < f.getStackSize(); s++) {
								BasicValue value = f.getStack(s);
								if (!(value instanceof DefUseBasicValue))
									continue;
								DefUseBasicValue val = (DefUseBasicValue) value;
								if (!val.isNullsafeCall())
									continue;
								nullsafeCalls.add(val);
							}
						}
						for (DefUseBasicValue call : nullsafeCalls)
							call.makeNullSafe(mn);
					}
				};
			}
		}, 0);
		byte[] newDefinition = cw.toByteArray();
		if (withTrace) {
			TraceClassVisitor tcv = new TraceClassVisitor(new PrintWriter(System.out));
			cr = new ClassReader(newDefinition);
			cr.accept(tcv, 0);
		}
		return newDefinition;
	}

	public static void premain(String agentArgs, Instrumentation instrumentation) {
		boolean withTrace = agentArgs != null && agentArgs.contains("t");
		instrumentation.addTransformer(new Agent(withTrace));
	}

}
