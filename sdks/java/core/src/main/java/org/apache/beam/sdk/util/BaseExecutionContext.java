/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.beam.sdk.util;

import org.apache.beam.sdk.coders.Coder;
import org.apache.beam.sdk.transforms.windowing.BoundedWindow;
import org.apache.beam.sdk.util.common.worker.StateSampler;
import org.apache.beam.sdk.util.state.StateInternals;
import org.apache.beam.sdk.values.TupleTag;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Base class for implementations of {@link ExecutionContext}.
 *
 * <p>A concrete subclass should implement {@link #createStepContext} to create the appropriate
 * {@link StepContext} implementation. Any {@code StepContext} created will
 * be cached for the lifetime of this {@link ExecutionContext}.
 *
 * <p>BaseExecutionContext is generic to allow implementing subclasses to return a concrete subclass
 * of {@link StepContext} from {@link #getOrCreateStepContext(String, String, StateSampler)} and
 * {@link #getAllStepContexts()} without forcing each subclass to override the method, e.g.
 * <pre>
 * @Override
 * StreamingModeExecutionContext.StepContext getOrCreateStepContext(...) {
 *   return (StreamingModeExecutionContext.StepContext) super.getOrCreateStepContext(...);
 * }
 * </pre>
 *
 * <p>When a subclass of {@code BaseExecutionContext} has been downcast, the return types of
 * {@link #createStepContext(String, String, StateSampler)},
 * {@link #getOrCreateStepContext(String, String, StateSampler}, and {@link #getAllStepContexts()}
 * will be appropriately specialized.
 */
public abstract class BaseExecutionContext<T extends ExecutionContext.StepContext>
    implements ExecutionContext {

  private Map<String, T> cachedStepContexts = new HashMap<>();

  /**
   * Implementations should override this to create the specific type
   * of {@link StepContext} they need.
   */
  protected abstract T createStepContext(
      String stepName, String transformName, StateSampler stateSampler);


  /**
   * Returns the {@link StepContext} associated with the given step.
   */
  @Override
  public T getOrCreateStepContext(
      String stepName, String transformName, StateSampler stateSampler) {
    T context = cachedStepContexts.get(stepName);
    if (context == null) {
      context = createStepContext(stepName, transformName, stateSampler);
      cachedStepContexts.put(stepName, context);
    }
    return context;
  }

  /**
   * Returns a collection view of all of the {@link StepContext}s.
   */
  @Override
  public Collection<? extends T> getAllStepContexts() {
    return Collections.unmodifiableCollection(cachedStepContexts.values());
  }

  /**
   * Hook for subclasses to implement that will be called whenever
   * {@link org.apache.beam.sdk.transforms.DoFn.Context#output}
   * is called.
   */
  @Override
  public void noteOutput(WindowedValue<?> output) {}

  /**
   * Hook for subclasses to implement that will be called whenever
   * {@link org.apache.beam.sdk.transforms.DoFn.Context#sideOutput}
   * is called.
   */
  @Override
  public void noteSideOutput(TupleTag<?> tag, WindowedValue<?> output) {}

  /**
   * Base class for implementations of {@link ExecutionContext.StepContext}.
   *
   * <p>To complete a concrete subclass, implement {@link #timerInternals} and
   * {@link #stateInternals}.
   */
  public abstract static class StepContext implements ExecutionContext.StepContext {
    private final ExecutionContext executionContext;
    private final String stepName;
    private final String transformName;

    public StepContext(ExecutionContext executionContext, String stepName, String transformName) {
      this.executionContext = executionContext;
      this.stepName = stepName;
      this.transformName = transformName;
    }

    @Override
    public String getStepName() {
      return stepName;
    }

    @Override
    public String getTransformName() {
      return transformName;
    }

    @Override
    public void noteOutput(WindowedValue<?> output) {
      executionContext.noteOutput(output);
    }

    @Override
    public void noteSideOutput(TupleTag<?> tag, WindowedValue<?> output) {
      executionContext.noteSideOutput(tag, output);
    }

    @Override
    public <T, W extends BoundedWindow> void writePCollectionViewData(
        TupleTag<?> tag,
        Iterable<WindowedValue<T>> data, Coder<Iterable<WindowedValue<T>>> dataCoder,
        W window, Coder<W> windowCoder) throws IOException {
      throw new UnsupportedOperationException("Not implemented.");
    }

    @Override
    public abstract StateInternals<?> stateInternals();

    @Override
    public abstract TimerInternals timerInternals();
  }
}
