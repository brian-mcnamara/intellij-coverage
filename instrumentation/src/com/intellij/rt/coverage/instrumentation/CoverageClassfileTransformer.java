/*
 * Copyright 2000-2018 JetBrains s.r.o.
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

package com.intellij.rt.coverage.instrumentation;

import com.intellij.rt.coverage.data.ProjectData;
import com.intellij.rt.coverage.instrumentation.filters.FilterUtils;
import com.intellij.rt.coverage.instrumentation.filters.classFilter.PrivateConstructorOfUtilClassFilter;
import com.intellij.rt.coverage.instrumentation.filters.classSignature.ClassSignatureFilter;
import com.intellij.rt.coverage.instrumentation.testTracking.TestTrackingMode;
import com.intellij.rt.coverage.util.ClassNameUtil;
import com.intellij.rt.coverage.util.OptionsUtil;
import com.intellij.rt.coverage.util.classFinder.ClassFinder;
import org.jetbrains.coverage.org.objectweb.asm.ClassReader;
import org.jetbrains.coverage.org.objectweb.asm.ClassVisitor;
import org.jetbrains.coverage.org.objectweb.asm.Opcodes;

import java.util.List;
import java.util.regex.Pattern;

public class CoverageClassfileTransformer extends AbstractIntellijClassfileTransformer {
  private static final List<ClassSignatureFilter> ourFilters = FilterUtils.createClassSignatureFilters();

  private final ProjectData data;
  private final boolean shouldCalculateSource;
  private final List<Pattern> excludePatterns;
  private final List<Pattern> includePatterns;
  private final ClassFinder cf;
  private final TestTrackingMode testTrackingMode;

  public CoverageClassfileTransformer(ProjectData data, boolean shouldCalculateSource, List<Pattern> excludePatterns, List<Pattern> includePatterns, ClassFinder cf) {
    this(data, shouldCalculateSource, excludePatterns, includePatterns, cf, null);
  }

  public CoverageClassfileTransformer(ProjectData data, boolean shouldCalculateSource, List<Pattern> excludePatterns, List<Pattern> includePatterns, ClassFinder cf, TestTrackingMode testTrackingMode) {
    this.data = data;
    this.shouldCalculateSource = shouldCalculateSource;
    this.excludePatterns = excludePatterns;
    this.includePatterns = includePatterns;
    this.cf = cf;
    this.testTrackingMode = testTrackingMode;
  }

  @Override
  protected ClassVisitor createClassVisitor(String className, ClassLoader loader, ClassReader cr, ClassVisitor cw) {
    return createInstrumenter(data, className, cr, cw, testTrackingMode, data.isSampling(),
        shouldCalculateSource, OptionsUtil.IGNORE_PRIVATE_CONSTRUCTOR_OF_UTIL_CLASS);
  }

  /**
   * Create instrumenter for class or return null if class should be ignored.
   */
  static ClassVisitor createInstrumenter(ProjectData data, String className,
                                         ClassReader cr, ClassVisitor cw, TestTrackingMode testTrackingMode,
                                         boolean isSampling,
                                         boolean shouldCalculateSource,
                                         boolean shouldIgnorePrivateConstructorOfUtilCLass) {
    for (ClassSignatureFilter filter : ourFilters) {
      if (filter.shouldFilter(cr)) return null;
    }
    final Instrumenter instrumenter;
    if (isSampling) {
      if (OptionsUtil.NEW_SAMPLING_ENABLED) {
        if (OptionsUtil.CONDY_ENABLED && InstrumentationUtils.getBytecodeVersion(cr) >= Opcodes.V11) {
          instrumenter = new CondySamplingInstrumenter(data, cw, className, shouldCalculateSource);
        } else {
          //wrap cw with new TraceClassVisitor(cw, new PrintWriter(new StringWriter())) to get readable bytecode
          instrumenter = new NewSamplingInstrumenter(data, cw, cr, className, shouldCalculateSource);
        }
      } else {
        instrumenter = new SamplingInstrumenter(data, cw, className, shouldCalculateSource);
      }
    } else {
      if (OptionsUtil.NEW_TRACING_ENABLED) {
        if (data.isTestTracking() && testTrackingMode != null) {
          instrumenter = testTrackingMode.createInstrumenter(data, cw, cr, className, shouldCalculateSource);
        } else {
          if (OptionsUtil.CONDY_ENABLED && InstrumentationUtils.getBytecodeVersion(cr) >= Opcodes.V11) {
            instrumenter = new CondyTracingInstrumenter(data, cw, className, shouldCalculateSource);
          } else {
            instrumenter = new NewTracingInstrumenter(data, cw, cr, className, shouldCalculateSource);
          }
        }
      } else {
        instrumenter = new TracingInstrumenter(data, cw, className, shouldCalculateSource);
      }
    }
    ClassVisitor result = instrumenter;
    if (shouldIgnorePrivateConstructorOfUtilCLass) {
      result = PrivateConstructorOfUtilClassFilter.createWithContext(result, instrumenter);
    }
    return result;
  }

  @Override
  protected boolean shouldExclude(String className) {
    return ClassNameUtil.matchesPatterns(className, excludePatterns);
  }

  @Override
  protected InclusionPattern getInclusionPattern() {
    return includePatterns.isEmpty() ? null : new InclusionPattern() {
      public boolean accept(String className) {
        return ClassNameUtil.matchesPatterns(className, includePatterns);
      }
    };
  }

  @Override
  protected void visitClassLoader(ClassLoader classLoader) {
    cf.addClassLoader(classLoader);
  }

  @Override
  protected boolean isStopped() {
    return data.isStopped();
  }
}
