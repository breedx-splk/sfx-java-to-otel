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

## Reporter

tbd 

# signalfx-yammer

`signalfx-yammer` is built with yammer metrics core 2.0.0, from Feb. 2012. 
This is ancient in modern terms. Fortunately, this library only provides a small number
of classes. Let's look at each of them.

## SignalFxReporter
## SfUtil
## MetricMetadata

# signalfx-codahale


