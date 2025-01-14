/*
 * Copyright 2019 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package brave.secondary_sampling;

import brave.propagation.Propagation.Setter;
import brave.propagation.TraceContext;
import brave.propagation.TraceContext.Injector;
import brave.secondary_sampling.SecondarySampling.Extra;
import java.util.StringJoiner;

/**
 * This writes the {@link SecondarySampling#fieldName sampling header}, with an updated {@code
 * spanId} parameters for each sampled key. The Zipkin endpoint can use that span ID to correct the
 * parent hierarchy.
 */
final class SecondarySamplingInjector<C, K> implements Injector<C> {
  final Injector<C> delegate;
  final Setter<C, K> setter;
  final K samplingKey;

  SecondarySamplingInjector(SecondarySampling.Propagation<K> propagation, Setter<C, K> setter) {
    this.delegate = propagation.delegate.injector(setter);
    this.setter = setter;
    this.samplingKey = propagation.samplingKey;
  }

  @Override public void inject(TraceContext traceContext, C carrier) {
    delegate.inject(traceContext, carrier);
    Extra extra = traceContext.findExtra(Extra.class);
    if (extra == null || extra.isEmpty()) return;
    setter.put(carrier, samplingKey, serializeWithSpanId(extra, traceContext.spanIdString()));
  }

  static String serializeWithSpanId(Extra extra, String spanId) {
    StringJoiner joiner = new StringJoiner(",");
    extra.forEach((state, sampled) -> joiner.merge(serializeWithSpanId(state, sampled, spanId)));
    return joiner.toString();
  }

  static StringJoiner serializeWithSpanId(SecondarySamplingState state, boolean sampled,
    String spanId) {
    StringJoiner joiner = new StringJoiner(";");
    joiner.add(state.samplingKey());

    String upstreamSpanId = state.parameter("spanId");
    state.forEachParameter((key, value) -> {
      if (!"spanId".equals(key)) joiner.add(key + "=" + value);
    });

    if (sampled) {
      joiner.add("spanId=" + spanId);
    } else if (spanId != null) { // pass through the upstream span ID
      joiner.add("spanId=" + upstreamSpanId);
    }

    return joiner;
  }
}
