/*
 * Copyright 2000-2020 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.rt.coverage.instrumentation.filters;

import com.intellij.rt.coverage.instrumentation.InstrumentationUtils;
import com.intellij.rt.coverage.instrumentation.Instrumenter;
import com.intellij.rt.coverage.instrumentation.kotlin.KotlinUtils;
import org.jetbrains.coverage.org.objectweb.asm.*;

/**
 * Filter out generated Kotlin coroutines state machines.
 * Namely, TABLESWITCH on state and 'if suspend' checks are ignored.
 */
public abstract class KotlinCoroutinesFilter extends MethodVisitor {
  private boolean myGetCoroutinesSuspendedVisited = false;
  private boolean myStoreCoroutinesSuspendedVisited = false;
  private boolean myLoadCoroutinesSuspendedVisited = false;
  private boolean myLoadStateLabelVisited = false;
  private boolean mySuspendCallVisited = false;
  private int myCoroutinesSuspendedIndex = -1;
  private int myLine = -1;
  private boolean myHadLineDataBefore = false;
  private final Instrumenter myContext;

  public KotlinCoroutinesFilter(MethodVisitor methodVisitor, Instrumenter instrumenter) {
    super(Opcodes.API_VERSION, methodVisitor);
    myContext = instrumenter;
  }


  protected abstract void onIgnoredJump();

  protected abstract void onIgnoredSwitch(Label dflt, Label... labels);

  protected void onIgnoredLine(int line) {
    myContext.removeLine(line);
  }

  /**
   * This filter is applicable for suspend methods of Kotlin class.
   * It could be a suspend lambda, then it's name is 'invokeSuspend'.
   * Or it could be a suspend method, then it's last parameter is a Continuation.
   */
  public static boolean isApplicable(Instrumenter context, String name, String desc) {
    return KotlinUtils.isKotlinClass(context)
        && (name.equals("invokeSuspend") || desc.endsWith("Lkotlin/coroutines/Continuation;)" + InstrumentationUtils.OBJECT_TYPE));
  }

  @Override
  public void visitLineNumber(int line, Label start) {
    myHadLineDataBefore = myContext.getLineData(line) != null;
    super.visitLineNumber(line, start);
    myLine = line;
  }

  @Override
  public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
    super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);

    boolean getCoroutinesSuspendedVisited = opcode == Opcodes.INVOKESTATIC
        && owner.equals("kotlin/coroutines/intrinsics/IntrinsicsKt")
        && name.equals("getCOROUTINE_SUSPENDED")
        && descriptor.equals("()" + InstrumentationUtils.OBJECT_TYPE);
    boolean suspendCallVisited = descriptor.endsWith("Lkotlin/coroutines/Continuation;)" + InstrumentationUtils.OBJECT_TYPE)
        || owner.startsWith("kotlin/jvm/functions/Function")
        && name.equals("invoke") && opcode == Opcodes.INVOKEINTERFACE
        && descriptor.endsWith(")" + InstrumentationUtils.OBJECT_TYPE);
    if (getCoroutinesSuspendedVisited || suspendCallVisited) {
      myGetCoroutinesSuspendedVisited |= getCoroutinesSuspendedVisited;
      mySuspendCallVisited |= suspendCallVisited;
    } else {
      myGetCoroutinesSuspendedVisited = false;
      mySuspendCallVisited = false;
    }
  }

  @Override
  public void visitVarInsn(int opcode, int var) {
    super.visitVarInsn(opcode, var);
    if (!myStoreCoroutinesSuspendedVisited && myGetCoroutinesSuspendedVisited && opcode == Opcodes.ASTORE) {
      myStoreCoroutinesSuspendedVisited = true;
      myCoroutinesSuspendedIndex = var;
    }
    myLoadCoroutinesSuspendedVisited = myStoreCoroutinesSuspendedVisited
        && opcode == Opcodes.ALOAD
        && var == myCoroutinesSuspendedIndex;
  }

  /**
   * Ignore 'if' if it compares with 'COROUTINE_SUSPENDED' object.
   * Comparison could be with stored variable or with just returned from 'getCOROUTINE_SUSPENDED' value.
   */
  @Override
  public void visitJumpInsn(int opcode, Label label) {
    super.visitJumpInsn(opcode, label);
    boolean compareWithCoroutinesSuspend = myLoadCoroutinesSuspendedVisited || myGetCoroutinesSuspendedVisited;
    if (compareWithCoroutinesSuspend && mySuspendCallVisited && opcode == Opcodes.IF_ACMPNE) {
      onIgnoredJump();
      mySuspendCallVisited = false;
    }
    myLoadCoroutinesSuspendedVisited = false;
  }

  @Override
  public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
    super.visitFieldInsn(opcode, owner, name, descriptor);
    final boolean labelVisited = name.equals("label")
        && descriptor.equals("I")
        && Type.getObjectType(owner).getClassName().startsWith(myContext.getClassName());
    myLoadStateLabelVisited = labelVisited && opcode == Opcodes.GETFIELD;
  }

  /**
   * Ignore TABLESWITCH on coroutine state, which is stored in 'label' field.
   */
  @Override
  public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
    super.visitTableSwitchInsn(min, max, dflt, labels);
    if (myLoadStateLabelVisited) {
      onIgnoredSwitch(dflt, labels);
      if (!myHadLineDataBefore) {
        onIgnoredLine(myLine);
      }
    }
    myLoadStateLabelVisited = false;
  }

  @Override
  public void visitInsn(int opcode) {
    super.visitInsn(opcode);
    // ignore generated return on the first line
    if (opcode == Opcodes.ARETURN && myLoadCoroutinesSuspendedVisited && !myHadLineDataBefore) {
      onIgnoredLine(myLine);
    }
  }
}
