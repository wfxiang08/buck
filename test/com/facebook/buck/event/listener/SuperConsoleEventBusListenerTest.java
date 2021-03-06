/*
 * Copyright 2013-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.facebook.buck.event.listener;

import static com.facebook.buck.event.TestEventConfigerator.configureTestEventAtTime;
import static org.junit.Assert.assertEquals;

import com.facebook.buck.cli.InstallEvent;
import com.facebook.buck.event.BuckEventBus;
import com.facebook.buck.event.LogEvent;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.model.BuildTargetPattern;
import com.facebook.buck.parser.ParseEvent;
import com.facebook.buck.rules.BuildEvent;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleEvent;
import com.facebook.buck.rules.BuildRuleStatus;
import com.facebook.buck.rules.BuildRuleSuccess;
import com.facebook.buck.rules.BuildRuleType;
import com.facebook.buck.rules.CacheResult;
import com.facebook.buck.rules.DependencyGraph;
import com.facebook.buck.rules.FakeBuildRule;
import com.facebook.buck.step.FakeStep;
import com.facebook.buck.step.StepEvent;
import com.facebook.buck.testutil.TestConsole;
import com.facebook.buck.timing.Clock;
import com.facebook.buck.timing.IncrementingFakeClock;
import com.facebook.buck.util.environment.DefaultExecutionEnvironment;
import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

public class SuperConsoleEventBusListenerTest {
  @Test
  public void testSimpleBuild() {
    Clock fakeClock = new IncrementingFakeClock(TimeUnit.SECONDS.toNanos(1));
    Supplier<Long> threadIdSupplier = BuckEventBus.getDefaultThreadIdSupplier();
    BuckEventBus eventBus = new BuckEventBus(fakeClock, threadIdSupplier);
    TestConsole console = new TestConsole();

    BuildTarget fakeTarget = BuildTargetFactory.newInstance("//banana:stand");
    BuildTarget cachedTarget = BuildTargetFactory.newInstance("//chicken:dance");
    ImmutableList<BuildTarget> buildTargets = ImmutableList.of(fakeTarget, cachedTarget);
    FakeBuildRule fakeRule = new FakeBuildRule(BuildRuleType.GENRULE,
        fakeTarget,
        ImmutableSortedSet.<BuildRule>of(),
        ImmutableSet.<BuildTargetPattern>of());
    FakeBuildRule cachedRule = new FakeBuildRule(BuildRuleType.GENRULE,
        cachedTarget,
        ImmutableSortedSet.<BuildRule>of(),
        ImmutableSet.<BuildTargetPattern>of());

    SuperConsoleEventBusListener listener =
        new SuperConsoleEventBusListener(console, fakeClock, new DefaultExecutionEnvironment());
    eventBus.register(listener);

    eventBus.postDirectlyToAsyncEventBusForTesting(configureTestEventAtTime(
        BuildEvent.started(buildTargets),
        0L, TimeUnit.MILLISECONDS, /* threadId */ 0L));
    eventBus.postDirectlyToAsyncEventBusForTesting(configureTestEventAtTime(
        ParseEvent.started(buildTargets),
        0L, TimeUnit.MILLISECONDS, /* threadId */ 0L));

    validateConsole(console, listener, 300L, ImmutableList.of("[+] PARSING BUILD FILES...0.30s"));

    eventBus.postDirectlyToAsyncEventBusForTesting(
        configureTestEventAtTime(ParseEvent.finished(buildTargets,
                                                     Optional.<DependencyGraph>absent()),
        400L, TimeUnit.MILLISECONDS, /* threadId */ 0L));

    final String parsingLine = "[-] PARSING BUILD FILES...FINISHED 0.40s";

    validateConsole(console, listener, 540L, ImmutableList.of(parsingLine,
        "[+] BUILDING...0.14s"));

    eventBus.postDirectlyToAsyncEventBusForTesting(configureTestEventAtTime(
        BuildRuleEvent.started(fakeRule),
        600L, TimeUnit.MILLISECONDS, /* threadId */ 0L));


    validateConsole(console, listener, 800L, ImmutableList.of(parsingLine,
        "[+] BUILDING...0.40s",
        " |=> //banana:stand...  0.20s (checking local cache)"));

    FakeStep fakeStep = new FakeStep("doing_something", "working hard", 0);
    eventBus.postDirectlyToAsyncEventBusForTesting(configureTestEventAtTime(
        StepEvent.started(fakeStep, "working hard"),
          800L, TimeUnit.MILLISECONDS, /* threadId */ 0L));

    validateConsole(console, listener, 900L, ImmutableList.of(parsingLine,
        "[+] BUILDING...0.50s",
        " |=> //banana:stand...  0.30s (running doing_something[0.10s])"));

    eventBus.postDirectlyToAsyncEventBusForTesting(configureTestEventAtTime(
        StepEvent.finished(fakeStep, "working hard", 0),
        900L, TimeUnit.MILLISECONDS, /* threadId */ 0L));
    eventBus.postDirectlyToAsyncEventBusForTesting(configureTestEventAtTime(
        BuildRuleEvent.finished(
          fakeRule,
          BuildRuleStatus.SUCCESS,
          CacheResult.MISS,
          Optional.of(BuildRuleSuccess.Type.BUILT_LOCALLY)),
        1000L, TimeUnit.MILLISECONDS, /* threadId */ 0L));

    validateConsole(console, listener, 1000L, ImmutableList.of(parsingLine,
        "[+] BUILDING...0.60s",
        " |=> IDLE"));

    eventBus.postDirectlyToAsyncEventBusForTesting(configureTestEventAtTime(
        BuildRuleEvent.started(cachedRule),
        1010L, TimeUnit.MILLISECONDS, /* threadId */ 2L));

    validateConsole(console, listener, 1100L, ImmutableList.of(parsingLine,
        "[+] BUILDING...0.70s",
        " |=> IDLE",
        " |=> //chicken:dance...  0.09s (checking local cache)"));

    eventBus.postDirectlyToAsyncEventBusForTesting(configureTestEventAtTime(
        BuildRuleEvent.finished(
          cachedRule,
          BuildRuleStatus.SUCCESS,
          CacheResult.MISS,
          Optional.of(BuildRuleSuccess.Type.BUILT_LOCALLY)),
        1120L, TimeUnit.MILLISECONDS, /* threadId */ 2L));

    eventBus.postDirectlyToAsyncEventBusForTesting(configureTestEventAtTime(
        BuildEvent.finished(buildTargets, 0),
        1234L, TimeUnit.MILLISECONDS, /* threadId */ 0L));

    final String buildingLine = "[-] BUILDING...FINISHED 0.83s";

    validateConsole(console, listener, 1300L, ImmutableList.of(parsingLine, buildingLine));

    eventBus.postDirectlyToAsyncEventBusForTesting(configureTestEventAtTime(
        LogEvent.severe("I've made a huge mistake."),
        1500L, TimeUnit.MILLISECONDS, /* threadId */ 0L));

    validateConsole(console, listener, 1600L, ImmutableList.of(parsingLine,
        buildingLine,
        "Log:",
        "I've made a huge mistake."));

    eventBus.postDirectlyToAsyncEventBusForTesting(configureTestEventAtTime(
        InstallEvent.started(fakeTarget),
        2500L, TimeUnit.MILLISECONDS, /* threadId */ 0L));

    validateConsole(console, listener, 3000L, ImmutableList.of(parsingLine,
        buildingLine,
        "[+] INSTALLING...0.50s",
        "Log:",
        "I've made a huge mistake."));

    eventBus.postDirectlyToAsyncEventBusForTesting(configureTestEventAtTime(
        InstallEvent.finished(fakeTarget, true),
        4000L, TimeUnit.MILLISECONDS, /* threadId */ 0L));

    validateConsole(console, listener, 5000L, ImmutableList.of(parsingLine,
        buildingLine,
        "[-] INSTALLING...FINISHED 1.50s",
        "Log:",
        "I've made a huge mistake."));

    listener.render();
    String beforeStderrWrite = console.getTextWrittenToStdErr();
    console.getStdErr().print("ROFLCOPTER");
    listener.render();
    assertEquals("After stderr is written to by someone other than SuperConsole, rendering " +
        "should be a noop.",
        beforeStderrWrite + "ROFLCOPTER", console.getTextWrittenToStdErr());
  }

  private void validateConsole(TestConsole console,
      SuperConsoleEventBusListener listener,
      long timeMs,
      ImmutableList<String> lines) {
    assertEquals("", console.getTextWrittenToStdOut());
    assertEquals("", console.getTextWrittenToStdErr());
    assertEquals(lines, listener.createRenderLinesAtTime(timeMs));
  }
}
