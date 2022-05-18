/**
 * Copyright (c) 2019 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at:
 *
 *     https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.jkube.kit.common.util;

import java.util.Arrays;
import java.util.Collection;
import java.util.function.BooleanSupplier;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Parameterized.class)
public class OpenshiftHelperStatusTest {
  @Parameterized.Parameters(name = "{0} - {2}")
  public static Collection<Object[]> data() {
    return Arrays.asList(
        // input, method, expectedValue
        new Object[] { "IsCancelledTrue", (BooleanSupplier)(() -> OpenshiftHelper.isCancelled("Cancelled")), true},
        new Object[] { "IsCancelledFalse", (BooleanSupplier)(() -> OpenshiftHelper.isCancelled("not Cancelled")), false},
        new Object[] { "IsFailedTrueWithFail", (BooleanSupplier)(() -> OpenshiftHelper.isFailed("Fail")), true},
        new Object[] { "IsFailedTrueWithError", (BooleanSupplier)(() -> OpenshiftHelper.isFailed("Error")), true},
        new Object[] { "IsFailedFalse", (BooleanSupplier)(() -> OpenshiftHelper.isFailed(null)), false},
        new Object[] { "IsCompletedTrue", (BooleanSupplier)(() -> OpenshiftHelper.isCompleted("Complete")), true},
        new Object[] { "IsCompletedFalse", (BooleanSupplier)(() -> OpenshiftHelper.isCompleted("not Complete")), false},
        new Object[] { "IsFinishedComplete", (BooleanSupplier)(() -> OpenshiftHelper.isFinished("Complete")), true},
        new Object[] { "IsFinishedFailed", (BooleanSupplier)(() -> OpenshiftHelper.isFinished("Error")), true},
        new Object[] { "IsFinishedCancelled", (BooleanSupplier)(() -> OpenshiftHelper.isFinished("Cancelled")), true},
        new Object[] { "IsFinishedFalse", (BooleanSupplier)(() -> OpenshiftHelper.isFinished("not Complete")), false}
    );
  }

  @Parameterized.Parameter
  public String input;

  @Parameterized.Parameter (1)
  public BooleanSupplier statusSupplier;

  @Parameterized.Parameter (2)
  public boolean expectedValue;

  @Test
  public void testOpenShiftStatus() {
    assertThat(statusSupplier.getAsBoolean()).isEqualTo(expectedValue);
  }
}
