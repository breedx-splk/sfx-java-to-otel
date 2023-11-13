# sfx-java-to-otel

How to migrate from signalfx-java yammer/codahale to OpenTelemetry.

In July 2023, we announced the end of support date for `signalfx-codahale` and
`signalfx-yammer`. These SignalFx libraries originally helped with people using
Dropwizard metrics (also previously called yammer or codahale) to deal with 
and get data into SignalFx. This is no longer a supported option, and Splunk
strongly encourages users to switch to OpenTelemetry for their metrics solutions.
This migration guide is intended to help users understand how they may 
leverage OpenTelemetry Java to replace their usages of `signalfx-codahale`
and `signalfx-yammer` in their projects.

If you encounter a specific scenario that needs clarification or if you are
having trouble migrating to OpenTelemetry metrics from `signalfx-java`, 
please feel free to [open an issue](https://github.com/signalfx/signalfx-java/issues/new).

# At a Glance

Let's start by looking at some of the core concepts and how they might map over
from Dropwizard to OpenTelemetry.

## Metric Types

Dropwizard has the following metric types:

* counter
* gauge
* histogram
* meter
* timer

OpenTelemetry has the following metric types:

* counter
* gauge
* histogram
* up/down counter

Let's look at how each of the various Dropwizard types should be converted to
OpenTelemetry types.

### counter

The Dropwizard counter may be incremented or decremented, and it only handles long values. 
Therefore, it is essentially the same as the OpenTelemetry up/down long counter.

With your otel `meter`, you can call `meter.upDownCounterBuilder()` to get an instance of 
a `LongUpDownCounterBuilder` which you can configure before calling `build()`.

### gauge

The Dropwizard gauge is simply a generically types value that can be measured. There are no 
type constraints around the gauge's generic type, but presumably it's almost always something numeric.
If you are using some nonstandard types within a gauge, the migration may be more complicated.
It is not currently expected that many users have built non-numeric or other complicated compound
gauge types.

The Dropwizard `Gauge.getValue()` interface method is effectively a callback, so this should 
be treated effectively the same as the OpenTelemetry asynchronous gauge. The familiar pattern is
`meter.gaugeBuilder("gaugeName").buildWithCallback(...)`. 

One notable difference is that OpenTelemetry only supports asynchronous long and double gauges.
Integer, short, and other types are not supported. Users of floating point value types (float, double)
should generally use the `DoubleGauge` and users of fixed point types (integer, long) should
use the `LongGauge`. If your migration causes you to use a larger type, you may end up using 
more bandwidth and storage.

See [this example](https://github.com/open-telemetry/opentelemetry-java-examples/blob/main/metrics/src/main/java/io/opentelemetry/example/metrics/GaugeExample.java#L23) 
for additional details on how to use a gauge.


#### RatioGauge

The Dropwizard `RatioGauge` is simply a convenience abstraction on top of an asynchronous double gauge.
Users should be able to build their own callback methods that can do this division and return
a double value to the OpenTelemetry gauge.

#### CachedGauge

The Dropwizard `CachedGauge` is a convenience API that helps to prevent invoking expensive or 
time-consuming operations more frequently than desired. Users can build their own value caching 
mechanism to accomplish the same thing.

#### DerivativeGauge

This is a convenience API for building gauges that delegate to another gauge and transform
the value. It is assumed that users can build their own simple delegation implementation 
without much fuss.

#### JMXAttributeGauge

Dropwizard provides gauge type that can measure the value returned from a JMX MBean.
OpenTelemetry does not have a corresponding type, but one could be built and contributed
to the OpenTelemetry project. Help wanted!

If you are using the OpenTelemetry Java Instrumentation, you can leverage the 
[`jmx-metrics`](https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/instrumentation/jmx-metrics/javaagent)
instrumentation to do something very similar.

Another alternative approach is to poll the metrics from JMX with an external utility,
much like the [JMX Metric Gatherer](https://github.com/open-telemetry/opentelemetry-java-contrib/tree/main/jmx-metrics).

### histogram

Dropwizard histograms are uniquely interesting because they are primarily interested in generating
summary data combined with _quantiles_ (think median, p90, p99, etc). This is pretty different
from OpenTelemetry histograms, which primarily classify measurements into discretized ranges called buckets.
It's similar, but not really the same. The Dropwizard histograms also provide several "reservoir" strategies
that can be leveraged for various use cases.

There is currently no drop-in replacement for Dropwizard histograms. 

Users may be able to leverage the OpenTelemetry histogram without buckets in order to provide the summary data
(min/max/count/sum). If quantiles are needed, users will need to manage their own reservoir implementation
in order to compute these quantiles, and then represent each quantile as an individual
gauge. 

### meter

The Dropwizard meter tracks the rate that something happens, and tracks the rate as lifetime average (mean) 
and also over sliding 1, 5, and 15 minute windows. This is essentially 4 metrics tracked together in one bundle.

There is currently no drop-in replacement for the Dropwizard meter.

For now, users are encouraged to leverage the OpenTelemetry counter metric with delta temporality.
The number of counts between measurement times is easily converted to a rate, and a 1 minute measurement rate
can be aggregated to 5 and 15 minute windows.

### timer

The Dropwizard timer is described as a histogram that also uses a meter for rate. This is typically
used to measure how long a certain piece of code takes to run. The examples are synchronous, and it
is unclear whether or not `TimerContext` is thread-safe, so it is assumed that most uses are 
for synchronous, single-threaded operations.

Users are encouraged to use an OpenTelemetry histogram, but making note that the original 
Dropwizard histogram was tracking quantiles and not wall times. If fine-grained precision (say nanosecond instead
of millisecond) precision is needed, an exponential bucket histogram can be useful here. 
Exponential bucket histograms are not yet supported in the Splunk O11y Suite.

### IncrementalCounter

`signalfx-codahale` includes an extension class from `Counter` called `IncrementalCounter`. 
This metric serves to change the cumulative temporality of the codahale counter to 
delta temporality.

Users are encouraged to leverage the OpenTelemetry counter metric with delta temporality.

## A Note About Temporality

The OpenTelemetry specification contains a 
[detailed description of temporality](https://github.com/open-telemetry/opentelemetry-specification/blob/d855249b210ecd832889fc4ce91a4444f82e252a/specification/metrics/data-model.md#temporality).
In brief, there are two primary ways of reporting measured metric quantities. The first is 
accomplished by sending the actual current absolute measured value. This is called __cumulative temporality__.
The other approach is to report the difference between the current measured value and the current value. 
This is called __delta temporality__.

It is generally possible to convert cumulative measurements to delta temporality with only a single point 
lookback, if delivery order is guaranteed. Going from delta to cumulative is usually more challenging 
because it requires an absolute measurement and complete measurement replay.

Codahale generally uses cumulative temporality. OpenTelemetry is slightly more flexible and allows users to choose
temporality for each instrument type. Note: this is not the same as per-instrument temporality.

## SfUtil

`SfUtil.java` is merely a utility class that provides a single static method called `cumulativeCounter()`.
Its implementation is straightforward -- it makes a Dropwizard gauage whose generic type is bound
to `Long`. It also takes a callback, so therefore it is async. 

The migration here is straightforward -- users should use an OpenTelemetry async `LongCounter`
with cumulative temporality.

## SignalFxReporter

The `SignalFxReporter` is an instance of a `ScheduledReporter` and provides 
a way of periodically sending metrics to Splunk (formerly SignalFx). Users who are 
migrating to OpenTelemetry java and/or the instrumentation agent will get this core
functionality out of the box. Periodically measuring and sending metrics is fundamental
to OpenTelemetry SDKs.

If you use `SignalFxReporter` it is one of two implementations, both of these 
exist in the `com.signalfx.codahale.reporter` package but in different modules
(either `signalfx-codahale` or `signalfx-yammer`). These serve to operate with either
codehale or yammer, depending on the system being used.

One feature of the `SignalFxReporter` is the ability to pass in a predicate 
(either yammer `MetricPredicate` or a codahale `MetricFilter`) in order to 
select which metrics get sent. OpenTelemetry usually encourages using 
the otel collector to perform operations like this, but in the event that this 
filtering is absolutely required to be performed in the JVM, there are two recommendations.

If the Java SDK autoconfigure functionality is being used (preferred), then the 
`AutoConfigurationCustomizer.addMetricExporterCustomizer()` spi may be leveraged.
[An example of this is shown in this test](https://github.com/open-telemetry/opentelemetry-java/blob/976edfde504193f84d19936b97e2eb8d8cf060e2/sdk-extensions/autoconfigure/src/testFullConfig/java/io/opentelemetry/sdk/autoconfigure/provider/MetricCustomizer.java#L37).

If autoconfigure is not being leveraged, considerably more work is required to filter metrics.
You can construct a `MeterProvider` that is created with a `PeriodicMetricReader` that has
been created with an exporter that wraps a delegate exporter in order to filter.

If there is another feature of the `SignalFxReporter` that you are unsure how 
to accomplish with OpenTelemetry, please open an issue in `signalfx-java`.

## MetricMetadata

`MetricMetadata` is an interface that is used by the `SignalFxReporter` and 
`AggregateMetricSenderSessionWrapper` essentially to "tag" metrics with dimensions, potentially
overloading the SignalFx chosen default dimensions. 

In OpenTelemetry, attributes are a first-class component of metrics, and the APIs 
support attributes directly. Instead of implementing the `MetricMetadata` interface
or using `MetricMetadataImpl`, users can create their own `Attributes` instances 
to be used directly with the OpenTelemetry APIs.

## BasicJvmMetrics

This class sets up several codahale metrics that source telemetry from various MXBeans.
Users who rely on this functionality can substitute their usage of this class with the `jmx-metrics` 
[instrumentation](https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/instrumentation/jmx-metrics/library)
from OpenTelemetry Java Instrumentation.

A feature-parity configuration for the otel instrumentation is not provided.

## ResettingHistogram

`sigfnalfx-java` offers a custom type called `ResettingHistogram` which extends the codahale histogram.
This behaves just like the codahale histogram, except that it uses a copy of a codehale
reservoir class that clears itself after each snapshot.

There is no equivalent in OpenTelemetry, and users who need this functionality may consider
building their own implementation.

## ResettingTimer

Apparently because the codahale timer is basically a histogram, there is also a SignalFx specific
extension class called `ResettingTimer`. Much like the `ResettingHistogram`, it uses a reservoir that
clears itself when it is snapshotted.

There is no equivalent in OpenTelemetry, and users who need this functionality may consider
building their own implementation.

## SettableLongGauge, SettableDoubleGauge

These are both SignalFx specific implementation classes that provide synchronous measurements
to the otherwise normally async codahale gauge implementations.

The synchronous OpenTelemetry gauge should be a natural replacement.

# Conclusion

Most, but not all, codahale/yammer metrics have a replacement available with OpenTelemetry.
In the few corner cases where a direct migration cannot be performed, it is expected that some  
users may need to build their own bespoke functionality. Regardless, users are strongly 
encouraged to embrace OpenTelemetry as the future of their metrics collection and reporting system.

In the long term, this deprecation and removal of codahale metrics should be 
viewed as a positive step forward. Users are assured that Splunk and a vast community 
of users and vendors are all committed to the future of OpenTelemetry. 


 